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

import static com.google.common.base.Preconditions.checkArgument;

import net.consensys.shomei.trie.StoredSparseMerkleTrie.GetAndProve;
import net.consensys.shomei.trie.model.FlattenedLeaf;
import net.consensys.shomei.trie.model.LeafOpening;
import net.consensys.shomei.trie.node.EmptyLeafNode;
import net.consensys.shomei.trie.path.PathResolver;
import net.consensys.shomei.trie.proof.MerkleInclusionProof;
import net.consensys.shomei.trie.proof.MerkleNonInclusionProof;
import net.consensys.shomei.trie.proof.MerkleProof;
import net.consensys.shomei.trie.storage.InMemoryStorage;
import net.consensys.shomei.trie.storage.TrieStorage;
import net.consensys.shomei.trie.storage.TrieStorage.Range;
import net.consensys.shomei.trie.trace.EmptyTrace;
import net.consensys.shomei.trie.trace.Trace;
import net.consensys.shomei.trie.trace.TraceProof;
import net.consensys.shomei.trie.trace.builder.DeletionTraceBuilder;
import net.consensys.shomei.trie.trace.builder.InsertionTraceBuilder;
import net.consensys.shomei.trie.trace.builder.ReadTraceBuilder;
import net.consensys.shomei.trie.trace.builder.ReadZeroTraceBuilder;
import net.consensys.shomei.trie.trace.builder.UpdateTraceBuilder;
import net.consensys.shomei.util.bytes.MimcSafeBytes;
import net.consensys.zkevm.HashProvider;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.trie.Proof;

/**
 * The ZKTrie class represents a zero-knowledge Merkle trie. It provides methods for storing,
 * retrieving, and manipulating data in the trie. The ZKTrie implements the Trie interface and uses
 * a Merkle tree structure for efficient storage and retrieval.
 */
public class ZKTrie {

  public static final ZKTrie EMPTY_TRIE = generateEmptyTrie();

  public static final Hash DEFAULT_TRIE_ROOT =
      Hash.wrap(createTrie(new InMemoryStorage()).getTopRootHash());

  private static final int ZK_TRIE_DEPTH = 40;

  private final PathResolver pathResolver;
  private final StoredSparseMerkleTrie state;
  private final TrieStorage worldStateStorage;

  private final TrieStorage.TrieUpdater updater;

  public TrieStorage getWorldStateStorage() {
    return worldStateStorage;
  }

  /**
   * Creates a new ZK trie.
   *
   * @param rootHash the root hash of the trie
   * @param worldStateStorage the storage used to store the trie
   * @return the created ZK trie
   */
  private ZKTrie(final Bytes32 rootHash, final TrieStorage worldStateStorage) {
    this.worldStateStorage = worldStateStorage;
    this.updater = worldStateStorage.updater();
    this.state = new StoredSparseMerkleTrie(worldStateStorage::getTrieNode, rootHash, b -> b);
    this.pathResolver = new PathResolver(ZK_TRIE_DEPTH, state);
  }

  public static ZKTrie generateEmptyTrie() {
    final InMemoryStorage inMemoryStorage =
        new InMemoryStorage() {
          @Override
          public Optional<Bytes> getTrieNode(final Bytes location, final Bytes nodeHash) {
            return Optional.ofNullable(super.getTrieNodeStorage().get(nodeHash));
            // In a sparse Merkle trie, the hash value of a parent node is computed based on the
            // hashes of its children nodes.
            // so the hash value of a parent node at each level will be the same as long as its
            // children remain unchanged.
            // this ensures the consistency of the hash values within each level.
            // for that nodes are saved by hash in order to not have to fill all the trie during
            // the init step
          }

          @Override
          public void putTrieNode(final Bytes location, final Bytes nodeHash, final Bytes value) {
            super.getTrieNodeStorage().put(nodeHash, value);
          }
        };
    return new ZKTrie(initWorldState(inMemoryStorage::putTrieNode).getHash(), inMemoryStorage);
  }

  public static ZKTrie createTrie(final TrieStorage worldStateStorage) {
    final ZKTrie trie = new ZKTrie(EMPTY_TRIE.getTopRootHash(), worldStateStorage);
    trie.setHeadAndTail();
    return trie;
  }

  public static ZKTrie loadTrie(final Bytes32 rootHash, final TrieStorage worldStateStorage) {
    return new ZKTrie(rootHash, worldStateStorage);
  }

