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

package net.consensys.shomei.storage;

import net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentIdentifier.SegmentNames;
import net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentedStorage;
import net.consensys.shomei.trie.model.FlattenedLeaf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.primitives.Longs;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.storage.BidirectionalIterator;
import services.storage.KeyValueStorage;
import services.storage.KeyValueStorage.KeyValuePair;
import services.storage.KeyValueStorageTransaction;

public class PersistedWorldStateRepository implements WorldStateRepository {

  record UpdateableStorage(
      KeyValueStorage storage, AtomicReference<KeyValueStorageTransaction> txRef) {
    UpdateableStorage(KeyValueStorage storage) {
      this(storage, new AtomicReference<>(storage.startTransaction()));
    }

    synchronized Optional<byte[]> readThroughTx(byte[] key) {
      return txRef.getAcquire().get(key);
    }

    Optional<byte[]> get(byte[] key) {
      return storage.get(key);
    }

    synchronized void put(byte[] key, byte[] value) {
      txRef.getAcquire().put(key, value);
    }

    synchronized void remove(byte[] key) {
      txRef.getAcquire().remove(key);
    }

    synchronized Optional<BidirectionalIterator<KeyValuePair>> getNearestTo(byte[] key) {
      return txRef.getAcquire().getNearestTo(key);
    }

    synchronized void commit() {
      txRef.getAndUpdate(
          tx -> {
            tx.commit();
            return storage.startTransaction();
          });
    }
  }

  private static final Logger LOG = LoggerFactory.getLogger(PersistedWorldStateRepository.class);
  private final UpdateableStorage flatLeafStorage;
  private final UpdateableStorage trieLogStorage;
  private final UpdateableStorage trieNodeStorage;
  private final UpdateableStorage traceStorage;

  static final String ZK_STATE_ROOT_PREFIX = "zkStateRoot";

  public PersistedWorldStateRepository(final RocksDBSegmentedStorage storage) {
    this.flatLeafStorage =
        new UpdateableStorage(
            storage.getKeyValueStorageForSegment(
                SegmentNames.ZK_LEAF_INDEX.getSegmentIdentifier()));
    this.trieLogStorage =
        new UpdateableStorage(
            storage.getKeyValueStorageForSegment(SegmentNames.ZK_TRIE_LOG.getSegmentIdentifier()));
    this.trieNodeStorage =
        new UpdateableStorage(
            storage.getKeyValueStorageForSegment(SegmentNames.ZK_TRIE_NODE.getSegmentIdentifier()));
    this.traceStorage =
        new UpdateableStorage(
            storage.getKeyValueStorageForSegment(SegmentNames.ZK_TRACE.getSegmentIdentifier()));
  }

  @Override
  public Optional<FlattenedLeaf> getFlatLeaf(final Bytes hkey) {
    return flatLeafStorage
        .readThroughTx(hkey.toArrayUnsafe())
        .map(Bytes::wrap)
        .map(RLP::input)
        .map(FlattenedLeaf::readFrom);
  }

  @Override
  public Range getNearestKeys(final Bytes hkey) {
    // call nearest key of rocksdb
    final Optional<BidirectionalIterator<KeyValuePair>> nearestTo =
        flatLeafStorage.getNearestTo(hkey.toArrayUnsafe());
    if (nearestTo.isPresent()) {
      try (var iterator = nearestTo.get()) {
        final KeyValuePair next = iterator.current();
        final Range range;
        if (Bytes.wrap(next.key()).equals(hkey)) {
          iterator.previous();
          final KeyValuePair left = iterator.next();
          final KeyValuePair middle = iterator.next();
          final KeyValuePair right = iterator.next();
          range =
              new Range(
                  Map.entry(
                      Bytes.wrap(left.key()),
                      FlattenedLeaf.readFrom(RLP.input(Bytes.wrap(left.value())))),
                  Optional.of(
                      Map.entry(
                          Bytes.wrap(middle.key()),
                          FlattenedLeaf.readFrom(RLP.input(Bytes.wrap(middle.value()))))),
                  Map.entry(
                      Bytes.wrap(right.key()),
                      FlattenedLeaf.readFrom(RLP.input(Bytes.wrap(right.value())))));
        } else {
          final KeyValuePair left = iterator.next();
          final KeyValuePair right = iterator.next();
          range =
              new Range(
                  Map.entry(
                      Bytes.wrap(left.key()),
                      FlattenedLeaf.readFrom(RLP.input(Bytes.wrap(left.value())))),
                  Optional.empty(),
                  Map.entry(
                      Bytes.wrap(right.key()),
                      FlattenedLeaf.readFrom(RLP.input(Bytes.wrap(right.value())))));
        }
        iterator.close();
        return range;
      } catch (Exception ex) {
        LOG.error("failed to get nearest keys", ex);
      }
    }
    throw new RuntimeException("not found leaf index");
  }

