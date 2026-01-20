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
package net.consensys.shomei.trie.trace.builder;

import org.hyperledger.besu.ethereum.trie.Node;

import net.consensys.shomei.trie.model.LeafOpening;
import net.consensys.shomei.trie.trace.InsertionTrace;
import net.consensys.shomei.trie.trace.TraceProof;
import org.apache.tuweni.bytes.Bytes;

public final class InsertionTraceBuilder {
  private Bytes location = Bytes.EMPTY;
  private long newNextFreeNode;
  private Node<Bytes> oldSubRoot;
  private Node<Bytes> newSubRoot;
  private TraceProof leftProof;
  private TraceProof newProof;
  private TraceProof rightProof;
  private Bytes key;
  private Bytes value;
  private LeafOpening priorLeftLeaf;
  private LeafOpening priorRightLeaf;

  private InsertionTraceBuilder() {}

  public static InsertionTraceBuilder anInsertionTrace() {
    return new InsertionTraceBuilder();
  }

  public InsertionTraceBuilder withLocation(Bytes location) {
    this.location = location;
    return this;
  }

  public InsertionTraceBuilder withNewNextFreeNode(long newNextFreeNode) {
    this.newNextFreeNode = newNextFreeNode;
    return this;
  }

  public InsertionTraceBuilder withOldSubRoot(Node<Bytes> oldSubRoot) {
    this.oldSubRoot = oldSubRoot;
    return this;
  }

  public InsertionTraceBuilder withNewSubRoot(Node<Bytes> newSubRoot) {
    this.newSubRoot = newSubRoot;
    return this;
  }

  public InsertionTraceBuilder withLeftProof(TraceProof leftProof) {
    this.leftProof = leftProof;
    return this;
  }

  public InsertionTraceBuilder withNewProof(TraceProof newProof) {
    this.newProof = newProof;
    return this;
  }

  public InsertionTraceBuilder withRightProof(TraceProof rightProof) {
    this.rightProof = rightProof;
    return this;
  }

  public InsertionTraceBuilder withKey(Bytes key) {
    this.key = key;
    return this;
  }

  public InsertionTraceBuilder withValue(Bytes value) {
    this.value = value;
    return this;
  }

  public InsertionTraceBuilder withPriorLeftLeaf(LeafOpening priorLeftLeaf) {
    this.priorLeftLeaf = priorLeftLeaf;
    return this;
  }

  public InsertionTraceBuilder withPriorRightLeaf(LeafOpening priorRightLeaf) {
    this.priorRightLeaf = priorRightLeaf;
    return this;
  }

  public InsertionTrace build() {
    return new InsertionTrace(
        location,
        newNextFreeNode,
        oldSubRoot,
        newSubRoot,
        leftProof,
        newProof,
        rightProof,
        key,
        value,
        priorLeftLeaf,
        priorRightLeaf);
  }
}
