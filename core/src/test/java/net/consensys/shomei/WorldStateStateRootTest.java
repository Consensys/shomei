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
                "0x017095f9f125ac3d74c7e0bb482f3cfb7409915d27ecad1b1bb4ca65103f13ce"));

    final ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));
    accountStateTrie.putWithTrace(
        zkAccount.getHkey(), zkAccount.getAddress(), zkAccount.getEncodedBytes());

    assertThat(accountStateTrie.getSubRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x0285aa2ff8faa86ea35ca9ffe18474233445148f22c5a5e2680d2fcc3b4042a1"));

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x02d507540514011b52e7debd8a136c6c28819bb128f0cd52ef6d5a753966cb88"));
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
                "0x017095f9f125ac3d74c7e0bb482f3cfb7409915d27ecad1b1bb4ca65103f13ce"));

    final ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));
    accountStateTrie.putWithTrace(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());
    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x03b9395928c56d45c10d317c7102e9950d03b4f55f6ad043000fd19987fa961f"));
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
                "0x0e96b1188b30497434accbf8b64047863d56568d6526c25751a2a253393da8e0"));
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
                "0x061caf856bd61568a5f3bbd2983f6e270eb88d07685b5aa7940034dd2822c9ae"));
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
                "0x116a0520d95c7f2d0fe67cc2a3ce07d497aeda71b4cdfdd505d9b5ac829cb4f7"));

    // clean storage B
    account2Storage.removeWithTrace(slotKeyHash, slotKey);
    zkAccount2.setStorageRoot(Hash.wrap(account2Storage.getTopRootHash()));
    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x1184b2790357fdfe64095f296d7002ef330ad56bc26c92e7ce6a58261e00f068"));

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
                "0x0b34fb264bffa61fb7540a76a702dbf7ccc04300d2c6d8a72fc1f4e4d42b0af5"));
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
                "0x06309a0479348bf18d63d896dcac6ce365982ec46e36d278d7127d8ab841e4fb"));
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
                "0x0e96b1188b30497434accbf8b64047863d56568d6526c25751a2a253393da8e0"));
    accountStateTrie.commit();
    // revert all addition
    accountStateTrie.removeWithTrace(account.getHkey(), account.getAddress());
    accountStateTrie.decrementNextFreeNode();
    accountStateTrie.removeWithTrace(zkAccount2.getHkey(), zkAccount2.getAddress());
    accountStateTrie.decrementNextFreeNode();
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x090dccbc1e7b264c40101a13610bd84942fa6837bfa8e0c67ede029c5ad4d00c"));
    accountStateTrie.commit();
    // add account again
    accountStateTrie.putWithTrace(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());
    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x0e96b1188b30497434accbf8b64047863d56568d6526c25751a2a253393da8e0"));
  }
}
