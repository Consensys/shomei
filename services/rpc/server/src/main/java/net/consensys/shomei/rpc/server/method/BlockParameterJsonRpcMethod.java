/*
 * Copyright ConsenSys Software Inc., 2025
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

import net.consensys.shomei.storage.ZkWorldStateArchive;
import net.consensys.shomei.worldview.ZkEvmWorldState;

import java.util.Optional;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameterOrBlockHash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;

public abstract class BlockParameterJsonRpcMethod implements JsonRpcMethod {

  protected BlockParameterOrBlockHash getBlockParameterOrBlockHash(
      final int index, final JsonRpcRequestContext request) {
    try {
      return request.getRequiredParameter(index, BlockParameterOrBlockHash.class);
    } catch (JsonRpcParameter.JsonRpcParameterException e) {
      throw new RuntimeException(e);
    }
  }

  protected Optional<ZkEvmWorldState> resolveBlockParameterToWorldState(
      final BlockParameterOrBlockHash blockParameterOrBlockHash,
      final ZkWorldStateArchive worldStateArchive) {
    Optional<ZkEvmWorldState> worldState = Optional.empty();
    if (blockParameterOrBlockHash.isNumeric()) {
      worldState =
          worldStateArchive.getCachedWorldState(blockParameterOrBlockHash.getNumber().getAsLong());
    } else if (blockParameterOrBlockHash.getBlockHash()) {
      worldState =
          worldStateArchive.getCachedWorldState(blockParameterOrBlockHash.getHash().orElseThrow());
    } else if (blockParameterOrBlockHash.isLatest()) {
      worldState = worldStateArchive.getCachedWorldState(worldStateArchive.getCurrentBlockHash());
    }
    return worldState;
  }
}
