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

package net.consensys.shomei.storage.worldstate;

import net.consensys.shomei.services.storage.api.BidirectionalIterator;
import net.consensys.shomei.services.storage.api.KeyValueStorage;
import net.consensys.shomei.services.storage.api.KeyValueStorage.KeyValuePair;
import net.consensys.shomei.services.storage.api.KeyValueStorageTransaction;
import net.consensys.shomei.services.storage.api.SnappableKeyValueStorage;
import net.consensys.shomei.storage.TraceManager;
import net.consensys.shomei.trie.model.FlattenedLeaf;

import java.io.IOException;
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

/** Persisted implementation of {@link WorldStateStorage}. */
public class PersistedWorldStateStorage implements WorldStateStorage {

  private static final Logger LOG = LoggerFactory.getLogger(PersistedWorldStateStorage.class);
  protected final KeyValueStorage flatLeafStorage;
  protected final AtomicReference<KeyValueStorageTransaction> flatLeafTx;
  protected final KeyValueStorage trieNodeStorage;
  protected final AtomicReference<KeyValueStorageTransaction> trieNodeTx;
  protected final TraceManager traceManager;

  public PersistedWorldStateStorage(
      final KeyValueStorage flatLeafStorage,
      final KeyValueStorage trieNodeStorage,
      final TraceManager traceManager) {
    this.flatLeafStorage = flatLeafStorage;
    flatLeafTx = new AtomicReference<>(flatLeafStorage.startTransaction());
    this.trieNodeStorage = trieNodeStorage;
    trieNodeTx = new AtomicReference<>(trieNodeStorage.startTransaction());
    this.traceManager = traceManager;
  }

  @Override
  public Optional<FlattenedLeaf> getFlatLeaf(final Bytes hkey) {
    return flatLeafTx
        .get()
        .get(hkey.toArrayUnsafe())
        .map(Bytes::wrap)
        .map(RLP::input)
        .map(FlattenedLeaf::readFrom);
  }

  @Override
  public Range getNearestKeys(final Bytes hkey) {
    // call nearest key of rocksdb
    final Optional<BidirectionalIterator<KeyValuePair>> nearestTo =
        flatLeafTx.get().getNearestTo(hkey.toArrayUnsafe());
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
  public Optional<Hash> getZkStateRootHash(final long blockNumber) {
    return traceManager.getZkStateRootHash(blockNumber);
  }

  @Override
  public Optional<Bytes> getTrieNode(final Bytes location, final Bytes nodeHash) {
    // TODO use location
    return trieNodeTx.get().get(location.toArrayUnsafe()).map(Bytes::wrap);
  }

  @Override
  public Optional<Hash> getWorldStateRootHash() {
    return getWorldStateBlockNumber()
        .flatMap(this::getZkStateRootHash)
        .map(Bytes32::wrap)
        .map(Hash::wrap);
  }

  @Override
  public SnapshotPersistedWorldStateStorage snapshot() {
    return new SnapshotPersistedWorldStateStorage(
        ((SnappableKeyValueStorage) flatLeafStorage).takeSnapshot(),
        ((SnappableKeyValueStorage) trieNodeStorage).takeSnapshot(),
        traceManager);
  }

  @Override
  public Optional<Hash> getWorldStateBlockHash() {
    return trieNodeTx.get().get(WORLD_BLOCK_HASH_KEY).map(Bytes32::wrap).map(Hash::wrap);
  }

  @Override
  public Optional<Long> getWorldStateBlockNumber() {
    return trieNodeTx.get().get(WORLD_BLOCK_NUMBER_KEY).map(Longs::fromByteArray);
  }

  @Override
  public WorldStateUpdater updater() {
    return new WorldStateUpdater() {
      @Override
      public void setBlockHash(final Hash blockHash) {
        trieNodeTx.get().put(WORLD_BLOCK_HASH_KEY, blockHash.toArrayUnsafe());
      }

      @Override
      public void setBlockNumber(final long blockNumber) {
        trieNodeTx.get().put(WORLD_BLOCK_NUMBER_KEY, Longs.toByteArray(blockNumber));
      }

      @Override
      public void saveZkStateRootHash(final long blockNumber, final Hash stateRoot) {
        traceManager.saveZkStateRootHash(blockNumber, stateRoot);
      }

      @Override
      public void putFlatLeaf(final Bytes key, final FlattenedLeaf value) {
        flatLeafTx.get().put(key.toArrayUnsafe(), RLP.encode(value::writeTo).toArrayUnsafe());
      }

      @Override
      public void putTrieNode(final Bytes location, final Bytes nodeHash, final Bytes value) {
        trieNodeTx.get().put(location.toArrayUnsafe(), value.toArrayUnsafe());
      }

      @Override
      public void removeFlatLeafValue(final Bytes key) {
        flatLeafTx.get().remove(key.toArrayUnsafe());
      }

      @Override
      public synchronized void commit() {
        flatLeafTx.getAndUpdate(
            flatTx -> {
              flatTx.commit();
              return flatLeafStorage.startTransaction();
            });
        trieNodeTx.getAndUpdate(
            trieTx -> {
              trieTx.commit();
              return trieNodeStorage.startTransaction();
            });
        traceManager.commit();
      }
    };
  }

  @Override
  public void close() {
    try {
      flatLeafStorage.close();
      trieNodeStorage.close();
    } catch (IOException e) {
      LOG.error("Failed to close storage", e);
      throw new RuntimeException(e);
    }
  }
}
