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

import net.consensys.shomei.observer.TrieLogObserver;
import net.consensys.shomei.services.storage.api.KeyValueStorage;
import net.consensys.shomei.services.storage.api.KeyValueStorageTransaction;
import net.consensys.shomei.services.storage.api.StorageException;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.primitives.Longs;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface TrieLogManager {

  TrieLogManager saveTrieLog(
      final TrieLogObserver.TrieLogIdentifier trieLogIdentifier, final Bytes rawTrieLogLayer);

  Optional<Bytes> getTrieLog(final long blockNumber);

  void commitTrieLogStorage();

  class TrieLogManagerImpl implements TrieLogManager {
    private static final Logger LOG = LoggerFactory.getLogger(TrieLogManager.class);
    static final int MAX_TX_RETRIES = 5;
    private final KeyValueStorage trieLogStorage;
    private AtomicReference<KeyValueStorageTransaction> trieLogTx;

    public TrieLogManagerImpl(final KeyValueStorage trieLogStorage) {
      this.trieLogStorage = trieLogStorage;
      trieLogTx = new AtomicReference<>(trieLogStorage.startTransaction());
    }

    @Override
    public TrieLogManager saveTrieLog(
        final TrieLogObserver.TrieLogIdentifier trieLogIdentifier, final Bytes rawTrieLogLayer) {
      trieLogTx
          .get()
          .put(Longs.toByteArray(trieLogIdentifier.blockNumber()), rawTrieLogLayer.toArrayUnsafe());
      return this;
    }

    @Override
    public Optional<Bytes> getTrieLog(final long blockNumber) {
      return trieLogTx.get().get(Longs.toByteArray(blockNumber)).map(Bytes::wrap);
    }

    @Override
    public void commitTrieLogStorage() {
      trieLogTx.getAndUpdate(
          tx -> {
            int tries = 0;
            KeyValueStorageTransaction newTx = null;
            while (newTx == null) {
              try {
                tries++;
                tx.commit();
                newTx = trieLogStorage.startTransaction();
              } catch (final StorageException e) {
                if (tries >= MAX_TX_RETRIES) {
                  LOG.atError()
                      .setMessage("Failed to commit trie log storage transaction after {} tries")
                      .addArgument(tries)
                      .setCause(e)
                      .log();
                  throw e;
                } else {
                  LOG.atWarn()
                      .setMessage("Failed to commit trie log storage transaction, retry #{}")
                      .addArgument(tries)
                      .log();
                  LOG.atDebug().setCause(e).log();
                }
              }
            }
            return newTx;
          });
    }
  }
}
