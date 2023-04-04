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

package net.consensys.shomei.trie.proof;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.trie.Node;

public class DeleteTrace implements Trace {

  public Node<Bytes> oldRoot;
  public Node<Bytes> newRoot;

  // `New` correspond to the inserted leaf
  public Proof proofLeft; // HKEY -
  public Proof proofDeleted; // hash(k)
  public Proof proofRight; // HKEY +
  public Bytes key;

  // Value of the leaf opening before being modified
  public Bytes priorLeftLeaf;
  public Bytes priorDeletedLeaf;
  public Bytes priorRightLeaf;

  public DeleteTrace(final Node<Bytes> oldRoot) {
    this.oldRoot = oldRoot;
  }

  public Node<Bytes> getOldRoot() {
    return oldRoot;
  }

  public Node<Bytes> getNewRoot() {
    return newRoot;
  }

  public void setNewRoot(final Node<Bytes> newRoot) {
    this.newRoot = newRoot;
  }

  public Proof getProofLeft() {
    return proofLeft;
  }

  public void setProofLeft(final Proof proofLeft) {
    this.proofLeft = proofLeft;
  }

  public Proof getProofDeleted() {
    return proofDeleted;
  }

  public void setProofDeleted(final Proof proofDeleted) {
    this.proofDeleted = proofDeleted;
  }

  public Proof getProofRight() {
    return proofRight;
  }

  public void setProofRight(final Proof proofRight) {
    this.proofRight = proofRight;
  }

  public Bytes getKey() {
    return key;
  }

  public void setKey(final Bytes key) {
    this.key = key;
  }

  public Bytes getPriorLeftLeaf() {
    return priorLeftLeaf;
  }

  public void setPriorLeftLeaf(final Bytes priorLeftLeaf) {
    this.priorLeftLeaf = priorLeftLeaf;
  }

  public Bytes getPriorDeletedLeaf() {
    return priorDeletedLeaf;
  }

  public void setPriorDeletedLeaf(final Bytes priorDeletedLeaf) {
    this.priorDeletedLeaf = priorDeletedLeaf;
  }

  public Bytes getPriorRightLeaf() {
    return priorRightLeaf;
  }

  public void setPriorRightLeaf(final Bytes priorRightLeaf) {
    this.priorRightLeaf = priorRightLeaf;
  }
}
