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

import net.consensys.shomei.trie.storage.InMemoryStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;

public class InMemoryWorldStateStorage extends InMemoryStorage
    implements WorldStateStorage, WorldStateStorage.WorldStateUpdater {

  private Optional<Long> currentBlockNumber = Optional.empty();

  private Optional<Hash> currentBlockHash = Optional.empty();

  private Optional<Hash> currentStateRootHash = Optional.empty();

  private final Map<Bytes, Bytes> trieLogStorage = new ConcurrentHashMap<>();

  private final Map<Long, Bytes> traces = new HashMap<>();

  @Override
  public Optional<Bytes> getTrieLog(final Hash blockHash) {
    return Optional.ofNullable(trieLogStorage.get(blockHash));
  }

  @Override
  public Optional<Bytes> getTrace(final long blockNumber) {
    return Optional.ofNullable(traces.get(blockNumber));
  }

  @Override
  public Optional<Hash> getWorldStateRootHash() {
    return currentStateRootHash;
  }

  @Override
  public Optional<Hash> getWorldStateBlockHash() {
    return currentBlockHash;
  }

  @Override
  public Optional<Long> getWorldStateBlockNumber() {
    return currentBlockNumber;
  }

  @Override
  public void setBlockHash(final Hash blockHash) {
    this.currentBlockHash = Optional.ofNullable(blockHash);
  }

  @Override
  public void setBlockNumber(final long blockNumber) {
    this.currentBlockNumber = Optional.of(blockNumber);
  }

  @Override
  public void saveTrieLog(final Hash blockHash, final Bytes rawTrieLogLayer) {
    trieLogStorage.put(blockHash, rawTrieLogLayer);
  }

  @Override
  public void saveTrace(final long blockNumber, final Bytes rawTrace) {
    traces.put(blockNumber, rawTrace);
  }
}
