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

import java.nio.file.Path;

/** The Rocks db configuration. */
public class RocksDBConfiguration {

  private final Path databaseDir;
  private final int maxOpenFiles;
  private final int maxBackgroundCompactions;
  private final int backgroundThreadCount;
  private final long cacheCapacity;

  /**
   * Instantiates a new RocksDb configuration.
   *
   * @param databaseDir the database dir
   * @param maxOpenFiles the max open files
   * @param maxBackgroundCompactions the max background compactions
   * @param backgroundThreadCount the background thread count
   * @param cacheCapacity the cache capacity
   */
  public RocksDBConfiguration(
      final Path databaseDir,
      final int maxOpenFiles,
      final int maxBackgroundCompactions,
      final int backgroundThreadCount,
      final long cacheCapacity) {
    this.maxBackgroundCompactions = maxBackgroundCompactions;
    this.backgroundThreadCount = backgroundThreadCount;
    this.databaseDir = databaseDir;
    this.maxOpenFiles = maxOpenFiles;
    this.cacheCapacity = cacheCapacity;
  }

  /**
   * Gets database dir.
   *
   * @return the database dir
   */
  public Path getDatabaseDir() {
    return databaseDir;
  }

  /**
   * Gets max open files.
   *
   * @return the max open files
   */
  public int getMaxOpenFiles() {
    return maxOpenFiles;
  }

  /**
   * Gets max background compactions.
   *
   * @return the max background compactions
   */
  public int getMaxBackgroundCompactions() {
    return maxBackgroundCompactions;
  }

  /**
   * Gets background thread count.
   *
   * @return the background thread count
   */
  public int getBackgroundThreadCount() {
    return backgroundThreadCount;
  }

  /**
   * Gets cache capacity.
   *
   * @return the cache capacity
   */
  public long getCacheCapacity() {
    return cacheCapacity;
  }
}
