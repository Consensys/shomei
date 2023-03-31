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

import net.consensys.shomei.trie.model.StateLeafValue;
import net.consensys.shomei.trie.node.EmptyLeafNode;
import net.consensys.shomei.trie.storage.LeafIndexLoader;
import net.consensys.shomei.trie.storage.LeafIndexManager;
import net.consensys.shomei.trie.visitor.CommitVisitor;
import net.consensys.zkevm.HashProvider;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;

public class ZKTrie {

  private static final int ZK_TRIE_DEPTH = 40;

  private final PathResolver pathResolver;
  private final StoredSparseMerkleTrie state;
  private final LeafIndexManager leafIndexManager;

  private final NodeUpdater nodeUpdater;

  private ZKTrie(
      final Bytes32 rootHash,
      final LeafIndexManager leafIndexManager,
      final NodeLoader nodeLoader,
      final NodeUpdater nodeUpdater) {
    this.leafIndexManager = leafIndexManager;
    this.state = new StoredSparseMerkleTrie(nodeLoader, rootHash, b -> b, b -> b);
    this.pathResolver = new PathResolver(ZK_TRIE_DEPTH, state);
    this.nodeUpdater = nodeUpdater;
  }

  public static ZKTrie createInMemoryTrie() {
    final LeafIndexManager inMemoryLeafIndexManager = new LeafIndexManager();
    final Map<Bytes, Bytes> storage = new HashMap<>();
    return createTrie(
        inMemoryLeafIndexManager,
        (location, hash) -> Optional.ofNullable(storage.get(hash)),
        (location, hash, value) -> storage.put(hash, value));
  }

  public static ZKTrie createTrie(
      final LeafIndexManager leafIndexManager,
      final NodeLoader nodeLoader,
      final NodeUpdater nodeUpdater) {
    final ZKTrie trie =
        new ZKTrie(
            ZKTrie.initWorldState(nodeUpdater).getHash(),
            leafIndexManager,
            nodeLoader,
            nodeUpdater);
    trie.setHeadAndTail();
    return trie;
  }

  public static ZKTrie loadTrie(
      final Bytes32 rootHash,
      final LeafIndexManager leafIndexManager,
      final NodeLoader nodeLoader,
      final NodeUpdater nodeUpdater) {
    return new ZKTrie(rootHash, leafIndexManager, nodeLoader, nodeUpdater);
  }

  private static Node<Bytes> initWorldState(final NodeUpdater nodeUpdater) {
    // if empty we need to fill the sparse trie with zero leaves
    final StoredNodeFactory nodeFactory =
        new StoredNodeFactory((location, hash) -> Optional.empty(), a -> a, b -> b);
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
    leafIndexManager.putKeyIndex(StateLeafValue.HEAD.getHkey(), headIndex);
    state.put(pathResolver.getLeafPath(headIndex), StateLeafValue.HEAD.getEncodesBytes());
    pathResolver.incrementNextFreeLeafNodeIndex();
    // tail
    final Long tailIndex = pathResolver.getNextFreeLeafNodeIndex();
    leafIndexManager.putKeyIndex(StateLeafValue.TAIL.getHkey(), tailIndex);
    state.put(pathResolver.getLeafPath(tailIndex), StateLeafValue.TAIL.getEncodesBytes());
    pathResolver.incrementNextFreeLeafNodeIndex();
  }

  public Bytes32 getRootHash() {
    return state.getNode(pathResolver.geRootPath()).getHash();
  }

  public Bytes32 getTopRootHash() {
    return state.getRootHash();
  }

  public Optional<Bytes> get(final Hash key) {
    return leafIndexManager
        .getKeyIndex(key)
        .map(pathResolver::getLeafPath)
        .flatMap(path -> get(key, path));
  }

  public Optional<Bytes> get(final Hash hkey, final Bytes path) {
    return state.get(hkey, path);
  }

