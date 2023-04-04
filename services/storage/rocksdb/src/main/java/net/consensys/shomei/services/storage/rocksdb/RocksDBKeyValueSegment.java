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

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.rocksdb.ReadOptions;
import services.storage.BidirectionalIterator;
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
  public RocksDBKeyValueSegment(final RocksDBSegmentedStorage.RocksDBSegment segment) {
    this.segment = segment;
  }

  @Override
  public void truncate() throws StorageException {
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
  public Optional<BidirectionalIterator<KeyValuePair>> getNearestTo(final byte[] key)
      throws StorageException {
    return segment.getNearestTo(readOptions, key);
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
    return segment.stream(readOptions);
  }

  @Override
  public Stream<byte[]> streamKeys() {
    return segment.streamKeys(readOptions);
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
