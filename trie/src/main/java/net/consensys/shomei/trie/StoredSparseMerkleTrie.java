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
package net.consensys.shomei.trie;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeFactory;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.trie.StoredNode;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import net.consensys.shomei.trie.visitor.CommitVisitor;
import net.consensys.shomei.trie.visitor.GetVisitor;
import net.consensys.shomei.trie.visitor.PutVisitor;
import net.consensys.shomei.trie.visitor.RemoveVisitor;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * The StoredSparseMerkleTrie class represents a stored sparse Merkle trie. It provides methods for
 * storing, retrieving, and manipulating data in the trie, and it leverages storage to optimize
 * memory usage by storing only the modified nodes. The StoredSparseMerkleTrie implements the Trie
 * interface and uses a sparse Merkle tree structure.
 */
public class StoredSparseMerkleTrie {

  protected final NodeFactory<Bytes> nodeFactory;

  protected Node<Bytes> root;

  public StoredSparseMerkleTrie(
      final NodeLoader nodeLoader,
      final Bytes32 rootHash,
      final Function<Bytes, Bytes> valueSerializer) {
    this.nodeFactory = new StoredNodeFactory(nodeLoader, valueSerializer);
    this.root = new StoredNode<>(nodeFactory, Bytes.EMPTY, rootHash);
  }

  public Bytes32 getRootHash() {
    return root.getHash();
  }

  public Node<Bytes> getNode(final Bytes path) {
    checkNotNull(path);
    return root.accept(getGetVisitor(), path);
  }

  public Optional<Bytes> get(final Bytes path) {
    checkNotNull(path);
    return root.accept(getGetVisitor(), path).getValue();
  }

  public record GetAndProve(
      Optional<Bytes> nodeValue, List<Node<Bytes>> subProof, Optional<Node<Bytes>> leaf) {}

  /**
   * The `getAndProve` method retrieves the value associated with the given key in the sparse Merkle
   * trie and generates a subProof of existence for the key-value pair.
   *
   * @param path The path for which to retrieve the value and generate a subProof.
   */
  public GetAndProve getAndProve(final Bytes path) {
    checkNotNull(path);
    final GetVisitor<Bytes> getVisitor = getGetVisitor();
    final Node<Bytes> node = root.accept(getVisitor, path);
    return new GetAndProve(node.getValue(), getVisitor.getSubProof(), getVisitor.getLeaf());
  }

  public void put(final Bytes path, final Bytes value) {
    checkNotNull(path);
    checkNotNull(value);
    this.root = root.accept(getPutVisitor(value), path);
  }

  /**
   * The `putAndProve` method inserts or updates a key-value pair in the sparse Merkle trie and
   * generates a subProof of inclusion for the updated state.
   */
  public List<Node<Bytes>> putAndProve(final Bytes path, final Bytes value) {
    checkNotNull(path);
    checkNotNull(value);
    final PutVisitor<Bytes> putVisitor = getPutVisitor(value);
    this.root = root.accept(putVisitor, path);
    return putVisitor.getProof();
  }

  /**
   * The `removeAndProve` method removes a key-value pair from the sparse Merkle trie and generates
   * a subProof of exclusion for the removed state.
   */
  public List<Node<Bytes>> removeAndProve(final Bytes path) {
    checkNotNull(path);
    final RemoveVisitor<Bytes> removeVisitor = getRemoveVisitor();
    this.root = root.accept(removeVisitor, path);
    return removeVisitor.getProof();
  }

  public void commit(final NodeUpdater nodeUpdater) {
    commit(nodeUpdater, new CommitVisitor<>(nodeUpdater));
  }

  public void commit(final NodeUpdater nodeUpdater, final CommitVisitor<Bytes> commitVisitor) {
    root.accept(Bytes.EMPTY, commitVisitor);
    // Make sure root node was stored
    if (root.isDirty() && root.getEncodedBytesRef().size() < 32) {
      nodeUpdater.store(Bytes.EMPTY, root.getHash(), root.getEncodedBytesRef());
    }
    this.root = new StoredNode<>(nodeFactory, Bytes.EMPTY, root.getHash());
  }

  public GetVisitor<Bytes> getGetVisitor() {
    return new GetVisitor<>();
  }

  public RemoveVisitor<Bytes> getRemoveVisitor() {
    return new RemoveVisitor<>();
  }

  public PutVisitor<Bytes> getPutVisitor(final Bytes value) {
    return new PutVisitor<>(nodeFactory, value);
  }
}
