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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import net.consensys.shomei.storage.ZkWorldStateArchive;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RollupGetZkEVMBlockNumberTest {

  @Mock public ZkWorldStateArchive worldStateArchive;
  public RollupGetZkEVMBlockNumber method;

  @Before
  public void setup() {
    method = new RollupGetZkEVMBlockNumber(worldStateArchive);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("rollup_getZkEVMBlockNumber");
  }

  @Test
  public void shouldReturnZeroWhenBlockNumberNotAvailable() {
    final JsonRpcRequestContext request = request();
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, Bytes.ofUnsignedLong(0L).toShortHexString());
    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnCurrentBlockNumber() {
    when(worldStateArchive.getCurrentBlockNumber()).thenReturn(10L);
    final JsonRpcRequestContext request = request();
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, Bytes.ofUnsignedLong(10L).toShortHexString());
    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  private JsonRpcRequestContext request() {
    return new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", "rollup_getZkEVMBlockNumber", new Object[] {}));
  }
}
