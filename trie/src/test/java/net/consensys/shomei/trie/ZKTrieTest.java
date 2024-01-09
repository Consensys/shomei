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

import static net.consensys.shomei.trie.DigestGenerator.createDumDigest;
import static net.consensys.shomei.util.bytes.MimcSafeBytes.unsafeFromBytes;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.trie.storage.InMemoryStorage;
import net.consensys.shomei.util.bytes.MimcSafeBytes;
import net.consensys.zkevm.HashProvider;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.junit.Test;

public class ZKTrieTest {

  @Test
  public void testEmptyRootHash() {

    final InMemoryStorage storage = new InMemoryStorage();
    ZKTrie zkTrie = ZKTrie.createTrie(storage);
    zkTrie.commit();

    assertThat(storage.getTrieNodeStorage()).isNotEmpty();
    assertThat(zkTrie.getSubRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x02c922349d06bd81028ae748ed367a1c41073612886253d0219a20d5e4422efe"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x090dccbc1e7b264c40101a13610bd84942fa6837bfa8e0c67ede029c5ad4d00c"));
  }

  @Test
  public void testInsertionRootHash() {

    final InMemoryStorage storage = new InMemoryStorage();
    ZKTrie zkTrie = ZKTrie.createTrie(storage);

    final MimcSafeBytes<Bytes> key = unsafeFromBytes(createDumDigest(58));
    final MimcSafeBytes<Bytes> value = unsafeFromBytes(createDumDigest(42));
    final Hash hkey = HashProvider.trieHash(key);

    zkTrie.putWithTrace(hkey, key, value);
    zkTrie.commit();
    assertThat(storage.getTrieNodeStorage()).isNotEmpty();
    assertThat(zkTrie.getSubRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x0500936c306714ae84a942536b21ecc0c4c7d1f41ac1fa052baeeda3e9d8ef78"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x043c9421381594b60cc83e617eee1bcef4dc9612f2a4246627967bc97dfa2478"));
  }

  @Test
  public void testInsertionAndUpdateRootHash() {

    final InMemoryStorage storage = new InMemoryStorage();
    ZKTrie zkTrie = ZKTrie.createTrie(storage);

    final MimcSafeBytes<Bytes> key = unsafeFromBytes(createDumDigest(58));
    final MimcSafeBytes<Bytes> dumValue = unsafeFromBytes(createDumDigest(41));
    final MimcSafeBytes<Bytes> newDumValue = unsafeFromBytes(createDumDigest(42));
    final Hash hkey = HashProvider.trieHash(key);

    zkTrie.putWithTrace(hkey, key, dumValue);

    assertThat(zkTrie.getSubRootHash())
        .isNotEqualTo(
            Bytes.fromHexString(
                "0x0500936c306714ae84a942536b21ecc0c4c7d1f41ac1fa052baeeda3e9d8ef78"));
    assertThat(zkTrie.getTopRootHash())
        .isNotEqualTo(
            Bytes.fromHexString(
                "0x043c9421381594b60cc83e617eee1bcef4dc9612f2a4246627967bc97dfa2478"));

    // Note : the tree should be in exactly the same state as after directly
    // inserting 42
    zkTrie.putWithTrace(hkey, key, newDumValue);

    assertThat(zkTrie.getSubRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x0500936c306714ae84a942536b21ecc0c4c7d1f41ac1fa052baeeda3e9d8ef78"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x043c9421381594b60cc83e617eee1bcef4dc9612f2a4246627967bc97dfa2478"));
  }

  @Test
  public void testInsertionAndDeleteRootHash() {
    final InMemoryStorage storage = new InMemoryStorage();
    ZKTrie zkTrie = ZKTrie.createTrie(storage);

    final MimcSafeBytes<Bytes> key = unsafeFromBytes(createDumDigest(58));
    final MimcSafeBytes<Bytes> value = unsafeFromBytes(createDumDigest(41));
    final Hash hkey = HashProvider.trieHash(key);

    zkTrie.putWithTrace(hkey, key, value);

    assertThat(zkTrie.getSubRootHash())
        .isNotEqualTo(
            Bytes.fromHexString(
                "0x02c922349d06bd81028ae748ed367a1c41073612886253d0219a20d5e4422efe"));
    assertThat(zkTrie.getTopRootHash())
        .isNotEqualTo(
            Bytes.fromHexString(
                "0x0fe7cd3903ac559aff000495f4c4a82b21f83bc11988f900ba5e46c1a106a885"));

    zkTrie.removeWithTrace(hkey, key);

    assertThat(zkTrie.getSubRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x02c922349d06bd81028ae748ed367a1c41073612886253d0219a20d5e4422efe"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x0fe7cd3903ac559aff000495f4c4a82b21f83bc11988f900ba5e46c1a106a885"));
  }
}
