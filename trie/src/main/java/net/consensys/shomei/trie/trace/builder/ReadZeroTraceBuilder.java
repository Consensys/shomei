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
import net.consensys.shomei.trie.trace.ReadZeroTrace;
import net.consensys.shomei.trie.trace.TraceProof;
import org.apache.tuweni.bytes.Bytes;

public final class ReadZeroTraceBuilder {
  private Bytes location = Bytes.EMPTY;
  private long nextFreeNode;
  private Node<Bytes> subRoot;
  private LeafOpening leftLeaf;
  private LeafOpening rightLeaf;
  private TraceProof leftProof;
  private TraceProof rightProof;
  private Bytes key;

  private ReadZeroTraceBuilder() {}

  public static ReadZeroTraceBuilder aReadZeroTrace() {
    return new ReadZeroTraceBuilder();
  }

  public ReadZeroTraceBuilder withLocation(Bytes location) {
    this.location = location;
    return this;
  }

  public ReadZeroTraceBuilder withNextFreeNode(long nextFreeNode) {
    this.nextFreeNode = nextFreeNode;
    return this;
  }

  public ReadZeroTraceBuilder withSubRoot(Node<Bytes> subRoot) {
    this.subRoot = subRoot;
    return this;
  }

  public ReadZeroTraceBuilder withLeftLeaf(LeafOpening leftLeaf) {
    this.leftLeaf = leftLeaf;
    return this;
  }

  public ReadZeroTraceBuilder withRightLeaf(LeafOpening rightLeaf) {
    this.rightLeaf = rightLeaf;
    return this;
  }

  public ReadZeroTraceBuilder withLeftProof(TraceProof leftProof) {
    this.leftProof = leftProof;
    return this;
  }

  public ReadZeroTraceBuilder withRightProof(TraceProof rightProof) {
    this.rightProof = rightProof;
    return this;
  }

  public ReadZeroTraceBuilder withKey(Bytes key) {
    this.key = key;
    return this;
  }

  public ReadZeroTrace build() {
    return new ReadZeroTrace(
        location, nextFreeNode, subRoot, leftLeaf, rightLeaf, leftProof, rightProof, key);
  }
}
