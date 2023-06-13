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
import net.consensys.shomei.observer.TrieLogObserver.TrieLogIdentifier;
import net.consensys.shomei.storage.worldstate.WorldStateStorage;
import net.consensys.shomei.trielog.TrieLogLayer;
import net.consensys.shomei.trielog.TrieLogLayerConverter;
import net.consensys.shomei.worldview.ZkEvmWorldState;

import java.io.Closeable;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

import com.google.common.annotations.VisibleForTesting;
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
    this.trieLogManager = storageProvider.getTrieLogManager();
    this.traceManager = storageProvider.getTraceManager();
    this.headWorldStateStorage = storageProvider.getWorldStateStorage();
    this.headWorldState = fromWorldStateStorage(headWorldStateStorage);
    this.trieLogLayerConverter = new TrieLogLayerConverter(headWorldStateStorage);
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

  private ZkEvmWorldState fromWorldStateStorage(WorldStateStorage storage) {
    return new ZkEvmWorldState(storage, traceManager::saveTrace);
  }

  public void importBlock(
      final TrieLogIdentifier trieLogIdentifier, final boolean shouldGenerateTrace)
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
      if (shouldGenerateTrace) {
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
