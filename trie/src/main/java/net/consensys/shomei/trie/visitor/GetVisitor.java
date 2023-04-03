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

package net.consensys.shomei.trie.visitor;

import net.consensys.shomei.trie.node.EmptyLeafNode;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NullNode;
import org.hyperledger.besu.ethereum.trie.PathNodeVisitor;
import org.hyperledger.besu.ethereum.trie.patricia.BranchNode;
import org.hyperledger.besu.ethereum.trie.patricia.ExtensionNode;
import org.hyperledger.besu.ethereum.trie.patricia.LeafNode;

public class GetVisitor<V> implements PathNodeVisitor<V> {

  @Override
  public Node<V> visit(final BranchNode<V> branchNode, final Bytes path) {
    if (path.isEmpty()) {
      return branchNode;
    }
    final byte childIndex = path.get(0);
    return branchNode.child(childIndex).accept(this, path.slice(1));
  }

  @Override
  public Node<V> visit(final LeafNode<V> leafNode, final Bytes path) {
    return leafNode;
  }

  @Override
  public Node<V> visit(final NullNode<V> nullNode, final Bytes path) {
    return EmptyLeafNode.instance();
  }

  @Override
  public Node<V> visit(final ExtensionNode<V> extensionNode, final Bytes path) {
    throw new MerkleTrieException("extension node not allowed in sparse merkle trie");
  }
}