  /**
   * Fills the sparse Merkle trie by creating a single node on each level with two children per
   * level. Each level is filled with branch nodes, except for the bottom level with empty leaf
   * nodes.
   *
   * <p>This approach simplifies the population process by creating a balanced trie structure with a
   * fixed number of nodes on each level. Each level (except the bottom level) has two children,
   * which are empty branch nodes. The bottom level has empty leaf nodes.
   *
   * <p>Algorithm: 1. Start with an empty trie. 2. Create leaf nodes at the bottom level. 3. For
   * each level of the trie (starting from the bottom level and moving upwards): - Create a parent
   * node with the leaf/branch nodes of the current level as children. - Insert the parent node into
   * the trie.
   */
  private static Node<Bytes> initWorldState(final NodeUpdater nodeUpdater) {
    // if empty we need to fill the sparse trie with zero leaves
    final StoredNodeFactory nodeFactory =
        new StoredNodeFactory((location, hash) -> Optional.empty(), a -> a);
    Node<Bytes> defaultNode = EmptyLeafNode.instance();
    for (int i = 0; i < ZK_TRIE_DEPTH; i++) {
      nodeUpdater.store(null, defaultNode.getHash(), defaultNode.getEncodedBytes());
      defaultNode = nodeFactory.createBranch(Collections.nCopies(2, defaultNode), Optional.empty());
    }
    nodeUpdater.store(null, defaultNode.getHash(), defaultNode.getEncodedBytes());

    // create root node
    defaultNode =
        nodeFactory.createBranch(List.of(EmptyLeafNode.instance(), defaultNode), Optional.empty());

    nodeUpdater.store(null, defaultNode.getHash(), defaultNode.getEncodedBytes());

    return defaultNode;
  }

  public void setHeadAndTail() {
    // head
    final Long headIndex = pathResolver.getNextFreeLeafNodeIndex();
    updater.putFlatLeaf(LeafOpening.HEAD.getHkey(), FlattenedLeaf.HEAD);
    state.put(pathResolver.getLeafPath(headIndex), LeafOpening.HEAD.getEncodesBytes());
    pathResolver.incrementNextFreeLeafNodeIndex();
    // tail
    final Long tailIndex = pathResolver.getNextFreeLeafNodeIndex();
    updater.putFlatLeaf(LeafOpening.TAIL.getHkey(), FlattenedLeaf.TAIL);
    state.put(pathResolver.getLeafPath(tailIndex), LeafOpening.TAIL.getEncodesBytes());
    pathResolver.incrementNextFreeLeafNodeIndex();
  }

  public Node<Bytes> getSubRootNode() {
    return state.getNode(pathResolver.geRootPath());
  }

  public Node<Bytes> getTopRootNode() {
    return state.root;
  }

  public Bytes32 getSubRootHash() {
    return getSubRootNode().getHash();
  }

  public Bytes32 getTopRootHash() {
    return state.getRootHash();
  }

  public long getNextFreeNode() {
    return pathResolver.getNextFreeLeafNodeIndex();
  }

  public Optional<FlattenedLeaf> getFlatLeaf(final Hash hkey) {
    return worldStateStorage.getFlatLeaf(hkey);
  }

  public Optional<Long> getLeafIndex(final Hash hkey) {
    return getFlatLeaf(hkey).map(FlattenedLeaf::leafIndex);
  }

  public Optional<Bytes> get(final Hash hkey) {
    return worldStateStorage
        .getFlatLeaf(hkey)
        .map(FlattenedLeaf::leafIndex)
        .map(pathResolver::getLeafPath)
        .flatMap(this::get);
  }

  public GetAndProve getValueAndMerkleProof(final Long leafIndex) {
    return getValueAndMerkleProof(pathResolver.getLeafPath(leafIndex));
  }

  private Optional<Bytes> get(final Bytes path) {
    return state.get(path);
  }

  private GetAndProve getValueAndMerkleProof(final Bytes path) {
    return state.getAndProve(path);
  }

