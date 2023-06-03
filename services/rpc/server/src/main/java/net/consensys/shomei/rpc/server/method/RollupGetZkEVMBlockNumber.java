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
import net.consensys.shomei.storage.WorldStateRepository;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class RollupGetZkEVMBlockNumber implements JsonRpcMethod {

  final WorldStateRepository worldStateRepository;

  public RollupGetZkEVMBlockNumber(final WorldStateRepository worldStateRepository) {
    this.worldStateRepository = worldStateRepository;
  }

  @Override
  public String getName() {
    return ShomeiRpcMethod.ROLLUP_GET_ZKEVM_BLOCK_NUMBER.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(),
        Bytes.ofUnsignedLong(worldStateRepository.getWorldStateBlockNumber().orElse(0L))
            .toShortHexString());
  }
}
