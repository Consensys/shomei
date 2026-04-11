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

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.observer.TrieLogObserver.TrieLogIdentifier;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;
import net.consensys.shomei.storage.worldstate.LayeredWorldStateStorage;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.storage.AccountTrieRepositoryWrapper;
import net.consensys.shomei.trielog.TrieLogLayer;
import net.consensys.shomei.trielog.TrieLogLayerConverter;
import net.consensys.shomei.worldview.ZkEvmWorldState;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test that loads real trie logs from {@code trielogs.dat} (blocks 0–33 from a real
 * Shomei database dump), builds the world state sequentially in a real RocksDB instance, and
 * verifies that rolling back to each cached block number produces a world state whose state root
 * and account-trie nextFreeNode match the cached snapshot for that block.
 *
 * <p>The test is designed to surface bugs in the rollback path without relying on synthetic data.
 */
public class TrieLogRollbackIntegrationTest {

  @TempDir Path tempDir;

  private RocksDBStorageProvider provider;
  private ZkWorldStateArchive archive;

  @BeforeEach
  void setup() {
    provider =
        new RocksDBStorageProvider(
            new RocksDBConfigurationBuilder().databaseDir(tempDir).build());
    archive = new ZkWorldStateArchive(provider);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (archive != null) {
      archive.close();
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Parses {@code trielogs.dat} from the test classpath.
   *
   * <p>Each line has the form: {@code 0x<16-hex-digit-block-num> ==> 0x<hex-rlp>}
   *
   * @return list of (blockNumber, rawRlpBytes) pairs, sorted ascending by block number
   */
  private List<TrieLogEntry> parseTrieLogDat() throws Exception {
    final List<TrieLogEntry> entries = new ArrayList<>();
    try (final InputStream is =
            getClass().getClassLoader().getResourceAsStream("trielogs.dat");
        final BufferedReader reader =
            new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) {
          continue;
        }
        final int sep = line.indexOf(" ==> ");
        if (sep < 0) {
          continue;
        }
        final String[] parts = {line.substring(0, sep), line.substring(sep + 5)};
        final long blockNumber = Long.parseUnsignedLong(parts[0].substring(2), 16);
        final Bytes rawRlp = Bytes.fromHexString(parts[1]);
        entries.add(new TrieLogEntry(blockNumber, rawRlp));
      }
    }
    entries.sort((a, b) -> Long.compare(a.blockNumber(), b.blockNumber()));
    return entries;
  }

  @SuppressWarnings("UnusedVariable") // errorprone false positive for record components
  private record TrieLogEntry(long blockNumber, Bytes rawRlp) {}

  /**
   * Extracts the block hash (first field in the RLP list) from a raw trie-log byte array without
   * fully decoding the entry.
   */
  private Bytes32 extractBlockHash(final Bytes rawRlp) {
    final RLPInput input = RLP.input(rawRlp);
    input.enterList();
    return input.readBytes32();
  }

  /**
   * Loads the account ZKTrie for {@code worldState} and returns its current nextFreeNode counter.
   * This is a read-only operation; nothing is committed.
   */
  private long loadNextFreeNode(final ZkEvmWorldState worldState) {
    final AccountTrieRepositoryWrapper wrapper =
        new AccountTrieRepositoryWrapper(worldState.getZkEvmWorldStateStorage());
    final ZKTrie trie = ZKTrie.loadTrie(worldState.getStateRootHash(), wrapper);
    return trie.getNextFreeNode();
  }

  // ---------------------------------------------------------------------------
  // Test
  // ---------------------------------------------------------------------------

