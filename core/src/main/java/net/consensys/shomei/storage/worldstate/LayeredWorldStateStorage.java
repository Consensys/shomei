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

import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;

/**
 * LayeredWorldStateStorage composes an in-memory overlay and a base (parent) storage.
 * All reads check the overlay first, then fall back to the parent.
 * All writes go only to the overlay, leaving the parent unmodified.
 * Deletes are tracked explicitly to prevent fallback to parent for deleted keys.
 *
 * This is useful for virtual/simulated blocks where we want to apply changes temporarily
 * without modifying the cached parent state or persist the state permanently.
 */
public class LayeredWorldStateStorage extends InMemoryWorldStateStorage {

  private final WorldStateStorage parent;

  public LayeredWorldStateStorage(final WorldStateStorage parent) {
    super();
    this.parent = parent;
  }

  @Override
  public Optional<FlattenedLeaf> getFlatLeaf(final Bytes key) {
    // null  = key not in overlay at all → ask parent
    // Optional.empty() = key explicitly deleted in overlay → return empty
    // Optional.of(val) = key exists in overlay → return value
    final Optional<FlattenedLeaf> overlayValue = getFlatLeafStorage().get(key);
    if (overlayValue == null) {
      return parent.getFlatLeaf(key);
    }
    return overlayValue;
  }

  @Override
  public Optional<Bytes> getTrieNode(final Bytes location, final Bytes nodeHash) {
    final Optional<Bytes> overlayValue = getTrieNodeStorage().get(location);
    if (overlayValue == null) {
      return parent.getTrieNode(location, nodeHash);
    }
    return overlayValue;
  }

  @Override
  public Range getNearestKeys(final Bytes hkey) {

    final Range parentRange = parent.getNearestKeys(hkey);

    // --- Center: check overlay directly, then parent ---
    Optional<Map.Entry<Bytes, FlattenedLeaf>> centerNode;
    final Optional<FlattenedLeaf> overlayCenter = getFlatLeafStorage().get(hkey);
    if (overlayCenter != null && overlayCenter.isPresent()) {
      centerNode = Optional.of(Map.entry(hkey, overlayCenter.get()));
    } else if (overlayCenter != null && overlayCenter.isEmpty()) {
      centerNode = Optional.empty();
    } else {
      centerNode = parentRange.getCenterNode();
    }

    // --- Left: closest key < hkey ---
    final Map.Entry<Bytes, FlattenedLeaf> leftNode =
            resolveLeftNode(hkey, parentRange);

    // --- Right: closest key > hkey ---
    final Map.Entry<Bytes, FlattenedLeaf> rightNode =
            resolveRightNode(hkey, parentRange);

    return new Range(leftNode, centerNode, rightNode);
  }

  private Map.Entry<Bytes, FlattenedLeaf> resolveLeftNode(
          final Bytes hkey, final Range parentRange) {

    final Map.Entry<Bytes, FlattenedLeaf> parentLeft =
            getValidParentNode(
                    parentRange.getLeftNodeKey(), parentRange.getLeftNodeValue(), true);

    Map.Entry<Bytes, Optional<FlattenedLeaf>> overlayLeft =
            getFlatLeafStorage().lowerEntry(hkey);
    // skip deleted entries
    while (overlayLeft != null && overlayLeft.getValue().isEmpty()) {
      overlayLeft = getFlatLeafStorage().lowerEntry(overlayLeft.getKey());
    }

    if (overlayLeft != null && overlayLeft.getValue().isPresent()
            && overlayLeft.getKey().compareTo(parentLeft.getKey()) > 0) {
      return Map.entry(overlayLeft.getKey(), overlayLeft.getValue().get());
    }

    return parentLeft;
  }

