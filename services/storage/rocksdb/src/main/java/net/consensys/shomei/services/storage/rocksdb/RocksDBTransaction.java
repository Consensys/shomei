/*
 * Copyright Hyperledger Besu Contributors.
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
 *
 */
package net.consensys.shomei.services.storage.rocksdb;


import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Snapshot;
import org.rocksdb.Transaction;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.storage.KeyValueStorage;
import services.storage.KeyValueStorageTransaction;
import services.storage.StorageException;

/** The Rocks db snapshot transaction. */
public class RocksDBTransaction implements KeyValueStorageTransaction, AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(RocksDBSnapshotTransaction.class);
  private static final String NO_SPACE_LEFT_ON_DEVICE = "No space left on device";

  protected final OptimisticTransactionDB db;
  protected final ColumnFamilyHandle columnFamilyHandle;
  protected final Transaction innerTx;
  protected final WriteOptions writeOptions;
  protected final ReadOptions readOptions;
  protected final AtomicBoolean isClosed = new AtomicBoolean(false);

  /**
   * Instantiates a new RocksDb snapshot transaction.
   *
   * @param db the db
   * @param columnFamilyHandle the column family handle
   */
  public RocksDBTransaction(
      final OptimisticTransactionDB db,
      final ColumnFamilyHandle columnFamilyHandle) {

    this.db = db;
    this.columnFamilyHandle = columnFamilyHandle;
    this.writeOptions = new WriteOptions();
    this.innerTx = db.beginTransaction(writeOptions);
    this.readOptions = new ReadOptions().setVerifyChecksums(false);
  }

  /**
   * Get data against given key.
   *
   * @param key the key
   * @return the optional data
   */
  public Optional<byte[]> get(final byte[] key) {
    if (isClosed.get()) {
      LOG.debug("Attempted to access closed snapshot");
      return Optional.empty();
    }

    try {
      return Optional.ofNullable(innerTx.get(columnFamilyHandle, readOptions, key));
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void put(final byte[] key, final byte[] value) {
    if (isClosed.get()) {
      LOG.debug("Attempted to access closed snapshot");
      return;
    }

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

  @Override
  public void remove(final byte[] key) {
    if (isClosed.get()) {
      LOG.debug("Attempted to access closed snapshot");
      return;
    }
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
   * Stream.
   *
   * @return the stream
   */
  public Stream<KeyValueStorage.KeyValuePair> stream() {
    final RocksIterator rocksIterator = db.newIterator(columnFamilyHandle, readOptions);
    rocksIterator.seekToFirst();
    return RocksDbIterator.create(rocksIterator).toStream();
  }

  /**
   * Stream keys.
   *
   * @return the stream
   */
  public Stream<byte[]> streamKeys() {
    final RocksIterator rocksIterator = db.newIterator(columnFamilyHandle, readOptions);
    rocksIterator.seekToFirst();
    return RocksDbIterator.create(rocksIterator).toStreamKeys();
  }

  @Override
  public void commit() throws StorageException {
    // no-op
  }

  @Override
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

  @Override
  public void close() {
    innerTx.close();
    writeOptions.close();
    readOptions.close();
    isClosed.set(true);
  }

  public static class RocksDBSnapshotTransaction extends RocksDBTransaction {
    private final Snapshot snapshot;

    public RocksDBSnapshotTransaction(
        final OptimisticTransactionDB db,
        final ColumnFamilyHandle columnFamilyHandle) {
      super(db, columnFamilyHandle);
      this.snapshot = db.getSnapshot();
      this.readOptions.setSnapshot(snapshot);
    }

    @Override
    public void close() {
      innerTx.close();
      db.releaseSnapshot(snapshot);
      writeOptions.close();
      readOptions.close();
      snapshot.close();
      isClosed.set(true);
    }
  }
}
