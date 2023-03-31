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

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.storage.KeyValueStorage;
import services.storage.KeyValueStorageTransaction;
import services.storage.StorageException;

public class RocksDBKeyValueSnapshot implements KeyValueStorage {
  private static final Logger LOG = LoggerFactory.getLogger(RocksDBKeyValueSnapshot.class);

  /** The Snap tx. */
  final RocksDBTransaction snapTx;

  private final AtomicBoolean closed = new AtomicBoolean(false);

  /**
   * Instantiates a new RocksDb columnar key value snapshot.
   *
   * @param segment the segment
   */
  RocksDBKeyValueSnapshot(final RocksDBSegmentedStorage.RocksDBSegment segment) {
    this.snapTx = segment.createSnapshotTransaction();
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
  public void truncate() {
    throw new UnsupportedOperationException("RocksDBKeyValueSnapshot does not support clear");
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
      throw new StorageException("Snapshot has been closed");
    }
  }
}
