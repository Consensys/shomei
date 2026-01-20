/*
 * Copyright Consensys Software Inc., 2025
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
import static net.consensys.shomei.util.bytes.PoseidonSafeBytesUtils.unsafeFromBytes;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;

import net.consensys.shomei.trie.model.LeafOpening;
import net.consensys.shomei.trie.storage.InMemoryStorage;
import net.consensys.shomei.util.bytes.PoseidonSafeBytes;
import net.consensys.zkevm.HashProvider;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

public class ZKTrieTest {

  @Test
  public void testWorldStateHead() {
    assertThat(HashProvider.trieHash(LeafOpening.HEAD.getEncodesBytes()))
        .isEqualTo(
            Bytes.fromHexString(
                "0x2f5de7ad279a134761de0d89702e52743b2f04f41b86cc5400dfae1d4b981340"));
  }

  @Test
  public void testWorldStateTail() {
    assertThat(HashProvider.trieHash(LeafOpening.TAIL.getEncodesBytes()))
        .isEqualTo(
            Bytes.fromHexString(
                "0x5c73480b5e85fc2c3ab61fc155cf475b74590d005d8916b85fdd5c32773b1255"));
  }

  @Test
  public void testEmptyRootHash() {

    final InMemoryStorage storage = new InMemoryStorage();
    ZKTrie zkTrie = ZKTrie.createTrie(storage);
    zkTrie.commit();

    assertThat(storage.getTrieNodeStorage()).isNotEmpty();
    assertThat(zkTrie.getSubRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x3a00a8e34a16f8a1225fee734816edb326f783bd6678d793345a28f046586ba6"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x2fa0344a2fab2b310d2af3155c330261263f887379aef18b4941e3ea1cc59df7"));
  }

  @Test
  public void testInsertionRootHash() {

    final InMemoryStorage storage = new InMemoryStorage();
    ZKTrie zkTrie = ZKTrie.createTrie(storage);

    final PoseidonSafeBytes<Bytes> key =
        unsafeFromBytes(
            Bytes32.fromHexString(
                "0x000000000000000000000000000000000000000000000000000000000000003a"));

    final PoseidonSafeBytes<Bytes> value =
        unsafeFromBytes(
            Bytes32.fromHexString(
                "0x000000000000000000000000000000000000000000000000000000000000002a"));
    final Hash hkey = key.hash();
    zkTrie.putWithTrace(hkey, key, value);
    zkTrie.commit();
    assertThat(storage.getTrieNodeStorage()).isNotEmpty();
    assertThat(zkTrie.getSubRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x50b4bb9d2b4c917f48ca9465613d7efe092934a95bcdc7a004ef3a6b7900fd5b"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x7a5cf31710a49b59489deb53505a3d5458625ef01b33914d0d784380464177ad"));
  }

  @Test
  public void testInsertionAndUpdateRootHash() {

    final InMemoryStorage storage = new InMemoryStorage();
    ZKTrie zkTrie = ZKTrie.createTrie(storage);

    final PoseidonSafeBytes<Bytes> key = unsafeFromBytes(createDumDigest(58));
    final PoseidonSafeBytes<Bytes> dumValue = unsafeFromBytes(createDumDigest(41));
    final PoseidonSafeBytes<Bytes> newDumValue = unsafeFromBytes(createDumDigest(42));
    final Hash hkey = key.hash();

    zkTrie.putWithTrace(hkey, key, dumValue);

    assertThat(zkTrie.getSubRootHash())
        .isNotEqualTo(
            Bytes.fromHexString(
                "0x0882afe875656680dceb7b17fcba7c136cec0c32becbe9039546c79f71c56d36"));
    assertThat(zkTrie.getTopRootHash())
        .isNotEqualTo(
            Bytes.fromHexString(
                "0x0cfdc3990045390093be4e1cc9907b220324cccd1c8ea9ede980c7afa898ef8d"));

    // Note : the tree should be in exactly the same state as after directly
    // inserting 42
    zkTrie.putWithTrace(hkey, key, newDumValue);

    assertThat(zkTrie.getSubRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x50b4bb9d2b4c917f48ca9465613d7efe092934a95bcdc7a004ef3a6b7900fd5b"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x7a5cf31710a49b59489deb53505a3d5458625ef01b33914d0d784380464177ad"));
  }

  @Test
  public void testInsertionAndDeleteRootHash() {
    final InMemoryStorage storage = new InMemoryStorage();
    ZKTrie zkTrie = ZKTrie.createTrie(storage);

    final PoseidonSafeBytes<Bytes> key = unsafeFromBytes(createDumDigest(58));
    final PoseidonSafeBytes<Bytes> value = unsafeFromBytes(createDumDigest(41));
    final Hash hkey = key.hash();

    zkTrie.putWithTrace(hkey, key, value);

    assertThat(zkTrie.getSubRootHash())
        .isNotEqualTo(
            Bytes.fromHexString(
                "0x3a00a8e34a16f8a1225fee734816edb326f783bd6678d793345a28f046586ba6"));
    assertThat(zkTrie.getTopRootHash())
        .isNotEqualTo(
            Bytes.fromHexString(
                "0x0bcb88342825fa7a079a5cf5f77d07b1590a140c311a35acd765080eea120329"));

    zkTrie.removeWithTrace(hkey, key);

    assertThat(zkTrie.getSubRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x3a00a8e34a16f8a1225fee734816edb326f783bd6678d793345a28f046586ba6"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x391010e55fad441a1e3a33b11fb8271f68e86b6c1812ebd402d678d343fded62"));
  }
}
