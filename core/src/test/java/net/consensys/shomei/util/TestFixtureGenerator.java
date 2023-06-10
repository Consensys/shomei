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

package net.consensys.shomei.util;

import static net.consensys.shomei.ZkAccount.EMPTY_CODE_HASH;
import static net.consensys.shomei.ZkAccount.EMPTY_KECCAK_CODE_HASH;
import static net.consensys.shomei.trie.ZKTrie.DEFAULT_TRIE_ROOT;
import static net.consensys.shomei.util.bytes.MimcSafeBytes.safeByte32;

import net.consensys.shomei.MutableZkAccount;
import net.consensys.shomei.ZkAccount;
import net.consensys.shomei.storage.worldstate.InMemoryWorldStateStorage;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.storage.StorageTrieRepositoryWrapper;
import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.util.bytes.MimcSafeBytes;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;

public class TestFixtureGenerator {

  public static final AccountKey ACCOUNT_KEY_1 = new AccountKey(createDumAddress(36));
  public static final AccountKey ACCOUNT_KEY_2 = new AccountKey(createDumAddress(47));

  private static final ZkAccount ZK_ACCOUNT =
      new ZkAccount(
          ACCOUNT_KEY_1,
          65,
          Wei.of(835),
          DEFAULT_TRIE_ROOT,
          EMPTY_CODE_HASH,
          EMPTY_KECCAK_CODE_HASH,
          0L);

  private static final ZkAccount ZK_ACCOUNT_2 =
      new ZkAccount(
          ACCOUNT_KEY_2,
          65,
          Wei.of(835),
          DEFAULT_TRIE_ROOT,
          EMPTY_CODE_HASH,
          EMPTY_KECCAK_CODE_HASH,
          0L);

  public static MutableZkAccount getAccountOne() {
    return new MutableZkAccount(ZK_ACCOUNT);
  }

  public static MutableZkAccount getAccountTwo() {
    return new MutableZkAccount(ZK_ACCOUNT_2);
  }

  public static ZKTrie getContractStorageTrie(final MutableZkAccount mutableZkAccount) {
    return ZKTrie.createTrie(
        new StorageTrieRepositoryWrapper(
            mutableZkAccount.hashCode(), new InMemoryWorldStateStorage()));
  }

  public static Address createDumAddress(int value) {
    MutableBytes mutableBytes = MutableBytes.create(Address.SIZE);
    mutableBytes.set(0, (byte) value);
    return Address.wrap(mutableBytes);
  }

  public static Bytes32 createDumDigest(int value) {
    MutableBytes32 mutableBytes = MutableBytes32.create();
    mutableBytes.set(Bytes32.SIZE - 1, (byte) value);
    return mutableBytes;
  }

  public static MimcSafeBytes<Bytes32> createDumFullBytes(int value) {
    MutableBytes32 mutableBytes = MutableBytes32.create();
    mutableBytes.set(0, (byte) value);
    return safeByte32(mutableBytes);
  }
}
