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

import static net.consensys.shomei.trie.storage.AccountTrieRepositoryWrapper.WRAP_ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.storage.worldstate.WorldStateStorage;
import net.consensys.shomei.trie.model.FlattenedLeaf;
import net.consensys.shomei.trie.storage.AccountTrieRepositoryWrapper;
import net.consensys.shomei.trie.storage.StorageTrieRepositoryWrapper;

import com.google.common.primitives.Longs;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public abstract class WorldStateWrapperTestBase {

  abstract WorldStateStorage getWorldStateStorage();

  @Test
  public void wrapCorrectlyPutKeyIndex() {
    final WorldStateStorage worldStateStorage = getWorldStateStorage();
    final FlattenedLeaf flatLeafValue = new FlattenedLeaf(1L, Bytes.EMPTY);
    final AccountTrieRepositoryWrapper wrapper =
        new AccountTrieRepositoryWrapper(worldStateStorage, worldStateStorage.updater());
    wrapper.updater().putFlatLeaf(Bytes.of(3), flatLeafValue);
    var res = worldStateStorage.getFlatLeaf(WRAP_ACCOUNT.apply(Bytes.of(3)));
    assertThat(res).isPresent();
    assertThat(res.get()).isEqualTo(flatLeafValue);
  }

  @Test
  public void wrapCorrectlyPutKeyIndexWithPrefix() {
    final WorldStateStorage worldStateStorage = getWorldStateStorage();
    final StorageTrieRepositoryWrapper wrapper =
        new StorageTrieRepositoryWrapper(1L, worldStateStorage, worldStateStorage.updater());
    final FlattenedLeaf flatLeafValue = new FlattenedLeaf(1L, Bytes.EMPTY);
    wrapper.updater().putFlatLeaf(Bytes.of(3), flatLeafValue);
    assertThat(worldStateStorage.getFlatLeaf(Bytes.of(3))).isEmpty();
    var res =
        worldStateStorage.getFlatLeaf(
            Bytes.concatenate(Bytes.wrap(Longs.toByteArray(1L)), Bytes.of(3)));
    assertThat(res).isPresent();
    assertThat(res.get()).isEqualTo(flatLeafValue);
  }

  @Test
  public void wrapCorrectlyRemoveKeyIndex() {
    final WorldStateStorage worldStateStorage = getWorldStateStorage();
    final AccountTrieRepositoryWrapper wrapper =
        new AccountTrieRepositoryWrapper(worldStateStorage, worldStateStorage.updater());
    final FlattenedLeaf flatLeafValue = new FlattenedLeaf(1L, Bytes.EMPTY);
    wrapper.updater().putFlatLeaf(Bytes.of(3), flatLeafValue);
    var res = worldStateStorage.getFlatLeaf(WRAP_ACCOUNT.apply(Bytes.of(3)));
    assertThat(res).isPresent();
    assertThat(res.get()).isEqualTo(flatLeafValue);
    wrapper.updater().removeFlatLeafValue(Bytes.of(3));
    assertThat(worldStateStorage.getFlatLeaf(Bytes.of(3))).isEmpty();
  }

  @Test
  public void wrapCorrectlyRemoveKeyIndexWithPrefix() {
    final WorldStateStorage worldStateStorage = getWorldStateStorage();
    final StorageTrieRepositoryWrapper wrapper =
        new StorageTrieRepositoryWrapper(1L, worldStateStorage, worldStateStorage.updater());
    final FlattenedLeaf flatLeafValue = new FlattenedLeaf(1L, Bytes.EMPTY);
    wrapper.updater().putFlatLeaf(Bytes.of(3), flatLeafValue);
    var storageKey = Bytes.concatenate(Bytes.wrap(Longs.toByteArray(1L)), Bytes.of(3));
    var res = worldStateStorage.getFlatLeaf(storageKey);
    assertThat(res).isPresent();
    assertThat(res.get()).isEqualTo(flatLeafValue);
    wrapper.updater().removeFlatLeafValue(Bytes.of(3));
    assertThat(worldStateStorage.getFlatLeaf(storageKey)).isEmpty();
  }

  @Test
  public void wrapCorrectlyPutTrieNode() {
    final WorldStateStorage worldStateStorage = getWorldStateStorage();
    final AccountTrieRepositoryWrapper wrapper =
        new AccountTrieRepositoryWrapper(worldStateStorage, worldStateStorage.updater());
    wrapper.updater().putTrieNode(Bytes.of(1), Bytes.of(1), Bytes.of(3));
    assertThat(worldStateStorage.getTrieNode(Bytes.of(1), Bytes.of(1))).contains(Bytes.of(3));
  }

  @Test
  public void wrapCorrectlyPutTrieNodeWithPrefix() {
    final WorldStateStorage worldStateStorage = getWorldStateStorage();
    final StorageTrieRepositoryWrapper wrapper =
        new StorageTrieRepositoryWrapper(1L, worldStateStorage, worldStateStorage.updater());
    wrapper.updater().putTrieNode(Bytes.of(1), Bytes.of(1), Bytes.of(3));
    assertThat(worldStateStorage.getTrieNode(Bytes.of(1), Bytes.of(1))).isEmpty();
    assertThat(
            worldStateStorage.getTrieNode(
                Bytes.concatenate(Bytes.wrap(Longs.toByteArray(1L)), Bytes.of(1)), Bytes.EMPTY))
        .contains(Bytes.of(3));
  }
}
