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

import net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentedStorage;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class WorldStatePersistedStorageProxyTest extends WorldStateWrapperTestBase {

  @Rule public final TemporaryFolder tempData = new TemporaryFolder();
  protected PersistedWorldStateRepository storage;

  @Before
  public void setup() {
    storage =
        new PersistedWorldStateRepository(
            new RocksDBSegmentedStorage(
                new RocksDBConfigurationBuilder()
                    .databaseDir(tempData.getRoot().toPath())
                    .build()));
  }

  @After
  public void tearDown() {
    storage.close();
    tempData.delete();
  }

  @Override
  WorldStateRepository getWorldStateStorage() {
    return storage;
  }
}
