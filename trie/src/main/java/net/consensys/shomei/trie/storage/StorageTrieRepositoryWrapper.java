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

import java.util.Map;
import java.util.Optional;

import com.google.common.primitives.Longs;
import org.apache.tuweni.bytes.Bytes;

/**
 * This class serves as a wrapper for the StorageTrieRepositoryWrapper, providing additional
 * functionality and abstraction for interacting with the storage trie data.
 */
public class StorageTrieRepositoryWrapper implements TrieStorage {

  private final Bytes accountPath;

  private final TrieStorage trieStorage;

  private final TrieStorage.TrieUpdater updater;

  public StorageTrieRepositoryWrapper(
      final long accountLeafIndex, final TrieStorage trieStorage, final TrieUpdater updater) {
    this.accountPath = Bytes.wrap(Longs.toByteArray(accountLeafIndex));
    this.trieStorage = trieStorage;
    this.updater = updater;
  }

  public StorageTrieRepositoryWrapper(final long accountLeafIndex, final TrieStorage trieStorage) {
    this.accountPath = Bytes.wrap(Longs.toByteArray(accountLeafIndex));
    this.trieStorage = trieStorage;
    this.updater = trieStorage.updater();
  }

  @Override
  public Optional<FlattenedLeaf> getFlatLeaf(final Bytes hkey) {
    return trieStorage.getFlatLeaf(getStorageKeyForAccount(hkey));
  }

  @Override
  public Range getNearestKeys(final Bytes hkey) {
    Range nearestKeys = trieStorage.getNearestKeys(getStorageKeyForAccount(hkey));
    final Map.Entry<Bytes, FlattenedLeaf> left =
        Map.entry(retrieveStorageKey(nearestKeys.getLeftNodeKey()), nearestKeys.getLeftNodeValue());
    final Optional<Map.Entry<Bytes, FlattenedLeaf>> center =
        nearestKeys
            .getCenterNode()
            .map(
                centerNode ->
                    Map.entry(retrieveStorageKey(centerNode.getKey()), centerNode.getValue()));
    final Map.Entry<Bytes, FlattenedLeaf> right =
        Map.entry(
            retrieveStorageKey(nearestKeys.getRightNodeKey()), nearestKeys.getRightNodeValue());
    return new Range(left, center, right);
  }

  @Override
  public Optional<Bytes> getTrieNode(final Bytes location, final Bytes nodeHash) {
    return trieStorage.getTrieNode(
        getStorageKeyForAccount(location), getStorageKeyForAccount(nodeHash));
  }

  @Override
  public TrieUpdater updater() {
    return new StorageUpdater(updater);
  }

  public class StorageUpdater implements TrieUpdater {

    private final TrieUpdater updater;

    public StorageUpdater(final TrieUpdater updater) {

      this.updater = updater;
    }

    @Override
    public void putFlatLeaf(final Bytes hkey, final FlattenedLeaf value) {
      updater.putFlatLeaf(getStorageKeyForAccount(hkey), value);
    }

    @Override
    public void putTrieNode(final Bytes location, final Bytes nodeHash, final Bytes value) {
      updater.putTrieNode(
          getStorageKeyForAccount(location), getStorageKeyForAccount(nodeHash), value);
    }

    @Override
    public void removeFlatLeafValue(final Bytes hkey) {
      updater.removeFlatLeafValue(getStorageKeyForAccount(hkey));
    }

    @Override
    public void commit() {
      updater.commit();
    }
  }

  private Bytes getStorageKeyForAccount(final Bytes storageKey) {
    if (storageKey == null) {
      return null;
    }
    return Bytes.concatenate(accountPath, storageKey);
  }

  private Bytes retrieveStorageKey(final Bytes key) {
    return key.slice(accountPath.size() - 1);
  }
}
