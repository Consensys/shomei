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

import net.consensys.shomei.trie.model.LeafOpening;
import net.consensys.shomei.trie.storage.InMemoryStorage;
import net.consensys.shomei.util.bytes.MimcSafeBytes;
import net.consensys.zkevm.HashProvider;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.junit.Test;

public class ZKTrieTest {

  @Test
  public void testWorldStateHead() {
    assertThat(HashProvider.trieHash(LeafOpening.HEAD.getEncodesBytes()))
        .isEqualTo(
            Bytes.fromHexString(
                "0x0891fa77c3d0c9b745840d71d41dcb58b638d4734bb4f0bba4a3d1a2d847b672"));
  }

  @Test
  public void testWorldStateTail() {
    assertThat(HashProvider.trieHash(LeafOpening.TAIL.getEncodesBytes()))
        .isEqualTo(
            Bytes.fromHexString(
                "0x10ba2286f648a549b50ea5f1b6e1155d22c31eb4727c241e76c420200cd5dbe0"));
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
                "0x0951bfcd4ac808d195af8247140b906a4379b3f2d37ec66e34d2f4a5d35fa166"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x07977874126658098c066972282d4c85f230520af3847e297fe7524f976873e5"));
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
                "0x0882afe875656680dceb7b17fcba7c136cec0c32becbe9039546c79f71c56d36"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x0cfdc3990045390093be4e1cc9907b220324cccd1c8ea9ede980c7afa898ef8d"));
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
                "0x0882afe875656680dceb7b17fcba7c136cec0c32becbe9039546c79f71c56d36"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x0cfdc3990045390093be4e1cc9907b220324cccd1c8ea9ede980c7afa898ef8d"));
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
                "0x0951bfcd4ac808d195af8247140b906a4379b3f2d37ec66e34d2f4a5d35fa166"));
    assertThat(zkTrie.getTopRootHash())
        .isNotEqualTo(
            Bytes.fromHexString(
                "0x0bcb88342825fa7a079a5cf5f77d07b1590a140c311a35acd765080eea120329"));

    zkTrie.removeWithTrace(hkey, key);

    assertThat(zkTrie.getSubRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x0951bfcd4ac808d195af8247140b906a4379b3f2d37ec66e34d2f4a5d35fa166"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x0bcb88342825fa7a079a5cf5f77d07b1590a140c311a35acd765080eea120329"));
  }
}
