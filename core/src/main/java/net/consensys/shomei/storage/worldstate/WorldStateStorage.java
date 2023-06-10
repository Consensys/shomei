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

import net.consensys.shomei.trie.storage.TrieStorage;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.hyperledger.besu.datatypes.Hash;

/**
 * The WorldStateStorage class is responsible for managing the world state of a blockchain. It
 * provides methods for accessing and modifying the state of accounts and storage in the world
 * state.
 */
public interface WorldStateStorage extends TrieStorage {

  /** key identifier of the block hash of the current world state. */
  byte[] WORLD_BLOCK_HASH_KEY = "blockHash".getBytes(StandardCharsets.UTF_8);

  /** key identifier of the block number of the current world state. */
  byte[] WORLD_BLOCK_NUMBER_KEY = "blockNumber".getBytes(StandardCharsets.UTF_8);

  Optional<Long> getWorldStateBlockNumber();

  /**
   * Returns the block hash of the current world state.
   *
   * @return the block hash of the current world state.
   */
  Optional<Hash> getWorldStateBlockHash();

  /**
   * Returns the zk state root of the given block number.
   *
   * @param blockNumber the block number.
   * @return the zk state root of the given block number.
   */
  Optional<Hash> getZkStateRootHash(long blockNumber);

  /**
   * Returns the zk state root of the current world state.
   *
   * @return the zk state root of the current world state.
   */
  Optional<Hash> getWorldStateRootHash();

  /**
   * Returns a non-persistable snapshot of this worldstate storage.
   *
   * @return worldstate snapshot.
   */
  WorldStateStorage snapshot();

  default void close() throws Exception {
    // no-op
  }

  /** Updater for the world state repository. */
  interface WorldStateUpdater extends TrieUpdater {

    void setBlockHash(final Hash blockHash);

    void setBlockNumber(final long blockNumber);

    void saveZkStateRootHash(long blockNumber, Hash stateRoot);
  }
}
