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

import static net.consensys.shomei.rpc.server.ShomeiVersion.IMPL_VERSION;

import net.consensys.shomei.rpc.client.BesuSimulateClient;
import net.consensys.shomei.rpc.server.ShomeiRpcMethod;
import net.consensys.shomei.rpc.server.error.ShomeiJsonRpcErrorResponse;
import net.consensys.shomei.rpc.server.model.RollupGetVirtualZkEVMStateMerkleProofV0Response;
import net.consensys.shomei.rpc.server.model.RollupGetVirtualZkEvmStateMerkleProofV0Parameter;
import net.consensys.shomei.storage.ZkWorldStateArchive;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.trace.Trace;
import net.consensys.shomei.trielog.TrieLogLayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.rlp.RLP;

public class RollupGetVirtualZkEVMStateMerkleProofV0 implements JsonRpcMethod {

  final ZkWorldStateArchive worldStateArchive;
  final BesuSimulateClient besuSimulateClient;

  public RollupGetVirtualZkEVMStateMerkleProofV0(
      final ZkWorldStateArchive worldStateArchive, final BesuSimulateClient besuSimulateClient) {
    this.worldStateArchive = worldStateArchive;
    this.besuSimulateClient = besuSimulateClient;
  }

  @Override
  public String getName() {
    return ShomeiRpcMethod.ROLLUP_GET_VIRTUAL_ZKEVM_STATE_MERKLE_PROOF_V0.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final RollupGetVirtualZkEvmStateMerkleProofV0Parameter param;
    try {
      param =
          requestContext.getRequiredParameter(
              0, RollupGetVirtualZkEvmStateMerkleProofV0Parameter.class);
    } catch (JsonRpcParameter.JsonRpcParameterException e) {
      throw new RuntimeException(e);
    }

    final long blockNumber = param.getBlockNumber();
    final long parentBlockNumber = blockNumber - 1;
    final String transactionRlp = param.getTransaction();

    // Check if the parent block (blockNumber - 1) exists in the canonical chain
    if (!worldStateArchive.getTraceManager().getTrace(parentBlockNumber).isPresent()) {
      return new ShomeiJsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          RpcErrorType.INVALID_PARAMS,
          "BLOCK_MISSING_IN_CHAIN - block %d is missing".formatted(parentBlockNumber));
    }

    try {
      // Call eth_simulateV1 to get the trielog for the virtual block
      final CompletableFuture<String> trieLogFuture =
          besuSimulateClient.simulateTransaction(parentBlockNumber, transactionRlp);

      final String trieLogHex = trieLogFuture.get();
      final Bytes trieLogBytes = Bytes.fromHexString(trieLogHex);

      // Log trielog for debugging
      System.out.println("Received trielog hex length: " + trieLogHex.length());
      System.out.println("Trielog hex prefix: " + (trieLogHex.length() > 200 ? trieLogHex.substring(0, 200) : trieLogHex));
      System.out.println("Trielog bytes: " + trieLogBytes);

      // Decode the trielog
      final TrieLogLayer trieLogLayer =
          worldStateArchive.getTrieLogLayerConverter().decodeTrieLog(RLP.input(trieLogBytes));

      // Apply the virtual trielog and generate the trace
      // This generates a trace without persisting the state
      final List<List<Trace>> traces =
          worldStateArchive.generateVirtualTrace(parentBlockNumber, trieLogLayer);

      // Get the parent state root hash
      final String zkParentStateRootHash =
          worldStateArchive
              .getTraceManager()
              .getZkStateRootHash(parentBlockNumber)
              .orElse(ZKTrie.DEFAULT_TRIE_ROOT)
              .toHexString();

      return new JsonRpcSuccessResponse(
          requestContext.getRequest().getId(),
          new RollupGetVirtualZkEVMStateMerkleProofV0Response(
              traces, zkParentStateRootHash, IMPL_VERSION));

    } catch (InterruptedException | ExecutionException e) {
      return new ShomeiJsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          RpcErrorType.INTERNAL_ERROR,
          "Failed to simulate transaction: " + e.getMessage());
    } catch (Exception e) {
      return new ShomeiJsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          RpcErrorType.INTERNAL_ERROR,
          "Error processing virtual block: " + e.getMessage());
    }
  }
}
