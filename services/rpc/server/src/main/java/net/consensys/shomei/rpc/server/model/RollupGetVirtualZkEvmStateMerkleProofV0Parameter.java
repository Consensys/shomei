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

package net.consensys.shomei.rpc.server.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RollupGetVirtualZkEvmStateMerkleProofV0Parameter {

  private final String blockNumber;

  private final String transaction;

  @JsonCreator
  public RollupGetVirtualZkEvmStateMerkleProofV0Parameter(
      @JsonProperty("blockNumber") final String blockNumber,
      @JsonProperty("transaction") final String transaction) {
    this.blockNumber = blockNumber;
    this.transaction = transaction;
  }

  public long getBlockNumber() {
    return Long.decode(blockNumber);
  }

  public String getTransaction() {
    return transaction;
  }

  @Override
  public String toString() {
    return "RollupGetVirtualZkEvmStateMerkleProofV0Parameter{"
        + "blockNumber="
        + blockNumber
        + ", transaction='"
        + transaction
        + '\''
        + '}';
  }
}
