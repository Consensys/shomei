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

import net.consensys.shomei.observer.TrieLogObserver.TrieLogIdentifier;
import net.consensys.shomei.storage.worldstate.InMemoryWorldStateStorage;
import net.consensys.shomei.storage.worldstate.WorldStateStorage;
import net.consensys.shomei.trie.proof.Trace;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;

public class InMemoryStorageProvider implements StorageProvider {
  @Override
  public WorldStateStorage getWorldStateStorage() {
    return new InMemoryWorldStateStorage();
  }

  @Override
  public TraceManager getTraceManager() {
    return new TraceManager() {
      private final HashMap<Long, List<Trace>> traceStorage = new HashMap<>();
      private final HashMap<Long, Hash> zkStateRootStorage = new HashMap<>();

      @Override
      public Optional<Bytes> getTrace(final long blockNumber) {
        return Optional.ofNullable(traceStorage.get(blockNumber)).map(Trace::serialize);
      }

      @Override
      public TraceManager saveTrace(final long blockNumber, final List<Trace> traces) {
        traceStorage.put(blockNumber, traces);
        return this;
      }

      @Override
      public Optional<Hash> getZkStateRootHash(final long blockNumber) {
        return Optional.of(zkStateRootStorage.get(blockNumber));
      }

      @Override
      public TraceManager saveZkStateRootHash(final long blockNumber, final Hash stateRoot) {
        zkStateRootStorage.put(blockNumber, stateRoot);
        return this;
      }

      @Override
      public TraceManager commit() {
        // no-op
        return this;
      }
    };
  }

  @Override
  public TrieLogManager getTrieLogManager() {
    HashMap<Long, Bytes> trieLogStorage = new HashMap<>();

    return new TrieLogManager() {
      @Override
      public TrieLogManager saveTrieLog(
          final TrieLogIdentifier trieLogIdentifier, final Bytes rawTrieLogLayer) {
        trieLogStorage.put(trieLogIdentifier.blockNumber(), rawTrieLogLayer);
        return this;
      }

      @Override
      public Optional<Bytes> getTrieLog(final long blockNumber) {
        return Optional.ofNullable(trieLogStorage.get(blockNumber));
      }

      @Override
      public void commitTrieLogStorage() {
        // no-op
      }
    };
  }
}
