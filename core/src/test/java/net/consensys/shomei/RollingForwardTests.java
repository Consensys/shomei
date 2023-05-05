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

import static net.consensys.shomei.trie.ZKTrie.DEFAULT_TRIE_ROOT;
import static net.consensys.shomei.util.TestFixtureGenerator.getAccountOne;
import static net.consensys.shomei.util.TestFixtureGenerator.getAccountTwo;
import static net.consensys.shomei.util.TestFixtureGenerator.getContractStorageTrie;
import static net.consensys.shomei.util.bytes.MimcSafeBytes.safeUInt256;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.storage.InMemoryWorldStateRepository;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.json.JsonTraceParser;
import net.consensys.shomei.trie.proof.Trace;
import net.consensys.shomei.trie.storage.AccountTrieRepositoryWrapper;
import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.trielog.StorageSlotKey;
import net.consensys.shomei.trielog.TrieLogLayer;
import net.consensys.shomei.util.bytes.MimcSafeBytes;
import net.consensys.shomei.worldview.ZKEvmWorldState;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.Before;
import org.junit.Test;

public class RollingForwardTests {

  private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();

  @Before
  public void setup() {
    JSON_OBJECT_MAPPER.registerModules(JsonTraceParser.modules);
  }

  @Test
  public void rollingForwardZeroReadAccount() throws JsonProcessingException {

    MutableZkAccount account = getAccountOne();
    MutableZkAccount missingAccount = getAccountTwo();

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateRepository()));

    final List<Trace> expectedTraces = new ArrayList<>();
    expectedTraces.add(
        accountStateTrieOne.readAndProve(missingAccount.getHkey(), missingAccount.getAddress()));
    expectedTraces.add(
        accountStateTrieOne.putAndProve(
            account.getHkey(), account.getAddress(), account.getEncodedBytes()));

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());
    assertThat(topRootHash)
        .isEqualTo(
            Hash.fromHexString("11aed727a707f2f1962e399bd4787153ba0e69b7224e8eecf4d1e4e6a8e8dafd"));

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(account.getAddress(), null, account);
    trieLogLayer.addAccountChange(missingAccount.getAddress(), null, null);

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateRepository());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, false);

    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(zkEvmWorldState.getLastTraces()))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(expectedTraces));
  }

  @Test
  public void rollingForwardOneAccount() throws JsonProcessingException {

    MutableZkAccount account = getAccountOne();

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateRepository()));

    Trace expectedTrace =
        accountStateTrieOne.putAndProve(
            account.getHkey(), account.getAddress(), account.getEncodedBytes());

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());
    assertThat(topRootHash)
        .isEqualTo(
            Hash.fromHexString("11aed727a707f2f1962e399bd4787153ba0e69b7224e8eecf4d1e4e6a8e8dafd"));

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(account.getAddress(), null, account);

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateRepository());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, false);

    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(zkEvmWorldState.getLastTraces()))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(List.of(expectedTrace)));
  }

  @Test
  public void rollingForwardUpdatingAccount() throws JsonProcessingException {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateRepository()));

    MutableZkAccount account = getAccountOne();
    accountStateTrieOne.putAndProve(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());

    // update account
    MutableZkAccount accountUpdated = new MutableZkAccount(account);
    accountUpdated.setBalance(Wei.of(100));

    Trace expectedTrace =
        accountStateTrieOne.putAndProve(
            accountUpdated.getHkey(),
            accountUpdated.getAddress(),
            accountUpdated.getEncodedBytes());

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());
    assertThat(topRootHash)
        .isEqualTo(
            Hash.fromHexString(
                "0x271a0e17054a194a6a1e227ddfa4bec3f22c55a0b061c5056a089bba1ae24ec9"));

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(account.getAddress(), null, account);

    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    trieLogLayer2.addAccountChange(account.getAddress(), account, accountUpdated);

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateRepository());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash())
        .isEqualTo(
            Hash.fromHexString("11aed727a707f2f1962e399bd4787153ba0e69b7224e8eecf4d1e4e6a8e8dafd"));

    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(zkEvmWorldState.getLastTraces()))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(List.of(expectedTrace)));
  }

  @Test
  public void rollingForwardTwoAccounts() throws JsonProcessingException {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateRepository()));

    List<Trace> expectedTraces = new ArrayList<>();
    MutableZkAccount account = getAccountTwo();
    expectedTraces.add(
        accountStateTrieOne.putAndProve(
            account.getHkey(),
            account.getAddress(),
            account.getEncodedBytes())); // respect the order of hkey because they are in the same
    // batch
    MutableZkAccount secondAccount = getAccountOne();
    expectedTraces.add(
        accountStateTrieOne.putAndProve(
            secondAccount.getHkey(), secondAccount.getAddress(), secondAccount.getEncodedBytes()));

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(secondAccount.getAddress(), null, secondAccount);
    trieLogLayer.addAccountChange(account.getAddress(), null, account);

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateRepository());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);

    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(zkEvmWorldState.getLastTraces()))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(expectedTraces));
  }

  @Test
  public void rollingForwardContractWithStorage() throws JsonProcessingException {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateRepository()));

    List<Trace> expectedTraces = new ArrayList<>();

    MutableZkAccount contract = getAccountTwo();
    StorageSlotKey storageSlotKey = new StorageSlotKey(UInt256.valueOf(14));
    MimcSafeBytes<UInt256> slotValue = safeUInt256(UInt256.valueOf(12));
    ZKTrie contractStorageTrie = getContractStorageTrie(contract);
    expectedTraces.add(
        contractStorageTrie.putAndProve(
            storageSlotKey.slotHash(), storageSlotKey.slotKey(), slotValue));
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    // add contract
    expectedTraces.add(
        accountStateTrieOne.putAndProve(
            contract.getHkey(),
            contract.getAddress(),
            contract.getEncodedBytes())); // respect the order of hkey because they are in the same

    // add simple account
    MutableZkAccount simpleAccount = getAccountOne();
    expectedTraces.add(
        accountStateTrieOne.putAndProve(
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

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateRepository());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);

    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(zkEvmWorldState.getLastTraces()))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(expectedTraces));
  }

  @Test
  public void rollingForwardContractWithStorageWithReadNonZero() throws JsonProcessingException {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateRepository()));

    MutableZkAccount contract = getAccountTwo();
    StorageSlotKey storageSlotKey = new StorageSlotKey(UInt256.valueOf(14));
    MimcSafeBytes<UInt256> slotValue = safeUInt256(UInt256.valueOf(12));
    ZKTrie contractStorageTrie = getContractStorageTrie(contract);
    contractStorageTrie.putAndProve(storageSlotKey.slotHash(), storageSlotKey.slotKey(), slotValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    // add contract
    accountStateTrieOne.putAndProve(
        contract.getHkey(),
        contract.getAddress(),
        contract.getEncodedBytes()); // respect the order of hkey because they are in the same batch

    // read non zero slot of the contract
    List<Trace> expectedTraces = new ArrayList<>();
    expectedTraces.add(accountStateTrieOne.readAndProve(contract.getHkey(), contract.getAddress()));
    expectedTraces.add(
        contractStorageTrie.readAndProve(storageSlotKey.slotHash(), storageSlotKey.slotKey()));

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

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateRepository());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, false);

    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(zkEvmWorldState.getLastTraces()))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(expectedTraces));
  }

  @Test
  public void rollingForwardAccountSelfDestructWithStorage() throws JsonProcessingException {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateRepository()));

    // create contract with storage
    MutableZkAccount contract = getAccountTwo();
    StorageSlotKey storageSlotKey = new StorageSlotKey(UInt256.valueOf(14));
    MimcSafeBytes<UInt256> slotValue = safeUInt256(UInt256.valueOf(12));
    ZKTrie contractStorageTrie = getContractStorageTrie(contract);
    contractStorageTrie.putAndProve(storageSlotKey.slotHash(), storageSlotKey.slotKey(), slotValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    accountStateTrieOne.putAndProve(
        contract.getHkey(), contract.getAddress(), contract.getEncodedBytes());

    final List<Trace> expectedTraces = new ArrayList<>();
    // read slot before selfdestruct
    expectedTraces.add(
        contractStorageTrie.readAndProve(storageSlotKey.slotHash(), storageSlotKey.slotKey()));

    // selfdestruct contract
    expectedTraces.add(
        accountStateTrieOne.removeAndProve(contract.getHkey(), contract.getAddress()));

    // recreate contract
    ZKTrie newContractStorageTrie = getContractStorageTrie(contract);
    expectedTraces.add(
        newContractStorageTrie.putAndProve(
            storageSlotKey.slotHash(), storageSlotKey.slotKey(), slotValue));
    contract.setStorageRoot(Hash.wrap(newContractStorageTrie.getTopRootHash()));
    expectedTraces.add(
        accountStateTrieOne.putAndProve(
            contract.getHkey(), contract.getAddress(), contract.getEncodedBytes()));

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateRepository());
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
    zkEvmWorldState.commit(0L, null, false);

    // delete and recreate the contract in the same batch
    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    accountKey = trieLogLayer2.addAccountChange(contract.getAddress(), contract, contract, true);
    trieLogLayer2.addStorageChange(
        accountKey,
        storageSlotKey.slotKey().getOriginalUnsafeValue(),
        slotValue.getOriginalUnsafeValue(),
        slotValue.getOriginalUnsafeValue());
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, false);

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(zkEvmWorldState.getLastTraces()))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(expectedTraces));
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
  }

  @Test
  public void rollingForwardAccountSelfDestructWithDifferentStorageValue()
      throws JsonProcessingException {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateRepository()));

    // create contract with storage
    MutableZkAccount contract = getAccountTwo();
    StorageSlotKey storageSlotKey = new StorageSlotKey(UInt256.valueOf(14));
    MimcSafeBytes<UInt256> slotValue = safeUInt256(UInt256.valueOf(12));
    ZKTrie contractStorageTrie = getContractStorageTrie(contract);
    contractStorageTrie.putAndProve(storageSlotKey.slotHash(), storageSlotKey.slotKey(), slotValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    accountStateTrieOne.putAndProve(
        contract.getHkey(), contract.getAddress(), contract.getEncodedBytes());

    List<Trace> expectedTraces = new ArrayList<>();
    // read slot before selfdestruct
    expectedTraces.add(
        contractStorageTrie.readAndProve(storageSlotKey.slotHash(), storageSlotKey.slotKey()));
    // selfdestruct contract
    expectedTraces.add(
        accountStateTrieOne.removeAndProve(contract.getHkey(), contract.getAddress()));

    // recreate contract
    MutableZkAccount updatedContract = getAccountTwo();
    ZKTrie newContractStorageTrie = getContractStorageTrie(updatedContract);
    MimcSafeBytes<UInt256> newSlotValue = safeUInt256(UInt256.valueOf(13));
    expectedTraces.add(
        newContractStorageTrie.putAndProve(
            storageSlotKey.slotHash(), storageSlotKey.slotKey(), newSlotValue));
    updatedContract.setStorageRoot(Hash.wrap(newContractStorageTrie.getTopRootHash()));
    expectedTraces.add(
        accountStateTrieOne.putAndProve(
            updatedContract.getHkey(),
            updatedContract.getAddress(),
            updatedContract.getEncodedBytes()));

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateRepository());
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
    zkEvmWorldState.commit(0L, null, false);

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
    zkEvmWorldState.commit(0L, null, false);

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(zkEvmWorldState.getLastTraces()))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(expectedTraces));
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
  }

  @Test
  public void rollingForwardAccountSelfDestructWithDifferentStorageKeyAndValue()
      throws JsonProcessingException {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateRepository()));

    // create contract with storage
    MutableZkAccount contract = getAccountTwo();
    StorageSlotKey storageSlotKey = new StorageSlotKey(UInt256.valueOf(14));
    MimcSafeBytes<UInt256> slotValue = safeUInt256(UInt256.valueOf(12));
    ZKTrie contractStorageTrie = getContractStorageTrie(contract);
    contractStorageTrie.putAndProve(storageSlotKey.slotHash(), storageSlotKey.slotKey(), slotValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    accountStateTrieOne.putAndProve(
        contract.getHkey(), contract.getAddress(), contract.getEncodedBytes());

    List<Trace> expectedTraces = new ArrayList<>();
    // read slot before selfdestruct
    expectedTraces.add(
        contractStorageTrie.readAndProve(storageSlotKey.slotHash(), storageSlotKey.slotKey()));
    // selfdestruct contract
    expectedTraces.add(
        accountStateTrieOne.removeAndProve(contract.getHkey(), contract.getAddress()));
    // recreate contract

    MutableZkAccount updatedContract = getAccountTwo();
    ZKTrie newContractStorageTrie = getContractStorageTrie(updatedContract);
    StorageSlotKey newStorageSlotKey = new StorageSlotKey(UInt256.valueOf(146));
    MimcSafeBytes<UInt256> newSlotValue = safeUInt256(UInt256.valueOf(13));
    expectedTraces.add(
        newContractStorageTrie.putAndProve(
            newStorageSlotKey.slotHash(), newStorageSlotKey.slotKey(), newSlotValue));
    updatedContract.setStorageRoot(Hash.wrap(newContractStorageTrie.getTopRootHash()));
    expectedTraces.add(
        accountStateTrieOne.putAndProve(
            updatedContract.getHkey(),
            updatedContract.getAddress(),
            updatedContract.getEncodedBytes()));

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateRepository());
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
    zkEvmWorldState.commit(0L, null, false);

    // delete and recreate the contract in the same batch
    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    accountKey =
        trieLogLayer2.addAccountChange(contract.getAddress(), contract, updatedContract, true);
    trieLogLayer2.addStorageChange(
        accountKey, storageSlotKey, slotValue.getOriginalUnsafeValue(), null, true);
    trieLogLayer2.addStorageChange(
        accountKey, newStorageSlotKey, null, newSlotValue.getOriginalUnsafeValue());
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, false);

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(zkEvmWorldState.getLastTraces()))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(expectedTraces));
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
  }
}
