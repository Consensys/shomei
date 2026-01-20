/*
 * Copyright Consensys Software Inc., 2023
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

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import net.consensys.shomei.proof.MerkleAccountProof;
import net.consensys.shomei.proof.WorldStateProofProvider;
import net.consensys.shomei.rpc.server.ShomeiRpcMethod;
import net.consensys.shomei.rpc.server.error.ShomeiJsonRpcErrorResponse;
import net.consensys.shomei.storage.ZkWorldStateArchive;
import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.trielog.StorageSlotKey;
import net.consensys.shomei.trielog.TrieLogLayer;
import net.consensys.shomei.trielog.TrieLogLayerConverter;
import net.consensys.shomei.worldview.ZkEvmWorldState;
import org.apache.tuweni.bytes.Bytes;

public class LineaGetTrielogProof extends BlockParameterJsonRpcMethod {

  private final ZkWorldStateArchive worldStateArchive;
  private final TrieLogLayerConverter trieLogLayerConverter;

  public LineaGetTrielogProof(
      final ZkWorldStateArchive worldStateArchive,
      final TrieLogLayerConverter trieLogLayerConverter) {
    this.worldStateArchive = worldStateArchive;
    this.trieLogLayerConverter = trieLogLayerConverter;
  }

  @Override
  public String getName() {
    return ShomeiRpcMethod.LINEA_GET_TRIELOG_PROOF.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {

    try {
      final String serializedTrieLogLayer = getSerializedTrieLogLayer(requestContext);
      final var blockParameter = getBlockParameterOrBlockHash(1, requestContext);

      final Bytes trieLogBytes = Bytes.fromHexString(serializedTrieLogLayer);
      final TrieLogLayer trieLogLayer =
          trieLogLayerConverter.decodeTrieLog(RLP.input(trieLogBytes));

      Optional<ZkEvmWorldState> worldState =
          resolveBlockParameterToWorldState(blockParameter, worldStateArchive);

      if (worldState.isPresent()) {
        final WorldStateProofProvider worldStateProofProvider =
            new WorldStateProofProvider(worldState.get());

        final Map<AccountKey, List<StorageSlotKey>> accountsWithStorageKeys =
            collectAccountsAndStorageKeys(trieLogLayer);

        final List<MerkleAccountProof> accountProofs = new ArrayList<>();

        for (final Map.Entry<AccountKey, List<StorageSlotKey>> entry :
            accountsWithStorageKeys.entrySet()) {
          final MerkleAccountProof accountProof =
              worldStateProofProvider.getAccountProof(entry.getKey(), entry.getValue());
          accountProofs.add(accountProof);
        }

        return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), accountProofs);
      } else {
        return new ShomeiJsonRpcErrorResponse(
            requestContext.getRequest().getId(),
            RpcErrorType.INVALID_REQUEST,
            "BLOCK_MISSING_IN_CHAIN - parent block is missing");
      }
    } catch (Exception e) {
      return new ShomeiJsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          RpcErrorType.INVALID_REQUEST,
          "INVALID_TRIELOG_SERIALIZATION - " + e.getMessage());
    }
  }

  private Map<AccountKey, List<StorageSlotKey>> collectAccountsAndStorageKeys(
      final TrieLogLayer trieLogLayer) {

    final Map<AccountKey, List<StorageSlotKey>> result = new HashMap<>();

    trieLogLayer
        .streamAccountChanges()
        .forEach(
            accountEntry -> {
              final AccountKey accountKey = accountEntry.getKey();
              result.putIfAbsent(accountKey, new ArrayList<>());
            });

    trieLogLayer
        .streamStorageChanges()
        .forEach(
            storageEntry -> {
              final AccountKey accountKey = storageEntry.getKey();
              final List<StorageSlotKey> storageKeys =
                  storageEntry.getValue().keySet().stream().collect(Collectors.toList());

              result.merge(
                  accountKey,
                  storageKeys,
                  (existing, newKeys) -> {
                    existing.addAll(newKeys);
                    return existing;
                  });
            });

    return result;
  }

  private String getSerializedTrieLogLayer(final JsonRpcRequestContext request) {
    try {
      return request.getRequiredParameter(0, String.class);
    } catch (JsonRpcParameter.JsonRpcParameterException e) {
      throw new RuntimeException(e);
    }
  }
}
