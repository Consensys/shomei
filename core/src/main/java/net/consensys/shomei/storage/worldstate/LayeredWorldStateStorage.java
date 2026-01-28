/*
 * Copyright ConsenSys Software Inc., 2026
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

package net.consensys.shomei.storage.worldstate;

import net.consensys.shomei.trie.model.FlattenedLeaf;
import net.consensys.shomei.trie.storage.TrieStorage;

import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;

/**
 * A layered worldstate storage that overlays an in-memory layer on top of a base (parent) storage.
 * All reads check the overlay first, then fall back to the parent.
 * All writes go only to the overlay, leaving the parent unmodified.
 * This is useful for virtual/simulated blocks where we want to apply changes temporarily
 * without modifying the cached parent state.
 */
public class LayeredWorldStateStorage implements WorldStateStorage {

  private final WorldStateStorage parentStorage;
  private final InMemoryWorldStateStorage overlayStorage;

  public LayeredWorldStateStorage(final WorldStateStorage parentStorage) {
    this.parentStorage = parentStorage;
    this.overlayStorage = new InMemoryWorldStateStorage();
  }

  @Override
  public Optional<FlattenedLeaf> getFlatLeaf(Bytes key) {
    // Check overlay first, then parent
    Optional<FlattenedLeaf> overlayResult = overlayStorage.getFlatLeaf(key);
    return overlayResult.isPresent() ? overlayResult : parentStorage.getFlatLeaf(key);
  }

  @Override
  public Optional<Bytes> getTrieNode(Bytes location, Bytes nodeHash) {
    // Check overlay first, then parent
    Optional<Bytes> overlayResult = overlayStorage.getTrieNode(location, nodeHash);
    return overlayResult.isPresent() ? overlayResult : parentStorage.getTrieNode(location, nodeHash);
  }

  @Override
  public TrieStorage.Range getNearestKeys(Bytes hkey) {
    // Get nearest keys from both overlay and parent
    final TrieStorage.Range overlayRange = overlayStorage.getNearestKeys(hkey);
    final TrieStorage.Range parentRange = parentStorage.getNearestKeys(hkey);

    // Check for exact match (center node) in overlay first, then parent
    Optional<Map.Entry<Bytes, FlattenedLeaf>> centerNode = overlayRange.getCenterNode();
    if (centerNode.isEmpty()) {
      centerNode = parentRange.getCenterNode();
    }

    // For left node: pick the one closest to hkey from below (largest key <= hkey)
    Map.Entry<Bytes, FlattenedLeaf> leftNode;
    Bytes overlayLeft = overlayRange.getLeftNodeKey();
    Bytes parentLeft = parentRange.getLeftNodeKey();

    // Compare which left key is larger (closer to hkey)
    if (overlayLeft.compareTo(parentLeft) > 0) {
      leftNode = Map.entry(overlayLeft, overlayRange.getLeftNodeValue());
    } else {
      leftNode = Map.entry(parentLeft, parentRange.getLeftNodeValue());
    }

    // For right node: pick the one closest to hkey from above (smallest key > hkey)
    Map.Entry<Bytes, FlattenedLeaf> rightNode;
    Bytes overlayRight = overlayRange.getRightNodeKey();
    Bytes parentRight = parentRange.getRightNodeKey();

    // Compare which right key is smaller (closer to hkey)
    if (overlayRight.compareTo(parentRight) < 0) {
      rightNode = Map.entry(overlayRight, overlayRange.getRightNodeValue());
    } else {
      rightNode = Map.entry(parentRight, parentRange.getRightNodeValue());
    }

    return new TrieStorage.Range(leftNode, centerNode, rightNode);
  }

  @Override
  public TrieStorage.TrieUpdater updater() {
    // Return the overlay's updater
    return overlayStorage.updater();
  }

  @Override
  public Optional<Long> getWorldStateBlockNumber() {
    // Check overlay first, then parent
    Optional<Long> overlayResult = overlayStorage.getWorldStateBlockNumber();
    return overlayResult.isPresent() ? overlayResult : parentStorage.getWorldStateBlockNumber();
  }

  @Override
  public Optional<Hash> getWorldStateBlockHash() {
    // Check overlay first, then parent
    Optional<Hash> overlayResult = overlayStorage.getWorldStateBlockHash();
    return overlayResult.isPresent() ? overlayResult : parentStorage.getWorldStateBlockHash();
  }

  @Override
  public Optional<Hash> getWorldStateRootHash() {
    // Check overlay first, then parent
    Optional<Hash> overlayResult = overlayStorage.getWorldStateRootHash();
    return overlayResult.isPresent() ? overlayResult : parentStorage.getWorldStateRootHash();
  }

  @Override
  public Optional<Hash> getZkStateRootHash(long blockNumber) {
    // Check overlay first, then parent
    Optional<Hash> overlayResult = overlayStorage.getZkStateRootHash(blockNumber);
    return overlayResult.isPresent() ? overlayResult : parentStorage.getZkStateRootHash(blockNumber);
  }

  @Override
  public WorldStateStorage snapshot() {
    // Snapshots not supported on layered storage
    throw new UnsupportedOperationException("Cannot snapshot a layered storage");
  }

  @Override
  public void close() throws Exception {
    // Close overlay only, don't touch parent (it's managed elsewhere)
    overlayStorage.close();
  }
}