  @VisibleForTesting
  protected void putValue(final Hash key, final Bytes32 value) {
    // GET the openings HKEY-,  hash(k) , HKEY+
    final LeafIndexLoader.Range nearestKeys = leafIndexManager.getNearestKeys(key);
    // CHECK if hash(k) exist
    if (nearestKeys.getMiddleNodeKey().isEmpty()) {
      // GET path of HKey-
      final Bytes nearestKeyPath =
          pathResolver.getLeafPath(nearestKeys.getLeftNodeIndex().toLong());
      // GET path of HKey+
      final Bytes nearestNextKeyPath =
          pathResolver.getLeafPath(nearestKeys.getRightNodeIndex().toLong());

      // FIND next free node
      final long nextFreeNode = pathResolver.getNextFreeLeafNodeIndex();

      // UPDATE HKey- with hash(k) for next
      final StateLeafValue leftKey =
          get(nearestKeys.getLeftNodeKey(), nearestKeyPath)
              .map(StateLeafValue::readFrom)
              .orElseThrow();
      leftKey.setNextLeaf(UInt256.valueOf(nextFreeNode));
      state.put(nearestKeys.getLeftNodeKey(), nearestKeyPath, leftKey.getEncodesBytes());

      // PUT hash(k) with HKey- for Prev and HKey+ for next
      final Bytes newKeyPath = pathResolver.getLeafPath(nextFreeNode);
      leafIndexManager.putKeyIndex(key, nextFreeNode);
      final StateLeafValue newKey =
          new StateLeafValue(
              nearestKeys.getLeftNodeIndex(), nearestKeys.getRightNodeIndex(), key, value);
      state.put(key, newKeyPath, newKey.getEncodesBytes());

      // UPDATE HKey+ with hash(k) for prev
      final StateLeafValue rightKey =
          get(nearestKeys.getRightNodeKey(), nearestNextKeyPath)
              .map(StateLeafValue::readFrom)
              .orElseThrow();
      rightKey.setPrevLeaf(UInt256.valueOf(nextFreeNode));
      state.put(nearestKeys.getRightNodeKey(), nearestNextKeyPath, rightKey.getEncodesBytes());

      // UPDATE NEXT FREE NODE
      pathResolver.incrementNextFreeLeafNodeIndex();

    } else {
      final Bytes updatedKeyPath =
          pathResolver.getLeafPath(nearestKeys.getMiddleNodeIndex().orElseThrow().toLong());
      final StateLeafValue updatedKey =
          get(key, updatedKeyPath).map(StateLeafValue::readFrom).orElseThrow();
      updatedKey.setValue(value);
      state.put(key, updatedKeyPath, updatedKey.getEncodesBytes());
    }
  }

  public void put(final Hash key, final Bytes value) {
    checkArgument(key.size() == Bytes32.SIZE);
    putValue(key, HashProvider.mimc(value));
  }

  public void remove(final Hash key) {
    checkArgument(key.size() == Bytes32.SIZE);
    final LeafIndexLoader.Range nearestKeys =
        leafIndexManager.getNearestKeys(key); // find HKey- and HKey+
    // CHECK if hash(k) exist
    if (nearestKeys.getMiddleNodeIndex().isPresent()) {
      final UInt256 prevIndex = nearestKeys.getLeftNodeIndex();
      final UInt256 nextIndex = nearestKeys.getRightNodeIndex();

      // UPDATE HKey- with HKey+ for next
      final Bytes prevKeyPath = pathResolver.getLeafPath(prevIndex.toLong());
      final StateLeafValue prevKey =
          get(nearestKeys.getLeftNodeKey(), prevKeyPath)
              .map(StateLeafValue::readFrom)
              .orElseThrow();
      prevKey.setNextLeaf(nextIndex);
      state.put(nearestKeys.getLeftNodeKey(), prevKeyPath, prevKey.getEncodesBytes());

      // REMOVE hash(k)
      final Bytes keyPathToDelete =
          pathResolver.getLeafPath(nearestKeys.getMiddleNodeIndex().orElseThrow().toLong());
      leafIndexManager.removeKeyIndex(key);
      get(key, keyPathToDelete); // read for the proof
      state.remove(keyPathToDelete);

      // UPDATE HKey+ with HKey- for prev
      final Bytes nextKeyPath = pathResolver.getLeafPath(nextIndex.toLong());
      final StateLeafValue nextKey =
          get(nearestKeys.getRightNodeKey(), nextKeyPath)
              .map(StateLeafValue::readFrom)
              .orElseThrow();
      nextKey.setPrevLeaf(prevIndex);
      state.put(nearestKeys.getRightNodeKey(), nextKeyPath, nextKey.getEncodesBytes());
    }
  }

  public void decrementNextFreeNode() {
    pathResolver.decrementNextFreeLeafNodeIndex();
  }

  public void commit() {
    state.commit(nodeUpdater);
  }

  public void commit(final NodeUpdater nodeUpdater) {
    state.commit(nodeUpdater);
  }

  public void commit(final NodeUpdater nodeUpdater, final CommitVisitor<Bytes> commitVisitor) {
    state.commit(nodeUpdater, commitVisitor);
  }
}
