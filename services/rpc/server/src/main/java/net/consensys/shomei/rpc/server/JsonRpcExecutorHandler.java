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

package net.consensys.shomei.rpc.server;

import static net.consensys.shomei.rpc.server.JsonRpcObjectExecutor.handleJsonRpcError;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.INTERNAL_ERROR;

import java.io.IOException;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonRpcExecutorHandler {
  private static final Logger LOG = LoggerFactory.getLogger(JsonRpcExecutorHandler.class);

  private JsonRpcExecutorHandler() {}

  public static Handler<RoutingContext> handler(
      final JsonRpcExecutor jsonRpcExecutor, final JsonRpcConfiguration jsonRpcConfiguration) {
    return ctx -> {
      try {
        JsonRpcObjectExecutor executor =
            new JsonRpcObjectExecutor(jsonRpcExecutor, ctx, jsonRpcConfiguration);
        try {
          executor.execute();
        } catch (IOException e) {
          final String method = executor.getRpcMethodName(ctx);
          LOG.error("{} - Error streaming JSON-RPC response", method, e);
          throw new RuntimeException(e);
        }
      } catch (final RuntimeException e) {
        handleJsonRpcError(ctx, null, INTERNAL_ERROR);
      }
    };
  }
}
