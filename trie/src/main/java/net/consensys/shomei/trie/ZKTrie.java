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
import net.consensys.shomei.trie.proof.DeleteTrace;
import net.consensys.shomei.trie.proof.EmptyTrace;
import net.consensys.shomei.trie.proof.InsertionTrace;
import net.consensys.shomei.trie.proof.Proof;
import net.consensys.shomei.trie.proof.Trace;
import net.consensys.shomei.trie.proof.UpdateTrace;
import net.consensys.shomei.trie.storage.LeafIndexLoader;
import net.consensys.shomei.trie.storage.LeafIndexManager;
import net.consensys.zkevm.HashProvider;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    return state.get(path);
  }

  public Trace putAndProve(final Hash key, final Bytes value) {
    checkArgument(key.size() == Bytes32.SIZE);

    // GET the openings HKEY-,  hash(k) , HKEY+
    final LeafIndexLoader.Range nearestKeys = leafIndexManager.getNearestKeys(key);
    // CHECK if hash(k) exist
    if (nearestKeys.getCenterNodeKey().isEmpty()) {
      // INIT trace
      final InsertionTrace insertionTrace = new InsertionTrace(state.root);
      insertionTrace.setKey(key);
      insertionTrace.setValue(value);

      // GET path of HKey-
      final Bytes leftKeyPath = pathResolver.getLeafPath(nearestKeys.getLeftNodeIndex());
      // GET path of HKey+
      final Bytes rightKeyPath = pathResolver.getLeafPath(nearestKeys.getRightNodeIndex());

      // FIND next free node
      final long nextFreeNode = pathResolver.getNextFreeLeafNodeIndex();

      // UPDATE HKey- with hash(k) for next
      final StateLeafValue leftKey =
          get(nearestKeys.getLeftNodeKey(), leftKeyPath)
              .map(StateLeafValue::readFrom)
              .orElseThrow();
      insertionTrace.setPriorLeftLeaf(leftKey.getEncodesBytes());
      leftKey.setNextLeaf(UInt256.valueOf(nextFreeNode));

      // PREPARE proof for HKEY -
      final List<Node<Bytes>> leftSiblings =
          state.putAndProve(nearestKeys.getLeftNodeKey(), leftKeyPath, leftKey.getEncodesBytes());
      insertionTrace.setProofLeft(new Proof(nearestKeys.getLeftNodeIndex(), leftSiblings));

      // PUT hash(k) with HKey- for Prev and HKey+ for next
      final Bytes newKeyPath = pathResolver.getLeafPath(nextFreeNode);
      leafIndexManager.putKeyIndex(key, nextFreeNode);
      final StateLeafValue newKey =
          new StateLeafValue(
              UInt256.valueOf(nearestKeys.getLeftNodeIndex()),
              UInt256.valueOf(nearestKeys.getRightNodeIndex()),
              key,
              HashProvider.mimc(value));
      // PREPARE proof for hash(k)
      final List<Node<Bytes>> centerSiblings =
          state.putAndProve(key, newKeyPath, newKey.getEncodesBytes());
      insertionTrace.setProofNew(new Proof(nextFreeNode, centerSiblings));

      // UPDATE HKey+ with hash(k) for prev
      final StateLeafValue rightKey =
          get(nearestKeys.getRightNodeKey(), rightKeyPath)
              .map(StateLeafValue::readFrom)
              .orElseThrow();
      insertionTrace.setPriorRightLeaf(rightKey.getEncodesBytes());
      leftKey.setNextLeaf(UInt256.valueOf(nextFreeNode));
      rightKey.setPrevLeaf(UInt256.valueOf(nextFreeNode));

      // PREPARE proof for  HKEY+
      final List<Node<Bytes>> rightSiblings =
          state.putAndProve(
              nearestKeys.getRightNodeKey(), rightKeyPath, rightKey.getEncodesBytes());
      insertionTrace.setProofRight(new Proof(nearestKeys.getRightNodeIndex(), rightSiblings));
      // insertionTrace.setOldOpenPlus();

      // UPDATE NEXT FREE NODE
      pathResolver.incrementNextFreeLeafNodeIndex();

      insertionTrace.setNewRoot(state.root);
      return insertionTrace;
    } else {

      // INIT trace
      final UpdateTrace updateTrace = new UpdateTrace(state.root);
      updateTrace.setKey(key);
      updateTrace.setNewValue(value);

      final Bytes updatedKeyPath =
          pathResolver.getLeafPath(nearestKeys.getCenterNodeIndex().orElseThrow());

      // RETRIEVE OLD VALUE
      final StateLeafValue updatedKey =
          get(key, updatedKeyPath).map(StateLeafValue::readFrom).orElseThrow();
      updateTrace.setOldValue(updatedKey.getValue());
      updateTrace.setPriorUpdatedLeaf(updatedKey.getEncodesBytes());
      // UPDATE VALUE
      updatedKey.setValue(HashProvider.mimc(value));
      updateTrace.setNewValue(updatedKey.getValue());

      final List<Node<Bytes>> siblings =
          state.putAndProve(key, updatedKeyPath, updatedKey.getEncodesBytes());
      updateTrace.setProof(new Proof(nearestKeys.getCenterNodeIndex().orElseThrow(), siblings));

      updateTrace.setNewRoot(state.root);

      return updateTrace;
    }
  }

  public Trace removeAndProve(final Hash key) {
    checkArgument(key.size() == Bytes32.SIZE);
    // GET the openings HKEY-,  hash(k) , HKEY+
    final LeafIndexLoader.Range nearestKeys = leafIndexManager.getNearestKeys(key);
    // CHECK if hash(k) exist
    if (nearestKeys.getCenterNodeIndex().isPresent()) {

      // INIT trace
      final DeleteTrace deleteTrace = new DeleteTrace(state.root);
      deleteTrace.setKey(key);

      final Long leftIndex = nearestKeys.getLeftNodeIndex();
      final Long rightIndex = nearestKeys.getRightNodeIndex();

      // UPDATE HKey- with HKey+ for next
      final Bytes leftKeyPath = pathResolver.getLeafPath(leftIndex);
      final StateLeafValue leftKey =
          get(nearestKeys.getLeftNodeKey(), leftKeyPath)
              .map(StateLeafValue::readFrom)
              .orElseThrow();
      deleteTrace.setPriorLeftLeaf(leftKey.getEncodesBytes());
      leftKey.setNextLeaf(UInt256.valueOf(rightIndex));

      final List<Node<Bytes>> leftSiblings =
          state.putAndProve(nearestKeys.getLeftNodeKey(), leftKeyPath, leftKey.getEncodesBytes());
      deleteTrace.setProofLeft(new Proof(nearestKeys.getLeftNodeIndex(), leftSiblings));

      // REMOVE hash(k)
      final Bytes keyPathToDelete =
          pathResolver.getLeafPath(nearestKeys.getCenterNodeIndex().orElseThrow());
      leafIndexManager.removeKeyIndex(key);
      final List<Node<Bytes>> centerSiblings = state.removeAndProve(key, keyPathToDelete);
      deleteTrace.setProofDeleted(
          new Proof(nearestKeys.getCenterNodeIndex().orElseThrow(), centerSiblings));
      // deleteTrace.setOldDeletedOpen()//TODO MANAGE THAT

      // UPDATE HKey+ with HKey- for prev
      final Bytes rightKeyPath = pathResolver.getLeafPath(rightIndex);
      final StateLeafValue rightKey =
          get(nearestKeys.getRightNodeKey(), rightKeyPath)
              .map(StateLeafValue::readFrom)
              .orElseThrow();
      deleteTrace.setPriorRightLeaf(rightKey.getEncodesBytes());
      rightKey.setPrevLeaf(UInt256.valueOf(leftIndex));
      final List<Node<Bytes>> rightSiblings =
          state.putAndProve(
              nearestKeys.getRightNodeKey(), rightKeyPath, rightKey.getEncodesBytes());
      deleteTrace.setProofRight(new Proof(nearestKeys.getRightNodeIndex(), rightSiblings));

      deleteTrace.setNewRoot(state.root);

      return deleteTrace;
    }

    return new EmptyTrace();
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
}
