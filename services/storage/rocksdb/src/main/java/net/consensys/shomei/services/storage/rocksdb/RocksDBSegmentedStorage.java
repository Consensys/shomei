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

import net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentIdentifier.SegmentNames;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfiguration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.rocksdb.AbstractRocksIterator;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.CompressionType;
import org.rocksdb.DBOptions;
import org.rocksdb.Env;
import org.rocksdb.LRUCache;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Statistics;
import org.rocksdb.Status;
import org.rocksdb.TransactionDBOptions;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.storage.BidirectionalIterator;
import services.storage.KeyValueStorage;
import services.storage.KeyValueStorageTransaction;
import services.storage.SnappableKeyValueStorage;
import services.storage.StorageException;

/** The RocksDb columnar key value storage. */
public class RocksDBSegmentedStorage implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(RocksDBSegmentedStorage.class);
  private static final int ROCKSDB_FORMAT_VERSION = 5;
  private static final long ROCKSDB_BLOCK_SIZE = 32768;
  private static final long ROCKSDB_BLOCKCACHE_SIZE = 1_073_741_824L;
  private static final long ROCKSDB_MEMTABLE_SIZE = 1_073_741_824L;

  static {
    loadNativeLibrary();
  }

  private final DBOptions options;
  private final TransactionDBOptions txOptions;
  private final OptimisticTransactionDB db;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  // TODO: concurrent hashmap and move truncation into outer class
  private final Map<RocksDBSegmentIdentifier, RocksDBSegment> columnHandlesByName;
  private final WriteOptions tryDeleteOptions =
      new WriteOptions().setNoSlowdown(true).setIgnoreMissingColumnFamilies(true);

  /**
   * Instantiates a new Rocks db columnar key value storage.
   *
   * @param configuration the configuration
   * @throws StorageException the storage exception
   */
  public RocksDBSegmentedStorage(final RocksDBConfiguration configuration) {
    this(configuration, EnumSet.allOf(SegmentNames.class));
  }

  /**
   * Instantiates a new Rocks db columnar key value storage.
   *
   * @param configuration the configuration
   * @param segmentNames the segments
   * @throws StorageException the storage exception
   */
  public RocksDBSegmentedStorage(
      final RocksDBConfiguration configuration, final Set<SegmentNames> segmentNames)
      throws StorageException {

    try {
      final List<ColumnFamilyDescriptor> columnDescriptors =
          segmentNames.stream()
              .map(
                  segment ->
                      new ColumnFamilyDescriptor(
                          Optional.of(segment)
                              .filter(z -> !z.equals(SegmentNames.DEFAULT))
                              .map(SegmentNames::getId)
                              .orElse(RocksDB.DEFAULT_COLUMN_FAMILY),
                          new ColumnFamilyOptions()
                              .setTtl(0)
                              .setCompressionType(CompressionType.LZ4_COMPRESSION)
                              .setTableFormatConfig(createBlockBasedTableConfig())))
              .collect(Collectors.toList());

      final Statistics stats = new Statistics();
      options =
          new DBOptions()
              .setCreateIfMissing(true)
              .setMaxOpenFiles(configuration.getMaxOpenFiles())
              .setDbWriteBufferSize(ROCKSDB_MEMTABLE_SIZE)
              .setMaxBackgroundCompactions(configuration.getMaxBackgroundCompactions())
              .setStatistics(stats)
              .setCreateMissingColumnFamilies(true)
              .setEnv(
                  Env.getDefault().setBackgroundThreads(configuration.getBackgroundThreadCount()));

      txOptions = new TransactionDBOptions();
      final List<ColumnFamilyHandle> columnHandles = new ArrayList<>(columnDescriptors.size());
      db =
          OptimisticTransactionDB.open(
              options, configuration.getDatabaseDir().toString(), columnDescriptors, columnHandles);

      columnHandlesByName =
          columnHandles.stream()
              .collect(Collectors.toMap(RocksDBSegmentIdentifier::fromHandle, RocksDBSegment::new));

    } catch (final RocksDBException | RuntimeException e) {
      throw new StorageException(e);
    }
  }

  private BlockBasedTableConfig createBlockBasedTableConfig() {
    final LRUCache cache = new LRUCache(ROCKSDB_BLOCKCACHE_SIZE);
    return new BlockBasedTableConfig()
        .setFormatVersion(ROCKSDB_FORMAT_VERSION)
        .setBlockCache(cache)
        .setFilterPolicy(new BloomFilter(10, false))
        .setPartitionFilters(true)
        .setCacheIndexAndFilterBlocks(false)
        .setBlockSize(ROCKSDB_BLOCK_SIZE);
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      txOptions.close();
      options.close();
      tryDeleteOptions.close();
      columnHandlesByName.values().stream()
          .map(RocksDBSegment::getHandle)
          .forEach(ColumnFamilyHandle::close);
      db.close();
    }
  }

  private void throwIfClosed() {
    if (closed.get()) {
      LOG.error("Attempting to use a closed RocksDbKeyValueStorage");
      throw new StorageException("Storage has been closed");
    }
  }

  private static void loadNativeLibrary() {
    try {
      RocksDB.loadLibrary();
    } catch (final ExceptionInInitializerError e) {
      LOG.error("Unable to load RocksDB library", e);
      throw new RuntimeException(e);
    }
  }

  public SnappableKeyValueStorage getKeyValueStorageForSegment(
      final RocksDBSegmentIdentifier segmentId) {
    throwIfClosed();
    return new RocksDBKeyValueSegment(columnHandlesByName.get(segmentId));
  }

  class RocksDBSegment {

    private final AtomicReference<ColumnFamilyHandle> reference;

    RocksDBSegment(ColumnFamilyHandle handle) {
      this.reference = new AtomicReference<>(handle);
    }

    /** Truncate. */
    void truncate() {
      throwIfClosed();
      reference.getAndUpdate(
          oldHandle -> {
            try {
              ColumnFamilyDescriptor descriptor =
                  new ColumnFamilyDescriptor(
                      oldHandle.getName(), oldHandle.getDescriptor().getOptions());
              db.dropColumnFamily(oldHandle);
              ColumnFamilyHandle newHandle = db.createColumnFamily(descriptor);
              oldHandle.close();
              return newHandle;
            } catch (final RocksDBException e) {
              throw new StorageException(e);
            }
          });
    }

    ColumnFamilyHandle getHandle() {
      return reference.get();
    }

    public Optional<byte[]> get(final ReadOptions readOptions, final byte[] key) {
      throwIfClosed();

      try {
        return Optional.ofNullable(db.get(this.getHandle(), readOptions, key));
      } catch (final RocksDBException e) {
        throw new StorageException(e);
      }
    }

    public Stream<KeyValueStorage.KeyValuePair> stream(final ReadOptions readOptions) {
      throwIfClosed();
      final RocksIterator rocksIterator = db.newIterator(this.getHandle(), readOptions);
      rocksIterator.seekToFirst();
      return RocksDBIterator.create(rocksIterator).toStream();
    }

    public Stream<byte[]> streamKeys(final ReadOptions readOptions) {
      throwIfClosed();
      final RocksIterator rocksIterator = db.newIterator(getHandle(), readOptions);
      rocksIterator.seekToFirst();
      return RocksDBIterator.create(rocksIterator).toStreamKeys();
    }

    public boolean tryDelete(final byte[] key) {
      throwIfClosed();
      try {
        db.delete(getHandle(), tryDeleteOptions, key);
        return true;
      } catch (RocksDBException e) {
        if (e.getStatus().getCode() == Status.Code.Incomplete) {
          return false;
        } else {
          throw new StorageException(e);
        }
      }
    }

    public KeyValueStorageTransaction startTransaction() {
      throwIfClosed();
      final WriteOptions options = new WriteOptions();
      options.setIgnoreMissingColumnFamilies(true);
      return new RocksDBTransaction(db, getHandle());
    }

    public KeyValueStorage takeSnapshot() {
      throwIfClosed();
      return new RocksDBKeyValueSnapshot(this);
    }

    RocksDBTransaction createSnapshotTransaction() {
      throwIfClosed();
      return new RocksDBTransaction.RocksDBSnapshotTransaction(db, getHandle());
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      RocksDBSegment that = (RocksDBSegment) o;
      return Objects.equals(reference.get(), that.reference.get());
    }

    @Override
    public int hashCode() {
      return reference.get().hashCode();
    }

    public Optional<BidirectionalIterator<KeyValueStorage.KeyValuePair>> getNearestTo(
        final ReadOptions readOptions, final byte[] key) {
      throwIfClosed();

      try {
        RocksIterator iterator = db.newIterator(getHandle(), readOptions);
        iterator.seekForPrev(key);

        return Optional.of(iterator)
            .filter(AbstractRocksIterator::isValid)
            .map(RocksDBIterator::create);
      } catch (final Throwable t) {
        throw new StorageException(t);
      }
    }
  }
}
