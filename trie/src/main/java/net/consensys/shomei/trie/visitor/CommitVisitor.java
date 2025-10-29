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
package net.consensys.shomei.trie.visitor;

import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.trie.NullNode;
import org.hyperledger.besu.ethereum.trie.patricia.ExtensionNode;

import org.apache.tuweni.bytes.Bytes;

/**
 * CommitVisitor is a visitor that stores nodes in the database. It is used to commit a trie to the
 * storage.
 *
 * @param <V> the type of values stored in the trie.
 */
public class CommitVisitor<V> extends org.hyperledger.besu.ethereum.trie.CommitVisitor<V> {
  public CommitVisitor(final NodeUpdater nodeUpdater) {
    super(nodeUpdater);
  }

  @Override
  public void maybeStoreNode(final Bytes location, final Node<V> node) {
    this.nodeUpdater.store(location, node.getHash(), node.getEncodedBytes());
  }

  @Override
  public void visit(final Bytes location, final NullNode<V> node) {
    if (!node.getEncodedBytes().isEmpty()) {
      this.nodeUpdater.store(location, node.getHash(), node.getEncodedBytes());
    }
  }

  @Override
  public void visit(Bytes location, ExtensionNode<V> extensionNode) {
    throw new MerkleTrieException("extension node not allowed in sparse merkle trie");
  }
}
