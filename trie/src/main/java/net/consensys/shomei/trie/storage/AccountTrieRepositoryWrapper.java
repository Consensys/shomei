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
import java.util.function.Function;

import com.google.common.primitives.Longs;
import org.apache.tuweni.bytes.Bytes;

/**
 * This class serves as a wrapper for the AccountTrieRepository, providing additional functionality
 * and abstraction for interacting with the account trie data.
 */
public class AccountTrieRepositoryWrapper implements TrieStorage {
  private static final Bytes ACCOUNT_TRIE_PREFIX = Bytes.wrap(Longs.toByteArray(Long.MAX_VALUE));
  public static final Function<Bytes, Bytes> WRAP_ACCOUNT =
      hkey -> Bytes.concatenate(ACCOUNT_TRIE_PREFIX, hkey);
  public static final Function<Bytes, Bytes> UNWRAP_ACCOUNT =
      key -> key.slice(ACCOUNT_TRIE_PREFIX.size() - 1);
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
    return trieStorage.getFlatLeaf(WRAP_ACCOUNT.apply(hkey));
  }

  @Override
  public Range getNearestKeys(final Bytes hkey) {
    Range nearestKeys = trieStorage.getNearestKeys(WRAP_ACCOUNT.apply(hkey));
    final Map.Entry<Bytes, FlattenedLeaf> left =
        Map.entry(
            UNWRAP_ACCOUNT.apply(nearestKeys.getLeftNodeKey()), nearestKeys.getLeftNodeValue());
    final Optional<Map.Entry<Bytes, FlattenedLeaf>> center =
        nearestKeys
            .getCenterNode()
            .map(
                centerNode ->
                    Map.entry(UNWRAP_ACCOUNT.apply(centerNode.getKey()), centerNode.getValue()));
    final Map.Entry<Bytes, FlattenedLeaf> right =
        Map.entry(
            UNWRAP_ACCOUNT.apply(nearestKeys.getRightNodeKey()), nearestKeys.getRightNodeValue());
    return new Range(left, center, right);
  }

  @Override
  public Optional<Bytes> getTrieNode(final Bytes location, final Bytes nodeHash) {
    return trieStorage.getTrieNode(location, nodeHash);
  }

  @Override
  public TrieUpdater updater() {
    return new AccountUpdater(worldStateUpdater);
  }

  public static class AccountUpdater implements TrieUpdater {

    private final TrieUpdater updater;

    public AccountUpdater(final TrieUpdater updater) {

      this.updater = updater;
    }

    @Override
    public void putFlatLeaf(final Bytes hkey, final FlattenedLeaf value) {
      updater.putFlatLeaf(WRAP_ACCOUNT.apply(hkey), value);
    }

    @Override
    public void putTrieNode(final Bytes location, final Bytes nodeHash, final Bytes value) {
      updater.putTrieNode(location, nodeHash, value);
    }

    @Override
    public void removeFlatLeafValue(final Bytes hkey) {
      updater.removeFlatLeafValue(WRAP_ACCOUNT.apply(hkey));
    }

    @Override
    public void commit() {
      updater.commit();
    }
  }
}
