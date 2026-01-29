/*
 * Copyright ConsenSys Software Inc., 2026
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

package net.consensys.shomei.rpc.server.method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import net.consensys.shomei.rpc.client.BesuSimulateClient;
import net.consensys.shomei.rpc.server.error.ShomeiJsonRpcErrorResponse;
import net.consensys.shomei.rpc.server.model.RollupGetVirtualZkEvmStateMerkleProofV0Parameter;
import net.consensys.shomei.storage.TraceManager;
import net.consensys.shomei.storage.ZkWorldStateArchive;
import net.consensys.shomei.trie.trace.Trace;
import net.consensys.shomei.trielog.TrieLogLayer;
import net.consensys.shomei.trielog.TrieLogLayerConverter;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RollupGetVirtualZkEVMStateMerkleProofV0Test {

  @Mock public ZkWorldStateArchive worldStateArchive;
  @Mock public BesuSimulateClient besuSimulateClient;
  @Mock public TraceManager traceManager;
  @Mock public TrieLogLayerConverter trieLogLayerConverter;
  @Mock public TrieLogLayer trieLogLayer;

  public RollupGetVirtualZkEVMStateMerkleProofV0 method;

  // private static final String TEST_ACCOUNT_ADDRESS = "f17f52151EbEF6C7334FAD080c5704D77216b732";
  private static final String TEST_ACCOUNT_PRIVATE_KEY =
      "0xae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f";
  // Test account from genesis with balance 0x09184e72a000 (10000000000000 wei)
  // private static final String TEST_ACCOUNT_PRIVATE_KEY =
  //     "0x45a915e4d060149eb4365960e6a7a45f334393093061116b197e3240065ff2d8";
  // private static final Address TEST_ACCOUNT_ADDRESS =
  //     Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b");

  @BeforeEach
  public void setup() {
    method = new RollupGetVirtualZkEVMStateMerkleProofV0(worldStateArchive, besuSimulateClient);
  }

  /**
   * Creates a signed transaction RLP using the test account that has balance in the genesis file.
   * Balance: 0x09184e72a000 (10000000000000 wei = 0.00001 ETH) Gas cost: 21000 * 100000000 =
   * 2100000000000 wei Value: 1000000000000 wei Total: 3100000000000 wei (fits in budget)
   */
  private String createTestTransactionRlp() {
    final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();
    final KeyPair keyPair =
        signatureAlgorithm.createKeyPair(
            signatureAlgorithm.createPrivateKey(
                Bytes.fromHexString(TEST_ACCOUNT_PRIVATE_KEY).toUnsignedBigInteger()));

    final Transaction transaction =
        Transaction.builder()
            .type(TransactionType.FRONTIER)
            .nonce(0)
            .gasPrice(Wei.of(100000000)) // 0.1 Gwei
            .gasLimit(21000)
            .to(Address.fromHexString("0x3535353535353535353535353535353535353535"))
            .value(Wei.of(1000000000000L)) // 0.000001 ETH
            .payload(Bytes.EMPTY)
            .chainId(BigInteger.ONE)
            .signAndBuild(keyPair);

    return transaction.encoded().toHexString();
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("rollup_getVirtualZkEVMStateMerkleProofV0");
  }

  @Test
  public void shouldReturnBlockMissingWhenParentBlockUnavailable() {
    when(worldStateArchive.getTraceManager()).thenReturn(traceManager);
    when(traceManager.getTrace(7L)).thenReturn(Optional.empty());

    final JsonRpcRequestContext request = request(8L, createTestTransactionRlp());
    final JsonRpcResponse response = method.response(request);

    assertThat(response).isInstanceOf(ShomeiJsonRpcErrorResponse.class);
    final ShomeiJsonRpcErrorResponse errorResponse = (ShomeiJsonRpcErrorResponse) response;
    assertThat(errorResponse.getError().getCode()).isEqualTo(RpcErrorType.INVALID_PARAMS.getCode());
    assertThat(errorResponse.getJsonError().message()).contains("BLOCK_MISSING_IN_CHAIN");
    assertThat(errorResponse.getJsonError().message()).contains("block 7 is missing");
  }

  @Test
  public void shouldReturnValidResponseWhenSimulationSucceeds() throws Exception {
    // Mock trielog bytes (RLP encoded trielog layer)
    final String mockTrieLogHex = createMockTrieLogHex();
    final String testTxRlp = createTestTransactionRlp();

    // Mock the BesuSimulateClient to return a trielog
    when(besuSimulateClient.simulateTransaction(eq(7L), eq(testTxRlp)))
        .thenReturn(CompletableFuture.completedFuture(mockTrieLogHex));

    // Mock world state archive behavior
    when(worldStateArchive.getTraceManager()).thenReturn(traceManager);
    when(traceManager.getTrace(7L))
        .thenReturn(Optional.of(Bytes.fromHexString("0x01"))); // Parent block exists
    when(traceManager.getZkStateRootHash(7L)).thenReturn(Optional.of(Hash.ZERO));

    // Mock the trielog layer converter
    when(worldStateArchive.getTrieLogLayerConverter()).thenReturn(trieLogLayerConverter);
    when(trieLogLayerConverter.decodeTrieLog(any())).thenReturn(trieLogLayer);

    // Mock virtual trace generation
    final List<List<Trace>> mockTraces = List.of(List.of());
    when(worldStateArchive.generateVirtualTrace(eq(7L), any(TrieLogLayer.class)))
        .thenReturn(mockTraces);

    final JsonRpcRequestContext request = request(8L, testTxRlp);
    final JsonRpcResponse response = method.response(request);

    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isNotNull();
  }

  @Test
  public void shouldReturnErrorWhenSimulationFails() {
    when(worldStateArchive.getTraceManager()).thenReturn(traceManager);
    when(traceManager.getTrace(7L))
        .thenReturn(Optional.of(Bytes.fromHexString("0x01")));

    // Mock simulation failure
    when(besuSimulateClient.simulateTransaction(anyLong(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(new RuntimeException("Simulation failed")));

    final JsonRpcRequestContext request = request(8L, createTestTransactionRlp());
    final JsonRpcResponse response = method.response(request);

    assertThat(response).isInstanceOf(ShomeiJsonRpcErrorResponse.class);
    final ShomeiJsonRpcErrorResponse errorResponse = (ShomeiJsonRpcErrorResponse) response;
    assertThat(errorResponse.getError().getCode()).isEqualTo(RpcErrorType.INTERNAL_ERROR.getCode());
    assertThat(errorResponse.getJsonError().message()).contains("Failed to simulate transaction");
  }

  @Test
  public void shouldReturnErrorWithDetailsWhenSimulationReturnsError() {
    when(worldStateArchive.getTraceManager()).thenReturn(traceManager);
    when(traceManager.getTrace(7L))
        .thenReturn(Optional.of(Bytes.fromHexString("0x01")));

    // Mock simulation returning a detailed error from eth_simulateV1
    final String detailedErrorMessage = "eth_simulateV1 error [code=-32000]: insufficient funds";
    when(besuSimulateClient.simulateTransaction(anyLong(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException(detailedErrorMessage)));

    final JsonRpcRequestContext request = request(8L, createTestTransactionRlp());
    final JsonRpcResponse response = method.response(request);

    assertThat(response).isInstanceOf(ShomeiJsonRpcErrorResponse.class);
    final ShomeiJsonRpcErrorResponse errorResponse = (ShomeiJsonRpcErrorResponse) response;
    assertThat(errorResponse.getError().getCode()).isEqualTo(RpcErrorType.INTERNAL_ERROR.getCode());
    assertThat(errorResponse.getJsonError().message()).contains("eth_simulateV1 error");
    assertThat(errorResponse.getJsonError().message()).contains("insufficient funds");
  }

  private JsonRpcRequestContext request(final long blockNumber, final String transactionRlp) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest(
            "2.0",
            "rollup_getVirtualZkEVMStateMerkleProofV0",
            new Object[] {
                new RollupGetVirtualZkEvmStateMerkleProofV0Parameter(
                    String.valueOf(blockNumber), transactionRlp)
            }));
  }

  private String createMockTrieLogHex() {
    // Create a simple RLP-encoded trielog
    final BytesValueRLPOutput output = new BytesValueRLPOutput();
    output.startList();
    output.writeBytes(Hash.ZERO); // blockHash
    output.startList(); // account changes
    output.endList();
    output.startList(); // storage changes
    output.endList();
    output.endList();
    return output.encoded().toHexString();
  }
}
