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
import org.hyperledger.besu.ethereum.trie.NullNode;
import org.hyperledger.besu.ethereum.trie.PathNodeVisitor;
import org.hyperledger.besu.ethereum.trie.patricia.BranchNode;
import org.hyperledger.besu.ethereum.trie.patricia.ExtensionNode;
import org.hyperledger.besu.ethereum.trie.patricia.LeafNode;

import java.util.ArrayList;
import java.util.List;

import net.consensys.shomei.trie.node.EmptyLeafNode;
import net.consensys.shomei.trie.node.NextFreeNode;
import org.apache.tuweni.bytes.Bytes;

/**
 * A visitor that removes a node from the trie and collects the subProof.
 *
 * @param <V> the type of the value stored in the trie.
 */
public class RemoveVisitor<V> implements PathNodeVisitor<V> {

  private final List<Node<V>> proof = new ArrayList<>();

  @Override
  public Node<V> visit(final BranchNode<V> branchNode, final Bytes path) {
    final byte childIndex = path.get(0);
    final Node<V> updatedChild = branchNode.child(childIndex).accept(this, path.slice(1));
    final Node<V> sibling = branchNode.child((byte) (childIndex ^ 1));
    if (!(sibling instanceof NextFreeNode<V>)) {
      /*
       *         o
       *        / \
       *       o   o (L1)
       *       / \
       *  (L2)o  x
       *
       * (L1) and (L3) represent the nodes that are part of the subProof for the second leaf x.
       * it's why we are adding the sibling to the subProof. (we removed the next free node from the subProof because we want only the subtrie that contains the key)
       */
      proof.add(sibling);
    }
    return branchNode.replaceChild(childIndex, updatedChild);
  }

  @Override
  public Node<V> visit(final LeafNode<V> leafNode, final Bytes path) {
    return EmptyLeafNode.instance();
  }

  @Override
  public Node<V> visit(final NullNode<V> nullNode, final Bytes path) {
    return EmptyLeafNode.instance();
  }

  public List<Node<V>> getProof() {
    return proof;
  }

  @Override
  public Node<V> visit(final ExtensionNode<V> extensionNode, final Bytes path) {
    throw new MerkleTrieException("extension node not allowed in sparse merkle trie");
  }
}
