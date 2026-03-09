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

import static net.consensys.shomei.util.TestFixtureGenerator.getContractStorageTrie;
import static net.consensys.shomei.util.bytes.PoseidonSafeBytesUtils.safeUInt256;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.context.ShomeiContext;
import net.consensys.shomei.storage.InMemoryStorageProvider;
import net.consensys.shomei.storage.StorageProvider;
import net.consensys.shomei.storage.ZkWorldStateArchive;
import net.consensys.shomei.storage.worldstate.InMemoryWorldStateStorage;
import net.consensys.shomei.storage.worldstate.WorldStateStorage;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.storage.InMemoryStorage;
import net.consensys.shomei.trielog.TrieLogLayer;
import net.consensys.shomei.trielog.TrieLogLayerConverter;
import net.consensys.shomei.trielog.ZkTrieLogFactory;
import net.consensys.shomei.util.TestFixtureGenerator;
import net.consensys.shomei.util.bytes.PoseidonSafeBytes;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.common.PmtStateTrieAccountValue;
import org.junit.jupiter.api.Test;

public class TrieLogShippingTests {

  @Test
  public void testTrielogShippingWithNewContractUpdate() {
    // Initialize in-memory storage and state trie
    final InMemoryStorage storage = new InMemoryStorage();
    ZKTrie accountStateTrie = ZKTrie.createTrie(storage);

    // Create a contract with initial storage
    MutableZkAccount contract = TestFixtureGenerator.getAccountTwo();
    PoseidonSafeBytes<UInt256> slotKey = safeUInt256(UInt256.valueOf(14));
    PoseidonSafeBytes<UInt256> slotValue = safeUInt256(UInt256.valueOf(12));
    ZKTrie contractStorageTrie = getContractStorageTrie(contract);

    // Update contract storage and state trie
    contractStorageTrie.putWithTrace(slotKey.hash(), slotKey, slotValue);
    contract.setStorageRoot(contractStorageTrie.getTopRootHash());
    accountStateTrie.putWithTrace(
        contract.getHkey(), contract.getAddress(), contract.getEncodedBytes());

    // Save the root hash before updating the storage
    Bytes32 topRootHashBeforeUpdate = accountStateTrie.getTopRootHash();

    // Update storage with new value
    final PoseidonSafeBytes<UInt256> newStorageValue = safeUInt256(UInt256.valueOf(22));
    contractStorageTrie.putWithTrace(slotKey.hash(), slotKey, newStorageValue);
    contract.setStorageRoot(contractStorageTrie.getTopRootHash());
    accountStateTrie.putWithTrace(
        contract.getHkey(), contract.getAddress(), contract.getEncodedBytes());

    // Save the root hash after updating the storage
    Bytes32 topRootHashAfterUpdate = accountStateTrie.getTopRootHash();

    // Simulate TrieLogLayer from Besu before the update
    org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.TrieLogLayer trieLogLayerBefore =
        new org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.TrieLogLayer();
    Address contractAddress = Address.wrap(contract.getAddress().getOriginalUnsafeValue());
    trieLogLayerBefore.addAccountChange(
        contractAddress,
        null,
        new PmtStateTrieAccountValue(
            contract.nonce.getOriginalUnsafeValue().toLong(),
            Wei.of(contract.balance.getOriginalUnsafeValue()),
            Hash.wrap(Bytes32.random()),
            Hash.wrap(contract.keccakCodeHash.getOriginalUnsafeValue())));
    trieLogLayerBefore.setBlockHash(Hash.wrap(Bytes32.random()));
    trieLogLayerBefore.setBlockNumber(0);
    trieLogLayerBefore.addStorageChange(
        contractAddress,
        new StorageSlotKey(slotKey.getOriginalUnsafeValue()),
        null,
        slotValue.getOriginalUnsafeValue());

    // Simulate TrieLogLayer from Besu after the update
    org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.TrieLogLayer trieLogLayerAfter =
        new org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.TrieLogLayer();
    trieLogLayerAfter.addAccountChange(
        contractAddress,
        new PmtStateTrieAccountValue(
            contract.nonce.getOriginalUnsafeValue().toLong(),
            Wei.of(contract.balance.getOriginalUnsafeValue()),
            Hash.wrap(Bytes32.random()),
            Hash.wrap(contract.keccakCodeHash.getOriginalUnsafeValue())),
        new PmtStateTrieAccountValue(
            contract.nonce.getOriginalUnsafeValue().toLong(),
            Wei.of(contract.balance.getOriginalUnsafeValue()),
            Hash.wrap(Bytes32.random()),
            Hash.wrap(contract.keccakCodeHash.getOriginalUnsafeValue())));
    trieLogLayerAfter.setBlockHash(Hash.wrap(Bytes32.random()));
    trieLogLayerAfter.setBlockNumber(1);
    trieLogLayerAfter.addStorageChange(
        contractAddress,
        new StorageSlotKey(slotKey.getOriginalUnsafeValue()),
        slotValue.getOriginalUnsafeValue(),
        newStorageValue.getOriginalUnsafeValue());

    // Initialize the ShomeiContext and world state entry point
    ZkTrieLogFactory zkTrieLogFactory =
        new ZkTrieLogFactory(ShomeiContext.ShomeiContextImpl.getOrCreate());
    InMemoryWorldStateStorage worldStateStorage = new InMemoryWorldStateStorage();
    StorageProvider inMemoryStorageProvider =
        new InMemoryStorageProvider() {
          @Override
          public WorldStateStorage getWorldStateStorage() {
            return worldStateStorage;
          }
        };

    ZkWorldStateArchive worldStateArchive = new ZkWorldStateArchive(inMemoryStorageProvider);

    // Verify initial world state matches the default state root hash
    assertThat(worldStateArchive.getHeadWorldState().getStateRootHash())
        .isEqualTo(ZKTrie.DEFAULT_TRIE_ROOT);

    // Decode and apply TrieLogLayer before the update
    TrieLogLayer decodedLayerBefore =
        new TrieLogLayerConverter(inMemoryStorageProvider.getWorldStateStorage())
            .decodeTrieLog(RLP.input(Bytes.wrap(zkTrieLogFactory.serialize(trieLogLayerBefore))));
    worldStateArchive.applyTrieLog(0, false, decodedLayerBefore);

    // Assert world state root matches before update
    assertThat(worldStateArchive.getHeadWorldState().getStateRootHash())
        .isEqualTo(topRootHashBeforeUpdate);

    // Decode and apply TrieLogLayer after the update
    TrieLogLayer decodedLayerAfter =
        new TrieLogLayerConverter(inMemoryStorageProvider.getWorldStateStorage())
            .decodeTrieLog(RLP.input(Bytes.wrap(zkTrieLogFactory.serialize(trieLogLayerAfter))));
    worldStateArchive.applyTrieLog(1, false, decodedLayerAfter);

    // Assert world state root matches after update
    assertThat(worldStateArchive.getHeadWorldState().getStateRootHash())
        .isEqualTo(topRootHashAfterUpdate);
  }

