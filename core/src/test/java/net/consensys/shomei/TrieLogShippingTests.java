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

// import static net.consensys.shomei.util.TestFixtureGenerator.getContractStorageTrie;
// import static net.consensys.shomei.util.bytes.MimcSafeBytes.safeUInt256;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import org.hyperledger.besu.datatypes.Hash;
// import org.hyperledger.besu.datatypes.StorageSlotKey;
// import org.hyperledger.besu.ethereum.rlp.RLP;
// import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
//
// import net.consensys.shomei.exception.MissingTrieLogException;
// import net.consensys.shomei.storage.ZkWorldStateArchive;
// import net.consensys.shomei.trie.ZKTrie;
// import net.consensys.shomei.trielog.TrieLogLayer;
// import net.consensys.shomei.trielog.TrieLogLayerConverter;
// import net.consensys.shomei.util.TestFixtureGenerator;
// import net.consensys.shomei.util.bytes.MimcSafeBytes;
// import org.apache.tuweni.bytes.Bytes;
// import org.apache.tuweni.bytes.Bytes32;
// import org.apache.tuweni.units.bigints.UInt256;
// import org.junit.Test;


import net.consensys.shomei.util.AcceptanceTestsUtils;
import okhttp3.Call;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeConfigurationBuilder;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.NodeConfigurationFactory;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationFactory;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static java.util.Arrays.asList;

@SuppressWarnings("unused")
public class TrieLogShippingTests extends AcceptanceTestBase {

    private static final String GENESIS_FILE = "/dev/dev_london.json";

    private final NodeConfigurationFactory node = new NodeConfigurationFactory();

    @Test
    public void testTrielogShippingWithNewContractUpdateA() throws IOException {
        final String[] validators = {"validator1"};

        final BesuNode validator1 =
                besu.create(new BesuNodeConfigurationBuilder().name("validator1").miningEnabled()
                        .jsonRpcConfiguration(createJsonRpcWithQbftEnabledConfig()).devMode(false)
                        .genesisConfigProvider((nodes) ->
                                this.node.createGenesisConfigForValidators(Arrays.asList(validators),
                                        nodes, collection -> GenesisConfigurationFactory.createCliqueGenesisConfig(nodes,
                                                new GenesisConfigurationFactory.CliqueOptions(1, 30000, false)))).build());

        cluster.start(validator1);

        final Account sender = accounts.createAccount("account1");
        final Account receiver = accounts.createAccount("account2");

        validator1.execute(accountTransactions.createTransfer(sender, 50));
        cluster.verify(sender.balanceEquals(50));

        AcceptanceTestsUtils acceptanceTestsUtils = new AcceptanceTestsUtils(validator1);
        Call call = acceptanceTestsUtils.callRpcRequest(acceptanceTestsUtils.createGetTrieLogRequest(1));
        System.out.println(call.execute().body().string());

    }


    public JsonRpcConfiguration createJsonRpcWithQbftEnabledConfig() {
        return node.createJsonRpcWithRpcApiEnabledConfig(RpcApis.QBFT.name(), RpcApis.MINER.name(), "SHOMEI");
    }

