/*
 * Copyright Consensys Software Inc., 2026
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

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.primitives.Longs;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

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

  /**
   * Tracks storage prefixes (8-byte big-endian leafIndex) for which
   * {@link #removeStorageForAccount} was called on this overlay.
   *
   * <p>{@link InMemoryWorldStateStorage#removeStorageForAccount} only tombstones entries that are
   * already in the overlay's own flat-leaf and trie-node maps; entries that exist <em>only</em> in
   * the parent would otherwise remain visible via the normal parent-fallback reads.  By recording
   * the deleted prefix here we can block that fallback for the affected storage namespace.
   */
  private final Set<Bytes> deletedStoragePrefixes = new HashSet<>();

  public LayeredWorldStateStorage(final WorldStateStorage parent) {
    super();
    this.parent = parent;
  }

  /**
   * Returns {@code true} when {@code key} belongs to a storage namespace (leafIndex prefix) that
   * has been fully deleted on this overlay via {@link #removeStorageForAccount}.
   */
  private boolean isInDeletedStoragePrefix(final Bytes key) {
    return key.size() >= Long.BYTES
        && deletedStoragePrefixes.contains(key.slice(0, Long.BYTES));
  }

  /**
   * Overrides the base implementation to also record the deleted prefix in
   * {@link #deletedStoragePrefixes}, blocking parent fallback for any flat-leaf or trie-node
   * belonging to this account's storage namespace.
   */
  @Override
  public void removeStorageForAccount(final long leafIndex) {
    deletedStoragePrefixes.add(Bytes.wrap(Longs.toByteArray(leafIndex)));
    super.removeStorageForAccount(leafIndex);
  }

  @Override
  public Optional<FlattenedLeaf> getFlatLeaf(final Bytes key) {
    // Check overlay first — entries written after removeStorageForAccount (e.g. HEAD/TAIL from
    // createTrie, or new slot values from putWithTrace) must remain visible.
    // null  = key not in overlay at all → apply deleted-prefix guard then ask parent
    // Optional.empty() = key explicitly deleted in overlay → return empty
    // Optional.of(val) = key exists in overlay → return value
    final Optional<FlattenedLeaf> overlayValue = getFlatLeafStorage().get(key);
    if (overlayValue != null) {
      return overlayValue;
    }
    // Block parent fallback for storage namespaces fully removed on this overlay.
    if (isInDeletedStoragePrefix(key)) {
      return Optional.empty();
    }
    return parent.getFlatLeaf(key);
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

    // When the queried key belongs to a deleted storage namespace, the parent's entries for that
    // prefix must not appear as neighbors — they belong to storage that was fully removed on this
    // overlay.  Use only overlay entries (HEAD/TAIL sentinels from createTrie(), plus any new
    // slots written in the current block).  Check the overlay for the center key explicitly so
    // that entries written after removeStorageForAccount are correctly reflected.
    if (isInDeletedStoragePrefix(hkey)) {
      final Optional<FlattenedLeaf> overlayCenter = getFlatLeafStorage().get(hkey);
      final Optional<Map.Entry<Bytes, FlattenedLeaf>> centerNode =
          (overlayCenter != null && overlayCenter.isPresent())
              ? Optional.of(Map.entry(hkey, overlayCenter.get()))
              : Optional.empty();
      return buildOverlayOnlyRange(hkey, centerNode);
    }

    // --- Center: check overlay ---
    final Optional<FlattenedLeaf> overlayCenter = getFlatLeafStorage().get(hkey);
    Optional<Map.Entry<Bytes, FlattenedLeaf>> centerNode;
    if (overlayCenter != null && overlayCenter.isPresent()) {
      centerNode = Optional.of(Map.entry(hkey, overlayCenter.get()));
    } else if (overlayCenter != null && overlayCenter.isEmpty()) {
      centerNode = Optional.empty(); // tombstoned in overlay
    } else {
      centerNode = null; // not in overlay; check parent below
    }

    // --- Try parent first for complete range ---
    // Parent can fail when hkey is outside the key-space of any entry in the parent
    // (e.g., a fresh account storage trie with HEAD/TAIL only in our overlay, but the
    // parent snapshot has no entries for this storage prefix yet).
    final Range parentRange;
    try {
      parentRange = parent.getNearestKeys(hkey);
    } catch (RuntimeException e) {
      // Parent has no entries near hkey — build range solely from overlay.
      return buildOverlayOnlyRange(hkey, centerNode);
    }

    if (centerNode == null) {
      centerNode = parentRange.getCenterNode();
    }

    // --- Left: closest key < hkey ---
    final Map.Entry<Bytes, FlattenedLeaf> leftNode = resolveLeftNode(hkey, parentRange);

    // --- Right: closest key > hkey ---
    final Map.Entry<Bytes, FlattenedLeaf> rightNode = resolveRightNode(hkey, parentRange);

    return new Range(leftNode, centerNode, rightNode);
  }

  /**
   * Build a Range using only entries present in the overlay (no parent call).
   * Called when the parent throws — e.g., the parent snapshot has no entries with keys ≤
   * {@code hkey} (fresh storage namespace, account trie entries are all lexicographically
   * greater).  The overlay must supply both a left and a right bound (HEAD/TAIL from
   * {@link net.consensys.shomei.trie.ZKTrie#createTrie}) for this to succeed.
   */
  private Range buildOverlayOnlyRange(
      final Bytes hkey, final Optional<Map.Entry<Bytes, FlattenedLeaf>> precomputedCenter) {

    Optional<Map.Entry<Bytes, FlattenedLeaf>> centerNode =
        precomputedCenter != null ? precomputedCenter : Optional.empty();

    Map.Entry<Bytes, Optional<FlattenedLeaf>> rawLeft = getFlatLeafStorage().lowerEntry(hkey);
    while (rawLeft != null && rawLeft.getValue().isEmpty()) {
      rawLeft = getFlatLeafStorage().lowerEntry(rawLeft.getKey());
    }

    Map.Entry<Bytes, Optional<FlattenedLeaf>> rawRight = getFlatLeafStorage().higherEntry(hkey);
    while (rawRight != null && rawRight.getValue().isEmpty()) {
      rawRight = getFlatLeafStorage().higherEntry(rawRight.getKey());
    }

    if (rawLeft == null || rawRight == null) {
      throw new RuntimeException("not found leaf index");
    }

    return new Range(
        Map.entry(rawLeft.getKey(), rawLeft.getValue().get()),
        centerNode,
        Map.entry(rawRight.getKey(), rawRight.getValue().get()));
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

    if (overlayLeft != null && overlayLeft.getValue().isPresent()) {
      // Prefer overlay over parent if overlay is closer to hkey.
      if (overlayLeft.getKey().compareTo(parentLeft.getKey()) > 0) {
        return Map.entry(overlayLeft.getKey(), overlayLeft.getValue().get());
      }
      // Also prefer overlay if the parent entry is not actually to the LEFT of hkey.
      // This happens when getValidParentNode walks tombstoned entries from one storage
      // namespace into a different namespace (e.g., account-trie entries at 0x7FFF…
      // after all storage entries for a prefix were tombstoned), producing a key that
      // is ≥ hkey and therefore invalid as a left neighbour.
      if (parentLeft.getKey().compareTo(hkey) >= 0) {
        return Map.entry(overlayLeft.getKey(), overlayLeft.getValue().get());
      }
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

    if (overlayRight != null && overlayRight.getValue().isPresent()) {
      // Prefer overlay over parent if overlay is closer to hkey.
      if (overlayRight.getKey().compareTo(parentRight.getKey()) < 0) {
        return Map.entry(overlayRight.getKey(), overlayRight.getValue().get());
      }
      // Also prefer overlay if the parent entry is not actually to the RIGHT of hkey.
      if (parentRight.getKey().compareTo(hkey) <= 0) {
        return Map.entry(overlayRight.getKey(), overlayRight.getValue().get());
      }
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
   *
   * <p>The walk is bounded by {@code Bytes.EMPTY} (the canonical "no left neighbor" sentinel used
   * throughout the codebase) and exits early if the parent throws — which can happen when the
   * current key is at the very beginning of the parent's key-space and has no further left
   * neighbour.  In that case the caller ({@link #resolveLeftNode} / {@link #resolveRightNode})
   * will fall back to the overlay's own bounding entry.
   */
  private Map.Entry<Bytes, FlattenedLeaf> getValidParentNode(
          final Bytes initialKey,
          final FlattenedLeaf initialValue,
          final boolean searchLeft) {

    Bytes currentKey = initialKey;
    FlattenedLeaf currentValue = initialValue;

    while (isDeletedInOverlay(currentKey) && !currentKey.equals(Bytes.EMPTY)) {
      final Range nextRange;
      try {
        nextRange = parent.getNearestKeys(currentKey);
      } catch (RuntimeException e) {
        // Parent has no entries at/near currentKey (boundary of its key-space).
        // Stop walking; the caller will prefer the overlay bound over this entry.
        break;
      }

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
    public void setBlockHash(final Bytes32 blockHash) {
      delegate.setBlockHash(blockHash);
    }

    @Override
    public void setBlockNumber(final long blockNumber) {
      delegate.setBlockNumber(blockNumber);
    }

    @Override
    public void removeStorageForAccount(final long leafIndex) {
      // Delegates to LayeredWorldStateStorage.removeStorageForAccount(), which:
      // - records the prefix in deletedStoragePrefixes (blocks parent fallback for that range)
      // - then calls super (InMemoryWorldStateStorage) to tombstone any overlay entries and
      //   the storage-trie root key (ensuring createTrie() is used rather than loadTrie())
      delegate.removeStorageForAccount(leafIndex);
    }
  }

  @Override
  public Optional<Long> getWorldStateBlockNumber() {
    final Optional<Long> overlayResult = super.getWorldStateBlockNumber();
    return overlayResult.isPresent() ? overlayResult : parent.getWorldStateBlockNumber();
  }

  @Override
  public Optional<Bytes32> getWorldStateBlockHash() {
    final Optional<Bytes32> overlayResult = super.getWorldStateBlockHash();
    return overlayResult.isPresent() ? overlayResult : parent.getWorldStateBlockHash();
  }

  @Override
  public Optional<Bytes32> getWorldStateRootHash() {
    final Optional<Bytes32> overlayResult = super.getWorldStateRootHash();
    return overlayResult.isPresent() ? overlayResult : parent.getWorldStateRootHash();
  }

  @Override
  public Optional<Bytes32> getZkStateRootHash(final long blockNumber) {
    final Optional<Bytes32> overlayResult = super.getZkStateRootHash(blockNumber);
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
