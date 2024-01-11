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

package net.consensys.shomei;

import static net.consensys.shomei.ZkAccount.EMPTY_CODE_HASH;
import static net.consensys.shomei.ZkAccount.EMPTY_KECCAK_CODE_HASH;
import static net.consensys.shomei.trie.ZKTrie.DEFAULT_TRIE_ROOT;
import static net.consensys.shomei.util.TestFixtureGenerator.createDumAddress;
import static net.consensys.shomei.util.TestFixtureGenerator.createDumDigest;
import static net.consensys.shomei.util.TestFixtureGenerator.createDumFullBytes;
import static net.consensys.shomei.util.TestFixtureGenerator.getAccountOne;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.storage.worldstate.InMemoryWorldStateStorage;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.storage.AccountTrieRepositoryWrapper;
import net.consensys.shomei.trie.storage.StorageTrieRepositoryWrapper;
import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.util.bytes.MimcSafeBytes;
import net.consensys.zkevm.HashProvider;

import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.Test;

// TODO add the location in the tests for consistency
public class WorldStateStateRootTest {

  @Test
  public void testWorldStateWithAnAccount() {

    final ZkAccount zkAccount =
        new ZkAccount(
            new AccountKey(createDumAddress(36)),
            65,
            Wei.of(835),
            DEFAULT_TRIE_ROOT,
            EMPTY_CODE_HASH,
            EMPTY_KECCAK_CODE_HASH,
            0L);

    assertThat(HashProvider.trieHash(zkAccount.getEncodedBytes()))
        .isEqualTo(
            Hash.fromHexString(
                "0x11314cf80cdd63a376e468ea9e6c672109bcfe516f0349382df82e1a876ca8b2"));

    final ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));
    accountStateTrie.putWithTrace(
        zkAccount.getHkey(), zkAccount.getAddress(), zkAccount.getEncodedBytes());

    assertThat(accountStateTrie.getSubRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x0e963ac1c981840721b20ccd7f5f2392697a8c9e1211dc67397a4a02e36ac23e"));

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x04c3c6de7195a187bc89fb4f8b68e93c7d675f1eed585b00d0e1e6241a321f86"));
  }

  @Test
  public void testWorldStateWithTwoAccount() {

    final ZkAccount zkAccount2 =
        new ZkAccount(
            new AccountKey(createDumAddress(41)),
            42,
            Wei.of(354),
            DEFAULT_TRIE_ROOT,
            EMPTY_CODE_HASH,
            EMPTY_KECCAK_CODE_HASH,
            0L);

    MutableZkAccount account = getAccountOne();

    assertThat(HashProvider.trieHash(account.getEncodedBytes()))
        .isEqualTo(
            Hash.fromHexString(
                "0x11314cf80cdd63a376e468ea9e6c672109bcfe516f0349382df82e1a876ca8b2"));

    final ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));
    accountStateTrie.putWithTrace(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());
    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x020e2a836e973eebd3c6367ef432ff21bb35102bc2ae3258b385e8cfbf4d46d4"));
  }

  @Test
  public void testWorldStateWithAccountAndContract() {

    final ZkAccount zkAccount2 =
        new ZkAccount(
            new AccountKey(createDumAddress(47)),
            41,
            Wei.of(15353),
            DEFAULT_TRIE_ROOT,
            Hash.wrap(createDumDigest(75)),
            createDumFullBytes(15),
            7L);

    final ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    MutableZkAccount account = getAccountOne();
    accountStateTrie.putWithTrace(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());
    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x0bc47df364adaecf61a5024f2b39603341077be453d88d21e627aee59ef7a6db"));
  }

  @Test
  public void testWorldStateWithUpdateContractStorage() {

    final MutableZkAccount zkAccount2 =
        new MutableZkAccount(
            new AccountKey(createDumAddress(47)),
            createDumFullBytes(15),
            Hash.wrap(createDumDigest(75)),
            7L,
            41,
            Wei.of(15353),
            DEFAULT_TRIE_ROOT);

    final ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    MutableZkAccount account = getAccountOne();
    accountStateTrie.putWithTrace(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());
    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    // Write something in the storage of B
    final ZKTrie account2Storage =
        ZKTrie.createTrie(
            new StorageTrieRepositoryWrapper(
                zkAccount2.hashCode(), new InMemoryWorldStateStorage()));
    final MimcSafeBytes<Bytes32> slotKey = createDumFullBytes(14);
    final Hash slotKeyHash = HashProvider.trieHash(slotKey);
    final MimcSafeBytes<Bytes32> slotValue = createDumFullBytes(18);
    account2Storage.putWithTrace(
        slotKeyHash,
        slotKey,
        slotValue); // for this test we don't really need to add the address location
    zkAccount2.setStorageRoot(Hash.wrap(account2Storage.getTopRootHash()));

    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x069b45f6f789581a3402103cd35168bf8d1de77eb5db9f79390ad29472e0846d"));
  }

  @Test
  public void testWorldStateWithDeleteAccountAndStorage() {

    final MutableZkAccount zkAccount2 =
        new MutableZkAccount(
            new AccountKey(createDumAddress(47)),
            createDumFullBytes(15),
            Hash.wrap(createDumDigest(75)),
            7L,
            41,
            Wei.of(15353),
            DEFAULT_TRIE_ROOT);

    final ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    MutableZkAccount account = getAccountOne();
    accountStateTrie.putWithTrace(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());
    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    // Write something in the storage of B
    final ZKTrie account2Storage =
        ZKTrie.createTrie(
            new StorageTrieRepositoryWrapper(
                zkAccount2.hashCode(), new InMemoryWorldStateStorage()));
    final MimcSafeBytes<Bytes32> slotKey = createDumFullBytes(14);
    final Hash slotKeyHash = HashProvider.trieHash(slotKey);
    final MimcSafeBytes<Bytes32> slotValue = createDumFullBytes(18);
    account2Storage.putWithTrace(slotKeyHash, slotKey, slotValue);
    zkAccount2.setStorageRoot(Hash.wrap(account2Storage.getTopRootHash()));
    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    // Delete account 1
    accountStateTrie.removeWithTrace(account.getHkey(), account.getAddress());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x0b6a3290b85cf230ce33cec4438aa907373c8a471c47346ad32fc170b8644ec3"));

    // clean storage B
    account2Storage.removeWithTrace(slotKeyHash, slotKey);
    zkAccount2.setStorageRoot(Hash.wrap(account2Storage.getTopRootHash()));
    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x0f11405ba708b9aeb8de0a341d80682b3a59c628e0694af97e357e86bb9567cf"));

    // Write again, somewhere else
    final MimcSafeBytes<Bytes32> newSlotKey = createDumFullBytes(11);
    final Hash newSlotKeyHash = HashProvider.trieHash(newSlotKey);
    final MimcSafeBytes<Bytes32> newSlotValue = createDumFullBytes(78);
    account2Storage.putWithTrace(newSlotKeyHash, newSlotKey, newSlotValue);
    zkAccount2.setStorageRoot(Hash.wrap(account2Storage.getTopRootHash()));
    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x06825644ff9ddf7d87b8a6f5d813254d535eae9d1bc2d2336b27211b1006f58c"));
  }

  @Test
  public void testAddAndDeleteAccounts() {

    final ZkAccount zkAccount2 =
        new ZkAccount(
            new AccountKey(createDumAddress(47)),
            41,
            Wei.of(15353),
            DEFAULT_TRIE_ROOT,
            Hash.wrap(createDumDigest(75)),
            createDumFullBytes(15),
            7L);

    final ZkAccount zkAccount3 =
        new ZkAccount(
            new AccountKey(createDumAddress(120)),
            48,
            Wei.of(9835),
            DEFAULT_TRIE_ROOT,
            Hash.wrap(createDumDigest(54)),
            createDumFullBytes(85),
            19L);

    final ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    MutableZkAccount account = getAccountOne();
    accountStateTrie.putWithTrace(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());
    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());
    accountStateTrie.removeWithTrace(account.getHkey(), account.getAddress());
    accountStateTrie.putWithTrace(
        zkAccount3.getHkey(), zkAccount3.getAddress(), zkAccount3.getEncodedBytes());

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x00b43fd65348b5a492ebcbd7ce3933fc963809ca4897d4fcd00d8661e45d9d55"));
  }

  @Test
  public void testRevertAddAccount() {
    final ZkAccount zkAccount2 =
        new ZkAccount(
            new AccountKey(createDumAddress(47)),
            41,
            Wei.of(15353),
            DEFAULT_TRIE_ROOT,
            Hash.wrap(createDumDigest(75)),
            createDumFullBytes(15),
            7L);

    final ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    // add account
    MutableZkAccount account = getAccountOne();
    accountStateTrie.putWithTrace(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());
    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x0bc47df364adaecf61a5024f2b39603341077be453d88d21e627aee59ef7a6db"));
    accountStateTrie.commit();
    // revert all addition
    accountStateTrie.removeWithTrace(account.getHkey(), account.getAddress());
    accountStateTrie.decrementNextFreeNode();
    accountStateTrie.removeWithTrace(zkAccount2.getHkey(), zkAccount2.getAddress());
    accountStateTrie.decrementNextFreeNode();
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x07977874126658098c066972282d4c85f230520af3847e297fe7524f976873e5"));
    accountStateTrie.commit();
    // add account again
    accountStateTrie.putWithTrace(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());
    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x0bc47df364adaecf61a5024f2b39603341077be453d88d21e627aee59ef7a6db"));
  }
}