  @Test
  public void testTrielogWithStorageOnlyChangeNoAccountChange() {
    MutableZkAccount contract = TestFixtureGenerator.getAccountTwo();
    PoseidonSafeBytes<UInt256> slotKey = safeUInt256(UInt256.valueOf(14));
    PoseidonSafeBytes<UInt256> slotValue = safeUInt256(UInt256.valueOf(12));
    ZKTrie contractStorageTrie = getContractStorageTrie(contract);

    contractStorageTrie.putWithTrace(slotKey.hash(), slotKey, slotValue);
    contract.setStorageRoot(contractStorageTrie.getTopRootHash());

    Address contractAddress2 = Address.wrap(contract.getAddress().getOriginalUnsafeValue());

    // Block 0: create the account with initial storage
    org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.TrieLogLayer block0 =
        new org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.TrieLogLayer();
    block0.addAccountChange(
        contractAddress2,
        null,
        new PmtStateTrieAccountValue(
            contract.nonce.getOriginalUnsafeValue().toLong(),
            Wei.of(contract.balance.getOriginalUnsafeValue()),
            Hash.wrap(Bytes32.random()),
            Hash.wrap(contract.keccakCodeHash.getOriginalUnsafeValue())));
    block0.setBlockHash(Hash.wrap(Bytes32.random()));
    block0.setBlockNumber(0);
    block0.addStorageChange(
        contractAddress2,
        new StorageSlotKey(slotKey.getOriginalUnsafeValue()),
        null,
        slotValue.getOriginalUnsafeValue());

    // Block 1: storage-only change — no account-level change, only a storage
    // read (old == new). This simulates the pattern from block 27470962 where an
    // account has SLOAD activity without nonce/balance changes.
    org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.TrieLogLayer block1 =
        new org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.TrieLogLayer();
    block1.setBlockHash(Hash.wrap(Bytes32.random()));
    block1.setBlockNumber(1);
    block1.addStorageChange(
        contractAddress2,
        new StorageSlotKey(slotKey.getOriginalUnsafeValue()),
        slotValue.getOriginalUnsafeValue(),
        slotValue.getOriginalUnsafeValue());

    ZkTrieLogFactory zkTrieLogFactory =
        new ZkTrieLogFactory(ShomeiContext.ShomeiContextImpl.getOrCreate());
    InMemoryWorldStateStorage worldStateStorage = new InMemoryWorldStateStorage();
    StorageProvider inMemoryStorageProvider =
        new InMemoryStorageProvider() {
          @Override
          public WorldStateStorage getWorldStateStorage() {
            return worldStateStorage;
          }
        };

    ZkWorldStateArchive worldStateArchive = new ZkWorldStateArchive(inMemoryStorageProvider);

    // Apply block 0 (creates account + storage)
    TrieLogLayer decodedBlock0 =
        new TrieLogLayerConverter(inMemoryStorageProvider.getWorldStateStorage())
            .decodeTrieLog(RLP.input(Bytes.wrap(zkTrieLogFactory.serialize(block0))));
    worldStateArchive.applyTrieLog(0, false, decodedBlock0);

    Bytes32 stateAfterBlock0 = worldStateArchive.getHeadWorldState().getStateRootHash();
    assertThat(stateAfterBlock0).isNotEqualTo(ZKTrie.DEFAULT_TRIE_ROOT);

    // Apply block 1 (storage-only, no account change) — before the fix this
    // threw "invalid trie log exception" because maybeAccountIndex was empty
    TrieLogLayer decodedBlock1 =
        new TrieLogLayerConverter(inMemoryStorageProvider.getWorldStateStorage())
            .decodeTrieLog(RLP.input(Bytes.wrap(zkTrieLogFactory.serialize(block1))));

    assertThat(decodedBlock1).isNotNull();
    worldStateArchive.applyTrieLog(1, false, decodedBlock1);

    // Storage read (old == new) should not change the state root
    assertThat(worldStateArchive.getHeadWorldState().getStateRootHash())
        .isEqualTo(stateAfterBlock0);
  }
}
