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

package net.consensys.shomei.storage.worldstate;

import net.consensys.shomei.services.storage.api.SnapshotKeyValueStorage;
import net.consensys.shomei.storage.TraceManager;
import net.consensys.shomei.trie.model.FlattenedLeaf;

import java.io.IOException;

import com.google.common.primitives.Longs;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapshotPersistedWorldStateStorage extends PersistedWorldStateStorage
    implements AutoCloseable {
  private static final Logger LOG =
      LoggerFactory.getLogger(SnapshotPersistedWorldStateStorage.class);

  public SnapshotPersistedWorldStateStorage(
      final SnapshotKeyValueStorage flatLeafStorage,
      final SnapshotKeyValueStorage trieNodeStorage,
      final TraceManager traceManager) {
    super(flatLeafStorage, trieNodeStorage, traceManager);
  }

  @Override
  public SnapshotPersistedWorldStateStorage snapshot() {
    throw new UnsupportedOperationException("Snapshots of snapshots are not supported.");
  }

  @Override
  public WorldStateUpdater updater() {
    return new WorldStateUpdater() {
      @Override
      public void setBlockHash(final Hash blockHash) {
        trieNodeTx.get().put(WORLD_BLOCK_HASH_KEY, blockHash.toArrayUnsafe());
      }

      @Override
      public void setBlockNumber(final long blockNumber) {
        trieNodeTx.get().put(WORLD_BLOCK_NUMBER_KEY, Longs.toByteArray(blockNumber));
      }

      @Override
      public void saveZkStateRootHash(final long blockNumber, final Hash stateRoot) {
        traceManager.saveZkStateRootHash(blockNumber, stateRoot);
      }

      @Override
      public void putFlatLeaf(final Bytes key, final FlattenedLeaf value) {
        flatLeafTx.get().put(key.toArrayUnsafe(), RLP.encode(value::writeTo).toArrayUnsafe());
      }

      @Override
      public void putTrieNode(final Bytes location, final Bytes nodeHash, final Bytes value) {
        trieNodeTx.get().put(location.toArrayUnsafe(), value.toArrayUnsafe());
      }

      @Override
      public void removeFlatLeafValue(final Bytes key) {
        flatLeafTx.get().remove(key.toArrayUnsafe());
      }

      @Override
      public void commit() {
        // no-op.  Snapshot storage is not committed.
      }
    };
  }

  @Override
  public void close() {
    try {
      flatLeafStorage.close();
    } catch (IOException e) {
      LOG.error("Failed to close flat leaf snapshot storage", e);
    }
    try {
      trieNodeStorage.close();
    } catch (IOException e) {
      LOG.error("Failed to close trie node snapshot storage", e);
    }
  }
}
