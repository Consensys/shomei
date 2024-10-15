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

import static net.consensys.shomei.rpc.server.ShomeiRpcMethod.ROLLUP_FORK_CHOICE_UPDATED;

import net.consensys.shomei.fullsync.FullSyncDownloader;
import net.consensys.shomei.rpc.server.error.ShomeiJsonRpcErrorResponse;
import net.consensys.shomei.rpc.server.model.RollupForkChoiceUpdatedParameter;
import net.consensys.shomei.storage.ZkWorldStateArchive;

import java.util.Optional;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;

public class RollupForkChoiceUpdated implements JsonRpcMethod {

  private final ZkWorldStateArchive zkWorldStateArchive;

  private final FullSyncDownloader fullSyncDownloader;

  public RollupForkChoiceUpdated(
      final ZkWorldStateArchive zkWorldStateArchive, final FullSyncDownloader fullSyncDownloader) {
    this.zkWorldStateArchive = zkWorldStateArchive;
    this.fullSyncDownloader = fullSyncDownloader;
  }

  @Override
  public String getName() {
    return ROLLUP_FORK_CHOICE_UPDATED.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final RollupForkChoiceUpdatedParameter param;
    try {
      param = requestContext.getRequiredParameter(0, RollupForkChoiceUpdatedParameter.class);
    } catch (JsonRpcParameter.JsonRpcParameterException e) {
      throw new RuntimeException(e);
    }
    if (param.getFinalizedBlockNumber() < zkWorldStateArchive.getCurrentBlockNumber()) {
      return new ShomeiJsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          RpcErrorType.INVALID_PARAMS,
          "Cannot set finalized %d lower than the current shomei head %s ."
              .formatted(
                  param.getFinalizedBlockNumber(), zkWorldStateArchive.getCurrentBlockNumber()));
    }
    if (!fullSyncDownloader.getFullSyncRules().isEnableFinalizedBlockLimit()) {
      return new ShomeiJsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          RpcErrorType.UNAUTHORIZED,
          "The --enable-finalized-block-limit feature must be activated in order to set the finalized block limit.");
    }
    // update full sync rules
    fullSyncDownloader
        .getFullSyncRules()
        .setFinalizedBlockNumberLimit(Optional.of(param.getFinalizedBlockNumber()));
    fullSyncDownloader
        .getFullSyncRules()
        .setFinalizedBlockHashLimit(Optional.of(param.getFinalizedBlockHash()));
    return new JsonRpcSuccessResponse(requestContext.getRequest().getId());
  }
}
