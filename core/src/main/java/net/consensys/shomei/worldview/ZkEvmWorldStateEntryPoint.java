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

package net.consensys.shomei.worldview;

import net.consensys.shomei.exception.MissingTrieLogException;
import net.consensys.shomei.observer.TrieLogObserver;
import net.consensys.shomei.storage.WorldStateRepository;
import net.consensys.shomei.trielog.TrieLogLayer;
import net.consensys.shomei.trielog.TrieLogLayerConverter;

import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;

import com.google.common.annotations.VisibleForTesting;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZkEvmWorldStateEntryPoint implements TrieLogObserver {

  private static final Logger LOG = LoggerFactory.getLogger(ZkEvmWorldStateEntryPoint.class);

  private static final int INITIAL_SYNC_BLOCK_NUMBER_RANGE = 2500;

  private final ZKEvmWorldState currentWorldState;

  private final TrieLogLayerConverter trieLogLayerConverter;

  private final WorldStateRepository worldStateStorage;

  private final Queue<TrieLogIdentifier> blockQueue = new PriorityBlockingQueue<>();
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  private volatile boolean isProcessing = false;

  public ZkEvmWorldStateEntryPoint(final WorldStateRepository worldStateStorage) {
    this.worldStateStorage = worldStateStorage;
    this.currentWorldState = new ZKEvmWorldState(worldStateStorage);
    this.trieLogLayerConverter = new TrieLogLayerConverter(worldStateStorage);
  }

  private void importBlock(final TrieLogIdentifier trieLogId) throws MissingTrieLogException {
    Optional<TrieLogLayer> trieLog =
        worldStateStorage
            .getTrieLog(trieLogId.blockNumber())
            .map(RLP::input)
            .map(trieLogLayerConverter::decodeTrieLog);
    if (trieLog.isPresent()) {
      applyTrieLog(trieLogId.blockNumber(), trieLogId.isInitialSync(), trieLog.get());
    } else {
      throw new MissingTrieLogException(trieLogId.blockNumber());
    }
  }

  @VisibleForTesting
  public void applyTrieLog(
      final long newBlockNumber, final boolean syncing, final TrieLogLayer trieLogLayer) {
    currentWorldState.getAccumulator().rollForward(trieLogLayer);
    currentWorldState.commit(newBlockNumber, trieLogLayer.getBlockHash(), syncing);
  }

  public Hash getCurrentRootHash() {
    return currentWorldState.getStateRootHash();
  }

  @Override
  public synchronized void onTrieLogAdded(final TrieLogIdentifier trieLogId) {
    LOG.atDebug().setMessage("receive trie log {} ").addArgument(trieLogId).log();
    blockQueue.offer(trieLogId);
    if (!isProcessing) {
      isProcessing = true;
      executor.execute(this::processQueue);
    }
  }

  public void processQueue() {
    boolean foundMissingTrieLog = false;
    while (!blockQueue.isEmpty() && !foundMissingTrieLog) {
      if (blockQueue.element().blockNumber() == currentWorldState.getBlockNumber() + 1) {
        final TrieLogIdentifier trieLogId = blockQueue.poll();
        try {
          importBlock(Objects.requireNonNull(trieLogId));
          if (currentWorldState
              .getBlockHash()
              .equals(Objects.requireNonNull(trieLogId.blockHash()))) {
            if (trieLogId.isInitialSync()) {
              if (trieLogId.blockNumber() % INITIAL_SYNC_BLOCK_NUMBER_RANGE == 0) {
                LOG.atInfo()
                    .setMessage("Block import progress: {}:{}")
                    .addArgument(trieLogId.blockNumber())
                    .addArgument(trieLogId.blockHash())
                    .log();
              }
            } else {
              LOG.atInfo()
                  .setMessage("Imported block {} ({})")
                  .addArgument(trieLogId.blockNumber())
                  .addArgument(trieLogId.blockHash())
                  .log();
            }
          } else {
            LOG.atError()
                .setMessage("Failed to import block {} ({})")
                .addArgument(trieLogId.blockNumber())
                .addArgument(trieLogId.blockHash())
                .log();
          }
        } catch (Exception e) {
          LOG.atError()
              .setMessage("Exception during import block {} ({}) : {}")
              .addArgument(trieLogId.blockNumber())
              .addArgument(trieLogId.blockHash())
              .addArgument(e.getMessage())
              .log();
        }
      } else if (blockQueue.element().blockNumber() <= currentWorldState.getBlockNumber()) {
        final TrieLogIdentifier removed = blockQueue.remove();
        LOG.atInfo()
            .setMessage("Ignore already applied trie log for block {} ({})")
            .addArgument(removed.blockNumber())
            .addArgument(removed.blockHash())
            .log();
      } else {
        LOG.atInfo().setMessage("Detect missing trie log, waiting ...").log();
        foundMissingTrieLog = true;
      }
    }
    isProcessing = false;
  }
}
