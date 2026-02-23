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

package net.consensys.shomei.storage;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.storage.worldstate.InMemoryWorldStateStorage;
import net.consensys.shomei.storage.worldstate.LayeredWorldStateStorage;
import net.consensys.shomei.storage.worldstate.WorldStateStorage;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.model.FlattenedLeaf;
import net.consensys.shomei.trie.storage.TrieStorage;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LayeredWorldStateStorageTest {

  private WorldStateStorage parentStorage;
  private LayeredWorldStateStorage layeredStorage;

  @BeforeEach
  public void setup() {
    parentStorage = new InMemoryWorldStateStorage();
    layeredStorage = new LayeredWorldStateStorage(parentStorage);
    // Initialize  storage with HEAD and TAIL like a real trie would
    ZKTrie.createTrie(parentStorage);
    ZKTrie.createTrie(layeredStorage);


  }

  @Test
  public void testGetFlatLeaf_ReturnsFromOverlayWhenPresent() {
    // Setup: put value in overlay
    final Bytes key = Bytes.of(1);
    final FlattenedLeaf overlayValue = new FlattenedLeaf(1L, Bytes.fromHexString("0x0100"));
    layeredStorage.updater().putFlatLeaf(key, overlayValue);

    // Assert: reads from overlay
    assertThat(layeredStorage.getFlatLeaf(key)).contains(overlayValue);
  }

  @Test
  public void testGetFlatLeaf_FallsBackToParentWhenNotInOverlay() {
    // Setup: put value in parent only
    final Bytes key = Bytes.of(1);
    final FlattenedLeaf parentValue = new FlattenedLeaf(2L, Bytes.fromHexString("0x0200"));
    parentStorage.updater().putFlatLeaf(key, parentValue);

    // Assert: reads from parent
    assertThat(layeredStorage.getFlatLeaf(key)).contains(parentValue);
  }

  @Test
  public void testGetFlatLeaf_DeletedKeyReturnsEmpty() {
    // Setup: put value in parent
    final Bytes key = Bytes.of(1);
    final FlattenedLeaf parentValue = new FlattenedLeaf(2L, Bytes.fromHexString("0x0200"));
    parentStorage.updater().putFlatLeaf(key, parentValue);

    // Delete in overlay
    layeredStorage.updater().removeFlatLeafValue(key);

    // Assert: deleted key returns empty, doesn't fall back to parent
    assertThat(layeredStorage.getFlatLeaf(key)).isEmpty();
  }

  @Test
  public void testGetFlatLeaf_ReinsertionAfterDeletion() {
    // Setup: put value in parent
    final Bytes key = Bytes.of(1);
    final FlattenedLeaf parentValue = new FlattenedLeaf(2L, Bytes.fromHexString("0x0200"));
    parentStorage.updater().putFlatLeaf(key, parentValue);

    // Delete in overlay
    layeredStorage.updater().removeFlatLeafValue(key);
    assertThat(layeredStorage.getFlatLeaf(key)).isEmpty();

    // Re-insert with new value
    final FlattenedLeaf newValue = new FlattenedLeaf(3L, Bytes.fromHexString("0x0300"));
    layeredStorage.updater().putFlatLeaf(key, newValue);

    // Assert: re-inserted key is readable
    assertThat(layeredStorage.getFlatLeaf(key)).contains(newValue);
  }

  @Test
  public void testGetNearestKeys_ExcludesDeletedCenterNode() {
    // Setup: put values in parent
    final Bytes key1 = Bytes.of(1);
    final Bytes key2 = Bytes.of(2);
    final Bytes key3 = Bytes.of(3);
    parentStorage.updater().putFlatLeaf(key1, new FlattenedLeaf(1L, Bytes.fromHexString("0x0100")));
    parentStorage.updater().putFlatLeaf(key2, new FlattenedLeaf(2L, Bytes.fromHexString("0x0200")));
    parentStorage.updater().putFlatLeaf(key3, new FlattenedLeaf(3L, Bytes.fromHexString("0x0300")));

    // Delete key2 in overlay
    layeredStorage.updater().removeFlatLeafValue(key2);

    // Query for nearest keys at key2
    TrieStorage.Range range = layeredStorage.getNearestKeys(key2);

    // Assert: center node should be empty (deleted)
    assertThat(range.getCenterNode()).isEmpty();

    // Assert: left and right nodes should still be present
    assertThat(range.getLeftNodeKey()).isEqualTo(key1);
    assertThat(range.getRightNodeKey()).isEqualTo(key3);
  }

  @Test
  public void testGetNearestKeys_ExcludesDeletedLeftNode() {
    // Setup: put values in parent - keys spaced out for testing
    final Bytes key1 = Bytes.of(10);
    final Bytes key2 = Bytes.of(20);
    final Bytes key3 = Bytes.of(30);
    parentStorage.updater().putFlatLeaf(key1, new FlattenedLeaf(1L, Bytes.fromHexString("0x0100")));
    parentStorage.updater().putFlatLeaf(key2, new FlattenedLeaf(2L, Bytes.fromHexString("0x0200")));
    parentStorage.updater().putFlatLeaf(key3, new FlattenedLeaf(3L, Bytes.fromHexString("0x0300")));

    // Delete key2 in overlay
    layeredStorage.updater().removeFlatLeafValue(key2);

    // Query for nearest keys at key3
    TrieStorage.Range range = layeredStorage.getNearestKeys(key3);

    // Assert: left node should skip deleted key2 and return key1
    assertThat(range.getLeftNodeKey()).isEqualTo(key1);
    assertThat(range.getCenterNode()).isPresent();
    assertThat(range.getCenterNode().get().getKey()).isEqualTo(key3);
  }

  @Test
  public void testGetNearestKeys_ExcludesDeletedRightNode() {
    // Setup: put values in parent
    final Bytes key1 = Bytes.of(10);
    final Bytes key2 = Bytes.of(20);
    final Bytes key3 = Bytes.of(30);
    parentStorage.updater().putFlatLeaf(key1, new FlattenedLeaf(1L, Bytes.fromHexString("0x0100")));
    parentStorage.updater().putFlatLeaf(key2, new FlattenedLeaf(2L, Bytes.fromHexString("0x0200")));
    parentStorage.updater().putFlatLeaf(key3, new FlattenedLeaf(3L, Bytes.fromHexString("0x0300")));

    // Delete key2 in overlay
    layeredStorage.updater().removeFlatLeafValue(key2);

    // Query for nearest keys at key1
    TrieStorage.Range range = layeredStorage.getNearestKeys(key1);

    // Assert: right node should skip deleted key2 and return key3
    assertThat(range.getRightNodeKey()).isEqualTo(key3);
    assertThat(range.getCenterNode()).isPresent();
    assertThat(range.getCenterNode().get().getKey()).isEqualTo(key1);
  }

  @Test
  public void testGetNearestKeys_OverlayOverridesParent() {
    // Setup: put value in parent
    final Bytes key = Bytes.of(1);
    final FlattenedLeaf parentValue = new FlattenedLeaf(1L, Bytes.fromHexString("0x0100"));
    parentStorage.updater().putFlatLeaf(key, parentValue);

    // Override with value in overlay
    final FlattenedLeaf overlayValue = new FlattenedLeaf(2L, Bytes.fromHexString("0x0200"));
    layeredStorage.updater().putFlatLeaf(key, overlayValue);

    // Query for nearest keys
    TrieStorage.Range range = layeredStorage.getNearestKeys(key);

    // Assert: should return overlay value, not parent value
    assertThat(range.getCenterNode()).isPresent();
    assertThat(range.getCenterNode().get().getValue()).isEqualTo(overlayValue);
  }

  @Test
  public void testGetNearestKeys_MultipleDeletesSkipsToValidKey() {
    // Setup: put multiple values in parent
    final Bytes key1 = Bytes.of(10);
    final Bytes key2 = Bytes.of(20);
    final Bytes key3 = Bytes.of(30);
    final Bytes key4 = Bytes.of(40);
    final Bytes key5 = Bytes.of(50);
    parentStorage.updater().putFlatLeaf(key1, new FlattenedLeaf(1L, Bytes.fromHexString("0x0100")));
    parentStorage.updater().putFlatLeaf(key2, new FlattenedLeaf(2L, Bytes.fromHexString("0x0200")));
    parentStorage.updater().putFlatLeaf(key3, new FlattenedLeaf(3L, Bytes.fromHexString("0x0300")));
    parentStorage.updater().putFlatLeaf(key4, new FlattenedLeaf(4L, Bytes.fromHexString("0x0400")));
    parentStorage.updater().putFlatLeaf(key5, new FlattenedLeaf(5L, Bytes.fromHexString("0x0500")));

    // Delete multiple consecutive keys
    layeredStorage.updater().removeFlatLeafValue(key2);
    layeredStorage.updater().removeFlatLeafValue(key3);

    // Query for nearest keys at key4
    TrieStorage.Range range = layeredStorage.getNearestKeys(key4);

    // Assert: left node should skip both deleted keys and return key1
    assertThat(range.getLeftNodeKey()).isEqualTo(key1);
    assertThat(range.getCenterNode().get().getKey()).isEqualTo(key4);
    assertThat(range.getRightNodeKey()).isEqualTo(key5);
  }

  @Test
  public void testParentStorageUnmodified() {
    // Setup: put value in parent
    final Bytes key = Bytes.of(1);
    final FlattenedLeaf parentValue = new FlattenedLeaf(1L, Bytes.fromHexString("0x0100"));
    parentStorage.updater().putFlatLeaf(key, parentValue);

    // Delete in overlay
    layeredStorage.updater().removeFlatLeafValue(key);

    // Assert: parent storage should still have the value
    assertThat(parentStorage.getFlatLeaf(key)).contains(parentValue);

    // Add new value in overlay
    final FlattenedLeaf overlayValue = new FlattenedLeaf(2L, Bytes.fromHexString("0x0200"));
    layeredStorage.updater().putFlatLeaf(key, overlayValue);

    // Assert: parent storage should still have original value
    assertThat(parentStorage.getFlatLeaf(key)).contains(parentValue);
  }
}
