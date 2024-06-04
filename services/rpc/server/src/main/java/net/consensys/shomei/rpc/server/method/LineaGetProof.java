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

import net.consensys.shomei.proof.WorldStateProofProvider;
import net.consensys.shomei.rpc.server.ShomeiRpcMethod;
import net.consensys.shomei.rpc.server.error.ShomeiJsonRpcErrorResponse;
import net.consensys.shomei.storage.ZkWorldStateArchive;
import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.trielog.StorageSlotKey;
import net.consensys.shomei.worldview.ZkEvmWorldState;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameterOrBlockHash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class LineaGetProof implements JsonRpcMethod {

  final ZkWorldStateArchive worldStateArchive;

  public LineaGetProof(final ZkWorldStateArchive worldStateArchive) {
    this.worldStateArchive = worldStateArchive;
  }

  @Override
  public String getName() {
    return ShomeiRpcMethod.LINEA_GET_PROOF.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {

    final AccountKey accountAddress = getAccountAddress(requestContext);
    final List<StorageSlotKey> slotKeys = getSlotKeys(requestContext);
    final BlockParameterOrBlockHash blockParameterOrBlockHash =
        getBlockParameterOrBlockHash(requestContext);

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
    if (worldState.isPresent()) {
      final WorldStateProofProvider worldStateProofProvider =
          new WorldStateProofProvider(worldState.get());
      return new JsonRpcSuccessResponse(
          requestContext.getRequest().getId(),
          worldStateProofProvider.getAccountProof(accountAddress, slotKeys));
    } else {
      return new ShomeiJsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          JsonRpcError.INVALID_REQUEST,
          "BLOCK_MISSING_IN_CHAIN - block is missing");
    }
  }

  private AccountKey getAccountAddress(final JsonRpcRequestContext request) {
    return new AccountKey(request.getRequiredParameter(0, Address.class));
  }

  private List<StorageSlotKey> getSlotKeys(final JsonRpcRequestContext request) {
    return Arrays.stream(request.getRequiredParameter(1, String[].class))
        .map(UInt256::fromHexString)
        .map(StorageSlotKey::new)
        .collect(Collectors.toList());
  }

  private BlockParameterOrBlockHash getBlockParameterOrBlockHash(
      final JsonRpcRequestContext request) {
    return request.getRequiredParameter(2, BlockParameterOrBlockHash.class);
  }
}
