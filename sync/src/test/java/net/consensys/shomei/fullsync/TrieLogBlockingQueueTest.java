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

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.fullsync.rules.BlockImportValidator;
import net.consensys.shomei.observer.TrieLogObserver.TrieLogIdentifier;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
public class TrieLogBlockingQueueTest {

  private ExecutorService executor;

  @BeforeEach
  public void setUp() {
    executor = Executors.newSingleThreadExecutor();
  }

  @AfterEach
  public void tearDown() {
    executor.shutdownNow();
  }

  // ===========================================================================
  // Helpers
  // ===========================================================================

  private static TrieLogIdentifier makeTrieLog(final long blockNumber) {
    return new TrieLogIdentifier(blockNumber, Hash.wrap(Bytes32.wrap(Bytes.random(32))), false);
  }

  private static BlockImportValidator alwaysAllow() {
    return () -> true;
  }

  private static BlockImportValidator alwaysBlock() {
    return () -> false;
  }

  // ===========================================================================
  // OFFER / CAPACITY
  // ===========================================================================

  @Test
  public void testOffer_withinCapacity() {
    final AtomicLong head = new AtomicLong(0);
    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    10,
                    List.of(alwaysAllow()),
                    head::get,
                    dist -> CompletableFuture.completedFuture(false));

