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

package net.consensys.shomei.services.storage.rocksdb;

import static java.nio.charset.StandardCharsets.UTF_8;
import static net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentIdentifier.SegmentNames.ZK_LEAF_INDEX;
import static net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentIdentifier.SegmentNames.ZK_TRACE;
import static net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentIdentifier.SegmentNames.ZK_TRIE_NODE;
import static net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration.DEFAULT_ROCKSDB_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.services.storage.api.AtomicCompositeTransaction;
import net.consensys.shomei.services.storage.api.KeyValueStorageTransaction;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfiguration;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test for RocksDB atomic transaction functionality.
 * Tests the actual RocksDB flat transaction implementation.
 */
class RocksDBAtomicTransactionIntegrationTest {

  @TempDir Path tempDir;

  final RocksDBKeyValueStorageFactory factory =
      new RocksDBKeyValueStorageFactory(DEFAULT_ROCKSDB_CONFIG);
  RocksDBConfiguration rocksDBConfiguration;
  RocksDBSegmentedStorage segmentedStorage;

  final byte[] key1 = "key1".getBytes(UTF_8);
  final byte[] value1 = "value1".getBytes(UTF_8);
  final byte[] key2 = "key2".getBytes(UTF_8);
  final byte[] value2 = "value2".getBytes(UTF_8);

  @BeforeEach
  public void setup() throws IOException {
    this.rocksDBConfiguration =
        new RocksDBConfigurationBuilder().databaseDir(tempDir).build();
    segmentedStorage = new RocksDBSegmentedStorage(rocksDBConfiguration);
  }

  @AfterEach
  public void cleanup() throws Exception {
    if (segmentedStorage != null) {
      segmentedStorage.close();
    }
  }

  @Test
  void shouldCreateAtomicTransactionAcrossMultipleSegments() {
    // Given - Get atomic transaction
    RocksDBFlatTransaction atomicTx = segmentedStorage.getRocksDBFlatTransaction();

    // When - create wrapped transactions for different segments
    var leafSegment = ZK_LEAF_INDEX.getSegmentIdentifier();
    var trieSegment = ZK_TRIE_NODE.getSegmentIdentifier();
    var traceSegment = ZK_TRACE.getSegmentIdentifier();

    KeyValueStorageTransaction leafTx = atomicTx.wrapAsSegmentTransaction(leafSegment);
    KeyValueStorageTransaction trieTx = atomicTx.wrapAsSegmentTransaction(trieSegment);
    KeyValueStorageTransaction traceTx = atomicTx.wrapAsSegmentTransaction(traceSegment);

    // Then - all transactions should be created successfully
    assertThat(leafTx).isNotNull();
    assertThat(trieTx).isNotNull();
    assertThat(traceTx).isNotNull();

    atomicTx.close();
  }

  @Test
  void shouldCommitDataAtomicallyAcrossSegments() {
    // Given - Get storage instances and atomic transaction
    var leafSegment = ZK_LEAF_INDEX.getSegmentIdentifier();
    var trieSegment = ZK_TRIE_NODE.getSegmentIdentifier();
    var leafStorage = segmentedStorage.getKeyValueStorageForSegment(leafSegment);
    var trieStorage = segmentedStorage.getKeyValueStorageForSegment(trieSegment);

    RocksDBFlatTransaction atomicTx = segmentedStorage.getRocksDBFlatTransaction();
    KeyValueStorageTransaction leafTx = atomicTx.wrapAsSegmentTransaction(leafSegment);
    KeyValueStorageTransaction trieTx = atomicTx.wrapAsSegmentTransaction(trieSegment);

    // When - write to multiple segments
    leafTx.put(key1, value1);
    trieTx.put(key2, value2);

    // Wrapped transactions should not commit the underlying transaction
    leafTx.commit();
    trieTx.commit();

    // When - commit the atomic transaction
    atomicTx.commit();

    // Then - data should be visible in both segments
    assertThat(leafStorage.get(key1)).contains(value1);
    assertThat(trieStorage.get(key2)).contains(value2);
  }

  @Test
  void shouldRollbackDataAtomicallyAcrossSegments() {
    // Given - Get atomic transaction and segment transactions
    RocksDBFlatTransaction atomicTx = segmentedStorage.getRocksDBFlatTransaction();

    var leafSegment = ZK_LEAF_INDEX.getSegmentIdentifier();
    var trieSegment = ZK_TRIE_NODE.getSegmentIdentifier();

    KeyValueStorageTransaction leafTx = atomicTx.wrapAsSegmentTransaction(leafSegment);
    KeyValueStorageTransaction trieTx = atomicTx.wrapAsSegmentTransaction(trieSegment);

    // When - write to multiple segments
    leafTx.put(key1, value1);
    trieTx.put(key2, value2);

    // When - rollback the atomic transaction
    atomicTx.rollback();

    // Then - data should not be visible in any segment
    var leafStorage = segmentedStorage.getKeyValueStorageForSegment(leafSegment);
    var trieStorage = segmentedStorage.getKeyValueStorageForSegment(trieSegment);
    assertThat(leafStorage.get(key1)).isEmpty();
    assertThat(trieStorage.get(key2)).isEmpty();
  }

  @Test
  void shouldReadUncommittedDataWithinTransaction() {
    // Given - Get atomic transaction and segment transactions
    RocksDBFlatTransaction atomicTx = segmentedStorage.getRocksDBFlatTransaction();

    var leafSegment = ZK_LEAF_INDEX.getSegmentIdentifier();
    KeyValueStorageTransaction leafTx = atomicTx.wrapAsSegmentTransaction(leafSegment);

    // When - write data within transaction
    leafTx.put(key1, value1);

    // Then - should be able to read uncommitted data within the same transaction
    Optional<byte[]> result = leafTx.get(key1);
    assertThat(result).contains(value1);

    atomicTx.close();
  }

  @Test
  void shouldHandleMultipleOperationsOnSameSegment() {
    // Given - Get atomic transaction and segment transaction
    RocksDBFlatTransaction atomicTx = segmentedStorage.getRocksDBFlatTransaction();

    var leafSegment = ZK_LEAF_INDEX.getSegmentIdentifier();
    KeyValueStorageTransaction leafTx = atomicTx.wrapAsSegmentTransaction(leafSegment);

    // When - perform multiple operations
    leafTx.put(key1, value1);
    leafTx.put(key2, value2);
    Optional<byte[]> result1 = leafTx.get(key1);
    leafTx.remove(key2);
    Optional<byte[]> result2 = leafTx.get(key2);

    // Then - operations should work correctly within transaction
    assertThat(result1).contains(value1);
    assertThat(result2).isEmpty(); // removed within transaction

    atomicTx.commit();

    // Verify final state after commit
    var leafStorage = segmentedStorage.getKeyValueStorageForSegment(leafSegment);
    assertThat(leafStorage.get(key1)).contains(value1);
    assertThat(leafStorage.get(key2)).isEmpty();
  }

  @Test
  void shouldImplementAtomicCompositeTransactionInterface() {
    // Given - Get atomic transaction as interface
    AtomicCompositeTransaction atomicTx = segmentedStorage.getRocksDBFlatTransaction();

    // When - use through interface
    var leafSegment = ZK_LEAF_INDEX.getSegmentIdentifier();
    KeyValueStorageTransaction leafTx = atomicTx.wrapAsSegmentTransaction(leafSegment);

    leafTx.put(key1, value1);
    atomicTx.commit();

    // Then - should work correctly through interface
    var leafStorage = segmentedStorage.getKeyValueStorageForSegment(leafSegment);
    assertThat(leafStorage.get(key1)).contains(value1);
  }
}
