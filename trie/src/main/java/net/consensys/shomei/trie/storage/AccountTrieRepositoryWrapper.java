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

package net.consensys.shomei.trie.storage;

import net.consensys.shomei.trie.model.FlattenedLeaf;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/**
 * This class serves as a wrapper for the AccountTrieRepository, providing additional functionality
 * and abstraction for interacting with the account trie data.
 */
public class AccountTrieRepositoryWrapper implements TrieStorage {

  private final TrieStorage trieStorage;

  private final TrieUpdater worldStateUpdater;

  public AccountTrieRepositoryWrapper(
      final TrieStorage trieStorage, final TrieUpdater worldStateUpdater) {
    this.trieStorage = trieStorage;
    this.worldStateUpdater = worldStateUpdater;
  }

  public AccountTrieRepositoryWrapper(final TrieStorage trieStorage) {
    this.trieStorage = trieStorage;
    this.worldStateUpdater = trieStorage.updater();
  }

  @Override
  public Optional<FlattenedLeaf> getFlatLeaf(final Bytes hkey) {
    return trieStorage.getFlatLeaf(hkey);
  }

  @Override
  public Range getNearestKeys(final Bytes hkey) {
    return trieStorage.getNearestKeys(hkey);
  }

  @Override
  public Optional<Bytes> getTrieNode(final Bytes location, final Bytes nodeHash) {
    return trieStorage.getTrieNode(location, nodeHash);
  }

  @Override
  public TrieUpdater updater() {
    return worldStateUpdater;
  }
}
