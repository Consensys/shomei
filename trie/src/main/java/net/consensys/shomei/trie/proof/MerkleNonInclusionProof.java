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
package net.consensys.shomei.trie.proof;

import org.hyperledger.besu.ethereum.trie.Proof;

import org.apache.tuweni.bytes.Bytes;

public class MerkleNonInclusionProof extends MerkleProof {

  private final Proof<Bytes> leftProof;
  private final Proof<Bytes> rightProof;

  private final long leftLeafIndex;
  private final long rightLeafIndex;

  public MerkleNonInclusionProof(
      final Bytes key,
      final long leftLeafIndex,
      final long rightLeafIndex,
      final Proof<Bytes> leftProof,
      final Proof<Bytes> rightProof) {
    super(key);
    this.leftLeafIndex = leftLeafIndex;
    this.rightLeafIndex = rightLeafIndex;
    this.leftProof = leftProof;
    this.rightProof = rightProof;
  }

  public long getLeftLeafIndex() {
    return leftLeafIndex;
  }

  public long getRightLeafIndex() {
    return rightLeafIndex;
  }

  public Proof<Bytes> getLeftProof() {
    return leftProof;
  }

  public Proof<Bytes> getRightProof() {
    return rightProof;
  }
}
