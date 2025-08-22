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

import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;
import net.consensys.shomei.storage.worldstate.PersistedWorldStateStorage;
import net.consensys.shomei.storage.worldstate.WorldStateStorage;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;


public class WorldStatePersistedStorageWrapperTest extends WorldStateWrapperTestBase {

  @TempDir
  Path tempData;
  protected PersistedWorldStateStorage storage;

  @BeforeEach
  public void setup() {
    var provider =
        new RocksDBStorageProvider(
            new RocksDBConfigurationBuilder().databaseDir(tempData).build());

    storage =
        new PersistedWorldStateStorage(
            provider.getFlatLeafStorage(),
            provider.getTrieNodeStorage(),
            provider.getTraceManager());
  }

  @AfterEach
  public void tearDown() {
    if (storage != null) {
      storage.close();
    }
  }

  @Override
  WorldStateStorage getWorldStateStorage() {
    return storage;
  }
}
