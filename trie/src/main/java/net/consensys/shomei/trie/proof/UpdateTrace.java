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

public class UpdateTrace implements Trace {

  public Node<Bytes> oldRoot;
  public Node<Bytes> newRoot;

  public Proof proof;

  public Bytes key;
  public Bytes oldValue;
  public Bytes newValue;

  // Value of the leaf opening before being modified
  public Bytes priorUpdatedLeaf;

  public UpdateTrace(final Node<Bytes> oldRoot) {
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

  public Proof getProof() {
    return proof;
  }

  public void setProof(final Proof proof) {
    this.proof = proof;
  }

  public Bytes getKey() {
    return key;
  }

  public void setKey(final Bytes key) {
    this.key = key;
  }

  public Bytes getOldValue() {
    return oldValue;
  }

  public void setOldValue(final Bytes oldValue) {
    this.oldValue = oldValue;
  }

  public Bytes getNewValue() {
    return newValue;
  }

  public void setNewValue(final Bytes newValue) {
    this.newValue = newValue;
  }

  public Bytes getPriorUpdatedLeaf() {
    return priorUpdatedLeaf;
  }

  public void setPriorUpdatedLeaf(final Bytes priorUpdatedLeaf) {
    this.priorUpdatedLeaf = priorUpdatedLeaf;
  }
}
