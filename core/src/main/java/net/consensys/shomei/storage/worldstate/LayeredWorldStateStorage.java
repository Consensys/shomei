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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;

/**
 * A layered worldstate storage that overlays an in-memory layer on top of a base (parent) storage.
 * All reads check the overlay first, then fall back to the parent.
 * All writes go only to the overlay, leaving the parent unmodified.
 * Deletes are tracked explicitly to prevent fallback to parent for deleted keys.
 * This is useful for virtual/simulated blocks where we want to apply changes temporarily
 * without modifying the cached parent state.
 */
public class LayeredWorldStateStorage implements WorldStateStorage {

  private final WorldStateStorage parentStorage;
  private final InMemoryWorldStateStorage overlayStorage;
  private final Set<Bytes> deletedKeys = ConcurrentHashMap.newKeySet();

  public LayeredWorldStateStorage(final WorldStateStorage parentStorage) {
    this.parentStorage = parentStorage;
    this.overlayStorage = new InMemoryWorldStateStorage();
  }

  @Override
  public Optional<FlattenedLeaf> getFlatLeaf(Bytes key) {
    // If key was deleted in overlay, don't fall back to parent
    if (deletedKeys.contains(key)) {
      return Optional.empty();
    }

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

  /**
   * Get a valid (non-deleted) node from parent storage, searching in the specified direction if
   * the initial node is deleted.
   *
   * @param initialKey the initial key to check
   * @param initialValue the initial value
   * @param searchLeft true to search left (for smaller keys), false to search right (for larger
   *     keys)
   * @return a non-deleted entry from parent, or the initial entry if no better option exists
   */
  private Map.Entry<Bytes, FlattenedLeaf> getValidParentNode(
      Bytes initialKey, FlattenedLeaf initialValue, boolean searchLeft) {
    Bytes currentKey = initialKey;
    FlattenedLeaf currentValue = initialValue;

    // Keep searching while the current key is deleted
    while (deletedKeys.contains(currentKey) && !currentKey.equals(Bytes.EMPTY)) {
      // Get nearest keys relative to current position
      TrieStorage.Range nextRange = parentStorage.getNearestKeys(currentKey);

      // Move in the specified direction
      if (searchLeft) {
        Bytes nextKey = nextRange.getLeftNodeKey();
        if (nextKey.equals(currentKey) || nextKey.equals(Bytes.EMPTY)) {
          break; // No more keys in this direction
        }
        currentKey = nextKey;
        currentValue = nextRange.getLeftNodeValue();
      } else {
        Bytes nextKey = nextRange.getRightNodeKey();
        if (nextKey.equals(currentKey) || nextKey.equals(Bytes.EMPTY)) {
          break; // No more keys in this direction
        }
        currentKey = nextKey;
        currentValue = nextRange.getRightNodeValue();
      }
    }

    return Map.entry(currentKey, currentValue);
  }

  @Override
  public TrieStorage.Range getNearestKeys(Bytes hkey) {
    // Get nearest keys from both overlay and parent
    final TrieStorage.Range overlayRange = overlayStorage.getNearestKeys(hkey);
    final TrieStorage.Range parentRange = parentStorage.getNearestKeys(hkey);

    // Check for exact match (center node) in overlay first, then parent (if not deleted)
    Optional<Map.Entry<Bytes, FlattenedLeaf>> centerNode = overlayRange.getCenterNode();
    if (centerNode.isEmpty()) {
      centerNode =
          parentRange
              .getCenterNode()
              .filter(entry -> !deletedKeys.contains(entry.getKey()));
    }

    // For left node: pick the one closest to hkey from below (largest key <= hkey)
    // Get valid (non-deleted) left node from parent
    Map.Entry<Bytes, FlattenedLeaf> parentLeftNode =
        getValidParentNode(
            parentRange.getLeftNodeKey(), parentRange.getLeftNodeValue(), true);

    Map.Entry<Bytes, FlattenedLeaf> leftNode;
    Bytes overlayLeft = overlayRange.getLeftNodeKey();
    Bytes parentLeft = parentLeftNode.getKey();

    // Compare which left key is larger (closer to hkey)
    if (overlayLeft.compareTo(parentLeft) > 0) {
      leftNode = Map.entry(overlayLeft, overlayRange.getLeftNodeValue());
    } else {
      leftNode = parentLeftNode;
    }

    // For right node: pick the one closest to hkey from above (smallest key > hkey)
    // Get valid (non-deleted) right node from parent
    Map.Entry<Bytes, FlattenedLeaf> parentRightNode =
        getValidParentNode(
            parentRange.getRightNodeKey(), parentRange.getRightNodeValue(), false);

    Map.Entry<Bytes, FlattenedLeaf> rightNode;
    Bytes overlayRight = overlayRange.getRightNodeKey();
    Bytes parentRight = parentRightNode.getKey();

    // Compare which right key is smaller (closer to hkey)
    if (overlayRight.compareTo(parentRight) < 0) {
      rightNode = Map.entry(overlayRight, overlayRange.getRightNodeValue());
    } else {
      rightNode = parentRightNode;
    }

    return new TrieStorage.Range(leftNode, centerNode, rightNode);
  }

  @Override
  public TrieStorage.TrieUpdater updater() {
    // Return a wrapper that tracks deletes and re-insertions
    return new LayeredTrieUpdater(overlayStorage.updater());
  }

  /**
   * Wrapper around the overlay's TrieUpdater that tracks deleted keys and handles re-insertions.
   */
  private class LayeredTrieUpdater implements TrieStorage.TrieUpdater {
    private final TrieStorage.TrieUpdater delegate;

    LayeredTrieUpdater(TrieStorage.TrieUpdater delegate) {
      this.delegate = delegate;
    }

    @Override
    public void putFlatLeaf(Bytes key, FlattenedLeaf value) {
      // If key was previously deleted, remove it from deleted set
      deletedKeys.remove(key);
      delegate.putFlatLeaf(key, value);
    }

    @Override
    public void removeFlatLeafValue(Bytes key) {
      // Track this deletion
      deletedKeys.add(key);
      delegate.removeFlatLeafValue(key);
    }

    @Override
    public void putTrieNode(Bytes location, Bytes nodeHash, Bytes value) {
      delegate.putTrieNode(location, nodeHash, value);
    }

    @Override
    public void commit() {
      delegate.commit();
    }
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
