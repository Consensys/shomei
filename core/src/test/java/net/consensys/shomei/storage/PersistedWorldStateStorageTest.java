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

package net.consensys.shomei.storage;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentedStorage;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;
import net.consensys.shomei.storage.WorldStateStorage.WorldStateUpdater;
import net.consensys.shomei.trie.model.FlattenedLeaf;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PersistedWorldStateStorageTest {
  private static final FlattenedLeaf FLAT_LEAF = new FlattenedLeaf(1L, Bytes.EMPTY);
  private static final Bytes BYTES_TEST = Bytes.fromHexString("0xfeeddeadbeef");

  @Rule public final TemporaryFolder tempData = new TemporaryFolder();
  protected PersistedWorldStateStorage storage;
  protected WorldStateUpdater updater;

  @Before
  public void setup() {
    storage =
        new PersistedWorldStateStorage(
            new RocksDBSegmentedStorage(
                new RocksDBConfigurationBuilder()
                    .databaseDir(tempData.getRoot().toPath())
                    .build()));
    updater = storage.updater();
  }

  @After
  public void tearDown() {
    storage.close();
    tempData.delete();
  }

  @Test
  public void assertReadConsistentBeforeCommit() {
    mutateWorldStateStorage();
    assertWorldStateStorage();
  }

  @Test
  public void assertReadConsistentAfterCommit() {
    mutateWorldStateStorage();
    updater.commit();
    assertWorldStateStorage();
  }

  @Test
  public void assertFreshUpdaterAfterCommit() {
    mutateWorldStateStorage();
    updater.commit();
    assertWorldStateStorage();
    var newTrieVal = Bytes.of("newval".getBytes(StandardCharsets.UTF_8));

    // assert the prior value
    assertVal(storage.getTrieNode(null, Bytes.of(1)), BYTES_TEST);
    updater.putTrieNode(null, Bytes.of(1), newTrieVal);

    // assert the new value
    assertVal(storage.getTrieNode(null, Bytes.of(1)), newTrieVal);
    updater.commit();

    // assert post commit returns the new value
    assertVal(storage.getTrieNode(null, Bytes.of(1)), newTrieVal);
  }

  void mutateWorldStateStorage() {
    updater.putFlatLeaf(Bytes.of(1), FLAT_LEAF);
    updater.putTrieNode(Bytes.of(1), Bytes.of(1), BYTES_TEST);
    updater.saveZkStateRootHash(1337L, BYTES_TEST);
    updater.saveTrace(80085, BYTES_TEST);
    updater.saveTrieLog(99L, BYTES_TEST);
  }

  void assertWorldStateStorage() {
    assertVal(storage.getFlatLeaf(Bytes.of(1)), FLAT_LEAF);
    assertVal(storage.getTrieNode(null, Bytes.of(1)), BYTES_TEST);
    assertVal(storage.getZkStateRootHash(1337L), BYTES_TEST);
    assertVal(storage.getTrace(80085), BYTES_TEST);
    assertVal(storage.getTrieLog(99L), BYTES_TEST);
  }

  <T> void assertVal(Optional<T> optional, T expected) {
    assertThat(optional).isPresent();
    assertThat(optional.get()).isEqualTo(expected);
  }
}
