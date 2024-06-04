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

import static net.consensys.shomei.trie.ZKTrie.EMPTY_TRIE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.consensys.shomei.rpc.server.error.ShomeiJsonRpcErrorResponse;
import net.consensys.shomei.storage.ZkWorldStateArchive;
import net.consensys.shomei.storage.worldstate.WorldStateStorage;
import net.consensys.shomei.trie.model.FlattenedLeaf;
import net.consensys.shomei.trie.storage.TrieStorage;
import net.consensys.shomei.worldview.ZkEvmWorldState;

import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class LineaGetProofTest {

  @Mock public ZkWorldStateArchive worldStateArchive;
  public LineaGetProof method;

  @Before
  public void setup() {
    final ZkEvmWorldState zkEvmWorldState = mock(ZkEvmWorldState.class);
    final WorldStateStorage worldStateStorage = mock(WorldStateStorage.class);
    when(worldStateArchive.getCurrentBlockHash()).thenReturn(Hash.EMPTY);
    when(worldStateArchive.getCachedWorldState(eq(1L))).thenReturn(Optional.of(zkEvmWorldState));
    when(worldStateArchive.getCachedWorldState(eq(Hash.EMPTY)))
        .thenReturn(Optional.of(zkEvmWorldState));
    when(zkEvmWorldState.getZkEvmWorldStateStorage()).thenReturn(worldStateStorage);
    when(zkEvmWorldState.getStateRootHash()).thenReturn(Hash.wrap(EMPTY_TRIE.getTopRootHash()));
    when(worldStateStorage.getTrieNode(any(Bytes.class), any(Bytes.class)))
        .thenReturn(Optional.of(EMPTY_TRIE.getTopRootHash()));
    when(worldStateStorage.getNearestKeys(any(Bytes.class)))
        .thenReturn(
            new TrieStorage.Range(
                Map.entry(Bytes.of(0x01), FlattenedLeaf.HEAD),
                Optional.empty(),
                Map.entry(Bytes.of(0x02), FlattenedLeaf.TAIL)));

    method = new LineaGetProof(worldStateArchive);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("linea_getProof");
  }

  @Test
  public void shouldReturnErrorWhenBlockMissing() {
    final JsonRpcRequestContext request = request("2");
    final JsonRpcResponse expectedResponse =
        new ShomeiJsonRpcErrorResponse(
            null, JsonRpcError.INVALID_REQUEST, "BLOCK_MISSING_IN_CHAIN - block is missing");
    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnProofWithLatest() {
    final JsonRpcRequestContext request = request("latest");
    final JsonRpcResponse response = method.response(request);
    assertThat(response).usingRecursiveComparison().isInstanceOf(JsonRpcSuccessResponse.class);
  }

  @Test
  public void shouldReturnProofWithValidBlock() {
    final JsonRpcRequestContext request = request("1");
    final JsonRpcResponse response = method.response(request);
    assertThat(response).usingRecursiveComparison().isInstanceOf(JsonRpcSuccessResponse.class);
  }

  private JsonRpcRequestContext request(final String blockNumber) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest(
            "2.0",
            "linea_getProof",
            new Object[] {
              Address.fromHexString("0x0000000000000000000000000000000000000000"),
              new String[] {Bytes.of(0x01).toString()},
              String.valueOf(blockNumber)
            }));
  }
}
