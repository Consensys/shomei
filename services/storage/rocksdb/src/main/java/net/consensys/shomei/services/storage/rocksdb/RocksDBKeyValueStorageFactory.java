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
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package net.consensys.shomei.services.storage.rocksdb;


import java.util.List;
import java.util.function.Supplier;

import net.consensys.shomei.config.ShomeiConfig;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfiguration;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.storage.KeyValueStorage;
import services.storage.KeyValueStorageFactory;
import services.storage.SegmentIdentifier;
import services.storage.StorageException;

/** The Rocks db key value storage factory. */
public class RocksDBKeyValueStorageFactory implements KeyValueStorageFactory {

  private static final Logger LOG = LoggerFactory.getLogger(RocksDBKeyValueStorageFactory.class);
  private static final String NAME = "rocksdb";

  private RocksDBColumnarKeyValueStorage rocksDBStorage;
  private RocksDBConfiguration rocksDBConfiguration;

  private final Supplier<RocksDBFactoryConfiguration> configuration;
  private final List<SegmentIdentifier> segments;

  /**
   * Instantiates a new RocksDb key value storage factory.
   *
   * @param configuration the configuration
   * @param segments the segments
   */
  public RocksDBKeyValueStorageFactory(
      final Supplier<RocksDBFactoryConfiguration> configuration,
      final List<SegmentIdentifier> segments) {
    this.configuration = configuration;
    this.segments = segments;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public KeyValueStorage create(
      final SegmentIdentifier segment,
      final ShomeiConfig shomeiConfig)
      throws StorageException {

    rocksDBConfiguration =
        RocksDBConfigurationBuilder.from(configuration.get())
            .databaseDir(shomeiConfig.getStoragePath())
            .build();
          if (rocksDBStorage == null) {

            rocksDBStorage =
                new RocksDBColumnarKeyValueStorage(
                    rocksDBConfiguration,
                    segments);
          }
          final RocksDBSegmentIdentifier rocksSegment =
              rocksDBStorage.getSegmentIdentifierByName(segment);
          return new SegmentedKeyValueStorageAdapter<>(
              segment, rocksDBStorage, () -> rocksDBStorage.takeSnapshot(rocksSegment));
        }
    }
  }

  /**
   * Storage path.
   *
   * @param shomeiConfig shomei configuration
   * @return the path
   */
  protected Path storagePath(final ShomeiConfig shomeiConfig) {
    return shomeiConfig.getStoragePath();
  }

  private boolean requiresInit() {
    return rocksDBStorage == null && unsegmentedStorage == null;
  }

  @Override
  public void close() throws IOException {
    if (rocksDBStorage != null) {
      rocksDBStorage.close();
    }
  }
}
