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

import net.consensys.shomei.services.storage.api.BidirectionalIterator;
import net.consensys.shomei.services.storage.api.KeyValueStorage.KeyValuePair;
import net.consensys.shomei.services.storage.api.KeyValueStorageTransaction;
import net.consensys.shomei.services.storage.api.StorageException;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.rocksdb.AbstractRocksIterator;
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
      final OptimisticTransactionDB db, final ColumnFamilyHandle columnFamilyHandle) {

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
  @Override
  public Optional<byte[]> get(final byte[] key) {
    throwIfClosed();

    try {
      return Optional.ofNullable(innerTx.get(columnFamilyHandle, readOptions, key));
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public Optional<BidirectionalIterator<KeyValuePair>> getNearestTo(byte[] key) {
    throwIfClosed();

    try {
      RocksIterator iterator = innerTx.getIterator(readOptions, columnFamilyHandle);
      iterator.seekForPrev(key);

      return Optional.of(iterator)
          .filter(AbstractRocksIterator::isValid)
          .map(RocksDBIterator::create);
    } catch (final Throwable t) {
      throw new StorageException(t);
    }
  }

  @Override
  public RocksDBTransaction put(final byte[] key, final byte[] value) {
    throwIfClosed();

    try {
      innerTx.put(columnFamilyHandle, key, value);
      return this;
    } catch (final RocksDBException e) {
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
        LOG.error(e.getMessage());
        System.exit(0);
      }
      throw new StorageException(e);
    }
  }

  @Override
  public RocksDBTransaction remove(final byte[] key) {
    throwIfClosed();

    try {
      innerTx.delete(columnFamilyHandle, key);
      return this;
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
  public Stream<KeyValuePair> stream() {
    throwIfClosed();

    final RocksIterator rocksIterator = innerTx.getIterator(readOptions, columnFamilyHandle);
    rocksIterator.seekToFirst();
    return RocksDBIterator.create(rocksIterator).toStream();
  }

  /**
   * Stream keys.
   *
   * @return the stream
   */
  public Stream<byte[]> streamKeys() {
    throwIfClosed();

    final RocksIterator rocksIterator = innerTx.getIterator(readOptions, columnFamilyHandle);
    rocksIterator.seekToFirst();
    return RocksDBIterator.create(rocksIterator).toStreamKeys();
  }

  @Override
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

  void throwIfClosed() {
    if (isClosed.get()) {
      LOG.debug("Attempted to access closed snapshot");
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

  public static class RocksDBSnapshotTransaction extends RocksDBTransaction {
    private final Snapshot snapshot;

    public RocksDBSnapshotTransaction(
        final OptimisticTransactionDB db, final ColumnFamilyHandle columnFamilyHandle) {
      super(db, columnFamilyHandle);
      this.snapshot = db.getSnapshot();
      this.readOptions.setSnapshot(snapshot);
    }

    @Override
    public void commit() throws StorageException {
      // no-op
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
