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

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class WorldStatePersistedStorageWrapperTest extends WorldStateWrapperTestBase {

  @Rule public final TemporaryFolder tempData = new TemporaryFolder();
  protected PersistedWorldStateStorage storage;

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
  }

  @After
  public void tearDown() {
    storage.close();
    tempData.delete();
  }

  @Override
  WorldStateStorage getWorldStateStorage() {
    return storage;
  }
}
