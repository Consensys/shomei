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

package net.consensys.shomei.trie;

import static java.lang.String.format;
import static net.consensys.shomei.trie.node.LeafType.fromBytes;

import net.consensys.shomei.trie.node.BranchNode;
import net.consensys.shomei.trie.node.EmptyLeafNode;
import net.consensys.shomei.trie.node.LeafNode;
import net.consensys.shomei.trie.node.LeafType;
import net.consensys.shomei.trie.node.NextFreeNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeFactory;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.NullNode;
import org.hyperledger.besu.ethereum.trie.StoredNode;

/**
 * The StoredNodeFactory class is responsible for creating and managing stored nodes in a stored
 * sparse Merkle trie. It provides methods for creating new stored nodes and retrieving existing
 * stored nodes by their location/hash. The StoredNodeFactory is used by the StoredSparseMerkleTrie
 * to manage the lifecycle of stored nodes.
 */
public class StoredNodeFactory implements NodeFactory<Bytes> {

  @SuppressWarnings("rawtypes")
  public static final NullNode NULL_NODE = NullNode.instance();

  // The number of children in a branch node. max 2 for the sparse merkle trie.
  private static final int NB_CHILD = 2;

  private final NodeLoader nodeLoader;
  private final Function<Bytes, Bytes> valueSerializer;

  public StoredNodeFactory(
      final NodeLoader nodeLoader, final Function<Bytes, Bytes> valueSerializer) {
    this.nodeLoader = nodeLoader;
    this.valueSerializer = valueSerializer;
  }

  /**
   * The `createExtension` method is used to create an extension node in the sparse Merkle trie.
   *
   * <p>In this implementation, creating an extension node is not supported, and invoking this
   * method will throw an `UnsupportedOperationException`. The sparse Merkle trie does not allow the
   * creation of extension nodes, as it follows a representation where only leaf and branch nodes
   * are used.
   *
   * @param path The path representing the extension node.
   * @param child The child node that will be attached to the extension node.
   * @throws UnsupportedOperationException when attempting to create an extension node in the sparse
   *     Merkle trie.
   */
  @Override
  public Node<Bytes> createExtension(final Bytes path, final Node<Bytes> child) {
    throw new UnsupportedOperationException("cannot create extension in the sparse merkle trie");
  }

  @SuppressWarnings("unchecked")
  @Override
  public Node<Bytes> createBranch(
      final byte leftIndex,
      final Node<Bytes> left,
      final byte rightIndex,
      final Node<Bytes> right) {
    assert (leftIndex <= NB_CHILD);
    assert (rightIndex <= NB_CHILD);
    assert (leftIndex != rightIndex);

    final ArrayList<Node<Bytes>> children =
        new ArrayList<>(Collections.nCopies(NB_CHILD, (Node<Bytes>) NULL_NODE));

    if (leftIndex == NB_CHILD) {
      children.set(rightIndex, right);
      return createBranch(children, left.getValue());
    } else if (rightIndex == NB_CHILD) {
      children.set(leftIndex, left);
      return createBranch(children, right.getValue());
    } else {
      children.set(leftIndex, left);
      children.set(rightIndex, right);
      return createBranch(children, Optional.empty());
    }
  }

  @Override
  public Node<Bytes> createBranch(final List<Node<Bytes>> children, final Optional<Bytes> value) {
    return handleNewNode(new BranchNode<>(children, value, this, valueSerializer));
  }

  @Override
  public Node<Bytes> createLeaf(final Bytes path, final Bytes value) {
    if (fromBytes(path).equals(LeafType.NEXT_FREE_NODE)) {
      return handleNewNode(new NextFreeNode<>(path, value, this, valueSerializer));
    }
    return handleNewNode(new LeafNode<>(path, value, this, valueSerializer));
  }

  private Node<Bytes> handleNewNode(final Node<Bytes> node) {
    node.markDirty();
    return node;
  }

  @Override
  public Optional<Node<Bytes>> retrieve(final Bytes location, final Bytes32 hash)
      throws MerkleTrieException {

    /*
     * Fill the nodes of the sparse Merkle trie by storing values by hash in the database to avoid
     * duplicating all the nodes during initialization and to have only one instance per level.
     * Later, we use location-based optimization for pruning.
     *
     * When constructing a sparse Merkle trie, it is efficient to first store the hashed values
     * in a database instead of duplicating all the nodes. By storing the values hashed, we can
     * reduce storage consumption and avoid unnecessary duplication of identical values in the trie.
     *
     * Additionally, after initializing the trie, we can optimize pruning by using a
     * location-based approach.
     *
     * By combining the storage of hashed values during initialization and location-based
     * optimization for pruning, we can achieve an efficient and compact representation of the
     * sparse Merkle trie.
     */
    if (Bytes32.ZERO.equals(hash)) {
      return Optional.of(EmptyLeafNode.instance());
    }
    return nodeLoader
        .getNode(location, hash)
        .or(
            () ->
                ZKTrie.EMPTY_TRIE
                    .getWorldStateStorage()
                    .getTrieNode(location, hash)) // if not found in db try to find default nodes
        .map(
            encodedBytes ->
                decode(
                    location, encodedBytes, () -> format("Invalid RLP value for hash %s", hash)));
  }

  private Node<Bytes> decode(
      final Bytes location, final Bytes input, final Supplier<String> errMessage) {

    int type =
        input.size() == Hash.SIZE * 2
            ? 1
            : 2; // a leaf will only be bigger (leaf opening) or smaller (zero leaf)

    switch (type) {
      case 1 -> {
        if (location.isEmpty()) {
          return decodeRoot(input);
        }
        return decodeBranch(location, input);
      }
      case 2 -> {
        return decodeLeaf(input);
      }
      default -> throw new MerkleTrieException(
          errMessage.get() + format(": invalid node %s", type));
    }
  }

  protected BranchNode<Bytes> decodeRoot(final Bytes input) {
    final ArrayList<Node<Bytes>> children = new ArrayList<>(NB_CHILD);
    final Bytes32 nextFreeNode = Bytes32.wrap(input.slice(0, Bytes32.SIZE));
    children.add(
        new NextFreeNode<>(
            Bytes.of(LeafType.NEXT_FREE_NODE.getTerminatorPath()),
            nextFreeNode,
            this,
            valueSerializer));
    final Bytes32 childHash = Bytes32.wrap(input.slice(Bytes32.SIZE, Hash.SIZE));
    children.add(new StoredNode<>(this, Bytes.concatenate(Bytes.of((byte) 1)), childHash));
    return new BranchNode<>(Bytes.EMPTY, children, Optional.empty(), this, valueSerializer);
  }

  protected BranchNode<Bytes> decodeBranch(final Bytes location, final Bytes input) {
    final ArrayList<Node<Bytes>> children = new ArrayList<>(NB_CHILD);
    final int nbChilds = input.size() / Hash.SIZE;
    for (int i = 0; i < nbChilds; i++) {
      final Bytes32 childHash = Bytes32.wrap(input.slice(i * Bytes32.SIZE, Hash.SIZE));
      children.add(
          new StoredNode<>(
              this,
              location == null ? null : Bytes.concatenate(location, Bytes.of((byte) i)),
              childHash));
    }
    return new BranchNode<>(location, children, Optional.empty(), this, valueSerializer);
  }

  protected Node<Bytes> decodeLeaf(final Bytes input) {
    if (input.equals(Bytes32.ZERO)) {
      return EmptyLeafNode.instance();
    } else {
      return new LeafNode<>(Bytes.EMPTY, input, this, valueSerializer);
    }
  }
}
