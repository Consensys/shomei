/*
 * Copyright ConsenSys AG.
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

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentIdentifier.SegmentNames;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfiguration;
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
import services.storage.KeyValueStorage;
import services.storage.KeyValueStorageTransaction;
import services.storage.SnappedKeyValueStorage;
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
  private final Map<RocksDBSegmentIdentifier, RocksDBSegment> columnHandlesByName;
  private final WriteOptions tryDeleteOptions =
      new WriteOptions().setNoSlowdown(true).setIgnoreMissingColumnFamilies(true);
  private final ReadOptions readOptions = new ReadOptions().setVerifyChecksums(false);


  /**
   * Instantiates a new Rocks db columnar key value storage.
   *
   * @param configuration the configuration
   * @throws StorageException the storage exception
   */
  public RocksDBSegmentedStorage(
      final RocksDBConfiguration configuration) {
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
      final RocksDBConfiguration configuration,
      final Set<SegmentNames> segmentNames)
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
                  Env.getDefault()
                      .setBackgroundThreads(configuration.getBackgroundThreadCount()));

      txOptions = new TransactionDBOptions();
      final List<ColumnFamilyHandle> columnHandles = new ArrayList<>(columnDescriptors.size());
      db =
          OptimisticTransactionDB.open(
              options, configuration.getDatabaseDir().toString(), columnDescriptors, columnHandles);

      columnHandlesByName = columnHandles.stream()
          .collect(Collectors.toMap(
              RocksDBSegmentIdentifier::fromHandle,
              RocksDBSegment::new));

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

  /**
   * Take snapshot RocksDb columnar key value snapshot.
   *
   * @param segmentIdentifier the segment identifier to snapshot
   * @return the RocksDb columnar key value snapshot
   * @throws StorageException the storage exception
   */
  public RocksDBKeyValueStorage takeSnapshot(final RocksDBSegmentIdentifier segmentIdentifier)
      throws StorageException {
    throwIfClosed();
    return new RocksDBKeyValueStorage(new RocksDBKeyValueSnapshot(columnHandlesByName.get(segmentIdentifier)));
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
      throw new IllegalStateException("Storage has been closed");
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

  public KeyValueStorage getKeyValueStorageForSegment(final RocksDBSegmentIdentifier segment) {
    return new RocksDBKeyValueStorage(new RocksDBKeyValueSegment(columnHandlesByName.get(segment)));
  }


  static class RocksDBSegment {

    private final AtomicReference<ColumnFamilyHandle> reference;

    RocksDBSegment(ColumnFamilyHandle columnFamilyHandle) {
      this.reference = new AtomicReference<>(columnFamilyHandle);
    }

    /** Truncate. */
    void truncate(OptimisticTransactionDB db) {
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

  }
  class RocksDBKeyValueSegment implements KeyValueStorage {

    private static final Logger LOG = LoggerFactory.getLogger(RocksDBKeyValueSegment.class);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final RocksDBSegment segmentHandle;

    /**
     * Instantiates a new Segmented key value storage adapter.
     *
     * @param segmentHandle the segment
     */
    public RocksDBKeyValueSegment(
        final RocksDBSegment segmentHandle) {
      this.segmentHandle = segmentHandle;
    }


    @Override
    public void clear() throws StorageException {
      throwIfClosed();
      segmentHandle.truncate(db);
    }

    @Override
    public boolean containsKey(final byte[] key) throws StorageException {
      return get(key).isPresent();
    }

    @Override
    public Optional<byte[]> get(final byte[] key) throws StorageException {
      throwIfClosed();

      try {
        return Optional.ofNullable(db.get(segmentHandle.getHandle(), readOptions, key));
      } catch (final RocksDBException e) {
        throw new StorageException(e);
      }
    }

    @Override
    public Set<byte[]> getAllKeysThat(final Predicate<byte[]> returnCondition) {
      return stream()
          .filter(pair -> returnCondition.test(pair.key()))
          .map(KeyValuePair::key)
          .collect(toUnmodifiableSet());
    }

    @Override
    public Stream<KeyValuePair> stream() {
      throwIfClosed();
      final RocksIterator rocksIterator = db.newIterator();
      rocksIterator.seekToFirst();
      return RocksDbIterator.create(rocksIterator).toStream();
    }

    @Override
    public Stream<byte[]> streamKeys() {
      throwIfClosed();
      final RocksIterator rocksIterator = db.newIterator();
      rocksIterator.seekToFirst();
      return RocksDbIterator.create(rocksIterator).toStreamKeys();
    }

    @Override
    public Set<byte[]> getAllValuesFromKeysThat(final Predicate<byte[]> returnCondition) {
      return stream()
          .filter(pair -> returnCondition.test(pair.key()))
          .map(KeyValuePair::value)
          .collect(toUnmodifiableSet());
    }

    @Override
    public boolean tryDelete(final byte[] key) {
      throwIfClosed();
      try {
        db.delete(tryDeleteOptions, key);
        return true;
      } catch (RocksDBException e) {
        if (e.getStatus().getCode() == Status.Code.Incomplete) {
          return false;
        } else {
          throw new StorageException(e);
        }
      }
    }

    @Override
    public KeyValueStorageTransaction startTransaction() throws StorageException {
      throwIfClosed();
      final WriteOptions options = new WriteOptions();
      options.setIgnoreMissingColumnFamilies(true);
      return new RocksDBTransaction(db, segmentHandle.getHandle());
    }

    @Override
    public void close() {
      if (closed.compareAndSet(false, true)) {
        tryDeleteOptions.close();
        options.close();
        db.close();
      }
    }

    private void throwIfClosed() {
      if (closed.get()) {
        LOG.error("Attempting to use a closed RocksDBKeyValueSegment");
        throw new IllegalStateException("Storage has been closed");
      }
    }
  }
  class RocksDBKeyValueSnapshot implements SnappedKeyValueStorage {

    /** The Snap tx. */
    final RocksDBTransaction snapTx;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Instantiates a new RocksDb columnar key value snapshot.
     *
     * @param segment the segment
     */
    RocksDBKeyValueSnapshot(
        final RocksDBSegment segment) {
      this.snapTx = new RocksDBTransaction.RocksDBSnapshotTransaction(db, segment.getHandle());
    }

    @Override
    public Optional<byte[]> get(final byte[] key) throws StorageException {
      throwIfClosed();
      return snapTx.get(key);
    }

    @Override
    public Stream<KeyValuePair> stream() {
      throwIfClosed();
      return snapTx.stream();
    }

    @Override
    public Stream<byte[]> streamKeys() {
      throwIfClosed();
      return snapTx.streamKeys();
    }

    @Override
    public boolean tryDelete(final byte[] key) throws StorageException {
      throwIfClosed();
      snapTx.remove(key);
      return true;
    }

    @Override
    public Set<byte[]> getAllKeysThat(final Predicate<byte[]> returnCondition) {
      return streamKeys().filter(returnCondition).collect(toUnmodifiableSet());
    }

    @Override
    public Set<byte[]> getAllValuesFromKeysThat(final Predicate<byte[]> returnCondition) {
      return stream()
          .filter(pair -> returnCondition.test(pair.key()))
          .map(KeyValuePair::value)
          .collect(toUnmodifiableSet());
    }

    @Override
    public KeyValueStorageTransaction startTransaction() throws StorageException {
      // The use of a transaction on a transaction based key value store is dubious
      // at best.  return our snapshot transaction instead.
      return snapTx;
    }

    @Override
    public void clear() {
      throw new UnsupportedOperationException(
          "RocksDBKeyValueSnapshot does not support clear");
    }

    @Override
    public boolean containsKey(final byte[] key) throws StorageException {
      throwIfClosed();
      return snapTx.get(key).isPresent();
    }

    @Override
    public void close() throws IOException {
      if (closed.compareAndSet(false, true)) {
        snapTx.close();
      }
    }

    private void throwIfClosed() {
      if (closed.get()) {
        LOG.error("Attempting to use a closed RocksDBKeyValueSegment");
        throw new IllegalStateException("Storage has been closed");
      }
    }

    @Override
    public KeyValueStorageTransaction getSnapshotTransaction() {
      return snapTx;
    }
  }

}
