/*
 * Copyright ConsenSys Software Inc., 2026
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

import net.consensys.shomei.rpc.client.BesuRpcMethod;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestId;

public class SimulateV1Request extends JsonRpcRequest {

  public SimulateV1Request(final long requestId, final SimulateV1Params params) {
    super("2.0", BesuRpcMethod.BESU_ETH_SIMULATE_V1.getMethodName(), new Object[] {params});
    setId(new JsonRpcRequestId(requestId));
  }

  public static class SimulateV1Params {
    @JsonProperty("blockStateCalls")
    private final List<BlockStateCall> blockStateCalls;

    @JsonProperty("validation")
    private final boolean validation;

    @JsonProperty("returnTrieLog")
    private final boolean returnTrieLog;

    public SimulateV1Params(
        final List<BlockStateCall> blockStateCalls,
        final boolean validation,
        final boolean returnTrieLog) {
      this.blockStateCalls = blockStateCalls;
      this.validation = validation;
      this.returnTrieLog = returnTrieLog;
    }

    public List<BlockStateCall> getBlockStateCalls() {
      return blockStateCalls;
    }

    public boolean isValidation() {
      return validation;
    }

    public boolean isReturnTrieLog() {
      return returnTrieLog;
    }
  }

  public static class BlockStateCall {
    @JsonProperty("blockOverrides")
    private final Map<String, String> blockOverrides;

    @JsonProperty("calls")
    private final List<TransactionCall> calls;

    public BlockStateCall(
        final Map<String, String> blockOverrides, final List<TransactionCall> calls) {
      this.blockOverrides = blockOverrides;
      this.calls = calls;
    }

    public Map<String, String> getBlockOverrides() {
      return blockOverrides;
    }

    public List<TransactionCall> getCalls() {
      return calls;
    }
  }

  public static class TransactionCall {
    @JsonProperty("input")
    private final String input;

    public TransactionCall(final String input) {
      this.input = input;
    }

    public String getInput() {
      return input;
    }
  }
}
