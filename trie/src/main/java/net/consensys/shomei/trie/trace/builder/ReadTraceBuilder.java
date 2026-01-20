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
import net.consensys.shomei.trie.trace.ReadTrace;
import net.consensys.shomei.trie.trace.TraceProof;
import org.apache.tuweni.bytes.Bytes;

public final class ReadTraceBuilder {
  private Bytes location = Bytes.EMPTY;
  private long nextFreeNode;
  private Node<Bytes> subRoot;
  private LeafOpening leaf;
  private TraceProof proof;
  private Bytes key;
  private Bytes value;

  private ReadTraceBuilder() {}

  public static ReadTraceBuilder aReadTrace() {
    return new ReadTraceBuilder();
  }

  public ReadTraceBuilder withLocation(Bytes location) {
    this.location = location;
    return this;
  }

  public ReadTraceBuilder withNextFreeNode(long nextFreeNode) {
    this.nextFreeNode = nextFreeNode;
    return this;
  }

  public ReadTraceBuilder withSubRoot(Node<Bytes> subRoot) {
    this.subRoot = subRoot;
    return this;
  }

  public ReadTraceBuilder withLeaf(LeafOpening leaf) {
    this.leaf = leaf;
    return this;
  }

  public ReadTraceBuilder withProof(TraceProof proof) {
    this.proof = proof;
    return this;
  }

  public ReadTraceBuilder withKey(Bytes key) {
    this.key = key;
    return this;
  }

  public ReadTraceBuilder withValue(Bytes value) {
    this.value = value;
    return this;
  }

  public ReadTrace build() {
    return new ReadTrace(location, nextFreeNode, subRoot, leaf, proof, key, value);
  }
}
