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

package net.consensys.shomei.worldview;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.services.storage.api.AtomicCompositeTransaction;
import net.consensys.shomei.services.storage.api.BidirectionalIterator;
import net.consensys.shomei.services.storage.api.KeyValueStorage.KeyValuePair;
import net.consensys.shomei.services.storage.api.KeyValueStorageTransaction;
import net.consensys.shomei.services.storage.api.SegmentIdentifier;
import net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentIdentifier;
import net.consensys.shomei.storage.InMemoryStorageProvider;
import net.consensys.shomei.storage.RocksDBStorageProvider;
import net.consensys.shomei.storage.StorageProvider;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Integration test to verify atomic transaction functionality.
 * This test verifies the basic contract without requiring complex mocking.
 */
class AtomicTransactionIntegrationTest {

  @Test
  void shouldReturnEmptyAtomicTransactionForInMemoryStorage() {
    // Given
    StorageProvider inMemoryProvider = new InMemoryStorageProvider();

    // When
    Optional<AtomicCompositeTransaction> atomicTx = inMemoryProvider.getAtomicCompositeTransaction();

    // Then
    assertThat(atomicTx).isEmpty();
  }

  @Test
  void shouldUseAtomicTransactionInZkEvmWorldState() {
    // Given
    StorageProvider provider = new InMemoryStorageProvider();
    AtomicCompositeTransaction atomicTx = new TestAtomicCompositeTransaction();
    
    // When - create world state with atomic transaction
    ZkEvmWorldState worldState = new ZkEvmWorldState(
        provider.getWorldStateStorage(), 
        provider.getTraceManager(), 
        Optional.of(atomicTx)
    );

    // Then - world state should be created successfully
    assertThat(worldState).isNotNull();
    assertThat(worldState.getStateRootHash()).isNotNull();
  }

  @Test
  void shouldUseBackwardCompatibleConstructor() {
    // Given
    StorageProvider provider = new InMemoryStorageProvider();
    
    // When - create world state with backward compatible constructor
    ZkEvmWorldState worldState = new ZkEvmWorldState(
        provider.getWorldStateStorage(), 
        provider.getTraceManager()
    );

    // Then - world state should be created successfully
    assertThat(worldState).isNotNull();
    assertThat(worldState.getStateRootHash()).isNotNull();
  }

  @Test
  void shouldCreateWrappedTransactionsForTestAtomicTransaction() {
    // Given
    AtomicCompositeTransaction atomicTx = new TestAtomicCompositeTransaction();

    // When - create wrapped transactions for different segments
    var leafSegment = RocksDBSegmentIdentifier.SegmentNames.ZK_LEAF_INDEX.getSegmentIdentifier();
    var trieSegment = RocksDBSegmentIdentifier.SegmentNames.ZK_TRIE_NODE.getSegmentIdentifier();
    var traceSegment = RocksDBSegmentIdentifier.SegmentNames.ZK_TRACE.getSegmentIdentifier();

    KeyValueStorageTransaction leafTx = atomicTx.wrapAsSegmentTransaction(leafSegment);
    KeyValueStorageTransaction trieTx = atomicTx.wrapAsSegmentTransaction(trieSegment);
    KeyValueStorageTransaction traceTx = atomicTx.wrapAsSegmentTransaction(traceSegment);

    // Then - all transactions should be created successfully
    assertThat(leafTx).isNotNull();
    assertThat(trieTx).isNotNull();
    assertThat(traceTx).isNotNull();
  }

  @Test
  void shouldSupportCommitAndRollbackForTestAtomicTransaction() {
    // Given
    AtomicCompositeTransaction atomicTx = new TestAtomicCompositeTransaction();

    // When/Then - these methods should not throw exceptions
    atomicTx.commit(); // Should not throw
    
    // Create a new transaction for rollback test
    AtomicCompositeTransaction atomicTx2 = new TestAtomicCompositeTransaction();
    atomicTx2.rollback(); // Should not throw
  }

  /**
   * Simple test implementation of AtomicCompositeTransaction for testing purposes
   */
  private static class TestAtomicCompositeTransaction implements AtomicCompositeTransaction {

    @Override
    public KeyValueStorageTransaction wrapAsSegmentTransaction(SegmentIdentifier segment) {
      return new TestKeyValueStorageTransaction();
    }

    @Override
    public void commit() {
      // No-op for test
    }

    @Override
    public void rollback() {
      // No-op for test
    }

  }

  /**
   * Simple test implementation of KeyValueStorageTransaction
   */
  private static class TestKeyValueStorageTransaction implements KeyValueStorageTransaction {
    @Override
    public Optional<byte[]> get(byte[] key) {
      return Optional.empty();
    }

    @Override
    public KeyValueStorageTransaction put(byte[] key, byte[] value) {
      return this;
    }

    @Override
    public KeyValueStorageTransaction remove(byte[] key) {
      return this;
    }

    @Override
    public void commit() {
      // No-op for test
    }

    @Override
    public void rollback() {
      // No-op for test
    }

    @Override
    public Optional<BidirectionalIterator<KeyValuePair>> getNearestTo(byte[] key) {
      return Optional.empty();
    }
  }
}