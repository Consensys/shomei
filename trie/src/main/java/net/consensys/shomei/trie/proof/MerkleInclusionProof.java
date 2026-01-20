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

public class MerkleInclusionProof extends MerkleProof {

  private final long leafIndex;
  private final Proof<Bytes> proof;

  public MerkleInclusionProof(final Bytes key, final long leafIndex, final Proof<Bytes> proof) {
    super(key);
    this.leafIndex = leafIndex;
    this.proof = proof;
  }

  public long getLeafIndex() {
    return leafIndex;
  }

  public Proof<Bytes> getProof() {
    return proof;
  }
}
