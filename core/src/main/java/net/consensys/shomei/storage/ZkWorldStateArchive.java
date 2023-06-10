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
import net.consensys.shomei.storage.worldstate.WorldStateStorage;
import net.consensys.shomei.worldview.ZkEvmWorldState;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

import org.hyperledger.besu.datatypes.Hash;

public class ZkWorldStateArchive implements Closeable {

  private final TrieLogManager trieLogManager;
  private final TraceManager traceManager;
  private final WorldStateStorage headWorldStateStorage;
  private final ZkEvmWorldState headWorldState;

  public ZkWorldStateArchive(final StorageProvider storageProvider) {
    this.trieLogManager = storageProvider.getTrieLogManager();
    this.traceManager = storageProvider.getTraceManager();
    this.headWorldStateStorage = storageProvider.getWorldStateStorage();
    this.headWorldState = new ZkEvmWorldState(headWorldStateStorage, traceManager::saveTrace);
  }

  public Optional<ZkEvmWorldState> getWorldState(long blockNumber, boolean isPersistable) {
    return Optional.empty();
  }

  public boolean isWorldStateAvailable(final long blockNumber) {
    return false;
  }

  public void importBlock(
      final TrieLogObserver.TrieLogIdentifier trieLogIdentifier,
      final boolean shouldGenerateTrace) {
    // import block, optionally cache a snapshot and generate trace if not too far behind head
  }

  public ZkEvmWorldState getHeadWorldState() {
    return headWorldState;
  }

  public long getCurrentBlockNumber() {
    return 0;
  }

  public Hash getCurrentBlockHash() {
    return null;
  }

  public TrieLogManager getTrieLogManager() {
    return trieLogManager;
  }

  public TraceManager getTraceManager() {
    return traceManager;
  }

  @Override
  public void close() throws IOException {
    // close all storages
  }
}
