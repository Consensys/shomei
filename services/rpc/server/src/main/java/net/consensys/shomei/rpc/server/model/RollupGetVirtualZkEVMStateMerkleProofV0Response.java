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

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("unused")
public class RollupGetVirtualZkEVMStateMerkleProofV0Response {

  @JsonProperty("zkStateMerkleProof")
  private final String zkStateMerkleProof;

  @JsonProperty("zkParentStateRootHash")
  private final String zkParentStateRootHash;

  @JsonProperty("zkStateManagerVersion")
  private final String zkStateManagerVersion;

  public RollupGetVirtualZkEVMStateMerkleProofV0Response(
      final String zkStateMerkleProof,
      final String zkParentStateRootHash,
      final String zkStateManagerVersion) {
    this.zkStateMerkleProof = zkStateMerkleProof;
    this.zkParentStateRootHash = zkParentStateRootHash;
    this.zkStateManagerVersion = zkStateManagerVersion;
  }
}
