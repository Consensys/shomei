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

import net.consensys.shomei.rpc.server.ShomeiRpcMethod;
import net.consensys.shomei.rpc.server.error.ShomeiJsonRpcErrorResponse;
import net.consensys.shomei.rpc.server.model.RollupGetVirtualZkEVMStateMerkleProofV0Response;
import net.consensys.shomei.rpc.server.model.RollupGetVirtualZkEvmStateMerkleProofV0Parameter;
import net.consensys.shomei.storage.TraceManager;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;

public class RollupGetVirtualZkEVMStateMerkleProofV0 implements JsonRpcMethod {

  final TraceManager traceManager;

  public RollupGetVirtualZkEVMStateMerkleProofV0(final TraceManager traceManager) {
    this.traceManager = traceManager;
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

    // Check if the parent block (blockNumber - 1) exists in the canonical chain
    if (!traceManager.getTrace(parentBlockNumber).isPresent()) {
      return new ShomeiJsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          RpcErrorType.INVALID_PARAMS,
          "BLOCK_MISSING_IN_CHAIN - block %d is missing".formatted(parentBlockNumber));
    }

    // TODO: Implement actual virtual block creation and state proof generation
    // For now, return stubbed response with placeholder data
    final String zkParentStateRootHash =
        traceManager
            .getZkStateRootHash(parentBlockNumber)
            .map(hash -> hash.toHexString())
            .orElse("0x0000000000000000000000000000000000000000000000000000000000000000");

    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(),
        new RollupGetVirtualZkEVMStateMerkleProofV0Response(
            "TODO: Implement virtual state merkle proof generation",
            zkParentStateRootHash,
            IMPL_VERSION));
  }
}
