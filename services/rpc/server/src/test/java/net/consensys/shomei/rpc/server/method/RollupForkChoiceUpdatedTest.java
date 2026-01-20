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
package net.consensys.shomei.rpc.server.method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;

import java.util.Optional;

import net.consensys.shomei.fullsync.FullSyncDownloader;
import net.consensys.shomei.fullsync.rules.FullSyncRules;
import net.consensys.shomei.rpc.server.error.ShomeiJsonRpcErrorResponse;
import net.consensys.shomei.rpc.server.model.RollupForkChoiceUpdatedParameter;
import net.consensys.shomei.storage.ZkWorldStateArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RollupForkChoiceUpdatedTest {

  @Mock public ZkWorldStateArchive worldStateArchive;
  @Mock public FullSyncDownloader fullSyncDownloader;
  @Mock FullSyncRules fullSyncRules;

  public RollupForkChoiceUpdated method;

  @BeforeEach
  public void setup() {
    lenient().when(fullSyncDownloader.getFullSyncRules()).thenReturn(fullSyncRules);
    method = new RollupForkChoiceUpdated(worldStateArchive, fullSyncDownloader);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("rollup_forkChoiceUpdated");
  }

  @Test
  public void cannotChangeLimitWithBlockBeforeHead() {
    when(worldStateArchive.getCurrentBlockNumber()).thenReturn(1L);
    JsonRpcResponse response = method.response(request("0"));

    final JsonRpcResponse expectedResponse =
        new ShomeiJsonRpcErrorResponse(
            null,
            RpcErrorType.INVALID_PARAMS,
            "Cannot set finalized 0 lower than the current shomei head 1 .");

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void cannotChangeLimitWhenFinalizedBlockFeatureIsDisabled() {
    when(worldStateArchive.getCurrentBlockNumber()).thenReturn(1L);
    JsonRpcResponse response = method.response(request("2"));

    final JsonRpcResponse expectedResponse =
        new ShomeiJsonRpcErrorResponse(
            null,
            RpcErrorType.UNAUTHORIZED,
            "The --enable-finalized-block-limit feature must be activated in order to set the finalized block limit.");

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void canChangeLimitWithBlockAfterHead() {
    when(worldStateArchive.getCurrentBlockNumber()).thenReturn(1L);
    when(fullSyncRules.isEnableFinalizedBlockLimit()).thenReturn(true);
    JsonRpcResponse response = method.response(request("2"));
    verify(fullSyncRules, times(1)).setFinalizedBlockNumberLimit(Optional.of(2L));
    verify(fullSyncRules, times(1)).setFinalizedBlockHashLimit(Optional.of(Hash.EMPTY));
    assertThat(response).usingRecursiveComparison().isInstanceOf(JsonRpcSuccessResponse.class);
  }

  private JsonRpcRequestContext request(final String blockNumber) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest(
            "2.0",
            "rollup_forkChoiceUpdated",
            new Object[] {new RollupForkChoiceUpdatedParameter(blockNumber, Hash.EMPTY)}));
  }
}
