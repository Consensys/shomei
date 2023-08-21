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

package net.consensys.shomei.rpc.server.method;

import net.consensys.shomei.rpc.server.ShomeiRpcMethod;
import net.consensys.shomei.rpc.server.model.RollupDeleteZkEvmStateByRangeParameter;
import net.consensys.shomei.storage.TraceManager;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class RollupDeleteZkEVMStateMerkleProofByRange implements JsonRpcMethod {

  final TraceManager traceManager;

  public RollupDeleteZkEVMStateMerkleProofByRange(final TraceManager traceManager) {
    this.traceManager = traceManager;
  }

  @Override
  public String getName() {
    return ShomeiRpcMethod.ROLLUP_DELETE_ZKEVM_STATE_MERKLE_PROOF_BY_RANGE.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final RollupDeleteZkEvmStateByRangeParameter param =
        requestContext.getRequiredParameter(0, RollupDeleteZkEvmStateByRangeParameter.class);

    final TraceManager.TraceManagerUpdater updater = traceManager.updater();
    for (long i = param.getStartBlockNumber(); i <= param.getEndBlockNumber(); i++) {
      updater.removeTrace(i);
    }
    updater.commit();
    return new JsonRpcSuccessResponse(requestContext.getRequest().getId());
  }
}