    assertThat(queue.offer(makeTrieLog(1))).isTrue();
    assertThat(queue.offer(makeTrieLog(2))).isTrue();
    assertThat(queue.offer(makeTrieLog(3))).isTrue();
    assertThat(queue).hasSize(3);
  }

  @Test
  public void testOffer_atCapacity_higherPriorityReplacesLowest() {
    final AtomicLong head = new AtomicLong(0);
    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    3,
                    List.of(alwaysAllow()),
                    head::get,
                    dist -> CompletableFuture.completedFuture(false));

    queue.offer(makeTrieLog(1));
    queue.offer(makeTrieLog(2));
    queue.offer(makeTrieLog(3));
    assertThat(queue).hasSize(3);

    // Block 4 has higher priority (greater block number), should evict block 1
    assertThat(queue.offer(makeTrieLog(4))).isTrue();
    assertThat(queue).hasSize(3);

    // Block 1 should have been evicted; smallest remaining is block 2
    assertThat(queue.peek().blockNumber()).isEqualTo(2);
  }

  @Test
  public void testOffer_atCapacity_lowerPriorityRejected() {
    final AtomicLong head = new AtomicLong(0);
    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    3,
                    List.of(alwaysAllow()),
                    head::get,
                    dist -> CompletableFuture.completedFuture(false));

    queue.offer(makeTrieLog(5));
    queue.offer(makeTrieLog(6));
    queue.offer(makeTrieLog(7));

    // Block 3 is lower priority than everything in queue — should be rejected
    assertThat(queue.offer(makeTrieLog(3))).isFalse();
    assertThat(queue).hasSize(3);
    assertThat(queue.peek().blockNumber()).isEqualTo(5);
  }

  @Test
  public void testOffer_duplicateBlockNumbers() {
    final AtomicLong head = new AtomicLong(0);
    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    10,
                    List.of(alwaysAllow()),
                    head::get,
                    dist -> CompletableFuture.completedFuture(false));

    queue.offer(makeTrieLog(5));
    queue.offer(makeTrieLog(5));
    assertThat(queue).hasSize(2);
  }

  // ===========================================================================
  // STOP
  // ===========================================================================

  @Test
  public void testStop_causesWaitForNewElementToReturnNull() throws Exception {
    final AtomicLong head = new AtomicLong(0);
    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    10,
                    List.of(alwaysAllow()),
                    head::get,
                    dist -> CompletableFuture.completedFuture(false));

    Future<TrieLogIdentifier> future = executor.submit(queue::waitForNewElement);

    Thread.sleep(500);

    queue.stop();

    TrieLogIdentifier result = future.get(5, TimeUnit.SECONDS);
    assertThat(result).isNull();
  }

  @Test
  public void testStop_idempotent() {
    final AtomicLong head = new AtomicLong(0);
    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    10,
                    List.of(alwaysAllow()),
                    head::get,
                    dist -> CompletableFuture.completedFuture(false));

    queue.stop();
    queue.stop();
  }

  // ===========================================================================
  // WAIT FOR NEW ELEMENT — happy path (distance == 1)
  // ===========================================================================

  @Test
  public void testWaitForNewElement_returnsWhenNextBlockAvailable() throws Exception {
    final AtomicLong head = new AtomicLong(5);
    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    10,
                    List.of(alwaysAllow()),
                    head::get,
                    dist -> CompletableFuture.completedFuture(false));

    queue.offer(makeTrieLog(6));

    Future<TrieLogIdentifier> future = executor.submit(queue::waitForNewElement);

    TrieLogIdentifier result = future.get(5, TimeUnit.SECONDS);
    assertThat(result).isNotNull();
    assertThat(result.blockNumber()).isEqualTo(6);
    assertThat(queue).isEmpty();
  }

  @Test
  public void testWaitForNewElement_skipsAlreadyImportedBlocks() throws Exception {
    final AtomicLong head = new AtomicLong(10);
    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    10,
                    List.of(alwaysAllow()),
                    head::get,
                    dist -> CompletableFuture.completedFuture(false));

    queue.offer(makeTrieLog(8));
    queue.offer(makeTrieLog(9));
    queue.offer(makeTrieLog(10));
    queue.offer(makeTrieLog(11));

    Future<TrieLogIdentifier> future = executor.submit(queue::waitForNewElement);

    TrieLogIdentifier result = future.get(5, TimeUnit.SECONDS);
    assertThat(result).isNotNull();
    assertThat(result.blockNumber()).isEqualTo(11);
  }

  // ===========================================================================
  // WAIT FOR NEW ELEMENT — missing trie logs (distance > 1)
  // ===========================================================================

  @Test
  public void testWaitForNewElement_callsOnTrieLogMissingWhenGap() throws Exception {
    final AtomicLong head = new AtomicLong(5);
    final CountDownLatch missingCalled = new CountDownLatch(1);
    final AtomicReference<Long> capturedDistance = new AtomicReference<>();

    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    10,
                    List.of(alwaysAllow()),
                    head::get,
                    dist -> {
                      capturedDistance.set(dist);
                      missingCalled.countDown();
                      return CompletableFuture.completedFuture(true);
                    });

    queue.offer(makeTrieLog(10));

    Future<TrieLogIdentifier> future = executor.submit(queue::waitForNewElement);

    assertThat(missingCalled.await(5, TimeUnit.SECONDS)).isTrue();

    TrieLogIdentifier result = future.get(5, TimeUnit.SECONDS);
    assertThat(result).isNull();
    assertThat(capturedDistance.get()).isEqualTo(5L);
  }

  @Test
  public void testWaitForNewElement_retriesWhenOnTrieLogMissingReturnsFalse() throws Exception {
    final AtomicLong head = new AtomicLong(5);
    final AtomicLong missingCallCount = new AtomicLong(0);

    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    10,
                    List.of(alwaysAllow()),
                    head::get,
                    dist -> {
                      long count = missingCallCount.incrementAndGet();
                      if (count >= 2) {
                        return CompletableFuture.completedFuture(true);
                      }
                      return CompletableFuture.completedFuture(false);
                    });

    queue.offer(makeTrieLog(10));

    Future<TrieLogIdentifier> future = executor.submit(queue::waitForNewElement);

    TrieLogIdentifier result = future.get(15, TimeUnit.SECONDS);
    assertThat(result).isNull();
    assertThat(missingCallCount.get()).isGreaterThanOrEqualTo(2);
  }

  // ===========================================================================
  // WAIT FOR NEW ELEMENT — blocked by import validator
  // ===========================================================================

  @Test
  public void testWaitForNewElement_blockedByValidator_thenUnblocked() throws Exception {
    final AtomicLong head = new AtomicLong(5);
    final AtomicReference<Boolean> canImport = new AtomicReference<>(false);

    final BlockImportValidator validator = canImport::get;

    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    10,
                    List.of(validator),
                    head::get,
                    dist -> CompletableFuture.completedFuture(false));

    queue.offer(makeTrieLog(6));

    Future<TrieLogIdentifier> future = executor.submit(queue::waitForNewElement);

    Thread.sleep(2000);
    assertThat(future.isDone()).isFalse();

    canImport.set(true);
    queue.offer(makeTrieLog(6));

    TrieLogIdentifier result = future.get(10, TimeUnit.SECONDS);
    assertThat(result).isNotNull();
    assertThat(result.blockNumber()).isEqualTo(6);
  }

  @Test
  public void testWaitForNewElement_validatorClearsQueue() throws Exception {
    final AtomicLong head = new AtomicLong(5);
    final AtomicReference<Boolean> canImport = new AtomicReference<>(false);

    final BlockImportValidator validator = canImport::get;

    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    10,
                    List.of(validator),
                    head::get,
                    dist -> CompletableFuture.completedFuture(false));

    queue.offer(makeTrieLog(6));
    queue.offer(makeTrieLog(7));
    queue.offer(makeTrieLog(8));
    assertThat(queue).hasSize(3);

    Future<TrieLogIdentifier> future = executor.submit(queue::waitForNewElement);

    Thread.sleep(2000);

    queue.stop();
    future.get(5, TimeUnit.SECONDS);
  }

  // ===========================================================================
  // WAIT FOR NEW ELEMENT — empty queue
  // ===========================================================================

  @Test
  public void testWaitForNewElement_emptyQueue_usesInitialSyncRange() throws Exception {
    final AtomicLong head = new AtomicLong(5);
    final AtomicReference<Long> capturedDistance = new AtomicReference<>();

    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    10,
                    List.of(alwaysAllow()),
                    head::get,
                    dist -> {
                      capturedDistance.set(dist);
                      return CompletableFuture.completedFuture(true);
                    });

    Future<TrieLogIdentifier> future = executor.submit(queue::waitForNewElement);

    TrieLogIdentifier result = future.get(5, TimeUnit.SECONDS);
    assertThat(result).isNull();
    assertThat(capturedDistance.get())
            .isEqualTo(TrieLogBlockingQueue.INITIAL_SYNC_BLOCK_NUMBER_RANGE);
  }

  // ===========================================================================
  // WAIT FOR NEW ELEMENT — onTrieLogMissing throws / times out
  // ===========================================================================

  @Test
  public void testWaitForNewElement_onTrieLogMissingThrows_continuesLoop() throws Exception {
    final AtomicLong head = new AtomicLong(5);
    final AtomicLong callCount = new AtomicLong(0);

    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    10,
                    List.of(alwaysAllow()),
                    head::get,
                    dist -> {
                      long count = callCount.incrementAndGet();
                      if (count == 1) {
                        CompletableFuture<Boolean> f = new CompletableFuture<>();
                        f.completeExceptionally(new RuntimeException("simulated failure"));
                        return f;
                      }
                      return CompletableFuture.completedFuture(true);
                    });

    queue.offer(makeTrieLog(10));

    Future<TrieLogIdentifier> future = executor.submit(queue::waitForNewElement);

    TrieLogIdentifier result = future.get(15, TimeUnit.SECONDS);
    assertThat(result).isNull();
    assertThat(callCount.get()).isGreaterThanOrEqualTo(2);
  }

  @Test
  public void testWaitForNewElement_onTrieLogMissingTimesOut_continuesLoop() throws Exception {
    final AtomicLong head = new AtomicLong(5);
    final AtomicLong callCount = new AtomicLong(0);

    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    10,
                    List.of(alwaysAllow()),
                    head::get,
                    dist -> {
                      long count = callCount.incrementAndGet();
                      if (count == 1) {
                        return new CompletableFuture<>();
                      }
                      return CompletableFuture.completedFuture(true);
                    });

    queue.offer(makeTrieLog(10));

    Future<TrieLogIdentifier> future = executor.submit(queue::waitForNewElement);

    TrieLogIdentifier result = future.get(15, TimeUnit.SECONDS);
    assertThat(result).isNull();
    assertThat(callCount.get()).isGreaterThanOrEqualTo(2);
  }

  // ===========================================================================
  // WAIT FOR NEW ELEMENT — interrupted
  // ===========================================================================

  @Test
  public void testWaitForNewElement_interruptedReturnsNull() throws Exception {
    final AtomicLong head = new AtomicLong(5);

    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    10,
                    List.of(alwaysAllow()),
                    head::get,
                    dist -> new CompletableFuture<>());

    queue.offer(makeTrieLog(10));

    Future<TrieLogIdentifier> future = executor.submit(queue::waitForNewElement);

    Thread.sleep(500);

    executor.shutdownNow();

    TrieLogIdentifier result = future.get(10, TimeUnit.SECONDS);
    assertThat(result).isNull();
  }

  // ===========================================================================
  // WAIT FOR NEW ELEMENT — head advances during wait
  // ===========================================================================

  @Test
  public void testWaitForNewElement_headAdvancesDuringWait() throws Exception {
    final AtomicLong head = new AtomicLong(5);

    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    10,
                    List.of(alwaysAllow()),
                    head::get,
                    dist -> {
                      head.set(9);
                      return CompletableFuture.completedFuture(false);
                    });

    queue.offer(makeTrieLog(8));
    queue.offer(makeTrieLog(9));
    queue.offer(makeTrieLog(10));

    Future<TrieLogIdentifier> future = executor.submit(queue::waitForNewElement);

    TrieLogIdentifier result = future.get(15, TimeUnit.SECONDS);
    assertThat(result).isNotNull();
    assertThat(result.blockNumber()).isEqualTo(10);
  }

  // ===========================================================================
  // WAIT FOR NEW ELEMENT — multiple validators
  // ===========================================================================

  @Test
  public void testWaitForNewElement_multipleValidators_anyBlockingPreventsImport()
          throws Exception {
    final AtomicLong head = new AtomicLong(5);
    final AtomicReference<Boolean> secondValidatorAllows = new AtomicReference<>(false);

    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    10,
                    List.of(alwaysAllow(), secondValidatorAllows::get),
                    head::get,
                    dist -> CompletableFuture.completedFuture(false));

    queue.offer(makeTrieLog(6));

    Future<TrieLogIdentifier> future = executor.submit(queue::waitForNewElement);

    Thread.sleep(2000);
    assertThat(future.isDone()).isFalse();

    secondValidatorAllows.set(true);
    queue.offer(makeTrieLog(6));

    TrieLogIdentifier result = future.get(10, TimeUnit.SECONDS);
    assertThat(result).isNotNull();
    assertThat(result.blockNumber()).isEqualTo(6);
  }

  // ===========================================================================
  // WAIT FOR NEW ELEMENT — block added after wait starts
  // ===========================================================================

  @Test
  public void testWaitForNewElement_blockAddedLaterDuringWait() throws Exception {
    final AtomicLong head = new AtomicLong(5);
    final AtomicLong missingCallCount = new AtomicLong(0);

    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    10,
                    List.of(alwaysAllow()),
                    head::get,
                    dist -> {
                      missingCallCount.incrementAndGet();
                      return CompletableFuture.completedFuture(false);
                    });

    Future<TrieLogIdentifier> future = executor.submit(queue::waitForNewElement);

    Thread.sleep(4000);
    queue.offer(makeTrieLog(6));

    TrieLogIdentifier result = future.get(15, TimeUnit.SECONDS);
    assertThat(result).isNotNull();
    assertThat(result.blockNumber()).isEqualTo(6);
  }

  // ===========================================================================
  // DISTANCE / PEEK
  // ===========================================================================

  @Test
  public void testDistance_emptyQueue_returnsEmpty() {
    final AtomicLong head = new AtomicLong(0);
    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    10,
                    Collections.emptyList(),
                    head::get,
                    dist -> CompletableFuture.completedFuture(false));

    assertThat(queue.peek()).isNull();
    assertThat(queue).isEmpty();
  }

  @Test
  public void testPeek_returnsSmallestBlockNumber() {
    final AtomicLong head = new AtomicLong(0);
    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    10,
                    Collections.emptyList(),
                    head::get,
                    dist -> CompletableFuture.completedFuture(false));

    queue.offer(makeTrieLog(10));
    queue.offer(makeTrieLog(5));
    queue.offer(makeTrieLog(8));

    assertThat(queue.peek().blockNumber()).isEqualTo(5);
  }

  // ===========================================================================
  // STOP during various states
  // ===========================================================================

  @Test
  public void testStop_duringOnTrieLogMissing() throws Exception {
    final AtomicLong head = new AtomicLong(5);
    final CountDownLatch insideMissing = new CountDownLatch(1);

    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    10,
                    List.of(alwaysAllow()),
                    head::get,
                    dist -> {
                      insideMissing.countDown();
                      return new CompletableFuture<>();
                    });

    queue.offer(makeTrieLog(10));

    Future<TrieLogIdentifier> future = executor.submit(queue::waitForNewElement);

    assertThat(insideMissing.await(5, TimeUnit.SECONDS)).isTrue();

    queue.stop();

    TrieLogIdentifier result = future.get(5, TimeUnit.SECONDS);
    assertThat(result).isNull();
  }

  @Test
  public void testStop_duringValidatorBlock() throws Exception {
    final AtomicLong head = new AtomicLong(5);

    final TrieLogBlockingQueue queue =
            new TrieLogBlockingQueue(
                    10,
                    List.of(alwaysBlock()),
                    head::get,
                    dist -> CompletableFuture.completedFuture(false));

    queue.offer(makeTrieLog(6));

    Future<TrieLogIdentifier> future = executor.submit(queue::waitForNewElement);

    Thread.sleep(2000);
    assertThat(future.isDone()).isFalse();

    queue.stop();

    TrieLogIdentifier result = future.get(5, TimeUnit.SECONDS);
    assertThat(result).isNull();
  }
}
