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

package net.consensys.shomei.fullsync;

import static net.consensys.shomei.fullsync.TrieLogBlockingQueue.INITIAL_SYNC_BLOCK_NUMBER_RANGE;

import net.consensys.shomei.fullsync.rules.BlockConfirmationMinRequirementValidator;
import net.consensys.shomei.fullsync.rules.BlockImportValidator;
import net.consensys.shomei.fullsync.rules.FinalizedBlockLimitValidator;
import net.consensys.shomei.fullsync.rules.FullSyncRules;
import net.consensys.shomei.observer.TrieLogObserver;
import net.consensys.shomei.rpc.client.GetRawTrieLogClient;
import net.consensys.shomei.storage.ZkWorldStateArchive;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.common.annotations.VisibleForTesting;
import io.vertx.core.AbstractVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The FullSyncDownloader class is responsible for downloading data during a full synchronization
 * process. It retrieves trie logs and associated data from a remote Besu node, stores them and
 * import them.
 */
@SuppressWarnings("FutureReturnValueIgnored")
public class FullSyncDownloader extends AbstractVerticle implements TrieLogObserver {

  private static final Logger LOG = LoggerFactory.getLogger(FullSyncDownloader.class);

  private final ExecutorService importBlockTask = Executors.newSingleThreadExecutor();
  private final TrieLogBlockingQueue blockQueue;

  private final ZkWorldStateArchive zkWorldStateArchive;

  private final GetRawTrieLogClient getRawTrieLog;

  private final FullSyncRules fullSyncRules;

  private CompletableFuture<Void> completableFuture;
  private Optional<Long> estimateBesuHeadBlockNumber = Optional.empty();

  public FullSyncDownloader(
      final ZkWorldStateArchive zkWorldStateArchive,
      final GetRawTrieLogClient getRawTrieLog,
      final FullSyncRules fullSyncRules) {
    this.zkWorldStateArchive = zkWorldStateArchive;
    this.getRawTrieLog = getRawTrieLog;
    this.fullSyncRules = fullSyncRules;
    this.blockQueue = createQueue();
  }

  @VisibleForTesting
  public FullSyncDownloader(
      final TrieLogBlockingQueue blockQueue,
      final ZkWorldStateArchive zkWorldStateArchive,
      final GetRawTrieLogClient getRawTrieLog,
      final FullSyncRules fullSyncRules) {
    this.blockQueue = blockQueue;
    this.zkWorldStateArchive = zkWorldStateArchive;
    this.getRawTrieLog = getRawTrieLog;
    this.fullSyncRules = fullSyncRules;
  }

  private TrieLogBlockingQueue createQueue() {
    final List<BlockImportValidator> blockImportValidators = new ArrayList<>();
    if (fullSyncRules.getMinConfirmationsBeforeImporting() > 0) {
      blockImportValidators.add(
          new BlockConfirmationMinRequirementValidator(
              zkWorldStateArchive::getCurrentBlockNumber,
              this::getEstimateBesuHeadBlockNumber,
              fullSyncRules::getMinConfirmationsBeforeImporting));
    }
    if (fullSyncRules.isEnableFinalizedBlockLimit()) {
      blockImportValidators.add(
          new FinalizedBlockLimitValidator(
              fullSyncRules::getFinalizedBlockNumberLimit,
              zkWorldStateArchive::getCurrentBlockNumber));
    }
    return new TrieLogBlockingQueue(
        INITIAL_SYNC_BLOCK_NUMBER_RANGE * 2,
        blockImportValidators,
        zkWorldStateArchive::getCurrentBlockNumber,
        this::findMissingTrieLogFromBesu);
  }

  @Override
  public void start() {
    LOG.atInfo().setMessage("Starting fullsync downloader service").log();
    importBlockTask.execute(this::startFullSync);
  }

  public void startFullSync() {
    LOG.atInfo().setMessage("Fullsync downloader service started").log();
    completableFuture = new CompletableFuture<>();
    while (!completableFuture.isDone()) {
      final TrieLogIdentifier trieLogId = blockQueue.waitForNewElement();
      if (trieLogId != null) {
        importBlock(trieLogId);
      }
    }
  }

  public void importBlock(final TrieLogIdentifier trieLogId) {
    if (trieLogId != null) {
      try {
        final boolean isTraceGenerationNeeded = isTraceGenerationAllowed(trieLogId.blockNumber());
        final boolean isSnapshotGenerationNeeded =
            isSnapshotGenerationAllowed(trieLogId.blockNumber());
        zkWorldStateArchive.importBlock(
            trieLogId, isTraceGenerationNeeded, isSnapshotGenerationNeeded);
        if (trieLogId.blockHash().equals(zkWorldStateArchive.getCurrentBlockHash())) {
          final boolean isFullBlockImportLogAllowed =
              isFullBlockImportLogAllowed(trieLogId.blockNumber());
          if (isFullBlockImportLogAllowed) {
            LOG.atInfo()
                .setMessage("Imported block {} ({})")
                .addArgument(trieLogId.blockNumber())
                .addArgument(trieLogId.blockHash())
                .log();
          } else {
            if (trieLogId.blockNumber() % INITIAL_SYNC_BLOCK_NUMBER_RANGE == 0) {
              LOG.atInfo()
                  .setMessage("Block import progress: {}:{}")
                  .addArgument(trieLogId.blockNumber())
                  .addArgument(trieLogId.blockHash())
                  .log();
            }
          }
        } else {
          throw new RuntimeException(
              "failed to import block %d".formatted(trieLogId.blockNumber()));
        }
      } catch (Exception e) {
        LOG.atError()
            .setMessage("Exception during import block {} ({}) : {}")
            .addArgument(trieLogId.blockNumber())
            .addArgument(trieLogId.blockHash())
            .addArgument(e.getMessage())
            .log();
      }
    }
  }

