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

import net.consensys.shomei.exception.MissingTrieLogException;
import net.consensys.shomei.observer.TrieLogObserver.TrieLogIdentifier;
import net.consensys.shomei.trielog.PluginTrieLogLayer;
import net.consensys.shomei.trielog.TrieLogLayerConverter;
import net.consensys.shomei.trielog.ZkTrieLogFactory;

import java.util.HashMap;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.junit.Test;

public class ZkWorldStateArchiveTests {

  ZkWorldStateArchive archive = new ZkWorldStateArchive(new InMemoryStorageProvider());
  TrieLogLayerConverter converter = new TrieLogLayerConverter(archive.getHeadWorldStateStorage());
  ZkTrieLogFactory encoder = new ZkTrieLogFactory();

  @Test
  public void shouldDropWorldStatesFromHead() {
    // fill the cache:
    for (long i = 0; i < ZkWorldStateArchive.MAX_CACHED_WORLDSTATES; i++) {
      archive.cacheSnapshot(
          new TrieLogIdentifier(i, Hash.ZERO), archive.getHeadWorldStateStorage());
    }

    // assert cache full, start is 0 and end is 127
    assertThat(archive.getCachedWorldStates().size())
        .isEqualTo(ZkWorldStateArchive.MAX_CACHED_WORLDSTATES);
    assertThat(archive.getCachedWorldState(0L).isPresent()).isTrue();
    assertThat(archive.getCachedWorldState(127L).isPresent()).isTrue();
    assertThat(archive.getCachedWorldState(128L).isPresent()).isFalse();

    // add block 128 to cache:
    archive.cacheSnapshot(
        new TrieLogIdentifier(128L, Hash.ZERO), archive.getHeadWorldStateStorage());

    // assert cache is full, start is 1 and end is 128:
    assertThat(archive.getCachedWorldStates().size())
        .isEqualTo(ZkWorldStateArchive.MAX_CACHED_WORLDSTATES);
    assertThat(archive.getCachedWorldState(0L).isPresent()).isFalse();
    assertThat(archive.getCachedWorldState(127L).isPresent()).isTrue();
    assertThat(archive.getCachedWorldState(128L).isPresent()).isTrue();
  }

  @Test
  public void verifyStorageSnapshot() throws MissingTrieLogException {

    PluginTrieLogLayer pluginLayer =
        new PluginTrieLogLayer(
            archive.getCurrentBlockHash(),
            Optional.of(0L),
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            false);
    TrieLogIdentifier genesis = new TrieLogIdentifier(0L, pluginLayer.getBlockHash());
    archive.getTrieLogManager().saveTrieLog(genesis, Bytes.of(encoder.serialize(pluginLayer)));

    archive.importBlock(new TrieLogIdentifier(0L, pluginLayer.getBlockHash()), true);

    assertThat(archive.getCachedWorldState(0L).isPresent()).isTrue();
    assertThat(archive.getCachedWorldState(pluginLayer.getBlockHash()).isPresent()).isTrue();
  }
}
