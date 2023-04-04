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

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.storage.BidirectionalIterator;
import services.storage.KeyValueStorage.KeyValuePair;

public class RocksDBIterator implements BidirectionalIterator<KeyValuePair>, AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(RocksDBIterator.class);

  private final RocksIterator rocksIterator;
  private final AtomicBoolean isClosed = new AtomicBoolean(false);

  private RocksDBIterator(final RocksIterator rocksIterator) {
    this.rocksIterator = rocksIterator;
  }

  public static RocksDBIterator create(final RocksIterator rocksIterator) {
    return new RocksDBIterator(rocksIterator);
  }

  @Override
  public boolean hasNext() {
    assertOpen();
    return rocksIterator.isValid();
  }

  @Override
  public KeyValuePair next() {
    assertOpen();
    checkStatus();
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    final byte[] key = rocksIterator.key();
    final byte[] value = rocksIterator.value();
    rocksIterator.next();
    return new KeyValuePair(key, value);
  }

  @Override
  public boolean hasPrevious() {
    assertOpen();
    return rocksIterator.isValid();
  }

  @Override
  public KeyValuePair previous() {
    assertOpen();
    checkStatus();
    if (!hasPrevious()) {
      throw new NoSuchElementException();
    }
    final byte[] key = rocksIterator.key();
    final byte[] value = rocksIterator.value();
    rocksIterator.prev();
    return new KeyValuePair(key, value);
  }

  private void checkStatus() {
    try {
      rocksIterator.status();
    } catch (final RocksDBException e) {
      LOG.error(
          String.format("%s encountered a problem while iterating.", getClass().getSimpleName()),
          e);
    }
  }

  public Stream<KeyValuePair> toStream() {
    assertOpen();
    final Spliterator<KeyValuePair> spliterator =
        Spliterators.spliteratorUnknownSize(
            this,
            Spliterator.IMMUTABLE
                | Spliterator.DISTINCT
                | Spliterator.NONNULL
                | Spliterator.ORDERED
                | Spliterator.SORTED);

    return StreamSupport.stream(spliterator, false).onClose(this::close);
  }

  /**
   * To stream keys.
   *
   * @return the stream
   */
  public Stream<byte[]> toStreamKeys() {
    assertOpen();
    final Spliterator<byte[]> spliterator =
        Spliterators.spliteratorUnknownSize(
            new Iterator<>() {
              @Override
              public boolean hasNext() {
                return RocksDBIterator.this.hasNext();
              }

              @Override
              public byte[] next() {
                return Optional.ofNullable(RocksDBIterator.this.next())
                    .map(KeyValuePair::key)
                    .orElse(null);
              }
            },
            Spliterator.IMMUTABLE
                | Spliterator.DISTINCT
                | Spliterator.NONNULL
                | Spliterator.ORDERED
                | Spliterator.SORTED);

    return StreamSupport.stream(spliterator, false).onClose(this::close);
  }

  private void assertOpen() {
    if (isClosed.get()) {
      throw new IllegalStateException(String.format("%s is closed.", getClass().getSimpleName()));
    }
  }

  @Override
  public void close() {
    if (isClosed.compareAndSet(false, true)) {
      rocksIterator.close();
    }
  }
}
