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
package net.consensys.shomei.trie.node;

import org.hyperledger.besu.ethereum.trie.LocationNodeVisitor;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeVisitor;
import org.hyperledger.besu.ethereum.trie.NullNode;
import org.hyperledger.besu.ethereum.trie.PathNodeVisitor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class EmptyLeafNode<V> extends NullNode<V> implements Node<V> {
  @SuppressWarnings("rawtypes")
  private static final EmptyLeafNode INSTANCE = new EmptyLeafNode();

  protected EmptyLeafNode() {}

  @SuppressWarnings("unchecked")
  public static <V> EmptyLeafNode<V> instance() {
    return INSTANCE;
  }

  @Override
  public Node<V> accept(final PathNodeVisitor<V> visitor, final Bytes path) {
    return visitor.visit(this, path);
  }

  @Override
  public void accept(final NodeVisitor<V> visitor) {
    visitor.visit(this);
  }

  @Override
  public void accept(final Bytes location, final LocationNodeVisitor<V> visitor) {
    visitor.visit(location, this);
  }

  @Override
  public Bytes getPath() {
    return Bytes.EMPTY;
  }

  @Override
  public Optional<V> getValue() {
    return Optional.empty();
  }

  @Override
  public List<Node<V>> getChildren() {
    return Collections.emptyList();
  }

  @Override
  public Bytes getEncodedBytes() {
    return Bytes32.ZERO;
  }

  @Override
  public Bytes getEncodedBytesRef() {
    return Bytes32.ZERO;
  }

  @Override
  public Bytes32 getHash() {
    return Bytes32.ZERO;
  }

  @Override
  public Node<V> replacePath(final Bytes path) {
    return this;
  }

  @Override
  public String print() {
    return "[EMPTY LEAF]";
  }

  @Override
  public boolean isDirty() {
    return false;
  }

  @Override
  public void markDirty() {
    // do nothing
  }

  @Override
  public boolean isHealNeeded() {
    return false;
  }

  @Override
  public void markHealNeeded() {
    // do nothing
  }

  @Override
  public boolean isReferencedByHash() {
    return true;
  }
}
