/*
 * Copyright Consensys Software Inc., 2025
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

import java.util.Optional;

import com.google.common.primitives.Longs;
import net.consensys.shomei.observer.TrieLogObserver;
import net.consensys.shomei.services.storage.api.KeyValueStorage;
import net.consensys.shomei.services.storage.api.KeyValueStorageTransaction;
import org.apache.tuweni.bytes.Bytes;

public interface TrieLogManager {

  Optional<Bytes> getTrieLog(final long blockNumber);

  TrieLogManagerUpdater updater();

  class TrieLogManagerImpl implements TrieLogManager {
    private final KeyValueStorage trieLogStorage;

    public TrieLogManagerImpl(final KeyValueStorage trieLogStorage) {
      this.trieLogStorage = trieLogStorage;
    }

    @Override
    public Optional<Bytes> getTrieLog(final long blockNumber) {
      return trieLogStorage.get(Longs.toByteArray(blockNumber)).map(Bytes::wrap);
    }

    @Override
    public TrieLogManagerUpdater updater() {
      return new TrieLogManagerUpdater(trieLogStorage.startTransaction());
    }
  }

  class TrieLogManagerUpdater {
    private final KeyValueStorageTransaction transaction;

    public TrieLogManagerUpdater(final KeyValueStorageTransaction transaction) {
      this.transaction = transaction;
    }

    public TrieLogManagerUpdater saveTrieLog(
        final TrieLogObserver.TrieLogIdentifier trieLogIdentifier, final Bytes rawTrieLogLayer) {
      transaction.put(
          Longs.toByteArray(trieLogIdentifier.blockNumber()), rawTrieLogLayer.toArrayUnsafe());
      return this;
    }

    public void commit() {
      transaction.commit();
    }
  }
}
