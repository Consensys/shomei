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

import net.consensys.shomei.observer.TrieLogObserver;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;
import net.consensys.shomei.storage.worldstate.PersistedWorldStateStorage;
import net.consensys.shomei.storage.worldstate.WorldStateStorage.WorldStateUpdater;
import net.consensys.shomei.trie.model.FlattenedLeaf;
import net.consensys.shomei.trie.proof.Trace;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PersistedWorldStateStorageTest {
  private static final FlattenedLeaf FLAT_LEAF = new FlattenedLeaf(1L, Bytes.EMPTY);
  private static final Bytes BYTES_TEST = Bytes.fromHexString("0xfeeddeadbeef");
  private static final List<Trace> TRACE_TEST =
      List.of(
          new Trace() {

            @Override
            public int getType() {
              return 0;
            }

            @Override
            public Bytes getLocation() {
              return BYTES_TEST;
            }

            @Override
            public void setLocation(final Bytes location) {}

            @Override
            public void writeTo(final RLPOutput out) {}
          });

  private static final Hash HASH_TEST = Hash.hash(BYTES_TEST);

  @Rule public final TemporaryFolder tempData = new TemporaryFolder();
  protected PersistedWorldStateStorage storage;
  protected TrieLogManager trieLogManager;
  protected TraceManager traceManager;
  protected WorldStateUpdater updater;

  @Before
  public void setup() {
    var provider =
        new RocksDBStorageProvider(
            new RocksDBConfigurationBuilder().databaseDir(tempData.getRoot().toPath()).build());
    storage =
        new PersistedWorldStateStorage(
            provider.getFlatLeafStorage(),
            provider.getTrieNodeStorage(),
            provider.getTraceManager());
    updater = storage.updater();
    trieLogManager = provider.getTrieLogManager();
    traceManager = provider.getTraceManager();
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
    assertVal(storage.getTrieNode(Bytes.of(1), Bytes.of(1)), BYTES_TEST);
    updater.putTrieNode(Bytes.of(1), Bytes.of(1), newTrieVal);

    // assert the new value
    assertVal(storage.getTrieNode(Bytes.of(1), Bytes.of(1)), newTrieVal);
    updater.commit();

    // assert post commit returns the new value
    assertVal(storage.getTrieNode(Bytes.of(1), Bytes.of(1)), newTrieVal);
  }

  void mutateWorldStateStorage() {
    updater.putFlatLeaf(Bytes.of(1), FLAT_LEAF);
    updater.putTrieNode(Bytes.of(1), Bytes.of(1), BYTES_TEST);
    updater.saveZkStateRootHash(1337L, HASH_TEST);
    traceManager.saveTrace(80085, TRACE_TEST);
    trieLogManager.saveTrieLog(new TrieLogObserver.TrieLogIdentifier(99L, Hash.ZERO), BYTES_TEST);
  }

  void assertWorldStateStorage() {
    assertVal(storage.getFlatLeaf(Bytes.of(1)), FLAT_LEAF);
    assertVal(storage.getTrieNode(Bytes.of(1), Bytes.of(1)), BYTES_TEST);
    assertVal(storage.getZkStateRootHash(1337L), HASH_TEST);
    assertVal(traceManager.getTrace(80085), Trace.serialize(TRACE_TEST));
    assertVal(trieLogManager.getTrieLog(99L), BYTES_TEST);
  }

  <T> void assertVal(Optional<T> optional, T expected) {
    assertThat(optional).isPresent();
    assertThat(optional.get()).isEqualTo(expected);
  }
}
