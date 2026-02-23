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

package net.consensys.shomei.rpc.client.model;

import net.consensys.shomei.rpc.model.TrieLogElement;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GetRawTrieLogRpcResponse {
  @JsonProperty("jsonrpc")
  private String jsonrpc;

  @JsonProperty("id")
  private Integer id;

  @JsonProperty("result")
  private List<TrieLogElement> result;

  @JsonProperty("error")
  private JsonRpcError error;

  @JsonProperty("jsonrpc")
  public String getJsonrpc() {
    return jsonrpc;
  }

  @JsonProperty("jsonrpc")
  public void setJsonrpc(String jsonrpc) {
    this.jsonrpc = jsonrpc;
  }

  @JsonProperty("id")
  public Integer getId() {
    return id;
  }

  @JsonProperty("id")
  public void setId(Integer id) {
    this.id = id;
  }

  @JsonProperty("result")
  public List<TrieLogElement> getResult() {
    return result;
  }

  @JsonProperty("result")
  public void setResult(List<TrieLogElement> result) {
    this.result = result;
  }

  @JsonProperty("error")
  public JsonRpcError getError() {
    return error;
  }

  @JsonProperty("error")
  public void setError(JsonRpcError error) {
    this.error = error;
  }

  @Override
  public String toString() {
    return "GetRawTrieLogRpcResponse{"
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