  @Override
  public void stop() throws Exception {
    LOG.atInfo().setMessage("Fullsync downloader service stopped").log();
    blockQueue.stop();
    completableFuture.complete(null);
    super.stop();
  }

  /**
   * Estimates the distance between the local head of a Shomei and the head of the network thanks to
   * the last trielog received from Besu.
   *
   * @return The estimated distance.
   */
  public long getEstimateDistanceFromTheBesuHead() {
    return estimateBesuHeadBlockNumber.orElse(-1L) - zkWorldStateArchive.getCurrentBlockNumber();
  }

  public Optional<Long> getEstimateBesuHeadBlockNumber() {
    return estimateBesuHeadBlockNumber;
  }

  public FullSyncRules getFullSyncRules() {
    return fullSyncRules;
  }

  @Override
  public void onNewBesuHeadReceived(final List<TrieLogObserver.TrieLogIdentifier> trieLogIds) {
    // Update the head only if the new besu head is higher than the previous one. receiving a trie
    // log
    // allows us to estimate the head of Besu.
    trieLogIds.stream()
        .max(Comparator.comparingLong(TrieLogIdentifier::blockNumber))
        .map(TrieLogIdentifier::blockNumber)
        .filter(onNewHead -> onNewHead > estimateBesuHeadBlockNumber.orElse(-1L))
        .ifPresent(aLong -> estimateBesuHeadBlockNumber = Optional.of(aLong));
  }

  public void addTrieLogs(final List<TrieLogObserver.TrieLogIdentifier> trieLogIds) {
    trieLogIds.forEach(
        trieLogIdentifier -> {
          if (isValidTrieLog(trieLogIdentifier)) {
            LOG.atDebug().setMessage("received trie log {} ").addArgument(trieLogIdentifier).log();
            blockQueue.offer(trieLogIdentifier);
          } else {
            throw new RuntimeException("invalid trie log received");
          }
        });
  }

  private boolean isValidTrieLog(final TrieLogIdentifier trieLogIdentifier) {
    if (fullSyncRules.getFinalizedBlockNumberLimit().isPresent()
        && fullSyncRules.getFinalizedBlockHashLimit().isPresent()) {
      if (trieLogIdentifier
          .blockNumber()
          .equals(fullSyncRules.getFinalizedBlockNumberLimit().get())) {
        return trieLogIdentifier
            .blockHash()
            .equals(fullSyncRules.getFinalizedBlockHashLimit().get());
      }
    }
    return true;
  }

  private boolean isBlockNumberBeyondTraceGenerationLimit(final long blockNumberToImport) {
    return blockNumberToImport >= fullSyncRules.getTraceStartBlockNumber();
  }

  private boolean isConfiguredBlockLimitReached(final long blockNumberToImport) {
    return fullSyncRules
        .getFinalizedBlockNumberLimit()
        .map(limit -> limit.equals(blockNumberToImport))
        .orElse(false);
  }

  private boolean isTraceGenerationAllowed(final long blockNumberToImport) {
    return fullSyncRules.isTraceGenerationEnabled()
        && isBlockNumberBeyondTraceGenerationLimit(blockNumberToImport);
  }

  private boolean isSnapshotGenerationAllowed(final long blockNumberToImport) {
    final boolean isBlockLimitConfigured = fullSyncRules.getFinalizedBlockNumberLimit().isPresent();
    final boolean isConfiguredBlockLimitReached =
        isConfiguredBlockLimitReached(blockNumberToImport);
    return (!isBlockLimitConfigured && isTraceGenerationAllowed(blockNumberToImport))
        || (isBlockLimitConfigured && isConfiguredBlockLimitReached);
  }

  private boolean isFullBlockImportLogAllowed(final long blockNumberToImport) {
    return isTraceGenerationAllowed(blockNumberToImport)
        || isConfiguredBlockLimitReached(blockNumberToImport);
  }

  /**
   * Checks if the trie log for the next block is available. If the trie log is not available, it
   * indicates that some trie logs are missing. In such cases, the component will calculate the
   * distance between the nearest available trie log and the current local head of Shomei. This
   * distance represents the number of trie logs that need to be retrieved to complete the missing
   * range.
   *
   * <p>If Besu returns the trie logs, they are immediately imported. Otherwise, the component waits
   * before retrying to check for the trie log availability.
   *
   * <p>For example, suppose we have the trie log for block 6, and our current local head is block
   * 3. We can determine that trie logs 4 and 5 are missing. In this case, we will immediately
   * request trie logs 4 and 5 from Besu to import them along with trie log 6.
   *
   * <p>It's important to note that this scenario occurs when the client detects missing trie logs
   * and attempts to retrieve them. However, Besu can also voluntarily send trie logs, and if it
   * sends the trie log for the next block, Shomei stops waiting and instantly imports that trie
   * log.
   */
  private CompletableFuture<Boolean> findMissingTrieLogFromBesu(final long missingTrieLogCount) {
    final long startBlockNumber = zkWorldStateArchive.getCurrentBlockNumber() + 1;
    final long endBlockNumber = zkWorldStateArchive.getCurrentBlockNumber() + missingTrieLogCount;
    return getRawTrieLog
        .getTrieLog(startBlockNumber, endBlockNumber)
        .whenComplete(
            (trieLogIdentifiers, throwable) -> {
              if (throwable == null) {
                if (!trieLogIdentifiers.isEmpty()) {
                  addTrieLogs(trieLogIdentifiers);
                  onNewBesuHeadReceived(trieLogIdentifiers);
                }
              }
            })
        .thenApply(z -> z.size() > 0);
  }
}
