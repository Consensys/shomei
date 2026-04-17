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
package net.consensys.shomei.rpc.server.model;

import net.consensys.shomei.trie.json.JsonTraceParser;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.tuweni.bytes.Bytes32;

public class RollupForkChoiceUpdatedParameter {

  private final String finalizedBlockNumber;

  private final Bytes32 finalizedBlockHash;

  @JsonCreator
  public RollupForkChoiceUpdatedParameter(
      @JsonProperty(required = true, value = "finalizedBlockNumber")
          final String finalizedBlockNumber,
      @JsonProperty(required = true, value = "finalizedBlockHash")
          @JsonDeserialize(using = JsonTraceParser.Bytes32Deserializer.class)
          final Bytes32 finalizedBlockHash) {
    this.finalizedBlockNumber = finalizedBlockNumber;
    this.finalizedBlockHash = finalizedBlockHash;
  }

  public long getFinalizedBlockNumber() {
    return Long.decode(finalizedBlockNumber);
  }

  public Bytes32 getFinalizedBlockHash() {
    return finalizedBlockHash;
  }

  @Override
  public String toString() {
    return "RollupForkChoiceUpdatedParameter{"
        + "finalizedBlockNumber='"
        + finalizedBlockNumber
        + '\''
        + ", finalizedBlockHash='"
        + finalizedBlockHash
        + '\''
        + '}';
  }
}
