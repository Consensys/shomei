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

import static java.nio.charset.StandardCharsets.UTF_8;
import static net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentIdentifier.SegmentNames.DEFAULT;
import static net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentIdentifier.SegmentNames.ZK_ACCOUNT_TRIE;
import static net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration.DEFAULT_ROCKSDB_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.consensys.shomei.config.ShomeiConfig;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfiguration;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;

import java.io.IOException;
import java.nio.file.Path;

import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import services.storage.SnappableKeyValueStorage;
import services.storage.StorageException;

public class RocksDBSegmentedStorageTest {

  @TempDir Path tempDir;

  final RocksDBKeyValueStorageFactory factory =
      new RocksDBKeyValueStorageFactory(DEFAULT_ROCKSDB_CONFIG);
  RocksDBConfiguration rocksDBConfiguration;

  final byte[] key = "key1".getBytes(UTF_8);
  final byte[] value = "value1".getBytes(UTF_8);

  @BeforeEach
  public void setup() {
    this.rocksDBConfiguration =
        RocksDBConfigurationBuilder.from(DEFAULT_ROCKSDB_CONFIG).databaseDir(tempDir).build();
  }

  @Test
  public void segmentedStorageTest() throws IOException {
    var defaultSegment = getKeyValueStorage(DEFAULT.getSegmentIdentifier());

    defaultSegment.startTransaction().put(key, value).commit();
    // assert key present
    assertThat(defaultSegment.get(key)).contains(value);

    defaultSegment.startTransaction().remove(key).commit();
    // assert key deleted
    defaultSegment.startTransaction().remove(key).commit();

    assertThat(defaultSegment.get(key)).isEmpty();
    defaultSegment.close();
    factory.close();
  }

  @Test
  public void snapshotStorageTest() throws IOException {

    var defaultSegment = getKeyValueStorage(DEFAULT.getSegmentIdentifier());

    defaultSegment.startTransaction().put(key, value).commit();
    // assert key present
    assertThat(defaultSegment.get(key)).contains(value);

    // snapshot with key present
    var snapshot = defaultSegment.takeSnapshot();

    defaultSegment.startTransaction().remove(key).commit();
    // assert deleted in segment storage
    assertThat(defaultSegment.get(key)).isEmpty();
    // assert present in snapshot storage:
    assertThat(snapshot.get(key)).contains(value);

    snapshot.close();
    defaultSegment.close();
    factory.close();
  }

  @Test
  public void assertSnapshotAccessThrowsWhenClosed() throws IOException {
    var defaultSegment = getKeyValueStorage(DEFAULT.getSegmentIdentifier());

    // snapshot with key present
    var snapshot = defaultSegment.takeSnapshot();
    // assert no exception thrown:
    assertThat(snapshot.get(key)).isEmpty();
    // assert no exception thrown
    defaultSegment.close();

    snapshot.close();

    // assert a closed snapshot throws StorageException
    assertThatThrownBy(
            () -> snapshot.get(key), "access to closed snapshot should throw StorageException")
        .isInstanceOf(StorageException.class);
  }

  @Test
  public void assertSegmentedStorageThrowsWhenClosed() throws IOException {
    var defaultSegment = getKeyValueStorage(DEFAULT.getSegmentIdentifier());

    // assert no exception thrown:
    assertThat(defaultSegment.get(key)).isEmpty();

    factory.close();

    // assert a closed snapshot throws StorageException for get
    assertThatThrownBy(
            () -> defaultSegment.get(key), "access to closed segment should throw StorageException")
        .isInstanceOf(StorageException.class);
    // assert a closed snapshot throws StorageException for truncate
    assertThatThrownBy(
            defaultSegment::truncate, "access to closed segment should throw StorageException")
        .isInstanceOf(StorageException.class);
    // assert a closed snapshot throws StorageException for takeSnapshot
    assertThatThrownBy(
            defaultSegment::takeSnapshot, "access to closed segment should throw StorageException")
        .isInstanceOf(StorageException.class);
    // assert a closed snapshot throws StorageException for startTransaction
    assertThatThrownBy(
            defaultSegment::startTransaction,
            "access to closed segment should throw StorageException")
        .isInstanceOf(StorageException.class);
    // assert a closed snapshot throws StorageException for stream
    assertThatThrownBy(
            defaultSegment::stream, "access to closed segment should throw StorageException")
        .isInstanceOf(StorageException.class);
    // assert a closed snapshot throws StorageException for streamKeys
    assertThatThrownBy(
            defaultSegment::streamKeys, "access to closed segment should throw StorageException")
        .isInstanceOf(StorageException.class);
    // assert a closed snapshot throws StorageException for tryDelete
    assertThatThrownBy(
            () -> defaultSegment.tryDelete(key),
            "access to closed segment should throw StorageException")
        .isInstanceOf(StorageException.class);
  }

  @Test
  public void assertFactoryThrowsWhenClosed() throws Throwable {
    ThrowableAssert.ThrowingCallable segmentCallable =
        () ->
            factory.create(
                DEFAULT.getSegmentIdentifier(),
                new ShomeiConfig(() -> rocksDBConfiguration.getDatabaseDir()));

    // init rocks storage, so it can be closed:
    segmentCallable.call();

    // close rocks storage:
    factory.close();

    // assert closed factory throws StorageException on create
    assertThatThrownBy(segmentCallable, "access to closed factory should throw StorageException")
        .isInstanceOf(StorageException.class);
  }

  @Test
  public void assertTruncationDoesNotSegfault() throws IOException {
    // cannot truncate default segment, use a different one
    var trieSegment = getKeyValueStorage(ZK_ACCOUNT_TRIE.getSegmentIdentifier());

    trieSegment.startTransaction().put(key, value).commit();
    // assert key present
    assertThat(trieSegment.get(key)).contains(value);

    trieSegment.truncate();

    // assert key deleted
    assertThat(trieSegment.get(key)).isEmpty();
    factory.close();
  }

  private SnappableKeyValueStorage getKeyValueStorage(RocksDBSegmentIdentifier segment) {
    return factory.create(segment, new ShomeiConfig(() -> rocksDBConfiguration.getDatabaseDir()));
  }
}