  public MerkleProof getProof(final Hash hkey, final MimcSafeBytes<? extends Bytes> key) {
    // GET the openings HKEY-,  hash(k) , HKEY+
    final Range nearestKeys = worldStateStorage.getNearestKeys(hkey);
    // CHECK if hash(k) exist
    if (nearestKeys.getCenterNodeKey().isEmpty()) {

      final FlattenedLeaf leftFlatLeafValue = nearestKeys.getLeftNodeValue();
      final FlattenedLeaf rightFlatLeafValue = nearestKeys.getRightNodeValue();

      // GET path of HKey-
      final Bytes leftLeafPath = pathResolver.getLeafPath(leftFlatLeafValue.leafIndex());
      // GET path of HKey+
      final Bytes rightLeafPath = pathResolver.getLeafPath(rightFlatLeafValue.leafIndex());

      final GetAndProve leftData = getValueAndMerkleProof(leftLeafPath);
      final GetAndProve rightData = getValueAndMerkleProof(rightLeafPath);

      return new MerkleNonInclusionProof(
          key.getOriginalUnsafeValue(),
          leftFlatLeafValue.leafIndex(),
          rightFlatLeafValue.leafIndex(),
          new Proof<>(
              Optional.of(leftFlatLeafValue.leafValue()),
              Stream.of(
                      List.of(leftData.leaf().orElseThrow()),
                      leftData.subProof(),
                      List.of(state.root))
                  .flatMap(List::stream)
                  .map(Node::getEncodedBytes)
                  .collect(
                      Collectors.collectingAndThen(
                          Collectors.toList(),
                          l -> {
                            Collections.reverse(l);
                            return l;
                          }))),
          new Proof<>(
              Optional.of(rightFlatLeafValue.leafValue()),
              Stream.of(
                      List.of(rightData.leaf().orElseThrow()),
                      rightData.subProof(),
                      List.of(state.root))
                  .flatMap(List::stream)
                  .map(Node::getEncodedBytes)
                  .collect(
                      Collectors.collectingAndThen(
                          Collectors.toList(),
                          l -> {
                            Collections.reverse(l);
                            return l;
                          }))));

    } else {

      final FlattenedLeaf currentFlatLeafValue = nearestKeys.getCenterNodeValue().orElseThrow();

      // GET path of hash(k)
      final Bytes leafPath = pathResolver.getLeafPath(currentFlatLeafValue.leafIndex());
      // READ hash(k)
      final GetAndProve data = getValueAndMerkleProof(leafPath);

      return new MerkleInclusionProof(
          key.getOriginalUnsafeValue(),
          currentFlatLeafValue.leafIndex(),
          new Proof<>(
              Optional.of(currentFlatLeafValue.leafValue()),
              Stream.of(List.of(data.leaf().orElseThrow()), data.subProof(), List.of(state.root))
                  .flatMap(List::stream)
                  .map(Node::getEncodedBytes)
                  .collect(
                      Collectors.collectingAndThen(
                          Collectors.toList(),
                          l -> {
                            Collections.reverse(l);
                            return l;
                          }))));
    }
  }

  public Trace readWithTrace(final Hash hkey, final MimcSafeBytes<? extends Bytes> key) {
    // GET the openings HKEY-,  hash(k) , HKEY+
    final Range nearestKeys = worldStateStorage.getNearestKeys(hkey);
    // CHECK if hash(k) exist
    if (nearestKeys.getCenterNodeKey().isEmpty()) {
      // INIT trace
      final ReadZeroTraceBuilder readZeroTrace =
          ReadZeroTraceBuilder.aReadZeroTrace().withSubRoot(getSubRootNode());

      // GET path of HKey-
      final Bytes leftLeafPath =
          pathResolver.getLeafPath(nearestKeys.getLeftNodeValue().leafIndex());
      // GET path of HKey+
      final Bytes rightLeafPath =
          pathResolver.getLeafPath(nearestKeys.getRightNodeValue().leafIndex());

      final GetAndProve leftData = getValueAndMerkleProof(leftLeafPath);
      final GetAndProve rightData = getValueAndMerkleProof(rightLeafPath);

      return readZeroTrace
          .withKey(key.getOriginalUnsafeValue())
          .withNextFreeNode(pathResolver.getNextFreeLeafNodeIndex())
          .withLeftLeaf(leftData.nodeValue().map(LeafOpening::readFrom).orElseThrow())
          .withRightLeaf(rightData.nodeValue().map(LeafOpening::readFrom).orElseThrow())
          .withLeftProof(
              new TraceProof(nearestKeys.getLeftNodeValue().leafIndex(), leftData.subProof()))
          .withRightProof(
              new TraceProof(nearestKeys.getRightNodeValue().leafIndex(), rightData.subProof()))
          .build();

    } else {
      // INIT trace
      final ReadTraceBuilder readTrace =
          ReadTraceBuilder.aReadTrace().withSubRoot(getSubRootNode());

      final FlattenedLeaf currentFlatLeafValue = nearestKeys.getCenterNodeValue().orElseThrow();

      // GET path of hash(k)
      final Bytes leafPath = pathResolver.getLeafPath(currentFlatLeafValue.leafIndex());
      // READ hash(k)
      final GetAndProve data = getValueAndMerkleProof(leafPath);

      return readTrace
          .withKey(key.getOriginalUnsafeValue())
          .withNextFreeNode(pathResolver.getNextFreeLeafNodeIndex())
          .withValue(currentFlatLeafValue.leafValue())
          .withLeaf(data.nodeValue().map(LeafOpening::readFrom).orElseThrow())
          .withProof(new TraceProof(currentFlatLeafValue.leafIndex(), data.subProof()))
          .build();
    }
  }

