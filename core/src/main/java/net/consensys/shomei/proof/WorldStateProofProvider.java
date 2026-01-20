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
package net.consensys.shomei.proof;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import net.consensys.shomei.ZkAccount;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.proof.MerkleInclusionProof;
import net.consensys.shomei.trie.proof.MerkleProof;
import net.consensys.shomei.trie.storage.AccountTrieRepositoryWrapper;
import net.consensys.shomei.trie.storage.StorageTrieRepositoryWrapper;
import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.trielog.StorageSlotKey;
import net.consensys.shomei.worldview.ZkEvmWorldState;

public class WorldStateProofProvider {

  private final ZkEvmWorldState zkEvmWorldState;

  public WorldStateProofProvider(final ZkEvmWorldState zkEvmWorldState) {
    this.zkEvmWorldState = zkEvmWorldState;
  }

  public MerkleAccountProof getAccountProof(
      final AccountKey accountKey, final List<StorageSlotKey> accountStorageKeys) {

    final ZKTrie accountTrie =
        ZKTrie.loadTrie(
            zkEvmWorldState.getStateRootHash(),
            new AccountTrieRepositoryWrapper(zkEvmWorldState.getZkEvmWorldStateStorage()));
    final MerkleProof accountProof =
        accountTrie.getProof(accountKey.accountHash(), accountKey.address());
    if (accountProof instanceof MerkleInclusionProof merkleInclusionProof) {
      ZkAccount zkAccount =
          ZkAccount.fromEncodedBytes(
              accountKey, merkleInclusionProof.getProof().getValue().orElseThrow());
      if (!zkAccount.getStorageRoot().equals(ZKTrie.DEFAULT_TRIE_ROOT)) {
        final List<MerkleProof> storageProofs =
            getStorageProofs(zkAccount, merkleInclusionProof.getLeafIndex(), accountStorageKeys);
        return new MerkleAccountProof(accountProof, storageProofs);
      } else {
        return new MerkleAccountProof(accountProof, new ArrayList<>());
      }
    } else {
      return new MerkleAccountProof(accountProof, new ArrayList<>());
    }
  }

  private List<MerkleProof> getStorageProofs(
      final ZkAccount account,
      final Long accountIndex,
      final List<StorageSlotKey> accountStorageKeys) {
    final ZKTrie storageTrie =
        ZKTrie.loadTrie(
            account.getStorageRoot(),
            new StorageTrieRepositoryWrapper(
                accountIndex, zkEvmWorldState.getZkEvmWorldStateStorage()));
    return accountStorageKeys.stream()
        .map(key -> storageTrie.getProof(key.slotHash(), key.slotKey()))
        .collect(Collectors.toList());
  }
}
