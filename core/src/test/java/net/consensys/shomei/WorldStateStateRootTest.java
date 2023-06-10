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

    assertThat(HashProvider.mimc(zkAccount.getEncodedBytes()))
        .isEqualTo(
            Hash.fromHexString("25ddd6106526ffb2c9b923617cf3bcab669a5d57821d0ec81daa23155c1513ea"));

    final ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));
    accountStateTrie.putAndProve(
        zkAccount.getHkey(), zkAccount.getAddress(), zkAccount.getEncodedBytes());

    assertThat(accountStateTrie.getSubRootHash())
        .isEqualTo(
            Hash.fromHexString("15154ecb54514b439ed9cd51078e08e51676c30fc2067d04320ef6b6d263584e"));

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("11aed727a707f2f1962e399bd4787153ba0e69b7224e8eecf4d1e4e6a8e8dafd"));
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

    assertThat(HashProvider.mimc(account.getEncodedBytes()))
        .isEqualTo(
            Hash.fromHexString("25ddd6106526ffb2c9b923617cf3bcab669a5d57821d0ec81daa23155c1513ea"));

    final ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));
    accountStateTrie.putAndProve(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());
    accountStateTrie.putAndProve(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("1f8b53f5cf08c25611e11f8bfd2ffdbdab2f12f7e0578e54282f31e0e6267ab4"));
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
    accountStateTrie.putAndProve(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());
    accountStateTrie.putAndProve(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("15471b9c6443332dccaef5b1544c5881e2c2a6e4576ad1696cec3d1769061e21"));
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
    accountStateTrie.putAndProve(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());
    accountStateTrie.putAndProve(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    // Write something in the storage of B
    final ZKTrie account2Storage =
        ZKTrie.createTrie(
            new StorageTrieRepositoryWrapper(
                zkAccount2.hashCode(), new InMemoryWorldStateStorage()));
    final MimcSafeBytes<Bytes32> slotKey = createDumFullBytes(14);
    final Hash slotKeyHash = HashProvider.mimc(slotKey);
    final MimcSafeBytes<Bytes32> slotValue = createDumFullBytes(18);
    account2Storage.putAndProve(
        slotKeyHash,
        slotKey,
        slotValue); // for this test we don't really need to add the address location
    zkAccount2.setStorageRoot(Hash.wrap(account2Storage.getTopRootHash()));

    accountStateTrie.putAndProve(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("16df792bd76d708e98bccd2f037d15e5b8d5fa816febf0bf0a30487b8e0ba117"));
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
    accountStateTrie.putAndProve(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());
    accountStateTrie.putAndProve(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    // Write something in the storage of B
    final ZKTrie account2Storage =
        ZKTrie.createTrie(
            new StorageTrieRepositoryWrapper(
                zkAccount2.hashCode(), new InMemoryWorldStateStorage()));
    final MimcSafeBytes<Bytes32> slotKey = createDumFullBytes(14);
    final Hash slotKeyHash = HashProvider.mimc(slotKey);
    final MimcSafeBytes<Bytes32> slotValue = createDumFullBytes(18);
    account2Storage.putAndProve(slotKeyHash, slotKey, slotValue);
    zkAccount2.setStorageRoot(Hash.wrap(account2Storage.getTopRootHash()));
    accountStateTrie.putAndProve(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    // Delete account 1
    accountStateTrie.removeAndProve(account.getHkey(), account.getAddress());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("2e603c5f62481d627428d9efbfccd33fc1474e1d191b9e93cefa337b4a0e67da"));

    // clean storage B
    account2Storage.removeAndProve(slotKeyHash, slotKey);
    zkAccount2.setStorageRoot(Hash.wrap(account2Storage.getTopRootHash()));
    accountStateTrie.putAndProve(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("1aee37cbf805a51f827b48eb8fab44a7012575876045dca6ea6faaaa2233b0b5"));

    // Write again, somewhere else
    final MimcSafeBytes<Bytes32> newSlotKey = createDumFullBytes(11);
    final Hash newSlotKeyHash = HashProvider.mimc(newSlotKey);
    final MimcSafeBytes<Bytes32> newSlotValue = createDumFullBytes(78);
    account2Storage.putAndProve(newSlotKeyHash, newSlotKey, newSlotValue);
    zkAccount2.setStorageRoot(Hash.wrap(account2Storage.getTopRootHash()));
    accountStateTrie.putAndProve(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("02b9fb86c95b0e45a3ad401f1267b62f80e1ec16057d1491c2c9b32b36a1478f"));
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
    accountStateTrie.putAndProve(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());
    accountStateTrie.putAndProve(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());
    accountStateTrie.removeAndProve(account.getHkey(), account.getAddress());
    accountStateTrie.putAndProve(
        zkAccount3.getHkey(), zkAccount3.getAddress(), zkAccount3.getEncodedBytes());

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("1cb213eb41f295fded1c6850d570beec729ca15541a33586320e0f097f0ed11b"));
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
    accountStateTrie.putAndProve(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());
    accountStateTrie.putAndProve(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x15471b9c6443332dccaef5b1544c5881e2c2a6e4576ad1696cec3d1769061e21"));
    accountStateTrie.commit();
    // revert all addition
    accountStateTrie.removeAndProve(account.getHkey(), account.getAddress());
    accountStateTrie.decrementNextFreeNode();
    accountStateTrie.removeAndProve(zkAccount2.getHkey(), zkAccount2.getAddress());
    accountStateTrie.decrementNextFreeNode();
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("2e7942bb21022172cbad3ffc38d1c59e998f1ab6ab52feb15345d04bbf859f14"));
    accountStateTrie.commit();
    // add account again
    accountStateTrie.putAndProve(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());
    accountStateTrie.putAndProve(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x15471b9c6443332dccaef5b1544c5881e2c2a6e4576ad1696cec3d1769061e21"));
  }
}
