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

package net.consensys.shomei.rpc.server;

public enum ShomeiRpcMethod {
  STATE_SEND_RAW_TRIE_LOG("state_sendRawTrieLog"),

  ROLLUP_GET_ZKEVM_BLOCK_NUMBER("rollup_getZkEVMBlockNumber"),

  ROLLUP_GET_PROOF("rollup_getProof"),

  ROLLUP_GET_ZKEVM_STATE_MERKLE_PROOF_V0("rollup_getZkEVMStateMerkleProofV0"),
  ROLLUP_DELETE_ZKEVM_STATE_MERKLE_PROOF_BY_RANGE("rollup_deleteZkEVMStateMerkleProofByRange");

  private final String methodName;

  public String getMethodName() {
    return methodName;
  }

  ShomeiRpcMethod(final String methodName) {
    this.methodName = methodName;
  }
}
