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

import static net.consensys.shomei.rpc.server.ShomeiVersion.IMPL_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.rpc.server.error.ShomeiJsonRpcErrorResponse;
import net.consensys.shomei.rpc.server.model.RollupDeleteZkEvmStateByRangeParameter;
import net.consensys.shomei.rpc.server.model.RollupGetZkEVMStateMerkleProofV0Response;
import net.consensys.shomei.rpc.server.model.RollupGetZkEvmStateV0Parameter;
import net.consensys.shomei.storage.InMemoryStorageProvider;
import net.consensys.shomei.storage.TraceManager;
import net.consensys.shomei.storage.worldstate.InMemoryWorldStateStorage;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.json.JsonTraceParser;
import net.consensys.shomei.trie.storage.AccountTrieRepositoryWrapper;
import net.consensys.shomei.trie.trace.Trace;
import net.consensys.shomei.util.bytes.MimcSafeBytes;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RollupDeleteZkEVMStateMerkleProofByRangeTest {

  private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();
  private final InMemoryStorageProvider storageProvider = new InMemoryStorageProvider();
  private final TraceManager traceManager = storageProvider.getTraceManager();

  public RollupGetZkEVMStateMerkleProofV0 requestMethod;
  public RollupDeleteZkEVMStateMerkleProofByRange deleteMethod;

  @Before
  public void setup() {
    JSON_OBJECT_MAPPER.registerModules(JsonTraceParser.modules);
    requestMethod = new RollupGetZkEVMStateMerkleProofV0(traceManager);
    deleteMethod = new RollupDeleteZkEVMStateMerkleProofByRange(traceManager);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(deleteMethod.getName()).isEqualTo("rollup_deleteZkEVMStateMerkleProofByRange");
  }

  @Test
  public void shouldDeleteWhenTraceAvailable() throws JsonProcessingException {
    ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    final List<Trace> traces =
        List.of(accountStateTrie.readWithTrace(Hash.ZERO, MimcSafeBytes.safeByte32(Hash.ZERO)));

    final List<Trace> traces2 =
        List.of(
            accountStateTrie.readWithTrace(
                Hash.wrap(Bytes32.random()), MimcSafeBytes.safeByte32(Bytes32.random())));

    final List<Trace> traces3 =
        List.of(
            accountStateTrie.readWithTrace(
                Hash.wrap(Bytes32.random()), MimcSafeBytes.safeByte32(Bytes32.random())));

    final TraceManager.TraceManagerUpdater updater = traceManager.updater();
    updater.saveZkStateRootHash(0, Hash.wrap(accountStateTrie.getTopRootHash()));
    updater.saveZkStateRootHash(1, Hash.wrap(accountStateTrie.getTopRootHash()));
    updater.saveZkStateRootHash(2, Hash.wrap(accountStateTrie.getTopRootHash()));
    updater.saveTrace(1, traces);
    updater.saveTrace(2, traces2);
    updater.saveTrace(3, traces3);
    updater.commit();

    // before deletion should have trace of block 1 and 2
    JsonRpcRequestContext request = requestTrace("1", "3", IMPL_VERSION);
    JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            null,
            new RollupGetZkEVMStateMerkleProofV0Response(
                accountStateTrie.getTopRootHash().toHexString(),
                List.of(traces, traces2, traces3),
                IMPL_VERSION));
    JsonRpcResponse response = requestMethod.response(request);

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(expectedResponse))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(response));

    // delete trace of block 1 and 2
    final JsonRpcResponse expectedDeletionResponse = new JsonRpcSuccessResponse(null);
    final JsonRpcRequestContext requestDeletion = requestDelete("1", "2");
    final JsonRpcResponse responseDeletion = deleteMethod.response(requestDeletion);
    assertThat(responseDeletion).usingRecursiveComparison().isEqualTo(expectedDeletionResponse);

    // after deletion should not have 1
    request = requestTrace("1", "1", IMPL_VERSION);
    expectedResponse =
        new ShomeiJsonRpcErrorResponse(
            null,
            JsonRpcError.INVALID_PARAMS,
            "BLOCK_MISSING_IN_CHAIN - block %d is missing".formatted(1));
    response = requestMethod.response(request);
    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);

    // after deletion should not have 2
    request = requestTrace("2", "2", IMPL_VERSION);
    expectedResponse =
        new ShomeiJsonRpcErrorResponse(
            null,
            JsonRpcError.INVALID_PARAMS,
            "BLOCK_MISSING_IN_CHAIN - block %d is missing".formatted(2));
    response = requestMethod.response(request);
    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);

    // after deletion should have 3
    request = requestTrace("3", "3", IMPL_VERSION);
    expectedResponse =
        new JsonRpcSuccessResponse(
            null,
            new RollupGetZkEVMStateMerkleProofV0Response(
                accountStateTrie.getTopRootHash().toHexString(), List.of(traces3), IMPL_VERSION));
    response = requestMethod.response(request);

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(expectedResponse))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(response));
  }

  private JsonRpcRequestContext requestTrace(
      final String startBlockNumber, final String endBlockNumber, final String version) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest(
            "2.0",
            "rollup_getZkEVMStateMerkleProofV0",
            new Object[] {
              new RollupGetZkEvmStateV0Parameter(startBlockNumber, endBlockNumber, version)
            }));
  }

  private JsonRpcRequestContext requestDelete(
      final String startBlockNumber, final String endBlockNumber) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest(
            "2.0",
            "rollup_deleteZkEVMStateMerkleProofByRange",
            new Object[] {
              new RollupDeleteZkEvmStateByRangeParameter(startBlockNumber, endBlockNumber)
            }));
  }
}
