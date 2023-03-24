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

package net.consensys.shomei.trie;

import static net.consensys.shomei.util.TestFixtureGenerator.createDumKey;
import static net.consensys.shomei.util.TestFixtureGenerator.createDumValue;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.trie.storage.InMemoryLeafIndexManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Test;

public class ZKTrieTest {

  @Test
  public void testEmptyRootHash() {

    final InMemoryLeafIndexManager inMemoryLeafIndexManager = new InMemoryLeafIndexManager();
    final Map<Bytes, Bytes> keyValueStorage = new HashMap<>();

    ZKTrie zkTrie =
        ZKTrie.createTrie(
            inMemoryLeafIndexManager,
            inMemoryLeafIndexManager,
            (location, hash) -> Optional.ofNullable(keyValueStorage.get(hash)),
            (location, hash, value) -> keyValueStorage.put(hash, value));
    zkTrie.commit();

    assertThat(keyValueStorage).isNotEmpty();
    assertThat(zkTrie.getRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "89835e84ad52c14bf64fc28e57cfca5cea785d81e4df6cb723aaf3b3ef56888b"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "a222476ab21332f448fdf2496ce1b8442761140856b48300d1fce9c5395e4305"));
  }

  @Test
  public void testInsertionRootHash() {

    final InMemoryLeafIndexManager inMemoryLeafIndexManager = new InMemoryLeafIndexManager();
    final Map<Bytes, Bytes> keyValueStorage = new HashMap<>();
    ZKTrie zkTrie =
        ZKTrie.createTrie(
            inMemoryLeafIndexManager,
            inMemoryLeafIndexManager,
            (location, hash) -> Optional.ofNullable(keyValueStorage.get(hash)),
            (location, hash, value) -> keyValueStorage.put(hash, value));
    zkTrie.putValue(createDumKey(58), createDumValue(42));
    zkTrie.commit();
    assertThat(keyValueStorage).isNotEmpty();
    assertThat(zkTrie.getRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "7a7658514516cf30456fe16730d186cce37c930edc4c0a91c85298db428a3f97"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "29d47648b374fb18e31fe50dc4ca65bdea4f1cd7664075f4ef52da18138304ab"));
  }

  @Test
  public void testInsertionAndUpdateRootHash() {

    final ZKTrie zkTrie = ZKTrie.createInMemoryTrie();

    final Bytes32 dumValue = createDumValue(41);
    final Bytes32 newDumValue = createDumValue(42);

    zkTrie.putValue(createDumKey(58), dumValue);

    assertThat(zkTrie.getRootHash())
        .isNotEqualTo(
            Bytes.fromHexString(
                "7a7658514516cf30456fe16730d186cce37c930edc4c0a91c85298db428a3f97"));
    assertThat(zkTrie.getTopRootHash())
        .isNotEqualTo(
            Bytes.fromHexString(
                "29d47648b374fb18e31fe50dc4ca65bdea4f1cd7664075f4ef52da18138304ab"));

    // Note : the tree should be in exactly the same state as after directly
    // inserting 42
    zkTrie.putValue(createDumKey(58), newDumValue);

    assertThat(zkTrie.getRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "7a7658514516cf30456fe16730d186cce37c930edc4c0a91c85298db428a3f97"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "29d47648b374fb18e31fe50dc4ca65bdea4f1cd7664075f4ef52da18138304ab"));
  }

  @Test
  public void testInsertionAndDeleteRootHash() {
    final ZKTrie zkTrie = ZKTrie.createInMemoryTrie();

    final Bytes32 dumKey = createDumKey(58);

    zkTrie.putValue(dumKey, createDumValue(41));

    assertThat(zkTrie.getRootHash())
        .isNotEqualTo(
            Bytes.fromHexString(
                "7a7658514516cf30456fe16730d186cce37c930edc4c0a91c85298db428a3f97"));
    assertThat(zkTrie.getTopRootHash())
        .isNotEqualTo(
            Bytes.fromHexString(
                "29d47648b374fb18e31fe50dc4ca65bdea4f1cd7664075f4ef52da18138304ab"));

    zkTrie.remove(dumKey);

    assertThat(zkTrie.getRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "89835e84ad52c14bf64fc28e57cfca5cea785d81e4df6cb723aaf3b3ef56888b"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "a3ff34462bfb7b99a97df2ca92e3218077a799841e73598bd632b9b65283bcc7"));
  }
}
