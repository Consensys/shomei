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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import net.consensys.shomei.observer.TrieLogObserver.TrieLogIdentifier;
import net.consensys.shomei.rpc.client.GetRawTrieLogClient;
import net.consensys.shomei.storage.ZkWorldStateArchive;

import java.util.List;

import org.hyperledger.besu.datatypes.Hash;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FullSyncDownloaderTest {

  @Mock ZkWorldStateArchive zkWorldStateArchive;

  FullSyncDownloader fullSyncDownloader;

  @Before
  public void setup() {
    TrieLogBlockingQueue blockingQueue =
        spy(
            new TrieLogBlockingQueue(
                INITIAL_SYNC_BLOCK_NUMBER_RANGE * 2,
                zkWorldStateArchive::getCurrentBlockNumber,
                aLong -> {
                  try {
                    fullSyncDownloader.stop(); // force stop the downloader
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                }));
    fullSyncDownloader =
        new FullSyncDownloader(
            blockingQueue, zkWorldStateArchive, Mockito.mock(GetRawTrieLogClient.class));
    doThrow(new RuntimeException()).when(blockingQueue).startWaiting();
  }

  @Test
  public void testNotTriggerImportWhenTrieLogMissing() throws Exception {
    fullSyncDownloader.startFullSync();
    Mockito.verify(zkWorldStateArchive, Mockito.never())
        .importBlock(Mockito.any(TrieLogIdentifier.class), Mockito.anyBoolean());
  }

  @Test
  public void testTriggerImportWhenTrieLogAvailableFromTrieLogShipping() throws Exception {
    fullSyncDownloader.onTrieLogsReceived(List.of(new TrieLogIdentifier(1L, Hash.EMPTY, true)));
    fullSyncDownloader.startFullSync();
    Mockito.verify(zkWorldStateArchive, times(1))
        .importBlock(Mockito.any(TrieLogIdentifier.class), Mockito.anyBoolean());
  }

  @Test
  public void testNotTriggerImportWhenTrieLogAvailableButAlreadyImported() throws Exception {
    fullSyncDownloader.onTrieLogsReceived(List.of(new TrieLogIdentifier(0L, Hash.EMPTY, true)));
    fullSyncDownloader.startFullSync();
    Mockito.verify(zkWorldStateArchive, never())
        .importBlock(Mockito.any(TrieLogIdentifier.class), Mockito.anyBoolean());
  }

  @Test
  public void testNotTriggerImportWhenTrieLogTooFarFromHead() throws Exception {
    fullSyncDownloader.onTrieLogsReceived(List.of(new TrieLogIdentifier(500L, Hash.EMPTY, true)));
    fullSyncDownloader.startFullSync();
    Mockito.verify(zkWorldStateArchive, never())
        .importBlock(Mockito.any(TrieLogIdentifier.class), Mockito.anyBoolean());
  }

  @Test
  public void testGetEstimateDistanceFromTheHead() {
    assertThat(fullSyncDownloader.getEstimateDistanceFromTheHead()).isEqualTo(-1);
    fullSyncDownloader.onTrieLogsReceived(List.of(new TrieLogIdentifier(500L, Hash.EMPTY, true)));
    assertThat(fullSyncDownloader.getEstimateDistanceFromTheHead()).isEqualTo(500L);
  }

  @Test
  public void testIsTooFarFromTheHead() {
    assertThat(fullSyncDownloader.isTooFarFromTheHead()).isTrue();
    fullSyncDownloader.onTrieLogsReceived(List.of(new TrieLogIdentifier(501L, Hash.EMPTY, true)));
    assertThat(fullSyncDownloader.isTooFarFromTheHead()).isTrue();
    fullSyncDownloader.onTrieLogsReceived(List.of(new TrieLogIdentifier(500L, Hash.EMPTY, true)));
    assertThat(fullSyncDownloader.isTooFarFromTheHead()).isFalse();
  }

  @Test
  public void getEstimateHeadBlockNumber() {
    assertThat(fullSyncDownloader.getEstimateHeadBlockNumber()).isEmpty();
    fullSyncDownloader.onTrieLogsReceived(List.of(new TrieLogIdentifier(1L, Hash.EMPTY, true)));
    assertThat(fullSyncDownloader.getEstimateHeadBlockNumber()).contains(1L);
    fullSyncDownloader.onTrieLogsReceived(List.of(new TrieLogIdentifier(2L, Hash.EMPTY, true)));
    assertThat(fullSyncDownloader.getEstimateHeadBlockNumber()).contains(2L);
  }

  @Test
  public void onTrieLogsReceivedUpdateEstimateHead() {
    assertThat(fullSyncDownloader.getEstimateHeadBlockNumber()).isEmpty();
    fullSyncDownloader.onTrieLogsReceived(List.of(new TrieLogIdentifier(1L, Hash.EMPTY, true)));
    assertThat(fullSyncDownloader.getEstimateHeadBlockNumber()).contains(1L);
  }
}
