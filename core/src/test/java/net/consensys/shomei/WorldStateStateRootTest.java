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
package net.consensys.shomei;

import static net.consensys.shomei.ZkAccount.EMPTY_CODE_HASH;
import static net.consensys.shomei.ZkAccount.EMPTY_KECCAK_CODE_HASH;
import static net.consensys.shomei.trie.ZKTrie.DEFAULT_TRIE_ROOT;
import static net.consensys.shomei.util.TestFixtureGenerator.createDumAddress;
import static net.consensys.shomei.util.TestFixtureGenerator.createDumDigest;
import static net.consensys.shomei.util.TestFixtureGenerator.createDumFullBytes;
import static net.consensys.shomei.util.TestFixtureGenerator.getAccountOne;
import static net.consensys.shomei.util.bytes.PoseidonSafeBytesUtils.safeByte32;
import static net.consensys.shomei.util.bytes.PoseidonSafeBytesUtils.safeUInt256;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;

import net.consensys.shomei.storage.worldstate.InMemoryWorldStateStorage;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.storage.AccountTrieRepositoryWrapper;
import net.consensys.shomei.trie.storage.StorageTrieRepositoryWrapper;
import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.util.bytes.PoseidonSafeBytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

// TODO add the location in the tests for consistency
public class WorldStateStateRootTest {

