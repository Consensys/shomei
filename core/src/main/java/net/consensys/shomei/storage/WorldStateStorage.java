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

import net.consensys.shomei.trie.storage.StorageProxy;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;

public interface WorldStateStorage extends StorageProxy {

  byte[] ZK_WORLD_STATE_ROOT_HASH_KEY = "zkStateRootHash".getBytes(StandardCharsets.UTF_8);

  byte[] WORLD_STATE_ROOT_HASH_KEY = "stateRootHash".getBytes(StandardCharsets.UTF_8);

  byte[] WORLD_BLOCK_HASH_KEY = "blockHash".getBytes(StandardCharsets.UTF_8);

  byte[] WORLD_BLOCK_NUMBER_KEY = "blockNumber".getBytes(StandardCharsets.UTF_8);

  Optional<Long> getWorldStateBlockNumber();

  Optional<Hash> getWorldStateBlockHash();

  Optional<Bytes> getZkStateRootHash(long blockNumber);

  Optional<Hash> getWorldStateRootHash();

  Optional<Bytes> getTrieLog(final long blockNumber);

  Optional<Bytes> getTrace(final long blockNumber);

  default void close() {
    // no-op
  }

  interface WorldStateUpdater extends Updater {

    void setBlockHash(final Hash blockHash);

    void setBlockNumber(final long blockNumber);

    void saveTrieLog(final long blockNumber, final Bytes rawTrieLogLayer);

    void saveZkStateRootHash(long blockNumber, Bytes stateRoot);

    void saveTrace(final long blockNumber, final Bytes rawTrace);
  }
}
