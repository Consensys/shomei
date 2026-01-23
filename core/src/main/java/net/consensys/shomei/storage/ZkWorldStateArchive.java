/*
 * Copyright ConsenSys Software Inc., 2023
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package net.consensys.shomei.storage;

import net.consensys.shomei.exception.MissingTrieLogException;
import net.consensys.shomei.metrics.MetricsService;
import net.consensys.shomei.observer.TrieLogObserver.TrieLogIdentifier;
import net.consensys.shomei.storage.worldstate.WorldStateStorage;
import net.consensys.shomei.trie.trace.Trace;
import net.consensys.shomei.trielog.TrieLogLayer;
import net.consensys.shomei.trielog.TrieLogLayerConverter;
import net.consensys.shomei.worldview.ZkEvmWorldState;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZkWorldStateArchive implements Closeable {

  static final int MAX_CACHED_WORLDSTATES = 128;
  private static final Logger LOG = LoggerFactory.getLogger(ZkWorldStateArchive.class);

  private final TrieLogManager trieLogManager;
  private final TraceManager traceManager;
  private final WorldStateStorage headWorldStateStorage;
  private final ZkEvmWorldState headWorldState;
  private final TrieLogLayerConverter trieLogLayerConverter;
  private final ConcurrentSkipListMap<TrieLogIdentifier, WorldStateStorage> cachedWorldStates =
      new ConcurrentSkipListMap<>(Comparator.comparing(TrieLogIdentifier::blockNumber));

  public ZkWorldStateArchive(final StorageProvider storageProvider) {
    this(storageProvider, false);
  }

  public ZkWorldStateArchive(
      final StorageProvider storageProvider, final boolean enableFinalizedBlockLimit) {
    this(
        storageProvider.getTrieLogManager(),
        storageProvider.getTraceManager(),
        storageProvider.getWorldStateStorage(),
        enableFinalizedBlockLimit,
        MetricsService.MetricsServiceProvider.getMetricsService());
  }

  public ZkWorldStateArchive(
      final TrieLogManager trieLogManager,
      final TraceManager traceManager,
      final WorldStateStorage headWorldStateStorage,
      final boolean enableFinalizedBlockLimit,
      final MetricsService metricsService) {
    this.trieLogManager = trieLogManager;
    this.traceManager = traceManager;
    this.headWorldStateStorage = headWorldStateStorage;
    this.headWorldState = fromWorldStateStorage(headWorldStateStorage);
    this.trieLogLayerConverter = new TrieLogLayerConverter(headWorldStateStorage);
    if (enableFinalizedBlockLimit) {
      cacheSnapshot(
          new TrieLogIdentifier(headWorldState.getBlockNumber(), headWorldState.getBlockHash()),
          headWorldStateStorage);
      LOG.debug("Worldstate archive created snapshot for " + headWorldState.getBlockNumber());
    }
    setupHeadMetrics(metricsService);
  }

  public Optional<ZkEvmWorldState> getCachedWorldState(Hash blockHash) {
    return cachedWorldStates.entrySet().stream()
        .filter(entry -> entry.getKey().blockHash().equals(blockHash))
        .map(Map.Entry::getValue)
        .map(this::fromWorldStateStorage)
        .findFirst();
  }

  public Optional<ZkEvmWorldState> getCachedWorldState(Long blockNumber) {
    return cachedWorldStates.entrySet().stream()
        .filter(entry -> entry.getKey().blockNumber().equals(blockNumber))
        .map(Map.Entry::getValue)
        .map(this::fromWorldStateStorage)
        .findFirst();
  }

  @VisibleForTesting
  WorldStateStorage getHeadWorldStateStorage() {
    return headWorldStateStorage;
  }

  @VisibleForTesting
  Map<TrieLogIdentifier, WorldStateStorage> getCachedWorldStates() {
    return cachedWorldStates;
  }

  private void setupHeadMetrics(MetricsService metricsService) {
    metricsService.addGauge(
        "shomei_blockchain_head",
        "current chain head block number which corresponds to shomei state",
        Collections.emptyList(),
        this::getCurrentBlockNumber);
  }

  private ZkEvmWorldState fromWorldStateStorage(WorldStateStorage storage) {
    return new ZkEvmWorldState(storage, traceManager);
  }

  public void importBlock(
      final TrieLogIdentifier trieLogIdentifier,
      final boolean shouldGenerateTrace,
      final boolean isSnapshotGenerationNeeded)
      throws MissingTrieLogException {
    // import block, optionally cache a snapshot and generate trace if not too far behind head
    Optional<TrieLogLayer> trieLog =
        trieLogManager
            .getTrieLog(trieLogIdentifier.blockNumber())
            .map(RLP::input)
            .map(trieLogLayerConverter::decodeTrieLog);
    if (trieLog.isPresent()) {
      applyTrieLog(trieLogIdentifier.blockNumber(), shouldGenerateTrace, trieLog.get());

      // if we generate a trace, cache a snapshot also:
      if (isSnapshotGenerationNeeded) {
        cacheSnapshot(trieLogIdentifier, headWorldStateStorage);
      }

    } else {
      throw new MissingTrieLogException(trieLogIdentifier.blockNumber());
    }
  }

  void cacheSnapshot(TrieLogIdentifier trieLogIdentifier, WorldStateStorage storage) {
    // create and cache the snapshot
    this.cachedWorldStates.put(trieLogIdentifier, storage.snapshot());
    // trim the cache if necessary
    while (cachedWorldStates.size() > MAX_CACHED_WORLDSTATES) {
      var keyToDrop = cachedWorldStates.firstKey();
      var storageToDrop = cachedWorldStates.get(keyToDrop);
      LOG.atTrace().setMessage("Dropping {}").addArgument(keyToDrop.toLogString()).log();
      cachedWorldStates.remove(keyToDrop);
      try {
        storageToDrop.close();
      } catch (Exception e) {
        LOG.atError()
            .setMessage("Error closing storage for dropped worldstate {}")
            .addArgument(keyToDrop.toLogString())
            .setCause(e)
            .log();
      }
    }
  }

  @VisibleForTesting
  void applyTrieLog(
      final long newBlockNumber, final boolean generateTrace, final TrieLogLayer trieLogLayer) {
    headWorldState.getAccumulator().rollForward(trieLogLayer);
    headWorldState.commit(newBlockNumber, trieLogLayer.getBlockHash(), generateTrace);
  }

  /**
   * Generate a virtual trace from a trielog without persisting state changes.
   * This is used for simulating transactions on a virtual block.
   *
   * @param parentBlockNumber the parent block number on which to base the virtual state
   * @param trieLogLayer the trielog to apply
   * @return the generated trace
   * @throws IllegalStateException if the worldstate for the parent block is not cached
   */
  public List<List<Trace>> generateVirtualTrace(
      final long parentBlockNumber, final TrieLogLayer trieLogLayer) {
    // Get the cached worldstate for the parent block
    final WorldStateStorage parentStorage = cachedWorldStates.entrySet().stream()
        .filter(entry -> entry.getKey().blockNumber().equals(parentBlockNumber))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(
            "Worldstate for parent block " + parentBlockNumber + " is not cached"));

    // Create a temporary snapshot from the parent worldstate and ensure it's cleaned up
    try (final WorldStateStorage virtualStorage = parentStorage.snapshot()) {
      // Use an in-memory trace manager that won't persist to disk
      final TraceManager ephemeralTraceManager = new InMemoryStorageProvider().getTraceManager();
      final ZkEvmWorldState virtualWorldState = new ZkEvmWorldState(virtualStorage, ephemeralTraceManager);

      // Apply the trielog and generate trace ephemerally
      // Use a temporary block number that won't conflict with real blocks
      final long ephemeralBlockNumber = Long.MIN_VALUE;
      virtualWorldState.getAccumulator().rollForward(trieLogLayer);
      virtualWorldState.commit(ephemeralBlockNumber, trieLogLayer.getBlockHash(), true);

      // Retrieve the ephemeral trace
      final Optional<Bytes> traceBytes = ephemeralTraceManager.getTrace(ephemeralBlockNumber);
      if (traceBytes.isEmpty()) {
        throw new IllegalStateException(
            "Failed to generate trace for virtual block on parent " + parentBlockNumber);
      }

      return List.of(Trace.deserialize(RLP.input(traceBytes.get())));
    } catch (Exception e) {
      if (e instanceof IllegalStateException) {
        throw (IllegalStateException) e;
      }
      throw new IllegalStateException(
          "Failed to generate virtual trace for parent block " + parentBlockNumber, e);
    }
  }

  public ZkEvmWorldState getHeadWorldState() {
    return headWorldState;
  }

  public long getCurrentBlockNumber() {
    return headWorldState.getBlockNumber();
  }

  public Hash getCurrentBlockHash() {
    return headWorldState.getBlockHash();
  }

  public TrieLogManager getTrieLogManager() {
    return trieLogManager;
  }

  public TraceManager getTraceManager() {
    return traceManager;
  }

  public TrieLogLayerConverter getTrieLogLayerConverter() {
    return trieLogLayerConverter;
  }

  @Override
  public void close() throws IOException {
    // close all storages
    cachedWorldStates.forEach(
        (key, value) -> {
          try {
            value.close();
          } catch (Exception e) {
            LOG.atError()
                .setMessage("Error closing storage for worldstate {}")
                .addArgument(key.toLogString())
                .log();
            LOG.atTrace().setCause(e).log();
          }
        });
  }
}