  @Test
  public void testWorldStateWithAnAccount() {

    final ZkAccount zkAccount =
        new ZkAccount(
            new AccountKey(createDumAddress(36)),
            safeUInt256(UInt256.valueOf(65L)),
            safeUInt256(UInt256.valueOf(835L)),
            DEFAULT_TRIE_ROOT,
            EMPTY_CODE_HASH,
            EMPTY_KECCAK_CODE_HASH,
            safeUInt256(UInt256.valueOf(0L)));

    assertThat(zkAccount.getEncodedBytes().hash())
        .isEqualTo(
            Hash.fromHexString(
                "0x495340db00ecc17b5cb435d5731f8d6635e6b3ef42507a8303a068d178a95d22"));

    final ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));
    accountStateTrie.putWithTrace(
        zkAccount.getHkey(), zkAccount.getAddress(), zkAccount.getEncodedBytes());

    assertThat(accountStateTrie.getSubRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x75a23718103d010a0357dee12411e1a036a989fa05adb8e05e5fdd5472ad37c9"));

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x6b5d2e111a55e55826396df03ac3d0055dc4a88671edbcc8274db3f0117e0f97"));
  }

  @Test
  public void testWorldStateWithTwoAccount() {

    final ZkAccount zkAccount2 =
        new ZkAccount(
            new AccountKey(createDumAddress(41)),
            safeUInt256(UInt256.valueOf(42L)),
            safeUInt256(UInt256.valueOf(354L)),
            DEFAULT_TRIE_ROOT,
            EMPTY_CODE_HASH,
            EMPTY_KECCAK_CODE_HASH,
            safeUInt256(UInt256.valueOf(0L)));

    MutableZkAccount account = getAccountOne();

    assertThat(account.getEncodedBytes().hash())
        .isEqualTo(
            Hash.fromHexString(
                "0x495340db00ecc17b5cb435d5731f8d6635e6b3ef42507a8303a068d178a95d22"));

    final ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));
    accountStateTrie.putWithTrace(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());
    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x7bcc50ea4546465f153f5c0a35e3cddb59f492f4183438ed52733bc92823fbb7"));
  }

  @Test
  public void testWorldStateWithAccountAndContract() {

    final ZkAccount zkAccount2 =
        new ZkAccount(
            new AccountKey(createDumAddress(47)),
            safeUInt256(UInt256.valueOf(41L)),
            safeUInt256(UInt256.valueOf(15353L)),
            DEFAULT_TRIE_ROOT,
            Hash.wrap(createDumDigest(75)),
            safeByte32(createDumFullBytes(15)),
            safeUInt256(UInt256.valueOf(7L)));

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
                "0x2d1812fb21abfe6c0b457a046f27fbfc6392e5935e21506c5f38c5f04bf48ad8"));
  }

  @Test
  public void testWorldStateWithUpdateContractStorage() {

    final MutableZkAccount zkAccount2 =
        new MutableZkAccount(
            new AccountKey(createDumAddress(47)),
            safeByte32(createDumFullBytes(15)),
            Hash.wrap(createDumDigest(75)),
            safeUInt256(UInt256.valueOf(7L)),
            safeUInt256(UInt256.valueOf(41L)),
            safeUInt256(UInt256.valueOf(15353L)),
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
    final PoseidonSafeBytes<Bytes32> slotKey = safeByte32(createDumFullBytes(14));
    final Hash slotKeyHash = slotKey.hash();
    final PoseidonSafeBytes<Bytes32> slotValue = safeByte32(createDumFullBytes(18));
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
                "0x1aae3e0a143c0ac31b469af05096c1000a64836f05c250ad0857d0a1496f0c71"));
  }

  @Test
  public void testWorldStateWithDeleteAccountAndStorage() {

    final MutableZkAccount zkAccount2 =
        new MutableZkAccount(
            new AccountKey(createDumAddress(47)),
            safeByte32(createDumFullBytes(15)),
            Hash.wrap(createDumDigest(75)),
            safeUInt256(UInt256.valueOf(7L)),
            safeUInt256(UInt256.valueOf(41L)),
            safeUInt256(UInt256.valueOf(15353L)),
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
    final PoseidonSafeBytes<Bytes32> slotKey = safeByte32(createDumFullBytes(14));
    final Hash slotKeyHash = slotKey.hash();
    final PoseidonSafeBytes<Bytes32> slotValue = safeByte32(createDumFullBytes(18));
    account2Storage.putWithTrace(slotKeyHash, slotKey, slotValue);
    zkAccount2.setStorageRoot(Hash.wrap(account2Storage.getTopRootHash()));
    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    // Delete account 1
    accountStateTrie.removeWithTrace(account.getHkey(), account.getAddress());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x2c00cb996b94822239a4930271af93270b81ebdf747f79e47e060ccc652b7b9b"));

    // clean storage B
    account2Storage.removeWithTrace(slotKeyHash, slotKey);
    zkAccount2.setStorageRoot(Hash.wrap(account2Storage.getTopRootHash()));
    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x16c9336e2a455bb872980a7d4173105e5483c347581bfd40576e0cd712812fb9"));

    // Write again, somewhere else
    final PoseidonSafeBytes<Bytes32> newSlotKey = safeByte32(createDumFullBytes(11));
    final Hash newSlotKeyHash = newSlotKey.hash();
    final PoseidonSafeBytes<Bytes32> newSlotValue = safeByte32(createDumFullBytes(78));
    account2Storage.putWithTrace(newSlotKeyHash, newSlotKey, newSlotValue);
    zkAccount2.setStorageRoot(Hash.wrap(account2Storage.getTopRootHash()));
    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x0c8c8cc90b3493c647e2569f31a8fe217e087a010aa45f586c35a933571d2827"));
  }

  @Test
  public void testAddAndDeleteAccounts() {

    final ZkAccount zkAccount2 =
        new ZkAccount(
            new AccountKey(createDumAddress(47)),
            safeUInt256(UInt256.valueOf(41L)),
            safeUInt256(UInt256.valueOf(15353L)),
            DEFAULT_TRIE_ROOT,
            Hash.wrap(createDumDigest(75)),
            safeByte32(createDumFullBytes(15)),
            safeUInt256(UInt256.valueOf(7L)));

    final ZkAccount zkAccount3 =
        new ZkAccount(
            new AccountKey(createDumAddress(120)),
            safeUInt256(UInt256.valueOf(48L)),
            safeUInt256(UInt256.valueOf(9835L)),
            DEFAULT_TRIE_ROOT,
            Hash.wrap(createDumDigest(54)),
            safeByte32(createDumFullBytes(85)),
            safeUInt256(UInt256.valueOf(19L)));

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
                "0x064dcee96c04c17973b5e072688768d66190513f4f62622d31cb5a3620912cd2"));
  }

  @Test
  public void testRevertAddAccount() {
    final ZkAccount zkAccount2 =
        new ZkAccount(
            new AccountKey(createDumAddress(47)),
            safeUInt256(UInt256.valueOf(41L)),
            safeUInt256(UInt256.valueOf(15353L)),
            DEFAULT_TRIE_ROOT,
            Hash.wrap(createDumDigest(75)),
            safeByte32(createDumFullBytes(15)),
            safeUInt256(UInt256.valueOf(7L)));

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
                "0x2d1812fb21abfe6c0b457a046f27fbfc6392e5935e21506c5f38c5f04bf48ad8"));
    accountStateTrie.commit();
    // revert all addition
    accountStateTrie.removeWithTrace(account.getHkey(), account.getAddress());
    accountStateTrie.decrementNextFreeNode();
    accountStateTrie.removeWithTrace(zkAccount2.getHkey(), zkAccount2.getAddress());
    accountStateTrie.decrementNextFreeNode();
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x2fa0344a2fab2b310d2af3155c330261263f887379aef18b4941e3ea1cc59df7"));
    accountStateTrie.commit();
    // add account again
    accountStateTrie.putWithTrace(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());
    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x2d1812fb21abfe6c0b457a046f27fbfc6392e5935e21506c5f38c5f04bf48ad8"));
  }
}