  public Trace putWithTrace(
      final Hash hKey,
      final MimcSafeBytes<? extends Bytes> key,
      final MimcSafeBytes<? extends Bytes> newValue) {
    checkArgument(hKey.size() == Bytes32.SIZE);

    // GET the openings HKEY-,  hash(k) , HKEY+
    final Range nearestKeys = worldStateStorage.getNearestKeys(hKey);

    // CHECK if hash(k) exist
    if (nearestKeys.getCenterNodeKey().isEmpty()) {

      // INIT trace
      final InsertionTraceBuilder insertionTrace =
          InsertionTraceBuilder.anInsertionTrace().withOldSubRoot(getSubRootNode());

      // GET path of HKey-
      final Bytes leftLeafPath =
          pathResolver.getLeafPath(nearestKeys.getLeftNodeValue().leafIndex());
      // GET path of HKey+
      final Bytes rightLeafPath =
          pathResolver.getLeafPath(nearestKeys.getRightNodeValue().leafIndex());

      // FIND next free node
      final long nextFreeNode = pathResolver.getNextFreeLeafNodeIndex();

      // UPDATE HKey- with hash(k) for next
      final LeafOpening priorLeftLeaf = get(leftLeafPath).map(LeafOpening::readFrom).orElseThrow();

      final LeafOpening newLeftLeaf = new LeafOpening(priorLeftLeaf);
      newLeftLeaf.setNextLeaf(nextFreeNode);

      final List<Node<Bytes>> leftSiblings =
          state.putAndProve(leftLeafPath, newLeftLeaf.getEncodesBytes());

      // PUT hash(k) with HKey- for Prev and HKey+ for next
      final Bytes leafPathToAdd = pathResolver.getLeafPath(nextFreeNode);
      updater.putFlatLeaf(hKey, new FlattenedLeaf(nextFreeNode, newValue.getOriginalUnsafeValue()));
      final LeafOpening newLeafValue =
          new LeafOpening(
              nearestKeys.getLeftNodeValue().leafIndex(),
              nearestKeys.getRightNodeValue().leafIndex(),
              hKey,
              HashProvider.trieHash(newValue));

      final List<Node<Bytes>> centerSiblings =
          state.putAndProve(leafPathToAdd, newLeafValue.getEncodesBytes());

      // UPDATE HKey+ with hash(k) for prev
      final LeafOpening priorRightLeaf =
          get(rightLeafPath).map(LeafOpening::readFrom).orElseThrow();
      final LeafOpening newRightLeaf = new LeafOpening(priorRightLeaf);
      newRightLeaf.setPrevLeaf(nextFreeNode);

      final List<Node<Bytes>> rightSiblings =
          state.putAndProve(rightLeafPath, newRightLeaf.getEncodesBytes());

      // UPDATE next free node
      pathResolver.incrementNextFreeLeafNodeIndex();

      return insertionTrace
          .withKey(key.getOriginalUnsafeValue())
          .withValue(newValue.getOriginalUnsafeValue())
          .withPriorLeftLeaf(priorLeftLeaf)
          .withPriorRightLeaf(priorRightLeaf)
          .withLeftProof(new TraceProof(nearestKeys.getLeftNodeValue().leafIndex(), leftSiblings))
          .withNewProof(new TraceProof(nextFreeNode, centerSiblings))
          .withRightProof(
              new TraceProof(nearestKeys.getRightNodeValue().leafIndex(), rightSiblings))
          .withNewNextFreeNode(pathResolver.getNextFreeLeafNodeIndex())
          .withNewSubRoot(getSubRootNode())
          .build();

    } else {

      // INIT trace
      final UpdateTraceBuilder updateTrace =
          UpdateTraceBuilder.anUpdateTrace().withOldSubRoot(getSubRootNode());

      final FlattenedLeaf currentFlatLeafValue = nearestKeys.getCenterNodeValue().orElseThrow();

      final Bytes leafPathToUpdate = pathResolver.getLeafPath(currentFlatLeafValue.leafIndex());
      updater.putFlatLeaf(
          hKey,
          new FlattenedLeaf(currentFlatLeafValue.leafIndex(), newValue.getOriginalUnsafeValue()));

      // RETRIEVE OLD VALUE
      final LeafOpening priorUpdatedLeaf =
          get(leafPathToUpdate).map(LeafOpening::readFrom).orElseThrow();
      final LeafOpening newUpdatedLeaf = new LeafOpening(priorUpdatedLeaf);
      newUpdatedLeaf.setHval(HashProvider.trieHash(newValue));

      final List<Node<Bytes>> siblings =
          state.putAndProve(leafPathToUpdate, newUpdatedLeaf.getEncodesBytes());

      return updateTrace
          .withKey(key.getOriginalUnsafeValue())
          .withOldValue(currentFlatLeafValue.leafValue())
          .withNewValue(newValue.getOriginalUnsafeValue())
          .withPriorUpdatedLeaf(priorUpdatedLeaf)
          .withProof(new TraceProof(currentFlatLeafValue.leafIndex(), siblings))
          .withNewNextFreeNode(pathResolver.getNextFreeLeafNodeIndex())
          .withNewSubRoot(getSubRootNode())
          .build();
    }
  }

