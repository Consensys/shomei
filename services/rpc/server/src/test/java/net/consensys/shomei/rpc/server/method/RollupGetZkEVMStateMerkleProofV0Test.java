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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import net.consensys.shomei.rpc.server.error.JsonInvalidVersionMessage;
import net.consensys.shomei.rpc.server.error.ShomeiJsonRpcErrorResponse;
import net.consensys.shomei.rpc.server.model.RollupGetZkEVMStateMerkleProofV0Response;
import net.consensys.shomei.rpc.server.model.RollupGetZkEvmStateV0Parameter;
import net.consensys.shomei.storage.TraceManager;
import net.consensys.shomei.storage.worldstate.InMemoryWorldStateStorage;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.proof.Trace;
import net.consensys.shomei.trie.storage.AccountTrieRepositoryWrapper;
import net.consensys.shomei.util.bytes.MimcSafeBytes;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RollupGetZkEVMStateMerkleProofV0Test {

  @Mock public TraceManager traceManager;
  public RollupGetZkEVMStateMerkleProofV0 method;

  @Before
  public void setup() {
    method = new RollupGetZkEVMStateMerkleProofV0(traceManager);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("rollup_getZkEVMStateMerkleProofV0");
  }

  @Test
  public void shouldReturnUnsupportedVersionWhenVersionIsInvalid() {
    final JsonRpcRequestContext request = request("0", "0", "invalidVersion");
    final JsonRpcResponse expectedResponse =
        new ShomeiJsonRpcErrorResponse(
            null,
            JsonRpcError.INVALID_PARAMS,
            "UNSUPPORTED_VERSION",
            new JsonInvalidVersionMessage("invalidVersion", IMPL_VERSION));
    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnBlockMissingWhenTraceUnavailable() {
    final JsonRpcRequestContext request = request("0", "0", IMPL_VERSION);
    final JsonRpcResponse expectedResponse =
        new ShomeiJsonRpcErrorResponse(
            null,
            JsonRpcError.INVALID_REQUEST,
            "BLOCK_MISSING_IN_CHAIN - block %d is missing".formatted(0));
    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnValidResponseWhenTraceAvailable() {
    ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    Bytes trace =
        Trace.serialize(
            List.of(accountStateTrie.readAndProve(Hash.ZERO, MimcSafeBytes.safeByte32(Hash.ZERO))));

    when(traceManager.getZkStateRootHash(anyLong()))
        .thenReturn(Optional.of(Hash.wrap(accountStateTrie.getTopRootHash())));
    when(traceManager.getTrace(anyLong())).thenReturn(Optional.of(trace));
    final JsonRpcRequestContext request = request("0", "0", IMPL_VERSION);
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            null,
            new RollupGetZkEVMStateMerkleProofV0Response(
                accountStateTrie.getTopRootHash().toHexString(),
                List.of(Trace.deserialize(RLP.input(trace))),
                IMPL_VERSION));
    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  private JsonRpcRequestContext request(
      final String startBlockNumber, final String endBlockNumber, final String version) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest(
            "2.0",
            "rollup_getZkEVMStateMerkleProofV0",
            new Object[] {
              new RollupGetZkEvmStateV0Parameter(startBlockNumber, endBlockNumber, version)
            }));
  }
}
