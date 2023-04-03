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

import static com.google.common.base.Preconditions.checkNotNull;

import net.consensys.shomei.trie.visitor.CommitVisitor;
import net.consensys.shomei.trie.visitor.GetVisitor;
import net.consensys.shomei.trie.visitor.PutVisitor;
import net.consensys.shomei.trie.visitor.RemoveVisitor;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeFactory;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.trie.NullNode;
import org.hyperledger.besu.ethereum.trie.StoredNode;

/** A {@link StoredSparseMerkleTrie} that persists trie nodes to a key/value store. */
public class StoredSparseMerkleTrie {

  protected final NodeFactory<Bytes> nodeFactory;

  protected Node<Bytes> root;

  public StoredSparseMerkleTrie(
      final NodeLoader nodeLoader,
      final Bytes32 rootHash,
      final Function<Bytes, Bytes> valueSerializer,
      final Function<Bytes, Bytes> valueDeserializer) {
    this.nodeFactory = new StoredNodeFactory(nodeLoader, valueSerializer, valueDeserializer);
    this.root =
        rootHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)
            ? NullNode.instance()
            : new StoredNode<>(nodeFactory, Bytes.EMPTY, rootHash);
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

  public void put(final Bytes path, final Bytes value) {
    checkNotNull(path);
    checkNotNull(value);
    this.root = root.accept(getPutVisitor(value), path);
  }

  public List<Node<Bytes>> putAndProve(final Hash key, final Bytes path, final Bytes value) {
    checkNotNull(path);
    checkNotNull(value);
    final PutVisitor<Bytes> putVisitor = getPutVisitor(value);
    this.root = root.accept(putVisitor, path);
    return putVisitor.getProof();
  }

  public void remove(final Bytes path) {
    checkNotNull(path);
    this.root = root.accept(getRemoveVisitor(), path);
  }

  public List<Node<Bytes>> removeAndProve(final Hash key, final Bytes path) {
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
    // Reset root so dirty nodes can be garbage collected
    final Bytes32 rootHash = root.getHash();
    this.root =
        rootHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)
            ? NullNode.instance()
            : new StoredNode<>(nodeFactory, Bytes.EMPTY, rootHash);
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
