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
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trielog.TrieLogAccountValue;
import net.consensys.shomei.trielog.TrieLogLayer;
import net.consensys.shomei.worldview.ZKEvmWorldState;
import net.consensys.zkevm.HashProvider;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.Test;

public class RollingTests {

  private static final Hash H_KEY = HashProvider.mimc(createDumAddress(36));
  private static final Hash H_KEY_2 = HashProvider.mimc(createDumAddress(47));

  private static final ZkAccount ZK_ACCOUNT =
      new ZkAccount(
          H_KEY,
          65,
          Wei.of(835),
          EMPTY_TRIE_ROOT,
          EMPTY_KECCAK_CODE_HASH,
          EMPTY_CODE_HASH,
          0L,
          false);

  private static final ZkAccount ZK_ACCOUNT_2 =
      new ZkAccount(
          H_KEY_2,
          65,
          Wei.of(835),
          EMPTY_TRIE_ROOT,
          EMPTY_KECCAK_CODE_HASH,
          EMPTY_CODE_HASH,
          0L,
          false);

  @Test
  public void rollingForwardOneAccount() {

    ZKTrie accountStateTrieOne = ZKTrie.createInMemoryTrie();

    accountStateTrieOne.put(ZK_ACCOUNT.getHkey(), ZK_ACCOUNT.serializeAccount());
    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());
    assertThat(topRootHash)
        .isEqualTo(
            Hash.fromHexString("828dd273c29ec50463bd7fac90e06b04b4010b72fe880df82e299bf162046e41"));

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(H_KEY, null, new TrieLogAccountValue(ZK_ACCOUNT));

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(EMPTY_TRIE_ROOT, Hash.EMPTY);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(topRootHash);
  }

  @Test
  public void rollingForwardTwoAccount() {

    ZKTrie accountStateTrieOne = ZKTrie.createInMemoryTrie();

    accountStateTrieOne.put(ZK_ACCOUNT.getHkey(), ZK_ACCOUNT.serializeAccount());
    accountStateTrieOne.put(ZK_ACCOUNT_2.getHkey(), ZK_ACCOUNT_2.serializeAccount());
    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(H_KEY, null, new TrieLogAccountValue(ZK_ACCOUNT));
    trieLogLayer.addAccountChange(H_KEY_2, null, new TrieLogAccountValue(ZK_ACCOUNT_2));

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(EMPTY_TRIE_ROOT, Hash.EMPTY);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(topRootHash);
  }
}
