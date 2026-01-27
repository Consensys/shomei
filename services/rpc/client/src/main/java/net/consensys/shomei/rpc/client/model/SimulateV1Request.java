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

  public SimulateV1Request(
      final long requestId, final SimulateV1Params params, final String blockParameter) {
    super(
        "2.0",
        BesuRpcMethod.BESU_ETH_SIMULATE_V1.getMethodName(),
        new Object[] {params, blockParameter});
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
    @JsonProperty("chainId")
    private final String chainId;

    @JsonProperty("from")
    private final String from;

    @JsonProperty("to")
    private final String to;

    @JsonProperty("gas")
    private final String gas;

    @JsonProperty("gasPrice")
    private final String gasPrice;

    @JsonProperty("maxPriorityFeePerGas")
    private final String maxPriorityFeePerGas;

    @JsonProperty("maxFeePerGas")
    private final String maxFeePerGas;

    @JsonProperty("maxFeePerBlobGas")
    private final String maxFeePerBlobGas;

    @JsonProperty("value")
    private final String value;

    @JsonProperty("nonce")
    private final String nonce;

    @JsonProperty("input")
    private final String input;

    @JsonProperty("accessList")
    private final List<Map<String, Object>> accessList;

    @JsonProperty("blobVersionedHashes")
    private final List<String> blobVersionedHashes;

    public TransactionCall(
        final String chainId,
        final String from,
        final String to,
        final String gas,
        final String gasPrice,
        final String maxPriorityFeePerGas,
        final String maxFeePerGas,
        final String maxFeePerBlobGas,
        final String value,
        final String nonce,
        final String input,
        final List<Map<String, Object>> accessList,
        final List<String> blobVersionedHashes) {
      this.chainId = chainId;
      this.from = from;
      this.to = to;
      this.gas = gas;
      this.gasPrice = gasPrice;
      this.maxPriorityFeePerGas = maxPriorityFeePerGas;
      this.maxFeePerGas = maxFeePerGas;
      this.maxFeePerBlobGas = maxFeePerBlobGas;
      this.value = value;
      this.nonce = nonce;
      this.input = input;
      this.accessList = accessList;
      this.blobVersionedHashes = blobVersionedHashes;
    }

    public String getChainId() {
      return chainId;
    }

    public String getFrom() {
      return from;
    }

    public String getTo() {
      return to;
    }

    public String getGas() {
      return gas;
    }

    public String getGasPrice() {
      return gasPrice;
    }

    public String getMaxPriorityFeePerGas() {
      return maxPriorityFeePerGas;
    }

    public String getMaxFeePerGas() {
      return maxFeePerGas;
    }

    public String getMaxFeePerBlobGas() {
      return maxFeePerBlobGas;
    }

    public String getValue() {
      return value;
    }

    public String getNonce() {
      return nonce;
    }

    public String getInput() {
      return input;
    }

    public List<Map<String, Object>> getAccessList() {
      return accessList;
    }

    public List<String> getBlobVersionedHashes() {
      return blobVersionedHashes;
    }
  }
}
