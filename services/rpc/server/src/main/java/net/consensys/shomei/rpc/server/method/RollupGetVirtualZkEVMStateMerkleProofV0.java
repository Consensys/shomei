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
import net.consensys.shomei.trielog.TrieLogLayer;

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


    try {

      // blockNumber represents the virtual block we want to build, fail early if we do not have it
      if (worldStateArchive.getTrieLogManager().getTrieLog(parentBlockNumber).isEmpty()) {
        return new ShomeiJsonRpcErrorResponse(
            requestContext.getRequest().getId(),
            RpcErrorType.INVALID_PARAMS,
            "BLOCK_MISSING_IN_CHAIN - block %d is missing".formatted(parentBlockNumber));
      }

      // do not have it in cache
      if (worldStateArchive.getCachedWorldState(parentBlockNumber).isEmpty()) {
        return new ShomeiJsonRpcErrorResponse(
            requestContext.getRequest().getId(),
            RpcErrorType.INVALID_PARAMS,
            "Worldstate for parent block %d is not cached".formatted(parentBlockNumber));
      }

      // Call eth_simulateV1 to get the trielog for the virtual block
      // Simulate on top of parentBlockNumber state to build virtual block at blockNumber
      final String trieLogHex =
          besuSimulateClient.simulateTransaction(parentBlockNumber, transactionRlp).join();
      final Bytes trieLogBytes = Bytes.fromHexString(trieLogHex);

      // Decode the trielog
      final TrieLogLayer trieLogLayer =
          worldStateArchive.getTrieLogLayerConverter().decodeTrieLog(RLP.input(trieLogBytes));

      // Apply the virtual trielog and generate the trace
      // This generates a trace without persisting the state
      // Use parentBlockNumber as the base state for applying the virtual trielog
      final var virtualTraceResult =
          worldStateArchive.generateVirtualTrace(parentBlockNumber, trieLogLayer);

      // Get the parent state root hash (state at parentBlockNumber that we're building on)
      final String zkParentStateRootHash =
          worldStateArchive
              .getTraceManager()
              .getZkStateRootHash(parentBlockNumber)
              .orElse(ZKTrie.DEFAULT_TRIE_ROOT)
              .toHexString();

      // Get the resulting state root hash (state after applying the virtual block)
      final String zkEndStateRootHash = virtualTraceResult.zkEndStateRootHash().toHexString();

      return new JsonRpcSuccessResponse(
          requestContext.getRequest().getId(),
          new RollupGetVirtualZkEVMStateMerkleProofV0Response(
              zkParentStateRootHash,
              zkEndStateRootHash,
              virtualTraceResult.traces(),
              IMPL_VERSION));

    } catch (Exception e) {
      return new ShomeiJsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          RpcErrorType.INTERNAL_ERROR,
          "Error processing virtual block: " + e.getMessage());
    }
  }
}