  private Map.Entry<Bytes, FlattenedLeaf> resolveRightNode(
          final Bytes hkey, final Range parentRange) {

    final Map.Entry<Bytes, FlattenedLeaf> parentRight =
            getValidParentNode(
                    parentRange.getRightNodeKey(), parentRange.getRightNodeValue(), false);

    Map.Entry<Bytes, Optional<FlattenedLeaf>> overlayRight =
            getFlatLeafStorage().higherEntry(hkey);
    // skip deleted entries
    while (overlayRight != null && overlayRight.getValue().isEmpty()) {
      overlayRight = getFlatLeafStorage().higherEntry(overlayRight.getKey());
    }

    if (overlayRight != null && overlayRight.getKey().compareTo(parentRight.getKey()) < 0) {
      return Map.entry(overlayRight.getKey(), overlayRight.getValue().get());
    }

    return parentRight;
  }

  /**
   * Check if a key has been explicitly deleted in the overlay.
   */
  private boolean isDeletedInOverlay(final Bytes key) {
    final Optional<FlattenedLeaf> value = getFlatLeafStorage().get(key);
    return value != null && value.isEmpty();
  }

  /**
   * Walk through parent storage to find a node that hasn't been deleted in the overlay.
   */
  private Map.Entry<Bytes, FlattenedLeaf> getValidParentNode(
          final Bytes initialKey,
          final FlattenedLeaf initialValue,
          final boolean searchLeft) {

    Bytes currentKey = initialKey;
    FlattenedLeaf currentValue = initialValue;

    while (isDeletedInOverlay(currentKey) && !currentKey.equals(Bytes.EMPTY)) {
      final Range nextRange = parent.getNearestKeys(currentKey);

      if (searchLeft) {
        final Bytes nextKey = nextRange.getLeftNodeKey();
        if (nextKey.equals(currentKey) || nextKey.equals(Bytes.EMPTY)) {
          break;
        }
        currentKey = nextKey;
        currentValue = nextRange.getLeftNodeValue();
      } else {
        final Bytes nextKey = nextRange.getRightNodeKey();
        if (nextKey.equals(currentKey) || nextKey.equals(Bytes.EMPTY)) {
          break;
        }
        currentKey = nextKey;
        currentValue = nextRange.getRightNodeValue();
      }
    }

    return Map.entry(currentKey, currentValue);
  }

  @Override
  public TrieUpdater updater() {
    return new LayeredTrieUpdater(this);
  }


  /**
   * Wrapper around the overlay's TrieUpdater that tracks deleted keys and handles re-insertions.
   */
  private static class LayeredTrieUpdater implements WorldStateUpdater {
    private final LayeredWorldStateStorage delegate;

    LayeredTrieUpdater(final LayeredWorldStateStorage delegate) {
      this.delegate = delegate;
    }

    @Override
    public void putFlatLeaf(Bytes key, FlattenedLeaf value) {
      delegate.putFlatLeaf(key, value);
    }

    @Override
    public void removeFlatLeafValue(Bytes key) {
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

    @Override
    public void setBlockHash(final Hash blockHash) {
      delegate.setBlockHash(blockHash);
    }

    @Override
    public void setBlockNumber(final long blockNumber) {
      delegate.setBlockNumber(blockNumber);
    }
  }

  @Override
  public Optional<Long> getWorldStateBlockNumber() {
    final Optional<Long> overlayResult = super.getWorldStateBlockNumber();
    return overlayResult.isPresent() ? overlayResult : parent.getWorldStateBlockNumber();
  }

  @Override
  public Optional<Hash> getWorldStateBlockHash() {
    final Optional<Hash> overlayResult = super.getWorldStateBlockHash();
    return overlayResult.isPresent() ? overlayResult : parent.getWorldStateBlockHash();
  }

  @Override
  public Optional<Hash> getWorldStateRootHash() {
    final Optional<Hash> overlayResult = super.getWorldStateRootHash();
    return overlayResult.isPresent() ? overlayResult : parent.getWorldStateRootHash();
  }

  @Override
  public Optional<Hash> getZkStateRootHash(final long blockNumber) {
    final Optional<Hash> overlayResult = super.getZkStateRootHash(blockNumber);
    return overlayResult.isPresent() ? overlayResult : parent.getZkStateRootHash(blockNumber);
  }

  @Override
  public WorldStateStorage snapshot() {
    // Snapshots not supported on layered storage
    throw new UnsupportedOperationException("Cannot snapshot a layered storage");
  }

  @Override
  public void close() {
    // Don't close parent — it's managed elsewhere
  }
}
