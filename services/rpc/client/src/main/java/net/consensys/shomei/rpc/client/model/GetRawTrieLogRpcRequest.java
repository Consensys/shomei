/*
 * Copyright Consensys Software Inc., 2025
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
package net.consensys.shomei.rpc.client.model;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestId;

import net.consensys.shomei.rpc.client.BesuRpcMethod;

public class GetRawTrieLogRpcRequest extends JsonRpcRequest {

  public GetRawTrieLogRpcRequest(final long requestId, final Object[] params) {
    super("2.0", BesuRpcMethod.BESU_GET_TRIE_LOGS_BY_RANGE.getMethodName(), params);
    setId(new JsonRpcRequestId(requestId));
  }
}
