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

import net.consensys.shomei.trie.storage.InMemoryStorage;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tuweni.bytes.Bytes32;

/** In memory implementation of {@link WorldStateStorage}. */
public class InMemoryWorldStateStorage extends InMemoryStorage
    implements WorldStateStorage, WorldStateStorage.WorldStateUpdater {

  private Optional<Long> currentBlockNumber = Optional.empty();

  private Optional<Bytes32> currentBlockHash = Optional.empty();

  private final Map<Long, Bytes32> zkStateRootHash = new ConcurrentHashMap<>();

  public InMemoryWorldStateStorage() {}

  private InMemoryWorldStateStorage(
      Optional<Long> currentBlockNumber,
      Optional<Bytes32> currentBlockHash,
      Map<Long, Bytes32> zkStateRootHash) {
    this.currentBlockHash = currentBlockHash;
    this.currentBlockNumber = currentBlockNumber;
    this.zkStateRootHash.putAll(zkStateRootHash);
  }

  @Override
  public Optional<Bytes32> getZkStateRootHash(final long blockNumber) {
    return Optional.ofNullable(zkStateRootHash.get(blockNumber));
  }

  @Override
  public Optional<Bytes32> getWorldStateRootHash() {
    return currentBlockNumber.flatMap(this::getZkStateRootHash);
  }

  @Override
  public WorldStateStorage snapshot() {
    return new InMemoryWorldStateStorage(currentBlockNumber, currentBlockHash, zkStateRootHash);
  }

  @Override
  public void close() {
    // no-op;
  }

  @Override
  public Optional<Bytes32> getWorldStateBlockHash() {
    return currentBlockHash;
  }

  @Override
  public Optional<Long> getWorldStateBlockNumber() {
    return currentBlockNumber;
  }

  @Override
  public void setBlockHash(final Bytes32 blockHash) {
    this.currentBlockHash = Optional.ofNullable(blockHash);
  }

  @Override
  public void setBlockNumber(final long blockNumber) {
    this.currentBlockNumber = Optional.of(blockNumber);
  }
}
