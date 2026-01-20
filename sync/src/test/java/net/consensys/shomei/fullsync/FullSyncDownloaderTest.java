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
package net.consensys.shomei.fullsync;

import static net.consensys.shomei.fullsync.TrieLogBlockingQueue.INITIAL_SYNC_BLOCK_NUMBER_RANGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import org.hyperledger.besu.datatypes.Hash;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.consensys.shomei.fullsync.rules.FullSyncRules;
import net.consensys.shomei.observer.TrieLogObserver.TrieLogIdentifier;
import net.consensys.shomei.rpc.client.GetRawTrieLogClient;
import net.consensys.shomei.storage.ZkWorldStateArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FullSyncDownloaderTest {

  @Mock ZkWorldStateArchive zkWorldStateArchive;

  private FullSyncDownloader fullSyncDownloader;
  private TrieLogBlockingQueue blockingQueue;

  @BeforeEach
  public void setup() {
    blockingQueue =
        spy(
            new TrieLogBlockingQueue(
                INITIAL_SYNC_BLOCK_NUMBER_RANGE * 2,
                Collections.emptyList(),
                zkWorldStateArchive::getCurrentBlockNumber,
                aLong -> {
                  try {
                    fullSyncDownloader.stop(); // force stop the downloader
                    return CompletableFuture.completedFuture(false);
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                }));
    fullSyncDownloader =
        new FullSyncDownloader(
            blockingQueue,
            zkWorldStateArchive,
            Mockito.mock(GetRawTrieLogClient.class),
            new FullSyncRules(true, 2, 0));
  }

  @Test
  public void testNotTriggerImportWhenTrieLogMissing() throws Exception {
    fullSyncDownloader.startFullSync();
    Mockito.verify(zkWorldStateArchive, Mockito.never())
        .importBlock(
            Mockito.any(TrieLogIdentifier.class), Mockito.anyBoolean(), Mockito.anyBoolean());
  }

  @Test
  public void testNotTriggerTraceGenerationBeforeFirstBlockNumber() throws Exception {
    fullSyncDownloader =
        new FullSyncDownloader(
            blockingQueue,
            zkWorldStateArchive,
            Mockito.mock(GetRawTrieLogClient.class),
            new FullSyncRules(true, 1, 0));
    List<TrieLogIdentifier> trieLogIdentifiers =
        List.of(new TrieLogIdentifier(1L, Hash.EMPTY, true));
    fullSyncDownloader.addTrieLogs(trieLogIdentifiers);
    fullSyncDownloader.onNewBesuHeadReceived(trieLogIdentifiers);
    fullSyncDownloader.startFullSync();
    Mockito.verify(zkWorldStateArchive, times(1))
        .importBlock(Mockito.any(TrieLogIdentifier.class), eq(true), eq(true));
  }

  @Test
  public void testTriggerTraceGenerationAfterFirstBlockNumber() throws Exception {
    fullSyncDownloader =
        new FullSyncDownloader(
            blockingQueue,
            zkWorldStateArchive,
            Mockito.mock(GetRawTrieLogClient.class),
            new FullSyncRules(true, 2, 0));
    List<TrieLogIdentifier> trieLogIdentifiers =
        List.of(new TrieLogIdentifier(1L, Hash.EMPTY, true));
    fullSyncDownloader.addTrieLogs(trieLogIdentifiers);
    fullSyncDownloader.onNewBesuHeadReceived(trieLogIdentifiers);
    fullSyncDownloader.startFullSync();
    Mockito.verify(zkWorldStateArchive, times(1))
        .importBlock(Mockito.any(TrieLogIdentifier.class), eq(false), eq(false));
  }

  @Test
  public void testTriggerImportWhenTrieLogAvailableFromTrieLogShipping() throws Exception {
    List<TrieLogIdentifier> trieLogIdentifiers =
        List.of(new TrieLogIdentifier(1L, Hash.EMPTY, true));
    fullSyncDownloader.addTrieLogs(trieLogIdentifiers);
    fullSyncDownloader.onNewBesuHeadReceived(trieLogIdentifiers);
    fullSyncDownloader.startFullSync();
    Mockito.verify(zkWorldStateArchive, times(1))
        .importBlock(
            Mockito.any(TrieLogIdentifier.class), Mockito.anyBoolean(), Mockito.anyBoolean());
  }

  @Test
  public void testNotTriggerImportWhenTrieLogAvailableButAlreadyImported() throws Exception {
    List<TrieLogIdentifier> trieLogIdentifiers =
        List.of(new TrieLogIdentifier(0L, Hash.EMPTY, true));
    fullSyncDownloader.addTrieLogs(trieLogIdentifiers);
    fullSyncDownloader.onNewBesuHeadReceived(trieLogIdentifiers);
    fullSyncDownloader.startFullSync();
    Mockito.verify(zkWorldStateArchive, never())
        .importBlock(
            Mockito.any(TrieLogIdentifier.class), Mockito.anyBoolean(), Mockito.anyBoolean());
  }

  @Test
  public void testNotTriggerImportWhenTrieLogTooFarFromHead() throws Exception {
    List<TrieLogIdentifier> trieLogIdentifiers =
        List.of(new TrieLogIdentifier(500L, Hash.EMPTY, true));
    fullSyncDownloader.addTrieLogs(trieLogIdentifiers);
    fullSyncDownloader.onNewBesuHeadReceived(trieLogIdentifiers);
    fullSyncDownloader.startFullSync();
    Mockito.verify(zkWorldStateArchive, never())
        .importBlock(
            Mockito.any(TrieLogIdentifier.class), Mockito.anyBoolean(), Mockito.anyBoolean());
  }

  @Test
  public void testGetEstimateDistanceFromTheHead() {
    assertThat(fullSyncDownloader.getEstimateDistanceFromTheBesuHead()).isEqualTo(-1);
    List<TrieLogIdentifier> trieLogIdentifiers =
        List.of(new TrieLogIdentifier(500L, Hash.EMPTY, true));
    fullSyncDownloader.onNewBesuHeadReceived(trieLogIdentifiers);
    assertThat(fullSyncDownloader.getEstimateDistanceFromTheBesuHead()).isEqualTo(500L);
  }

  @Test
  public void onlyUpdateWithHigherHead() {
    assertThat(fullSyncDownloader.getEstimateBesuHeadBlockNumber()).isEmpty();
    fullSyncDownloader.onNewBesuHeadReceived(
        List.of(new TrieLogIdentifier(501L, Hash.EMPTY, true)));
    assertThat(fullSyncDownloader.getEstimateBesuHeadBlockNumber()).contains(501L);
    fullSyncDownloader.onNewBesuHeadReceived(List.of(new TrieLogIdentifier(2L, Hash.EMPTY, true)));
    assertThat(fullSyncDownloader.getEstimateBesuHeadBlockNumber()).contains(501L);
  }

  @Test
  public void getEstimateHeadBlockNumber() {
    assertThat(fullSyncDownloader.getEstimateBesuHeadBlockNumber()).isEmpty();
    fullSyncDownloader.onNewBesuHeadReceived(List.of(new TrieLogIdentifier(1L, Hash.EMPTY, true)));
    assertThat(fullSyncDownloader.getEstimateBesuHeadBlockNumber()).contains(1L);
    fullSyncDownloader.onNewBesuHeadReceived(List.of(new TrieLogIdentifier(2L, Hash.EMPTY, true)));
    assertThat(fullSyncDownloader.getEstimateBesuHeadBlockNumber()).contains(2L);
  }

  @Test
  public void onTrieLogsReceivedUpdateEstimateHead() {
    assertThat(fullSyncDownloader.getEstimateBesuHeadBlockNumber()).isEmpty();
    fullSyncDownloader.onNewBesuHeadReceived(List.of(new TrieLogIdentifier(1L, Hash.EMPTY, true)));
    assertThat(fullSyncDownloader.getEstimateBesuHeadBlockNumber()).contains(1L);
  }
}
