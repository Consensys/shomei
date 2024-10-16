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

import net.consensys.shomei.observer.TrieLogObserver;
import net.consensys.shomei.rpc.model.TrieLogElement;
import net.consensys.shomei.rpc.server.ShomeiRpcMethod;
import net.consensys.shomei.storage.TrieLogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendRawTrieLog implements JsonRpcMethod {
  private static final Logger LOG = LoggerFactory.getLogger(SendRawTrieLog.class);
  final TrieLogObserver trieLogObserver;

  final TrieLogManager trieLogManager;

  public SendRawTrieLog(
      final TrieLogObserver trieLogObserver, final TrieLogManager trieLogManager) {
    this.trieLogObserver = trieLogObserver;
    this.trieLogManager = trieLogManager;
  }

  @Override
  public String getName() {
    return ShomeiRpcMethod.STATE_SEND_RAW_TRIE_LOG.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    try {
      final List<TrieLogObserver.TrieLogIdentifier> trieLogIdentifiers = new ArrayList<>();
      IntStream.range(0, requestContext.getRequest().getParamLength())
          .forEach(
              index -> {
                TrieLogElement param;
                try {
                  param =
                      requestContext.getRequest().getRequiredParameter(index, TrieLogElement.class);
                } catch (JsonRpcParameter.JsonRpcParameterException e) {
                  throw new RuntimeException(e);
                }
                trieLogIdentifiers.add(param.getTrieLogIdentifier());
              });
      trieLogObserver.onNewBesuHeadReceived(trieLogIdentifiers);
    } catch (RuntimeException e) {
      LOG.error("failed to handle new TrieLog {}", e.getMessage());
      LOG.debug("exception handling TrieLog", e);
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), RpcErrorType.INVALID_PARAMS);
    }
    return new JsonRpcSuccessResponse(requestContext.getRequest().getId());
  }
}