  /**
   * Loads blocks 0–33 into a real RocksDB-backed {@link ZkWorldStateArchive}, caching a snapshot
   * for each block, then for every non-head block N asserts:
   *
   * <ol>
   *   <li>Rolling back to N yields the same state root and nextFreeNode as the cached snapshot.
   *   <li>Trie log N+1 decodes successfully against the rolled-back worldstate N — the prior-value
   *       validation inside {@link TrieLogLayerConverter} would throw if the rolled-back state does
   *       not match the true prior values.
   *   <li>Re-applying trie log N+1 to the rolled-back state N (in a fresh layered overlay)
   *       reproduces the cached state root for block N+1.
   * </ol>
   *
   * <p>Because each block's snapshot is cached, the rollback for block N is always a single step
   * from block N+1, isolating any per-block rollback bug.
   */
  @Test
  void rolledBackWorldStateMatchesCachedWorldState() throws Exception {
    final List<TrieLogEntry> entries = parseTrieLogDat();
    assertThat(entries).isNotEmpty();

    final TrieLogManager trieLogManager = provider.getTrieLogManager();

    // ---- Step 1: persist all raw trie logs to the TrieLogManager ----
    // This must happen before any importBlock() call so that the archive can read them.
    final TrieLogManager.TrieLogManagerUpdater trieLogUpdater = trieLogManager.updater();
    for (final TrieLogEntry entry : entries) {
      final Bytes32 blockHash = extractBlockHash(entry.rawRlp());
      trieLogUpdater.saveTrieLog(
          new TrieLogIdentifier(entry.blockNumber(), blockHash), entry.rawRlp());
    }
    trieLogUpdater.commit();

    // ---- Step 2: import blocks in ascending order, caching a snapshot after each ----
    // Record the reference state root and nextFreeNode from each cached snapshot so that
    // they can be compared against the rolled-back state later.
    final List<Bytes32> cachedStateRoots = new ArrayList<>();
    final List<Long> cachedNextFreeNodes = new ArrayList<>();

    for (final TrieLogEntry entry : entries) {
      final Bytes32 blockHash = extractBlockHash(entry.rawRlp());
      archive.importBlock(
          new TrieLogIdentifier(entry.blockNumber(), blockHash),
          /* shouldGenerateTrace= */ false,
          /* isSnapshotGenerationNeeded= */ true);

      final ZkEvmWorldState cached =
          archive
              .getCachedWorldState(entry.blockNumber())
              .orElseThrow(
                  () ->
                      new AssertionError(
                          "Expected cached worldstate for block " + entry.blockNumber()));

      cachedStateRoots.add(cached.getStateRootHash());
      cachedNextFreeNodes.add(loadNextFreeNode(cached));
    }

    final long headBlockNum = entries.get(entries.size() - 1).blockNumber();

    // ---- Step 3: for every non-head block, roll back and compare ----
    for (int i = 0; i < entries.size() - 1; i++) {
      final long blockNum = entries.get(i).blockNumber();
      final Bytes32 expectedStateRoot = cachedStateRoots.get(i);
      final long expectedNextFreeNode = cachedNextFreeNodes.get(i);

      final ZkEvmWorldState rolledBack = archive.rollBackToBlock(blockNum);

      assertThat(rolledBack.getStateRootHash())
          .as(
              "state root mismatch after rollback to block %d (head=%d)",
              blockNum, headBlockNum)
          .isEqualTo(expectedStateRoot);

      assertThat(loadNextFreeNode(rolledBack))
          .as(
              "nextFreeNode mismatch after rollback to block %d (head=%d)",
              blockNum, headBlockNum)
          .isEqualTo(expectedNextFreeNode);

      // ---- Additional assertion: trie log N+1 must decode against rolled-back state N ----
      // TrieLogLayerConverter validates prior-account nonce/balance against the provided
      // WorldStateStorage and throws IllegalStateException on mismatch.  A correct rolled-back
      // state N will satisfy every prior-value check in trie log N+1.
      final TrieLogEntry nextEntry = entries.get(i + 1);
      final TrieLogLayerConverter converter = archive.getTrieLogLayerConverter();
      final TrieLogLayer nextLayer =
          converter.decodeTrieLog(
              RLP.input(nextEntry.rawRlp()), rolledBack.getZkEvmWorldStateStorage());

      assertThat(nextLayer.getBlockNumber())
          .as("decoded trie log block number after rollback to %d", blockNum)
          .isEqualTo(nextEntry.blockNumber());

      // ---- Re-apply trie log N+1 to a fresh overlay on the rolled-back state ----
      // If both the decode and the re-application succeed, the rolled-back state is a valid
      // prior for block N+1 and the resulting state root must equal the cached block N+1 root.
      final LayeredWorldStateStorage reapplyStorage =
          new LayeredWorldStateStorage(rolledBack.getZkEvmWorldStateStorage());
      final TraceManager ephemeralTrace = new InMemoryStorageProvider().getTraceManager();
      final ZkEvmWorldState reapplied = new ZkEvmWorldState(reapplyStorage, ephemeralTrace);
      reapplied.getAccumulator().rollForward(nextLayer);
      reapplied.commit(nextEntry.blockNumber(), extractBlockHash(nextEntry.rawRlp()), false);

      assertThat(reapplied.getStateRootHash())
          .as(
              "re-applying trie log %d to rolled-back state %d should reproduce cached block %d"
                  + " state root",
              nextEntry.blockNumber(), blockNum, nextEntry.blockNumber())
          .isEqualTo(cachedStateRoots.get(i + 1));
    }
  }
}
