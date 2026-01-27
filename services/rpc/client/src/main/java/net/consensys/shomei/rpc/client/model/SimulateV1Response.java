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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SimulateV1Response {
  @JsonProperty("jsonrpc")
  private String jsonrpc;

  @JsonProperty("id")
  private Integer id;

  @JsonProperty("result")
  private List<BlockResult> result;

  @JsonProperty("error")
  private JsonRpcError error;

  public String getJsonrpc() {
    return jsonrpc;
  }

  public void setJsonrpc(String jsonrpc) {
    this.jsonrpc = jsonrpc;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public List<BlockResult> getResult() {
    return result;
  }

  public void setResult(List<BlockResult> result) {
    this.result = result;
  }

  public JsonRpcError getError() {
    return error;
  }

  public void setError(JsonRpcError error) {
    this.error = error;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class BlockResult {
    @JsonProperty("number")
    private String number;

    @JsonProperty("hash")
    private String hash;

    @JsonProperty("stateRoot")
    private String stateRoot;

    @JsonProperty("gasUsed")
    private String gasUsed;

    @JsonProperty("blobGasUsed")
    private String blobGasUsed;

    @JsonProperty("baseFeePerGas")
    private String baseFeePerGas;

    @JsonProperty("trieLog")
    private String trieLog;

    @JsonProperty("calls")
    private List<CallResult> calls;

    public String getNumber() {
      return number;
    }

    public void setNumber(String number) {
      this.number = number;
    }

    public String getHash() {
      return hash;
    }

    public void setHash(String hash) {
      this.hash = hash;
    }

    public String getStateRoot() {
      return stateRoot;
    }

    public void setStateRoot(String stateRoot) {
      this.stateRoot = stateRoot;
    }

    public String getGasUsed() {
      return gasUsed;
    }

    public void setGasUsed(String gasUsed) {
      this.gasUsed = gasUsed;
    }

    public String getBlobGasUsed() {
      return blobGasUsed;
    }

    public void setBlobGasUsed(String blobGasUsed) {
      this.blobGasUsed = blobGasUsed;
    }

    public String getBaseFeePerGas() {
      return baseFeePerGas;
    }

    public void setBaseFeePerGas(String baseFeePerGas) {
      this.baseFeePerGas = baseFeePerGas;
    }

    public String getTrieLog() {
      return trieLog;
    }

    public void setTrieLog(String trieLog) {
      this.trieLog = trieLog;
    }

    public List<CallResult> getCalls() {
      return calls;
    }

    public void setCalls(List<CallResult> calls) {
      this.calls = calls;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class CallResult {
    @JsonProperty("status")
    private String status;

    @JsonProperty("gasUsed")
    private String gasUsed;

    @JsonProperty("logs")
    private List<Object> logs;

    @JsonProperty("returnData")
    private String returnData;

    @JsonProperty("error")
    private JsonRpcError error;

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public String getGasUsed() {
      return gasUsed;
    }

    public void setGasUsed(String gasUsed) {
      this.gasUsed = gasUsed;
    }

    public List<Object> getLogs() {
      return logs;
    }

    public void setLogs(List<Object> logs) {
      this.logs = logs;
    }

    public String getReturnData() {
      return returnData;
    }

    public void setReturnData(String returnData) {
      this.returnData = returnData;
    }

    public JsonRpcError getError() {
      return error;
    }

    public void setError(JsonRpcError error) {
      this.error = error;
    }
  }

  @Override
  public String toString() {
    return "SimulateV1Response{"
        + "jsonrpc='"
        + jsonrpc
        + '\''
        + ", id="
        + id
        + ", result="
        + result
        + ", error="
        + error
        + '}';
  }
}
