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
import static net.consensys.shomei.ZkAccount.EMPTY_TRIE_ROOT;
import static net.consensys.shomei.util.TestUtil.createDumAddress;
import static net.consensys.shomei.util.TestUtil.createDumDigest;
import static net.consensys.shomei.util.TestUtil.createSafeFieldElementSizeDumDigest;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.trie.ZKTrie;
import net.consensys.zkevm.HashProvider;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.Test;

@SuppressWarnings("unused")
public class WorldStateTest {

  @Test
  public void testWorldStateWithAnAccount() {
    final Address address = createDumAddress(36);

    final ZkAccount zkAccount =
        new ZkAccount(
            address, EMPTY_KECCAK_CODE_HASH, EMPTY_CODE_HASH, 0L, 65, Wei.of(835), EMPTY_TRIE_ROOT);

    assertThat(Hash.hash(zkAccount.serializeAccount()))
        .isEqualTo(
            Hash.fromHexString("ab023fb58c760f385eb5e68491287a46a51a653f3d7609b035b82a79df93f413"));

    ZKTrie accountStateTrie = ZKTrie.createInMemoryTrie();
    accountStateTrie.putAndProve(zkAccount.getHkey(), zkAccount.serializeAccount());

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("828dd273c29ec50463bd7fac90e06b04b4010b72fe880df82e299bf162046e41"));
  }

  @Test
  public void testWorldStateWithTwoAccount() {
    final Address address = createDumAddress(36);
    final Address address2 = createDumAddress(41);

    final ZkAccount zkAccount =
        new ZkAccount(
            address, EMPTY_KECCAK_CODE_HASH, EMPTY_CODE_HASH, 0L, 65, Wei.of(835), EMPTY_TRIE_ROOT);
    final ZkAccount zkAccount2 =
        new ZkAccount(
            address2,
            EMPTY_KECCAK_CODE_HASH,
            EMPTY_CODE_HASH,
            0L,
            42,
            Wei.of(354),
            EMPTY_TRIE_ROOT);

    assertThat(Hash.hash(zkAccount.serializeAccount()))
        .isEqualTo(
            Hash.fromHexString("ab023fb58c760f385eb5e68491287a46a51a653f3d7609b035b82a79df93f413"));

    ZKTrie accountStateTrie = ZKTrie.createInMemoryTrie();
    accountStateTrie.putAndProve(zkAccount.getHkey(), zkAccount.serializeAccount());
    accountStateTrie.putAndProve(zkAccount2.getHkey(), zkAccount2.serializeAccount());

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("527e480b526f4976528ef828ef90fc07b5758bf9f4e7db77e9bf5122b5935f2a"));
  }

  @Test
  public void testWorldStateWithAccountAndContract() {
    final Address address = createDumAddress(36);
    final Address address2 = createDumAddress(47);

    final ZkAccount zkAccount =
        new ZkAccount(
            address, EMPTY_KECCAK_CODE_HASH, EMPTY_CODE_HASH, 0L, 65, Wei.of(835), EMPTY_TRIE_ROOT);
    final ZkAccount zkAccount2 =
        new ZkAccount(
            address2,
            Hash.wrap(createDumDigest(15)),
            Hash.wrap(createDumDigest(75)),
            7L,
            41,
            Wei.of(15353),
            EMPTY_TRIE_ROOT);

    ZKTrie accountStateTrie = ZKTrie.createInMemoryTrie();
    accountStateTrie.putAndProve(zkAccount.getHkey(), zkAccount.serializeAccount());
    accountStateTrie.putAndProve(zkAccount2.getHkey(), zkAccount2.serializeAccount());

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("2990341de1a14455fee8441d7efca5ce7d6dcba95dc1655e1408af66f8b6dcd7"));
  }

  @Test
  public void testWorldStateWithUpdateContractStorage() {
    final Address address = createDumAddress(36);
    final Address address2 = createDumAddress(47);

    final ZkAccount zkAccount =
        new ZkAccount(
            address, EMPTY_KECCAK_CODE_HASH, EMPTY_CODE_HASH, 0L, 65, Wei.of(835), EMPTY_TRIE_ROOT);

    final MutableZkAccount zkAccount2 =
        new MutableZkAccount(
            address2,
            Hash.wrap(createDumDigest(15)),
            Hash.wrap(createDumDigest(75)),
            7L,
            41,
            Wei.of(15353),
            EMPTY_TRIE_ROOT);

    ZKTrie accountStateTrie = ZKTrie.createInMemoryTrie();
    accountStateTrie.putAndProve(zkAccount.getHkey(), zkAccount.serializeAccount());
    accountStateTrie.putAndProve(zkAccount2.getHkey(), zkAccount2.serializeAccount());

    // Write something in the storage of B
    final ZKTrie account2Storage = ZKTrie.createInMemoryTrie();
    account2Storage.putAndProve(
        HashProvider.mimc(createSafeFieldElementSizeDumDigest(14)),
        createSafeFieldElementSizeDumDigest(18));
    zkAccount2.setStorageRoot(Hash.wrap(account2Storage.getTopRootHash()));
    accountStateTrie.putAndProve(zkAccount2.getHkey(), zkAccount2.serializeAccount());

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("e98f0b735c00b52c526b50a50386bdd7c8bbee1f4ffb7bc39a7aa8ab5814d0ca"));
  }

  @Test
  public void testWorldStateWithDeleteAccountAndStorage() {
    final Address address = createDumAddress(36);
    final Address address2 = createDumAddress(47);

    final ZkAccount zkAccount =
        new ZkAccount(
            address, EMPTY_KECCAK_CODE_HASH, EMPTY_CODE_HASH, 0L, 65, Wei.of(835), EMPTY_TRIE_ROOT);

    final MutableZkAccount zkAccount2 =
        new MutableZkAccount(
            address2,
            Hash.wrap(createDumDigest(15)),
            Hash.wrap(createDumDigest(75)),
            7L,
            41,
            Wei.of(15353),
            EMPTY_TRIE_ROOT);

    ZKTrie accountStateTrie = ZKTrie.createInMemoryTrie();
    accountStateTrie.putAndProve(zkAccount.getHkey(), zkAccount.serializeAccount());
    accountStateTrie.putAndProve(zkAccount2.getHkey(), zkAccount2.serializeAccount());

    // Write something in the storage of B
    final ZKTrie account2StorageTrie = ZKTrie.createInMemoryTrie();
    account2StorageTrie.putAndProve(
        HashProvider.mimc(createSafeFieldElementSizeDumDigest(14)),
        createSafeFieldElementSizeDumDigest(18));
    zkAccount2.setStorageRoot(Hash.wrap(account2StorageTrie.getTopRootHash()));
    accountStateTrie.putAndProve(zkAccount2.getHkey(), zkAccount2.serializeAccount());

    // Delete account 1
    accountStateTrie.removeAndProve(zkAccount.getHkey());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("21a3343df41b60ef5359cdb31444748049fb9151b2663c44daf36dc77b253efa"));

    // clean storage B
    account2StorageTrie.removeAndProve(HashProvider.mimc(createSafeFieldElementSizeDumDigest(14)));
    zkAccount2.setStorageRoot(Hash.wrap(account2StorageTrie.getTopRootHash()));
    accountStateTrie.putAndProve(zkAccount2.getHkey(), zkAccount2.serializeAccount());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("5e017ff976bb15f2b52d636e8cb0dab7dfa7b048d91e448dc891001bf1024d3e"));

    // Write again, somewhere else
    account2StorageTrie.putAndProve(
        HashProvider.mimc(createSafeFieldElementSizeDumDigest(11)),
        createSafeFieldElementSizeDumDigest(78));
    zkAccount2.setStorageRoot(Hash.wrap(account2StorageTrie.getTopRootHash()));
    accountStateTrie.putAndProve(zkAccount2.getHkey(), zkAccount2.serializeAccount());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("7bd8507a3009144e2cf9b78fb40287a4befcdbb0ac525f7cc630e7f413e8edef"));
  }

  @Test
  public void testAddAndDeleteAccounts() {
    final Address address = createDumAddress(36);
    final Address address2 = createDumAddress(47);
    final Address address3 = createDumAddress(120);

    final ZkAccount zkAccount =
        new ZkAccount(
            address, EMPTY_KECCAK_CODE_HASH, EMPTY_CODE_HASH, 0L, 65, Wei.of(835), EMPTY_TRIE_ROOT);

    final ZkAccount zkAccount2 =
        new ZkAccount(
            address2,
            Hash.wrap(createDumDigest(15)),
            Hash.wrap(createDumDigest(75)),
            7L,
            41,
            Wei.of(15353),
            EMPTY_TRIE_ROOT);

    final ZkAccount zkAccount3 =
        new ZkAccount(
            address3,
            Hash.wrap(createDumDigest(85)),
            Hash.wrap(createDumDigest(54)),
            19L,
            48,
            Wei.of(9835),
            EMPTY_TRIE_ROOT);

    ZKTrie accountStateTrie = ZKTrie.createInMemoryTrie();
    accountStateTrie.putAndProve(zkAccount.getHkey(), zkAccount.serializeAccount());
    accountStateTrie.putAndProve(zkAccount2.getHkey(), zkAccount2.serializeAccount());
    accountStateTrie.removeAndProve(zkAccount.getHkey());
    accountStateTrie.putAndProve(zkAccount3.getHkey(), zkAccount3.serializeAccount());

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("0ccd7afcd88c99d9df5c00a914e44b5195dd0ff27696e79ad817a7d94ce7cfed"));
  }

  @Test
  public void testRevertAddAccount() {
    final Address address = createDumAddress(36);
    final Address address2 = createDumAddress(47);
    final Address address3 = createDumAddress(120);

    final ZkAccount zkAccount =
        new ZkAccount(
            address, EMPTY_KECCAK_CODE_HASH, EMPTY_CODE_HASH, 0L, 65, Wei.of(835), EMPTY_TRIE_ROOT);

    final ZkAccount zkAccount2 =
        new ZkAccount(
            address2,
            Hash.wrap(createDumDigest(15)),
            Hash.wrap(createDumDigest(75)),
            7L,
            41,
            Wei.of(15353),
            EMPTY_TRIE_ROOT);

    ZKTrie accountStateTrie = ZKTrie.createInMemoryTrie();
    // add account
    accountStateTrie.putAndProve(zkAccount.getHkey(), zkAccount.serializeAccount());
    accountStateTrie.putAndProve(zkAccount2.getHkey(), zkAccount2.serializeAccount());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("2990341de1a14455fee8441d7efca5ce7d6dcba95dc1655e1408af66f8b6dcd7"));
    accountStateTrie.commit();
    // revert all addition
    accountStateTrie.removeAndProve(zkAccount.getHkey());
    accountStateTrie.decrementNextFreeNode();
    accountStateTrie.removeAndProve(zkAccount2.getHkey());
    accountStateTrie.decrementNextFreeNode();
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("a222476ab21332f448fdf2496ce1b8442761140856b48300d1fce9c5395e4305"));
    accountStateTrie.commit();
    // add account again
    accountStateTrie.putAndProve(zkAccount.getHkey(), zkAccount.serializeAccount());
    accountStateTrie.putAndProve(zkAccount2.getHkey(), zkAccount2.serializeAccount());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("2990341de1a14455fee8441d7efca5ce7d6dcba95dc1655e1408af66f8b6dcd7"));
  }
}
