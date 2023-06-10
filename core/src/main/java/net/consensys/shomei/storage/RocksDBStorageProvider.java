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

import static net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentIdentifier.SegmentNames.ZK_LEAF_INDEX;
import static net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentIdentifier.SegmentNames.ZK_TRACE;
import static net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentIdentifier.SegmentNames.ZK_TRIE_LOG;
import static net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentIdentifier.SegmentNames.ZK_TRIE_NODE;

import net.consensys.shomei.services.storage.api.KeyValueStorage;
import net.consensys.shomei.services.storage.api.SnappableKeyValueStorage;
import net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentedStorage;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfiguration;
import net.consensys.shomei.storage.TrieLogManager.TrieLogManagerImpl;
import net.consensys.shomei.storage.worldstate.PersistedWorldStateStorage;
import net.consensys.shomei.storage.worldstate.WorldStateStorage;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.google.common.annotations.VisibleForTesting;

public class RocksDBStorageProvider implements StorageProvider {
  private final RocksDBSegmentedStorage segmentedStorage;
  private final AtomicReference<WorldStateStorage> worldStateStorage = new AtomicReference<>();
  private final AtomicReference<TraceManager> traceManager = new AtomicReference<>();
  private final AtomicReference<TrieLogManager> trieLogManager = new AtomicReference<>();

  public RocksDBStorageProvider(RocksDBConfiguration rocksDBconfig) {
    this.segmentedStorage = new RocksDBSegmentedStorage(rocksDBconfig);
  }

  @Override
  public WorldStateStorage getWorldStateStorage() {
    return initializeAndGet(
        worldStateStorage,
        () ->
            new PersistedWorldStateStorage(
                getFlatLeafStorage(), getTrieNodeStorage(), getTraceManager()));
  }

  @Override
  public TraceManager getTraceManager() {
    return initializeAndGet(
        traceManager, () -> new TraceManager.TraceManagerImpl(getTraceStorage()));
  }

  @Override
  public TrieLogManager getTrieLogManager() {
    return initializeAndGet(trieLogManager, () -> new TrieLogManagerImpl(getTrieLogStorage()));
  }

  @VisibleForTesting
  SnappableKeyValueStorage getTrieNodeStorage() {
    return segmentedStorage.getKeyValueStorageForSegment(ZK_TRIE_NODE.getSegmentIdentifier());
  }

  @VisibleForTesting
  SnappableKeyValueStorage getFlatLeafStorage() {
    return segmentedStorage.getKeyValueStorageForSegment(ZK_LEAF_INDEX.getSegmentIdentifier());
  }

  @VisibleForTesting
  KeyValueStorage getTrieLogStorage() {
    return segmentedStorage.getKeyValueStorageForSegment(ZK_TRIE_LOG.getSegmentIdentifier());
  }

  @VisibleForTesting
  KeyValueStorage getTraceStorage() {
    return segmentedStorage.getKeyValueStorageForSegment(ZK_TRACE.getSegmentIdentifier());
  }

  private <T> T initializeAndGet(AtomicReference<T> ref, Supplier<T> provider) {
    if (ref.get() == null) {
      ref.compareAndExchange(null, provider.get());
    }
    return ref.get();
  }
}
