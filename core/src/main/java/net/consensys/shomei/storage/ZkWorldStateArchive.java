/*
 * Copyright Consensys Software Inc., 2025
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
import net.consensys.shomei.storage.worldstate.LayeredWorldStateStorage;
import net.consensys.shomei.storage.worldstate.WorldStateStorage;
import net.consensys.shomei.trie.trace.Trace;
import net.consensys.shomei.trielog.TrieLogLayer;
import net.consensys.shomei.trielog.TrieLogLayerConverter;
import net.consensys.shomei.worldview.ZkEvmWorldState;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
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

  public Optional<ZkEvmWorldState> getCachedWorldState(Bytes32 blockHash) {
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
  public void applyTrieLog(
      final long newBlockNumber, final boolean generateTrace, final TrieLogLayer trieLogLayer) {
    headWorldState.getAccumulator().rollForward(trieLogLayer);
    headWorldState.commit(newBlockNumber, trieLogLayer.getBlockHash(), generateTrace);
  }

  /**
   * Result of generating a virtual trace.
   */
  public record VirtualTraceResult(List<List<Trace>> traces, Bytes32 zkEndStateRootHash) {}

  /**
   * Returns a {@link ZkEvmWorldState} for the given block number, loading it via rollback if it is
   * not cached. Cache hits are returned immediately; uncached blocks are reconstructed by rolling
   * back from the nearest available forward snapshot.
   *
   * @param blockNumber the target block number
   * @return world state at {@code blockNumber}
   * @throws MissingTrieLogException if any required trie log is absent during rollback
   * @throws IllegalStateException if {@code blockNumber} exceeds the current head
   */
  public ZkEvmWorldState getOrLoadWorldState(final long blockNumber)
      throws MissingTrieLogException {
    // 1. Cache hit
    final Optional<ZkEvmWorldState> cached = getCachedWorldState(blockNumber);
    if (cached.isPresent()) {
      return cached.get();
    }
    // 2. Head state
    if (blockNumber == getCurrentBlockNumber()) {
      return headWorldState;
    }
    // 3. Reconstruct via rollback from the nearest forward snapshot
    return rollBackToBlock(blockNumber);
  }

  /**
   * Generate a virtual trace from a trielog without persisting state changes.
   * This is used for simulating transactions on a virtual block.
   *
   * @param parentBlockNumber the parent block number on which to base the virtual state
   * @param trieLogLayer the trielog to apply
   * @return the generated trace and resulting state root hash
   * @throws MissingTrieLogException if the worldstate for the parent block cannot be reconstructed
   */
  public VirtualTraceResult generateVirtualTrace(
      final long parentBlockNumber,
      final TrieLogLayer trieLogLayer) throws MissingTrieLogException {

    // Get or reconstruct the worldstate for the parent block
    final WorldStateStorage parentStorage =
        getOrLoadWorldState(parentBlockNumber).getZkEvmWorldStateStorage();

    return generateVirtualTrace(parentBlockNumber, trieLogLayer, parentStorage);
  }

  /**
   * Generate a virtual trace from a trielog without persisting state changes.
   * This is used for simulating transactions on a virtual block.  Overload
   * using a passed worldstate storage to prevent double lookup / rollback.
   *
   * @param parentBlockNumber the parent block number on which to base the virtual state
   * @param trieLogLayer the trielog to apply
   * @param parentStorage parent worlstate storage to use, useful to prevent double rolling of state
   * @return the generated trace and resulting state root hash
   */
  public VirtualTraceResult generateVirtualTrace(
      final long parentBlockNumber,
      final TrieLogLayer trieLogLayer,
      final WorldStateStorage parentStorage) {

    // Create a layered storage that overlays in-memory writes on top of the parent snapshot
    // This ensures we don't modify the parent state during simulation
    try (final WorldStateStorage virtualStorage = new LayeredWorldStateStorage(parentStorage)) {
      // Use an in-memory trace manager that won't persist to disk
      final TraceManager ephemeralTraceManager = new InMemoryStorageProvider().getTraceManager();

      final ZkEvmWorldState virtualWorldState = new ZkEvmWorldState(virtualStorage, ephemeralTraceManager);

      // Apply the trielog and generate trace for the virtual block
      // Use the virtual block number from the trielog (parentBlockNumber + 1)
      final long virtualBlockNumber = trieLogLayer.getBlockNumber();

      virtualWorldState.getAccumulator().rollForward(trieLogLayer);
      virtualWorldState.commit(virtualBlockNumber, trieLogLayer.getBlockHash(), true);

      // Retrieve the trace for the virtual block
      final Optional<Bytes> traceBytes = ephemeralTraceManager.getTrace(virtualBlockNumber);

      if (traceBytes.isEmpty()) {
        throw new IllegalStateException(
            "Failed to generate trace for virtual block " + virtualBlockNumber +
            " on parent " + parentBlockNumber);
      }

      // Get the resulting state root hash after applying the virtual block
      final Bytes32 zkEndStateRootHash = ephemeralTraceManager
          .getZkStateRootHash(virtualBlockNumber)
          .orElseThrow(() -> new IllegalStateException(
              "Failed to get state root hash for virtual block " + virtualBlockNumber));

      return new VirtualTraceResult(
          List.of(Trace.deserialize(RLP.input(traceBytes.get()))),
          zkEndStateRootHash);
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to generate virtual trace for parent block " + parentBlockNumber, e);
    }
  }

  /**
   * Returns an ephemeral {@link ZkEvmWorldState} representing the state at {@code
   * targetBlockNumber} by rolling back from the nearest available forward snapshot.
   *
   * <p>The rollback is performed on a {@link LayeredWorldStateStorage} overlay so the head state
   * and all cached snapshots remain unmodified.
   *
   * <p>The caller is responsible for not holding a reference to the returned state past the point
   * where its parent snapshot may be evicted from the LRU cache.
   *
   * @param targetBlockNumber the block number to roll back to
   * @return ephemeral world state at {@code targetBlockNumber}
   * @throws MissingTrieLogException if any required trie log is absent
   * @throws IllegalStateException if {@code targetBlockNumber} &ge; the current head block
   */
  public ZkEvmWorldState rollBackToBlock(final long targetBlockNumber)
      throws MissingTrieLogException {

    // 1. Direct cache hit: return an ephemeral wrapper around the cached snapshot
    final Optional<ZkEvmWorldState> cached = getCachedWorldState(targetBlockNumber);
    if (cached.isPresent()) {
      return cached.get();
    }

    // 2. Locate the nearest forward snapshot (smallest cached block > targetBlockNumber).
    //    Fall back to a live snapshot of the head state if nothing is cached ahead.
    final WorldStateStorage baseStorage;
    final long startBlockNumber;

    final TrieLogIdentifier searchKey =
        new TrieLogIdentifier(targetBlockNumber, Bytes32.ZERO);
    final Map.Entry<TrieLogIdentifier, WorldStateStorage> forwardEntry =
        cachedWorldStates.higherEntry(searchKey);

    if (forwardEntry != null) {
      baseStorage = forwardEntry.getValue(); // already a read-only snapshot
      startBlockNumber = forwardEntry.getKey().blockNumber();
    } else if (headWorldState.getBlockNumber() > targetBlockNumber) {
      // No cached snapshot ahead of target; take a live snapshot of the head state.
      baseStorage = headWorldStateStorage.snapshot();
      startBlockNumber = headWorldState.getBlockNumber();
    } else {
      throw new IllegalStateException(
          "Cannot roll back to block "
              + targetBlockNumber
              + ": target is >= current head "
              + headWorldState.getBlockNumber());
    }

    // 3. Pre-load raw trie logs and extract block hashes.
    //    Blocks startBlockNumber → targetBlockNumber+1: raw bytes for interleaved decode+rollback.
    //    Block targetBlockNumber: only its hash is needed for the final commit.
    final Map<Long, Bytes> rawLogs = new HashMap<>();
    final Map<Long, Bytes32> blockHashes = new HashMap<>();
    for (long blockNum = startBlockNumber; blockNum >= targetBlockNumber; blockNum--) {
      final Optional<Bytes> rawLog = trieLogManager.getTrieLog(blockNum);
      if (rawLog.isEmpty()) {
        if (blockNum > targetBlockNumber) {
          throw new MissingTrieLogException(blockNum);
        }
        blockHashes.put(blockNum, Bytes32.ZERO);
        continue;
      }
      rawLogs.put(blockNum, rawLog.get());
      final RLPInput hashInput = RLP.input(rawLog.get());
      hashInput.enterList();
      blockHashes.put(blockNum, hashInput.readBytes32());
    }

    // 4. Create an ephemeral layered world state on top of the base snapshot.
    final LayeredWorldStateStorage layeredStorage = new LayeredWorldStateStorage(baseStorage);
    final TraceManager ephemeralTraceManager = new InMemoryStorageProvider().getTraceManager();
    final ZkEvmWorldState rollingState =
        new ZkEvmWorldState(layeredStorage, ephemeralTraceManager);

    // 5. Interleaved decode + rollback: process one block at a time so that each decode
    //    sees the correct rolling state (representing the block being rolled back from).
    //    decodeTrieLogForRollback reads the rolling-state account for ZK storage root and
    //    code hash without validating nonce/balance against the wrong snapshot.
    for (long blockNum = startBlockNumber; blockNum > targetBlockNumber; blockNum--) {
      final TrieLogLayer layer =
          trieLogLayerConverter.decodeTrieLogForRollback(
              RLP.input(rawLogs.get(blockNum)),
              rollingState.getZkEvmWorldStateStorage());
      rollingState.getAccumulator().rollBack(layer);
      final long priorBlockNumber = blockNum - 1;
      rollingState.commit(
          priorBlockNumber, blockHashes.getOrDefault(priorBlockNumber, Bytes32.ZERO), false);
    }

    LOG.atDebug()
        .setMessage("Rolled back from block {} to block {} in {} steps")
        .addArgument(startBlockNumber)
        .addArgument(targetBlockNumber)
        .addArgument(startBlockNumber - targetBlockNumber)
        .log();

    return rollingState;
  }

  public ZkEvmWorldState getHeadWorldState() {
    return headWorldState;
  }

  public long getCurrentBlockNumber() {
    return headWorldState.getBlockNumber();
  }

  public Bytes32 getCurrentBlockHash() {
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