  /*
  // TODO activate when ZkTrieLogFactoryImpl will be available
  @Test
  public void testTrielogShippingWithNewContractUpdate() throws MissingTrieLogException {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new WorldStateStorageProxy(new InMemoryWorldStateStorage()));

    // add contract with storage
    MutableZkAccount contract = TestFixtureGenerator.getAccountTwo();
    StorageSlotKey storageSlotKey = new StorageSlotKey(UInt256.valueOf(14));
    MimcSafeBytes<UInt256> slotValue = safeUInt256(UInt256.valueOf(12));
    ZKTrie contractStorageTrie = getContractStorageTrie(contract);
    contractStorageTrie.putAndProve(storageSlotKey.slotHash(), storageSlotKey.slotKey(), slotValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    accountStateTrieOne.putAndProve(
        contract.getHkey(), contract.getAddress(), contract.getEncodedBytes());

    Hash topRootHashBeforeUpdate = Hash.wrap(accountStateTrieOne.getTopRootHash());

    // change storage
    final MimcSafeBytes<UInt256> newStorageValue = safeUInt256(UInt256.valueOf(22));
    contractStorageTrie.putAndProve(
        storageSlotKey.slotHash(), storageSlotKey.slotKey(), newStorageValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));
    accountStateTrieOne.putAndProve(
        contract.getHkey(), contract.getAddress(), contract.getEncodedBytes());

    Hash topRootHashAfterUpdate = Hash.wrap(accountStateTrieOne.getTopRootHash());

    // simulate trielog from Besu before update
    org.hyperledger.besu.ethereum.bonsai.trielog.TrieLogLayer trieLogLayer =
        new org.hyperledger.besu.ethereum.bonsai.trielog.TrieLogLayer();
    trieLogLayer.addAccountChange(
        contract.getAddress().getOriginalUnsafeValue(),
        null,
        new StateTrieAccountValue(
            contract.nonce.toLong(),
            contract.balance,
            Hash.wrap(
                Bytes32.random()), // change storage root to simulate evm storage root sent by Besu
            Hash.wrap(contract.keccakCodeHash.getOriginalUnsafeValue())));
    trieLogLayer.setBlockHash(Hash.wrap(Bytes32.random()));
    trieLogLayer.addStorageChange(
        contract.getAddress().getOriginalUnsafeValue(),
        new org.hyperledger.besu.ethereum.bonsai.worldview.StorageSlotKey(
            storageSlotKey.slotKey().getOriginalUnsafeValue()),
        null,
        slotValue.getOriginalUnsafeValue());

    // simulate trielog from Besu after update
    org.hyperledger.besu.ethereum.bonsai.trielog.TrieLogLayer trieLogLayer2 =
        new org.hyperledger.besu.ethereum.bonsai.trielog.TrieLogLayer();
    trieLogLayer2.addAccountChange(
        contract.getAddress().getOriginalUnsafeValue(),
        new StateTrieAccountValue(
            contract.nonce.toLong(),
            contract.balance,
            Hash.wrap(
                Bytes32.random()), // change storage root to simulate evm storage root sent by Besu
            Hash.wrap(
                contract.keccakCodeHash
                    .getOriginalUnsafeValue())), // get update of the first trielog
        new StateTrieAccountValue(
            contract.nonce.toLong(),
            contract.balance,
            Hash.wrap(
                Bytes32.random()), // change storage root to simulate evm storage root sent by Besu
            Hash.wrap(contract.keccakCodeHash.getOriginalUnsafeValue())));
    trieLogLayer2.setBlockHash(Hash.wrap(Bytes32.random()));
    trieLogLayer2.addStorageChange(
        contract.getAddress().getOriginalUnsafeValue(),
        new org.hyperledger.besu.ethereum.bonsai.worldview.StorageSlotKey(
            storageSlotKey.slotKey().getOriginalUnsafeValue()),
        slotValue.getOriginalUnsafeValue(),
        newStorageValue.getOriginalUnsafeValue());

    ZkTrieLogFactoryImpl zkTrieLogFactory = new ZkTrieLogFactoryImpl();

    // init the worldstate entrypoint with empty worldstate
    InMemoryWorldStateStorage storage = new InMemoryWorldStateStorage();
    ZkWorldStateArchive evmWorldStateEntryPoint = new ZkWorldStateArchive(storage);
    assertThat(evmWorldStateEntryPoint.getCurrentRootHash()).isEqualTo(ZKTrie.EMPTY_TRIE_ROOT);

    // decode trielog from Besu
    TrieLogLayer decodedLayer =
        new TrieLogLayerConverter(storage)
            .decodeTrieLog(RLP.input(Bytes.wrap(zkTrieLogFactory.serialize(trieLogLayer))));

    // move head with the new trielog
    evmWorldStateEntryPoint.applyTrieLog(0, decodedLayer);
    assertThat(evmWorldStateEntryPoint.getCurrentRootHash()).isEqualTo(topRootHashBeforeUpdate);

    // decode second trielog from Besu
    TrieLogLayer decodedLayer2 =
        new TrieLogLayerConverter(storage)
            .decodeTrieLog(RLP.input(Bytes.wrap(zkTrieLogFactory.serialize(trieLogLayer2))));

    // move head with the second trielog
    evmWorldStateEntryPoint.applyTrieLog(1, decodedLayer2);
    assertThat(evmWorldStateEntryPoint.getCurrentRootHash()).isEqualTo(topRootHashAfterUpdate);
  }
  */
}
