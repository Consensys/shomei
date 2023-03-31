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

package net.consensys.shomei.services.storage.rocksdb.configuration;

import static net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_BACKGROUND_THREAD_COUNT;
import static net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_CACHE_CAPACITY;
import static net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_MAX_BACKGROUND_COMPACTIONS;
import static net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_MAX_OPEN_FILES;

import java.nio.file.Path;

/** The RocksDb configuration builder. */
public class RocksDBConfigurationBuilder {

  private Path databaseDir;
  private int maxOpenFiles = DEFAULT_MAX_OPEN_FILES;
  private long cacheCapacity = DEFAULT_CACHE_CAPACITY;
  private int maxBackgroundCompactions = DEFAULT_MAX_BACKGROUND_COMPACTIONS;
  private int backgroundThreadCount = DEFAULT_BACKGROUND_THREAD_COUNT;

  /**
   * Database dir.
   *
   * @param databaseDir the database dir
   * @return the rocks db configuration builder
   */
  public RocksDBConfigurationBuilder databaseDir(final Path databaseDir) {
    this.databaseDir = databaseDir;
    return this;
  }

  /**
   * Max open files.
   *
   * @param maxOpenFiles the max open files
   * @return the rocks db configuration builder
   */
  public RocksDBConfigurationBuilder maxOpenFiles(final int maxOpenFiles) {
    this.maxOpenFiles = maxOpenFiles;
    return this;
  }

  /**
   * Cache capacity.
   *
   * @param cacheCapacity the cache capacity
   * @return the rocks db configuration builder
   */
  public RocksDBConfigurationBuilder cacheCapacity(final long cacheCapacity) {
    this.cacheCapacity = cacheCapacity;
    return this;
  }

  /**
   * Max background compactions.
   *
   * @param maxBackgroundCompactions the max background compactions
   * @return the rocks db configuration builder
   */
  public RocksDBConfigurationBuilder maxBackgroundCompactions(final int maxBackgroundCompactions) {
    this.maxBackgroundCompactions = maxBackgroundCompactions;
    return this;
  }

  /**
   * Background thread count.
   *
   * @param backgroundThreadCount the background thread count
   * @return the rocks db configuration builder
   */
  public RocksDBConfigurationBuilder backgroundThreadCount(final int backgroundThreadCount) {
    this.backgroundThreadCount = backgroundThreadCount;
    return this;
  }

  /**
   * From.
   *
   * @param configuration the configuration
   * @return the rocks db configuration builder
   */
  public static RocksDBConfigurationBuilder from(final RocksDBFactoryConfiguration configuration) {
    return new RocksDBConfigurationBuilder()
        .backgroundThreadCount(configuration.getBackgroundThreadCount())
        .cacheCapacity(configuration.getCacheCapacity())
        .maxBackgroundCompactions(configuration.getMaxBackgroundCompactions())
        .maxOpenFiles(configuration.getMaxOpenFiles());
  }

  /**
   * Build rocks db configuration.
   *
   * @return the rocks db configuration
   */
  public RocksDBConfiguration build() {
    return new RocksDBConfiguration(
        databaseDir, maxOpenFiles, maxBackgroundCompactions, backgroundThreadCount, cacheCapacity);
  }
}
