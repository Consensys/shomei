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

package net.consensys.shomei.services.storage.rocksdb;

import net.consensys.shomei.config.ShomeiConfig;
import net.consensys.shomei.services.storage.api.KeyValueStorageFactory;
import net.consensys.shomei.services.storage.api.SegmentIdentifier;
import net.consensys.shomei.services.storage.api.SnappableKeyValueStorage;
import net.consensys.shomei.services.storage.api.StorageException;
import net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentIdentifier.SegmentNames;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfiguration;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Rocks db key value storage factory. */
public class RocksDBKeyValueStorageFactory implements KeyValueStorageFactory {
  private static final Logger LOG = LoggerFactory.getLogger(RocksDBKeyValueStorageFactory.class);

  private RocksDBSegmentedStorage rocksDBStorage;
  private RocksDBConfiguration rocksDBConfiguration;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  private final RocksDBFactoryConfiguration configuration;
  private final Set<SegmentNames> segmentNames;

  /**
   * Instantiates a new RocksDb key value storage factory.
   *
   * @param configuration the configuration
   */
  public RocksDBKeyValueStorageFactory(final RocksDBFactoryConfiguration configuration) {
    this(configuration, EnumSet.allOf(SegmentNames.class));
  }

  public RocksDBKeyValueStorageFactory(
      final RocksDBFactoryConfiguration configuration, final Set<SegmentNames> segmentNames) {
    this.configuration = configuration;
    this.segmentNames = segmentNames;
  }

  @Override
  public SnappableKeyValueStorage create(
      final SegmentIdentifier segmentId, final ShomeiConfig shomeiConfig) throws StorageException {
    throwIfClosed();
    RocksDBSegmentIdentifier rocksSegmentId =
        Optional.of(segmentId)
            .filter(z -> z instanceof RocksDBSegmentIdentifier)
            .map(RocksDBSegmentIdentifier.class::cast)
            .orElseThrow(
                () ->
                    new StorageException(
                        "Invalid segment type specified for RocksDB storage: "
                            + segmentId.getClass().getSimpleName()));

    if (rocksDBStorage == null) {
      rocksDBConfiguration =
          RocksDBConfigurationBuilder.from(configuration)
              .databaseDir(shomeiConfig.getStoragePath())
              .build();
      rocksDBStorage = new RocksDBSegmentedStorage(rocksDBConfiguration, segmentNames);
    }
    return rocksDBStorage.getKeyValueStorageForSegment(rocksSegmentId);
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

  @Override
  public void close() throws IOException {
    if (rocksDBStorage != null) {
      rocksDBStorage.close();
    }
  }

  private void throwIfClosed() {
    if (closed.get()) {
      LOG.error("Attempting to use a closed RocksDbKeyValueStorage");
      throw new StorageException("Storage has been closed");
    }
  }
}
