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

import net.consensys.shomei.services.storage.api.StorageException;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.Transaction;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal transaction implementation for a segmented RocksDB which allows mutating
 * multiple RocksDB segments with a single transaction.
 *
 */
public class RocksDBFlatTransaction implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(RocksDBFlatTransaction.class);
  private static final String NO_SPACE_LEFT_ON_DEVICE = "No space left on device";

  private final Transaction innerTx;
  private final WriteOptions writeOptions;
  private final ReadOptions readOptions;
  private final AtomicBoolean isClosed = new AtomicBoolean(false);

  /**
   * Instantiates a new raw/flat RocksDb transaction.
   *
   * @param db the db
   */
  public RocksDBFlatTransaction(final OptimisticTransactionDB db) {

    this.writeOptions = new WriteOptions();
    this.innerTx = db.beginTransaction(writeOptions);
    this.readOptions = new ReadOptions().setVerifyChecksums(false);
  }

  /**
   * Get data against given key.
   *
   * @param columnFamilyHandle the column family
   * @param key the key
   * @return the optional data
   */
  public Optional<byte[]> get(ColumnFamilyHandle columnFamilyHandle, final byte[] key) {
    throwIfClosed();

    try {
      return Optional.ofNullable(innerTx.get(columnFamilyHandle, readOptions, key));
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  /**
   * Put data for a given key.
   *
   * @param columnFamilyHandle the column family
   * @param key the key
   * @param value the data
   */
  public void put(ColumnFamilyHandle columnFamilyHandle, final byte[] key, final byte[] value) {
    throwIfClosed();

    try {
      innerTx.put(columnFamilyHandle, key, value);
    } catch (final RocksDBException e) {
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
        LOG.error(e.getMessage());
        System.exit(0);
      }
      throw new StorageException(e);
    }
  }

  /**
   * remove data for given key.
   *
   * @param columnFamilyHandle the column family
   * @param key the key
   */
  public void remove(ColumnFamilyHandle columnFamilyHandle, final byte[] key) {
    throwIfClosed();

    try {
      innerTx.delete(columnFamilyHandle, key);
    } catch (final RocksDBException e) {
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
        LOG.error(e.getMessage());
        System.exit(0);
      }
      throw new StorageException(e);
    }
  }

  /**
   * Commit and close the current transaction
   */
  public void commit() throws StorageException {
    throwIfClosed();
    try {
      innerTx.commit();
    } catch (final RocksDBException e) {
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
        LOG.error(e.getMessage());
        System.exit(0);
      }
      throw new StorageException(e);
    } finally {
      close();
    }
  }

  /**
   * Rollback and close the current transaction
   */
  public void rollback() {
    try {
      innerTx.rollback();
    } catch (final RocksDBException e) {
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
        LOG.error(e.getMessage());
        System.exit(0);
      }
      throw new StorageException(e);
    } finally {
      close();
    }
  }

  void throwIfClosed() {
    if (isClosed.get()) {
      LOG.debug("Attempted to access closed transaction");
      throw new StorageException("Attempted to access closed transaction");
    }
  }

  @Override
  public void close() {
    innerTx.close();
    writeOptions.close();
    readOptions.close();
    isClosed.set(true);
  }
}
