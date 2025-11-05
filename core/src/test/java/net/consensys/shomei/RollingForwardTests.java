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

import static net.consensys.shomei.trie.ZKTrie.DEFAULT_TRIE_ROOT;
import static net.consensys.shomei.util.TestFixtureGenerator.getAccountOne;
import static net.consensys.shomei.util.TestFixtureGenerator.getAccountTwo;
import static net.consensys.shomei.util.TestFixtureGenerator.getContractStorageTrie;
import static net.consensys.shomei.util.bytes.PoseidonSafeBytesUtils.safeUInt256;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.consensys.shomei.storage.InMemoryStorageProvider;
import net.consensys.shomei.storage.TraceManager;
import net.consensys.shomei.storage.worldstate.InMemoryWorldStateStorage;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.json.JsonTraceParser;
import net.consensys.shomei.trie.storage.AccountTrieRepositoryWrapper;
import net.consensys.shomei.trie.trace.ReadZeroTrace;
import net.consensys.shomei.trie.trace.Trace;
import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.trielog.StorageSlotKey;
import net.consensys.shomei.trielog.TrieLogLayer;
import net.consensys.shomei.util.bytes.PoseidonSafeBytes;
import net.consensys.shomei.worldview.ZkEvmWorldState;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RollingForwardTests {

  private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();

  private final InMemoryStorageProvider storageProvider = new InMemoryStorageProvider();
  private final TraceManager traceManager = storageProvider.getTraceManager();
  private final ZkEvmWorldState zkEvmWorldState =
      new ZkEvmWorldState(storageProvider.getWorldStateStorage(), traceManager);

  @BeforeEach
  public void setup() {
    JSON_OBJECT_MAPPER.registerModules(JsonTraceParser.modules);
  }

  @Test
  public void rollingForwardZeroReadAccount() throws JsonProcessingException {

    MutableZkAccount account = getAccountOne();
    MutableZkAccount missingAccount = getAccountTwo();

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    final List<Trace> expectedTraces = new ArrayList<>();
    expectedTraces.add(
        accountStateTrieOne.readWithTrace(missingAccount.getHkey(), missingAccount.getAddress()));
    expectedTraces.add(
        accountStateTrieOne.putWithTrace(
            account.getHkey(), account.getAddress(), account.getEncodedBytes()));

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());
    assertThat(topRootHash)
        .isEqualTo(
            Hash.fromHexString(
                "0x6b5d2e111a55e55826396df03ac3d0055dc4a88671edbcc8274db3f0117e0f97"));

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(account.getAddress(), null, account);
    trieLogLayer.addAccountChange(missingAccount.getAddress(), null, null);

    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, true);

    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
    assertThat(
            JSON_OBJECT_MAPPER.writeValueAsString(
                traceManager
                    .getTrace(0)
                    .map(bytes -> Trace.deserialize(RLP.input(bytes)))
                    .orElseThrow()))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(expectedTraces));
  }

  @Test
  public void rollingForwardOneAccount() throws JsonProcessingException {

    MutableZkAccount account = getAccountOne();

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    Trace expectedTrace =
        accountStateTrieOne.putWithTrace(
            account.getHkey(), account.getAddress(), account.getEncodedBytes());

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());
    assertThat(topRootHash)
        .isEqualTo(
            Hash.fromHexString(
                "0x6b5d2e111a55e55826396df03ac3d0055dc4a88671edbcc8274db3f0117e0f97"));

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(account.getAddress(), null, account);

    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, true);

    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
    assertThat(
            JSON_OBJECT_MAPPER.writeValueAsString(
                traceManager
                    .getTrace(0)
                    .map(bytes -> Trace.deserialize(RLP.input(bytes)))
                    .orElseThrow()))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(List.of(expectedTrace)));
  }

  @Test
  public void rollingForwardUpdatingAccount() throws JsonProcessingException {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    MutableZkAccount account = getAccountOne();
    accountStateTrieOne.putWithTrace(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());

    // update account
    MutableZkAccount accountUpdated = new MutableZkAccount(account);
    accountUpdated.setBalance(safeUInt256(UInt256.valueOf(100L)));

    Trace expectedTrace =
        accountStateTrieOne.putWithTrace(
            accountUpdated.getHkey(),
            accountUpdated.getAddress(),
            accountUpdated.getEncodedBytes());

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());
    assertThat(topRootHash)
        .isEqualTo(
            Hash.fromHexString(
                "0x2272f54e6eaac537629f136b4c6080c635db2b1135c4f94f4c34fff146c58471"));

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(account.getAddress(), null, account);

    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    trieLogLayer2.addAccountChange(account.getAddress(), account, accountUpdated);

    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, true);
    assertThat(zkEvmWorldState.getStateRootHash())
        .isEqualTo(
            Hash.fromHexString(
                "0x6b5d2e111a55e55826396df03ac3d0055dc4a88671edbcc8274db3f0117e0f97"));

    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, true);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
    assertThat(
            JSON_OBJECT_MAPPER.writeValueAsString(
                traceManager
                    .getTrace(0)
                    .map(bytes -> Trace.deserialize(RLP.input(bytes)))
                    .orElseThrow()))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(List.of(expectedTrace)));
  }

  @Test
  public void rollingForwardTwoAccounts() throws JsonProcessingException {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    List<Trace> expectedTraces = new ArrayList<>();
    MutableZkAccount account = getAccountTwo();
    expectedTraces.add(
        accountStateTrieOne.putWithTrace(
            account.getHkey(),
            account.getAddress(),
            account.getEncodedBytes())); // respect the order of hkey because they are in the same
    // batch
    MutableZkAccount secondAccount = getAccountOne();
    expectedTraces.add(
        accountStateTrieOne.putWithTrace(
            secondAccount.getHkey(), secondAccount.getAddress(), secondAccount.getEncodedBytes()));

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(secondAccount.getAddress(), null, secondAccount);
    trieLogLayer.addAccountChange(account.getAddress(), null, account);

    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);

    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, true);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
    assertThat(
            JSON_OBJECT_MAPPER.writeValueAsString(
                traceManager
                    .getTrace(0)
                    .map(bytes -> Trace.deserialize(RLP.input(bytes)))
                    .orElseThrow()))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(expectedTraces));
  }

  @Test
  public void rollingForwardContractWithoutStorageLoadTrieOnlyOnce()
      throws JsonProcessingException {

    MutableZkAccount contract = getAccountTwo();
    StorageSlotKey storageSlotKey = new StorageSlotKey(UInt256.ZERO);

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    final AccountKey contractAccountKey =
        trieLogLayer.addAccountChange(contract.getAddress(), null, contract);
    trieLogLayer.addStorageChange(
        contractAccountKey, storageSlotKey.slotKey().getOriginalUnsafeValue(), null, null);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, true);

    MutableZkAccount contractU = getAccountTwo();
    contractU.setBalance(safeUInt256(UInt256.valueOf(1L)));
    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    final AccountKey contractAccountKey2 =
        trieLogLayer2.addAccountChange(contract.getAddress(), contract, contractU);
    trieLogLayer2.addStorageChange(
        contractAccountKey2, storageSlotKey.slotKey().getOriginalUnsafeValue(), null, null);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(1L, null, true);

    List<Trace> foundTraces =
        traceManager.getTrace(1).map(bytes -> Trace.deserialize(RLP.input(bytes))).stream()
            .flatMap(List::stream)
            .filter(
                trace ->
                    trace
                        .getLocation()
                        .equals(contractAccountKey2.address().getOriginalUnsafeValue()))
            .toList();
    assertThat(foundTraces).hasSize(1);
    assertThat(((ReadZeroTrace) foundTraces.get(0)).getNextFreeNode()).isEqualTo(2);
  }

  @Test
  public void rollingForwardContractWithStorage() throws JsonProcessingException {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    List<Trace> expectedTraces = new ArrayList<>();

    MutableZkAccount contract = getAccountTwo();
    StorageSlotKey storageSlotKey = new StorageSlotKey(UInt256.valueOf(14));
    PoseidonSafeBytes<UInt256> slotValue = safeUInt256(UInt256.valueOf(12));
    ZKTrie contractStorageTrie = getContractStorageTrie(contract);
    expectedTraces.add(
        updateTraceStorageLocation(
            contract.getAddress(),
            contractStorageTrie.putWithTrace(
                storageSlotKey.slotHash(), storageSlotKey.slotKey(), slotValue)));
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    // add contract
    expectedTraces.add(
        accountStateTrieOne.putWithTrace(
            contract.getHkey(),
            contract.getAddress(),
            contract.getEncodedBytes())); // respect the order of hkey because they are in the same

    // add simple account
    MutableZkAccount simpleAccount = getAccountOne();
    expectedTraces.add(
        accountStateTrieOne.putWithTrace(
            simpleAccount.getHkey(), simpleAccount.getAddress(), simpleAccount.getEncodedBytes()));

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    final AccountKey contractAccountKey =
        trieLogLayer.addAccountChange(contract.getAddress(), null, contract);
    trieLogLayer.addAccountChange(simpleAccount.getAddress(), null, simpleAccount);
    trieLogLayer.addStorageChange(
        contractAccountKey,
        storageSlotKey.slotKey().getOriginalUnsafeValue(),
        null,
        slotValue.getOriginalUnsafeValue());

    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);

    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, true);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
    assertThat(
            JSON_OBJECT_MAPPER.writeValueAsString(
                traceManager
                    .getTrace(0)
                    .map(bytes -> Trace.deserialize(RLP.input(bytes)))
                    .orElseThrow()))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(expectedTraces));
  }

  @Test
  public void rollingForwardContractWithStorageWithReadNonZero() throws JsonProcessingException {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    MutableZkAccount contract = getAccountTwo();
    StorageSlotKey storageSlotKey = new StorageSlotKey(UInt256.valueOf(14));
    PoseidonSafeBytes<UInt256> slotValue = safeUInt256(UInt256.valueOf(12));
    ZKTrie contractStorageTrie = getContractStorageTrie(contract);
    contractStorageTrie.putWithTrace(
        storageSlotKey.slotHash(), storageSlotKey.slotKey(), slotValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    // add contract
    accountStateTrieOne.putWithTrace(
        contract.getHkey(),
        contract.getAddress(),
        contract.getEncodedBytes()); // respect the order of hkey because they are in the same batch

    // read non zero slot of the contract
    List<Trace> expectedTraces = new ArrayList<>();
    expectedTraces.add(
        accountStateTrieOne.readWithTrace(contract.getHkey(), contract.getAddress()));
    expectedTraces.add(
        updateTraceStorageLocation(
            contract.getAddress(),
            contractStorageTrie.readWithTrace(
                storageSlotKey.slotHash(), storageSlotKey.slotKey())));

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    final AccountKey accountKey2 =
        trieLogLayer.addAccountChange(contract.getAddress(), null, contract);
    trieLogLayer.addStorageChange(
        accountKey2,
        storageSlotKey.slotKey().getOriginalUnsafeValue(),
        null,
        slotValue.getOriginalUnsafeValue());

    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    trieLogLayer2.addAccountChange(contract.getAddress(), contract, contract);
    trieLogLayer2.addStorageChange(
        accountKey2,
        storageSlotKey.slotKey().getOriginalUnsafeValue(),
        slotValue.getOriginalUnsafeValue(),
        slotValue.getOriginalUnsafeValue());

    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, true);

    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, true);
    assertThat(
            JSON_OBJECT_MAPPER.writeValueAsString(
                traceManager
                    .getTrace(0)
                    .map(bytes -> Trace.deserialize(RLP.input(bytes)))
                    .orElseThrow()))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(expectedTraces));
  }

  @Test
  public void rollingForwardAccountSelfDestructWithStorage() throws JsonProcessingException {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    // create contract with storage
    MutableZkAccount contract = getAccountTwo();
    StorageSlotKey storageSlotKey = new StorageSlotKey(UInt256.valueOf(14));
    PoseidonSafeBytes<UInt256> slotValue = safeUInt256(UInt256.valueOf(12));
    ZKTrie contractStorageTrie = getContractStorageTrie(contract);
    contractStorageTrie.putWithTrace(
        storageSlotKey.slotHash(), storageSlotKey.slotKey(), slotValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    accountStateTrieOne.putWithTrace(
        contract.getHkey(), contract.getAddress(), contract.getEncodedBytes());

    final List<Trace> expectedTraces = new ArrayList<>();
    // read slot before selfdestruct
    expectedTraces.add(
        updateTraceStorageLocation(
            contract.getAddress(),
            contractStorageTrie.readWithTrace(
                storageSlotKey.slotHash(), storageSlotKey.slotKey())));

    // selfdestruct contract
    expectedTraces.add(
        accountStateTrieOne.removeWithTrace(contract.getHkey(), contract.getAddress()));

    // recreate contract
    ZKTrie newContractStorageTrie = getContractStorageTrie(contract);
    expectedTraces.add(
        updateTraceStorageLocation(
            contract.getAddress(),
            newContractStorageTrie.putWithTrace(
                storageSlotKey.slotHash(), storageSlotKey.slotKey(), slotValue)));
    contract.setStorageRoot(Hash.wrap(newContractStorageTrie.getTopRootHash()));
    expectedTraces.add(
        accountStateTrieOne.putWithTrace(
            contract.getHkey(), contract.getAddress(), contract.getEncodedBytes()));

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());

    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);

    // create account with the rolling
    TrieLogLayer trieLogLayer = new TrieLogLayer();
    AccountKey accountKey = trieLogLayer.addAccountChange(contract.getAddress(), null, contract);
    trieLogLayer.addStorageChange(
        accountKey,
        storageSlotKey.slotKey().getOriginalUnsafeValue(),
        null,
        slotValue.getOriginalUnsafeValue());
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, true);

    // delete and recreate the contract in the same batch
    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    accountKey = trieLogLayer2.addAccountChange(contract.getAddress(), contract, contract, true);
    trieLogLayer2.addStorageChange(
        accountKey,
        storageSlotKey.slotKey().getOriginalUnsafeValue(),
        slotValue.getOriginalUnsafeValue(),
        slotValue.getOriginalUnsafeValue());
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, true);

    assertThat(
            JSON_OBJECT_MAPPER.writeValueAsString(
                traceManager
                    .getTrace(0)
                    .map(bytes -> Trace.deserialize(RLP.input(bytes)))
                    .orElseThrow()))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(expectedTraces));
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
  }

  @Test
  public void rollingForwardAccountSelfDestructWithDifferentStorageValue()
      throws JsonProcessingException {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    // create contract with storage
    MutableZkAccount contract = getAccountTwo();
    StorageSlotKey storageSlotKey = new StorageSlotKey(UInt256.valueOf(14));
    PoseidonSafeBytes<UInt256> slotValue = safeUInt256(UInt256.valueOf(12));
    ZKTrie contractStorageTrie = getContractStorageTrie(contract);
    contractStorageTrie.putWithTrace(
        storageSlotKey.slotHash(), storageSlotKey.slotKey(), slotValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    accountStateTrieOne.putWithTrace(
        contract.getHkey(), contract.getAddress(), contract.getEncodedBytes());

    List<Trace> expectedTraces = new ArrayList<>();
    // read slot before selfdestruct
    expectedTraces.add(
        updateTraceStorageLocation(
            contract.getAddress(),
            contractStorageTrie.readWithTrace(
                storageSlotKey.slotHash(), storageSlotKey.slotKey())));
    // selfdestruct contract
    expectedTraces.add(
        accountStateTrieOne.removeWithTrace(contract.getHkey(), contract.getAddress()));

    // recreate contract
    MutableZkAccount updatedContract = getAccountTwo();
    ZKTrie newContractStorageTrie = getContractStorageTrie(updatedContract);
    PoseidonSafeBytes<UInt256> newSlotValue = safeUInt256(UInt256.valueOf(13));
    expectedTraces.add(
        updateTraceStorageLocation(
            contract.getAddress(),
            newContractStorageTrie.putWithTrace(
                storageSlotKey.slotHash(), storageSlotKey.slotKey(), newSlotValue)));
    updatedContract.setStorageRoot(Hash.wrap(newContractStorageTrie.getTopRootHash()));
    expectedTraces.add(
        accountStateTrieOne.putWithTrace(
            updatedContract.getHkey(),
            updatedContract.getAddress(),
            updatedContract.getEncodedBytes()));

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());

    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);

    // create account with the rolling
    TrieLogLayer trieLogLayer = new TrieLogLayer();
    AccountKey accountKey = trieLogLayer.addAccountChange(contract.getAddress(), null, contract);
    trieLogLayer.addStorageChange(
        accountKey,
        storageSlotKey.slotKey().getOriginalUnsafeValue(),
        null,
        slotValue.getOriginalUnsafeValue());
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, true);

    // delete and recreate the contract in the same batch
    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    accountKey =
        trieLogLayer2.addAccountChange(contract.getAddress(), contract, updatedContract, true);
    trieLogLayer2.addStorageChange(
        accountKey,
        storageSlotKey,
        slotValue.getOriginalUnsafeValue(),
        newSlotValue.getOriginalUnsafeValue(),
        true);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, true);

    assertThat(
            JSON_OBJECT_MAPPER.writeValueAsString(
                traceManager
                    .getTrace(0)
                    .map(bytes -> Trace.deserialize(RLP.input(bytes)))
                    .orElseThrow()))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(expectedTraces));
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
  }

  @Test
  public void rollingForwardAccountSelfDestructWithDifferentStorageKeyAndValue()
      throws JsonProcessingException {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    // create contract with storage
    MutableZkAccount contract = getAccountTwo();
    StorageSlotKey storageSlotKey = new StorageSlotKey(UInt256.valueOf(14));
    PoseidonSafeBytes<UInt256> slotValue = safeUInt256(UInt256.valueOf(12));
    ZKTrie contractStorageTrie = getContractStorageTrie(contract);
    contractStorageTrie.putWithTrace(
        storageSlotKey.slotHash(), storageSlotKey.slotKey(), slotValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    accountStateTrieOne.putWithTrace(
        contract.getHkey(), contract.getAddress(), contract.getEncodedBytes());

    List<Trace> expectedTraces = new ArrayList<>();
    // read slot before selfdestruct
    expectedTraces.add(
        updateTraceStorageLocation(
            contract.getAddress(),
            contractStorageTrie.readWithTrace(
                storageSlotKey.slotHash(), storageSlotKey.slotKey())));
    // selfdestruct contract
    expectedTraces.add(
        accountStateTrieOne.removeWithTrace(contract.getHkey(), contract.getAddress()));
    // recreate contract

    MutableZkAccount updatedContract = getAccountTwo();
    ZKTrie newContractStorageTrie = getContractStorageTrie(updatedContract);
    StorageSlotKey newStorageSlotKey = new StorageSlotKey(UInt256.valueOf(146));
    PoseidonSafeBytes<UInt256> newSlotValue = safeUInt256(UInt256.valueOf(13));
    expectedTraces.add(
        updateTraceStorageLocation(
            contract.getAddress(),
            newContractStorageTrie.putWithTrace(
                newStorageSlotKey.slotHash(), newStorageSlotKey.slotKey(), newSlotValue)));
    updatedContract.setStorageRoot(Hash.wrap(newContractStorageTrie.getTopRootHash()));
    expectedTraces.add(
        accountStateTrieOne.putWithTrace(
            updatedContract.getHkey(),
            updatedContract.getAddress(),
            updatedContract.getEncodedBytes()));

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());

    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);

    // create account with the rolling
    TrieLogLayer trieLogLayer = new TrieLogLayer();
    AccountKey accountKey = trieLogLayer.addAccountChange(contract.getAddress(), null, contract);
    trieLogLayer.addStorageChange(
        accountKey,
        storageSlotKey.slotKey().getOriginalUnsafeValue(),
        null,
        slotValue.getOriginalUnsafeValue());
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, true);

    // delete and recreate the contract in the same batch
    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    accountKey =
        trieLogLayer2.addAccountChange(contract.getAddress(), contract, updatedContract, true);
    trieLogLayer2.addStorageChange(
        accountKey, storageSlotKey, slotValue.getOriginalUnsafeValue(), null, true);
    trieLogLayer2.addStorageChange(
        accountKey, newStorageSlotKey, null, newSlotValue.getOriginalUnsafeValue());
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, true);

    assertThat(
            JSON_OBJECT_MAPPER.writeValueAsString(
                traceManager
                    .getTrace(0)
                    .map(bytes -> Trace.deserialize(RLP.input(bytes)))
                    .orElseThrow()))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(expectedTraces));
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
  }

  private Trace updateTraceStorageLocation(
      final PoseidonSafeBytes<Address> address, final Trace trace) {
    trace.setLocation(address.getOriginalUnsafeValue());
    return trace;
  }
}
