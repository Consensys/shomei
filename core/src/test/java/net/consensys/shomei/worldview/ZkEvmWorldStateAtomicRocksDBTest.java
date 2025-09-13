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

import net.consensys.shomei.services.storage.api.AtomicCompositeTransaction;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;
import net.consensys.shomei.storage.RocksDBStorageProvider;
import net.consensys.shomei.storage.StorageProvider;
import org.hyperledger.besu.datatypes.Hash;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.internal.verification.Times;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Integration test that validates ZkEvmWorldState correctly uses RocksDBFlatTransaction
 * for atomic operations when provided with a RocksDBStorageProvider.
 * This test ensures the real flat transaction usage patterns work as designed.
 */

public class ZkEvmWorldStateAtomicRocksDBTest {

  @TempDir Path tempDir;
  
  private ZkEvmWorldState worldState;

  @Test
  void shouldCommitAtomicallyUsingAtomicTransaction() {
    StorageProvider storageProvider = new RocksDBStorageProvider(
        new RocksDBConfigurationBuilder().databaseDir(tempDir).build());

    final var maybeAtomicTx = storageProvider.getAtomicCompositeTransaction().get();
    assertThat(maybeAtomicTx).isNotNull();
    AtomicCompositeTransaction spyAtomicTx = spy(maybeAtomicTx);

    // Create ZkEvmWorldState with the atomic transaction
    worldState = new ZkEvmWorldState(
        storageProvider.getWorldStateStorage(),
        storageProvider.getTraceManager(),
        Optional.of(spyAtomicTx)
    );

    assertThat(worldState.getStateRootHash()).isNotNull();
    worldState.commit(1L, Hash.EMPTY_TRIE_HASH, false);
    // should see once for TraceManager, and twice for Worldstate
    // this indicates we are using RocksDBFlatTransaction
    verify(spyAtomicTx, new Times(3)).wrapAsSegmentTransaction(any());
    // should see exactly one atomic commit from ZkEvmWorldState
    verify(spyAtomicTx, new Times(1)).commit();
  }

}
