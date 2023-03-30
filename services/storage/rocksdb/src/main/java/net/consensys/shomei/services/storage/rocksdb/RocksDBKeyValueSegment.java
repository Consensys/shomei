package net.consensys.shomei.services.storage.rocksdb;

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.rocksdb.ReadOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.storage.KeyValueStorage;
import services.storage.KeyValueStorageTransaction;
import services.storage.SnappableKeyValueStorage;
import services.storage.StorageException;

public class RocksDBKeyValueSegment implements SnappableKeyValueStorage {

  private final RocksDBSegmentedStorage.RocksDBSegment segment;
  private final ReadOptions readOptions = new ReadOptions().setVerifyChecksums(false);

  /**
   * Instantiates a new Segmented key value storage adapter.
   *
   * @param segment the segment
   */
  public RocksDBKeyValueSegment(
      final RocksDBSegmentedStorage.RocksDBSegment segment) {
    this.segment = segment;
  }

  @Override
  public void clear() throws StorageException {
    segment.truncate();
  }

  @Override
  public boolean containsKey(final byte[] key) throws StorageException {
    return get(key).isPresent();
  }

  @Override
  public Optional<byte[]> get(final byte[] key) throws StorageException {
      return segment.get(readOptions, key);
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
    return segment.stream();
  }

  @Override
  public Stream<byte[]> streamKeys() {
    return segment.streamKeys();
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
    return segment.tryDelete(key);
  }

  @Override
  public KeyValueStorageTransaction startTransaction() throws StorageException {
    return segment.startTransaction();
  }

  @Override
  public void close() {
    // no-op
  }

  @Override
  public KeyValueStorage takeSnapshot() {
    return segment.takeSnapshot();
  }
}
