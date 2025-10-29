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
import net.consensys.shomei.trie.trace.TraceProof;
import net.consensys.shomei.trie.trace.UpdateTrace;
import org.apache.tuweni.bytes.Bytes;

public final class UpdateTraceBuilder {
  private Bytes location = Bytes.EMPTY;
  private long newNextFreeNode;
  private Node<Bytes> oldSubRoot;
  private Node<Bytes> newSubRoot;
  private TraceProof proof;
  private Bytes key;
  private Bytes oldValue;
  private Bytes newValue;
  private LeafOpening priorUpdatedLeaf;

  private UpdateTraceBuilder() {}

  public static UpdateTraceBuilder anUpdateTrace() {
    return new UpdateTraceBuilder();
  }

  public UpdateTraceBuilder withLocation(Bytes location) {
    this.location = location;
    return this;
  }

  public UpdateTraceBuilder withNewNextFreeNode(long newNextFreeNode) {
    this.newNextFreeNode = newNextFreeNode;
    return this;
  }

  public UpdateTraceBuilder withOldSubRoot(Node<Bytes> oldSubRoot) {
    this.oldSubRoot = oldSubRoot;
    return this;
  }

  public UpdateTraceBuilder withNewSubRoot(Node<Bytes> newSubRoot) {
    this.newSubRoot = newSubRoot;
    return this;
  }

  public UpdateTraceBuilder withProof(TraceProof proof) {
    this.proof = proof;
    return this;
  }

  public UpdateTraceBuilder withKey(Bytes key) {
    this.key = key;
    return this;
  }

  public UpdateTraceBuilder withOldValue(Bytes oldValue) {
    this.oldValue = oldValue;
    return this;
  }

  public UpdateTraceBuilder withNewValue(Bytes newValue) {
    this.newValue = newValue;
    return this;
  }

  public UpdateTraceBuilder withPriorUpdatedLeaf(LeafOpening priorUpdatedLeaf) {
    this.priorUpdatedLeaf = priorUpdatedLeaf;
    return this;
  }

  public UpdateTrace build() {
    return new UpdateTrace(
        location,
        newNextFreeNode,
        oldSubRoot,
        newSubRoot,
        proof,
        key,
        oldValue,
        newValue,
        priorUpdatedLeaf);
  }
}