  @Override
  public Optional<Bytes> getTrieLog(final long blockNumber) {
    return trieLogStorage.get(Longs.toByteArray(blockNumber)).map(Bytes::wrap);
  }

  @Override
  public Optional<Hash> getZkStateRootHash(final long blockNumber) {
    return traceStorage
        .readThroughTx((ZK_STATE_ROOT_PREFIX + blockNumber).getBytes(StandardCharsets.UTF_8))
        .map(Bytes32::wrap)
        .map(Hash::wrap);
  }

  @Override
  public Optional<Bytes> getTrace(final long blockNumber) {
    return traceStorage.readThroughTx(Longs.toByteArray(blockNumber)).map(Bytes::wrap);
  }

  @Override
  public Optional<Bytes> getTrieNode(final Bytes location, final Bytes nodeHash) {
    // TODO use location
    return trieNodeStorage.readThroughTx(location.toArrayUnsafe()).map(Bytes::wrap);
  }

  @Override
  public Optional<Hash> getWorldStateRootHash() {
    return getWorldStateBlockNumber()
        .flatMap(this::getZkStateRootHash)
        .map(Bytes32::wrap)
        .map(Hash::wrap);
  }

  @Override
  public Optional<Hash> getWorldStateBlockHash() {
    return trieNodeStorage.readThroughTx(WORLD_BLOCK_HASH_KEY).map(Bytes32::wrap).map(Hash::wrap);
  }

  @Override
  public Optional<Long> getWorldStateBlockNumber() {
    return trieNodeStorage.readThroughTx(WORLD_BLOCK_NUMBER_KEY).map(Longs::fromByteArray);
  }

  @Override
  public PersistedWorldStateRepository saveTrieLog(
      final long blockNumber, final Bytes rawTrieLogLayer) {
    trieLogStorage.put(Longs.toByteArray(blockNumber), rawTrieLogLayer.toArrayUnsafe());
    return this;
  }

  @Override
  public void commitTrieLogStorage() {
    trieLogStorage.commit();
  }

  @Override
  public WorldStateUpdater updater() {
    return new WorldStateUpdater() {
      @Override
      public void setBlockHash(final Hash blockHash) {
        trieNodeStorage.put(WORLD_BLOCK_HASH_KEY, blockHash.toArrayUnsafe());
      }

      @Override
      public void setBlockNumber(final long blockNumber) {
        trieNodeStorage.put(WORLD_BLOCK_NUMBER_KEY, Longs.toByteArray(blockNumber));
      }

      @Override
      public void saveZkStateRootHash(final long blockNumber, final Hash stateRoot) {
        traceStorage.put(
            (ZK_STATE_ROOT_PREFIX + blockNumber).getBytes(StandardCharsets.UTF_8),
            stateRoot.toArrayUnsafe());
      }

      @Override
      public void saveTrace(final long blockNumber, final Bytes rawTrace) {
        traceStorage.txRef.get().put(Longs.toByteArray(blockNumber), rawTrace.toArrayUnsafe());
      }

      @Override
      public void putFlatLeaf(final Bytes key, final FlattenedLeaf value) {
        flatLeafStorage.put(key.toArrayUnsafe(), RLP.encode(value::writeTo).toArrayUnsafe());
      }

      @Override
      public void putTrieNode(final Bytes location, final Bytes nodeHash, final Bytes value) {
        trieNodeStorage.put(location.toArrayUnsafe(), value.toArrayUnsafe());
      }

      @Override
      public void removeFlatLeafValue(final Bytes key) {
        flatLeafStorage.remove(key.toArrayUnsafe());
      }

      @Override
      public void commit() {
        flatLeafStorage.commit();
        trieNodeStorage.commit();
        traceStorage.commit();
      }
    };
  }

  @Override
  public void close() {
    try {
      flatLeafStorage.storage.close();
      trieLogStorage.storage.close();
      trieNodeStorage.storage.close();
      traceStorage.storage.close();
    } catch (IOException e) {
      LOG.error("Failed to close storage", e);
      throw new RuntimeException(e);
    }
  }
}