  public Trace removeWithTrace(final Hash hkey, final MimcSafeBytes<? extends Bytes> key) {
    checkArgument(hkey.size() == Bytes32.SIZE);

    // GET the openings HKEY-,  hash(k) , HKEY+
    final Range nearestKeys = worldStateStorage.getNearestKeys(hkey);

    // CHECK if hash(k) exist
    if (nearestKeys.getCenterNode().isPresent()) {

      // INIT trace
      final DeletionTraceBuilder deleteTrace =
          DeletionTraceBuilder.aDeletionTrace().withOldSubRoot(getSubRootNode());

      final Long leftLeafIndex = nearestKeys.getLeftNodeValue().leafIndex();
      final Long rightLeafIndex = nearestKeys.getRightNodeValue().leafIndex();

      // UPDATE HKey- with HKey+ for next
      final Bytes leftLeafPath = pathResolver.getLeafPath(leftLeafIndex);
      final LeafOpening priorLeftLeaf = get(leftLeafPath).map(LeafOpening::readFrom).orElseThrow();
      final LeafOpening newLeftLeaf = new LeafOpening(priorLeftLeaf);
      newLeftLeaf.setNextLeaf(rightLeafIndex);

      final List<Node<Bytes>> leftSiblings =
          state.putAndProve(leftLeafPath, newLeftLeaf.getEncodesBytes());

      // REMOVE hash(k)
      final FlattenedLeaf currentFlatLeafValue = nearestKeys.getCenterNodeValue().orElseThrow();
      final Bytes leafPathToDelete = pathResolver.getLeafPath(currentFlatLeafValue.leafIndex());
      final LeafOpening priorDeletedLeaf =
          get(leafPathToDelete).map(LeafOpening::readFrom).orElseThrow();
      updater.removeFlatLeafValue(hkey);
      final List<Node<Bytes>> centerSiblings = state.removeAndProve(leafPathToDelete);

      // UPDATE HKey+ with HKey- for prev
      final Bytes rightLeafPath = pathResolver.getLeafPath(rightLeafIndex);
      final LeafOpening priorRightLeaf =
          get(rightLeafPath).map(LeafOpening::readFrom).orElseThrow();
      final LeafOpening newRightLeaf = new LeafOpening(priorRightLeaf);
      newRightLeaf.setPrevLeaf(leftLeafIndex);

      final List<Node<Bytes>> rightSiblings =
          state.putAndProve(rightLeafPath, newRightLeaf.getEncodesBytes());

      return deleteTrace
          .withKey(key.getOriginalUnsafeValue())
          .withDeletedValue(currentFlatLeafValue.leafValue())
          .withPriorLeftLeaf(priorLeftLeaf)
          .withPriorDeletedLeaf(priorDeletedLeaf)
          .withPriorRightLeaf(priorRightLeaf)
          .withLeftProof(new TraceProof(nearestKeys.getLeftNodeValue().leafIndex(), leftSiblings))
          .withDeletedProof(
              new TraceProof(
                  nearestKeys.getCenterNodeValue().orElseThrow().leafIndex(), centerSiblings))
          .withRightProof(
              new TraceProof(nearestKeys.getRightNodeValue().leafIndex(), rightSiblings))
          .withNewNextFreeNode(pathResolver.getNextFreeLeafNodeIndex())
          .withNewSubRoot(getSubRootNode())
          .build();
    }

    return new EmptyTrace();
  }

  public void decrementNextFreeNode() {
    pathResolver.decrementNextFreeLeafNodeIndex();
  }

  public void commit() {
    state.commit(updater::putTrieNode);
  }
}
