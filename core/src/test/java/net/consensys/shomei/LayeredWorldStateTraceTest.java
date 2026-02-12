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

package net.consensys.shomei;

import static net.consensys.shomei.ZkAccount.EMPTY_CODE_HASH;
import static net.consensys.shomei.ZkAccount.EMPTY_KECCAK_CODE_HASH;
import static net.consensys.shomei.trie.ZKTrie.DEFAULT_TRIE_ROOT;
import static net.consensys.shomei.util.TestFixtureGenerator.createDumAddress;
import static net.consensys.shomei.util.TestFixtureGenerator.createDumDigest;
import static net.consensys.shomei.util.TestFixtureGenerator.createDumFullBytes;
import static net.consensys.shomei.util.TestFixtureGenerator.getAccountOne;
import static net.consensys.shomei.util.bytes.MimcSafeBytes.unsafeFromBytes;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.storage.worldstate.InMemoryWorldStateStorage;
import net.consensys.shomei.storage.worldstate.LayeredWorldStateStorage;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.json.JsonTraceParser;
import net.consensys.shomei.trie.storage.AccountTrieRepositoryWrapper;
import net.consensys.shomei.trie.storage.StorageTrieRepositoryWrapper;
import net.consensys.shomei.trie.storage.TrieStorage;
import net.consensys.shomei.trie.trace.Trace;
import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.util.bytes.MimcSafeBytes;
import net.consensys.zkevm.HashProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LayeredWorldStateTraceTest {

  private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();

  @BeforeEach
  public void setup() {
    JSON_OBJECT_MAPPER.registerModules(JsonTraceParser.modules);
  }

  private ZKTrie loadAccountTrie(final Bytes32 stateRoot, final TrieStorage storage) {
    if (storage.getTrieNode(Bytes.EMPTY, null).isEmpty()) {
      return ZKTrie.createTrie(storage);
    } else {
      return ZKTrie.loadTrie(stateRoot, storage);
    }
  }

  private ZKTrie loadStorageTrie(final Bytes32 storageRoot, final TrieStorage storage) {
    if (storage.getTrieNode(Bytes.EMPTY, null).isEmpty()) {
      return ZKTrie.createTrie(storage);
    } else {
      return ZKTrie.loadTrie(storageRoot, storage);
    }
  }

    /**
     * Helper: create an empty committed parent and return its root + storage.
     */
    private static final class ParentState {
        private final Bytes32 root;
        private final InMemoryWorldStateStorage storage;

        /**
         *
         */
        private ParentState(Bytes32 root, InMemoryWorldStateStorage storage) {
            this.root = root;
            this.storage = storage;
        }

        public Bytes32 root() {
            return root;
        }

        public InMemoryWorldStateStorage storage() {
            return storage;
        }

    }

  private ParentState createEmptyParent() {
    InMemoryWorldStateStorage parentStorage = new InMemoryWorldStateStorage();
    AccountTrieRepositoryWrapper parentRepo = new AccountTrieRepositoryWrapper(parentStorage);
    ZKTrie parentTrie = loadAccountTrie(DEFAULT_TRIE_ROOT, parentRepo);
    parentTrie.commit();
    return new ParentState(parentTrie.getTopRootHash(), parentStorage);
  }

  // ===========================================================================
  // Common test accounts
  // ===========================================================================

  private ZkAccount makeAccount2Simple() {
    return new ZkAccount(
            new AccountKey(createDumAddress(41)),
            42,
            Wei.of(354),
            DEFAULT_TRIE_ROOT,
            EMPTY_CODE_HASH,
            EMPTY_KECCAK_CODE_HASH,
            0L);
  }

  private ZkAccount makeAccount2Contract() {
    return new ZkAccount(
            new AccountKey(createDumAddress(47)),
            41,
            Wei.of(15353),
            DEFAULT_TRIE_ROOT,
            Hash.wrap(createDumDigest(75)),
            createDumFullBytes(15),
            7L);
  }

  private MutableZkAccount makeMutableAccount2Contract() {
    return new MutableZkAccount(
            new AccountKey(createDumAddress(47)),
            createDumFullBytes(15),
            Hash.wrap(createDumDigest(75)),
            7L,
            41,
            Wei.of(15353),
            DEFAULT_TRIE_ROOT);
  }

  private ZkAccount makeAccount3() {
    return new ZkAccount(
            new AccountKey(createDumAddress(120)),
            48,
            Wei.of(9835),
            DEFAULT_TRIE_ROOT,
            Hash.wrap(createDumDigest(54)),
            createDumFullBytes(85),
            19L);
  }

  // ===========================================================================
  // READ ZERO
  // ===========================================================================

  /** Original: empty parent, single overlay, read zero. */
  @Test
  public void testTraceReadZero() throws IOException {
    final Bytes32 key = createDumDigest(36);
    final Hash hkey = HashProvider.trieHash(key);

    InMemoryWorldStateStorage parentStorage = new InMemoryWorldStateStorage();
    AccountTrieRepositoryWrapper parentRepo = new AccountTrieRepositoryWrapper(parentStorage);
    ZKTrie parentTrie = loadAccountTrie(DEFAULT_TRIE_ROOT, parentRepo);
    parentTrie.commit();
    final Bytes32 parentRoot = parentTrie.getTopRootHash();

    LayeredWorldStateStorage layeredStorage = new LayeredWorldStateStorage(parentStorage);
    AccountTrieRepositoryWrapper layeredRepo = new AccountTrieRepositoryWrapper(layeredStorage);
    ZKTrie layeredTrie = loadAccountTrie(parentRoot, layeredRepo);

    Trace trace = layeredTrie.readWithTrace(hkey, MimcSafeBytes.safeByte32(key));

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(trace))
            .isEqualToIgnoringWhitespace(getResources("testTraceReadZero.json"));
  }

  // ===========================================================================
  // READ SIMPLE VALUE
  // ===========================================================================

  /** Original: write in parent, read from overlay. */
  @Test
  public void testTraceReadSimpleValueFromParent() throws IOException {
    final MimcSafeBytes<Bytes> key = unsafeFromBytes(createDumDigest(36));
    final MimcSafeBytes<Bytes> value = unsafeFromBytes(createDumDigest(32));
    final Hash hkey = HashProvider.trieHash(key);

    InMemoryWorldStateStorage parentStorage = new InMemoryWorldStateStorage();
    AccountTrieRepositoryWrapper parentRepo = new AccountTrieRepositoryWrapper(parentStorage);
    ZKTrie parentTrie = loadAccountTrie(DEFAULT_TRIE_ROOT, parentRepo);
    parentTrie.putWithTrace(hkey, key, value);
    parentTrie.commit();
    final Bytes32 parentRoot = parentTrie.getTopRootHash();

    LayeredWorldStateStorage layeredStorage = new LayeredWorldStateStorage(parentStorage);
    AccountTrieRepositoryWrapper layeredRepo = new AccountTrieRepositoryWrapper(layeredStorage);
    ZKTrie layeredTrie = loadAccountTrie(parentRoot, layeredRepo);

    Trace trace = layeredTrie.readWithTrace(hkey, key);

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(trace))
            .isEqualToIgnoringWhitespace(getResources("testTraceReadSimpleValue.json"));
  }

  // ===========================================================================
  // READ ACCOUNT
  // ===========================================================================

  /** Original: write account in parent, read from overlay. */
  @Test
  public void testTraceReadAccountFromParent() throws IOException {
    MutableZkAccount account = getAccountOne();

    InMemoryWorldStateStorage parentStorage = new InMemoryWorldStateStorage();
    AccountTrieRepositoryWrapper parentRepo = new AccountTrieRepositoryWrapper(parentStorage);
    ZKTrie parentTrie = loadAccountTrie(DEFAULT_TRIE_ROOT, parentRepo);
    parentTrie.putWithTrace(account.getHkey(), account.getAddress(), account.getEncodedBytes());
    parentTrie.commit();
    final Bytes32 parentRoot = parentTrie.getTopRootHash();

    LayeredWorldStateStorage layeredStorage = new LayeredWorldStateStorage(parentStorage);
    AccountTrieRepositoryWrapper layeredRepo = new AccountTrieRepositoryWrapper(layeredStorage);
    ZKTrie layeredTrie = loadAccountTrie(parentRoot, layeredRepo);

    Trace trace = layeredTrie.readWithTrace(account.getHkey(), account.getAddress());

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(trace))
            .isEqualToIgnoringWhitespace(getResources("testTraceReadAccount.json"));
  }

  // ===========================================================================
  // TWO ACCOUNTS
  // ===========================================================================

  /** Original: account1 in parent, account2 in overlay. */
  @Test
  public void testWorldStateWithTwoAccountsSplitBetweenParentAndOverlay() throws IOException {
    final ZkAccount zkAccount2 = makeAccount2Simple();
    MutableZkAccount account = getAccountOne();

    InMemoryWorldStateStorage parentStorage = new InMemoryWorldStateStorage();
    AccountTrieRepositoryWrapper parentRepo = new AccountTrieRepositoryWrapper(parentStorage);
    ZKTrie parentTrie = loadAccountTrie(DEFAULT_TRIE_ROOT, parentRepo);
    Trace trace =
            parentTrie.putWithTrace(
                    account.getHkey(), account.getAddress(), account.getEncodedBytes());
    parentTrie.commit();
    final Bytes32 parentRoot = parentTrie.getTopRootHash();

    LayeredWorldStateStorage layeredStorage = new LayeredWorldStateStorage(parentStorage);
    AccountTrieRepositoryWrapper layeredRepo = new AccountTrieRepositoryWrapper(layeredStorage);
    ZKTrie layeredTrie = loadAccountTrie(parentRoot, layeredRepo);

    Trace trace2 =
            layeredTrie.putWithTrace(
                    zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(List.of(trace, trace2)))
            .isEqualToIgnoringWhitespace(getResources("testWorldStateWithTwoAccount.json"));
  }

  /** Both accounts written in overlay, empty parent. */
  @Test
  public void testWorldStateWithTwoAccounts_bothInOverlay() throws IOException {
    final ZkAccount zkAccount2 = makeAccount2Simple();
    MutableZkAccount account = getAccountOne();

    ParentState parent = createEmptyParent();

    LayeredWorldStateStorage layeredStorage = new LayeredWorldStateStorage(parent.storage());
    AccountTrieRepositoryWrapper layeredRepo = new AccountTrieRepositoryWrapper(layeredStorage);
    ZKTrie layeredTrie = loadAccountTrie(parent.root(), layeredRepo);

    Trace trace =
            layeredTrie.putWithTrace(
                    account.getHkey(), account.getAddress(), account.getEncodedBytes());
    Trace trace2 =
            layeredTrie.putWithTrace(
                    zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(List.of(trace, trace2)))
            .isEqualToIgnoringWhitespace(getResources("testWorldStateWithTwoAccount.json"));
  }

  // ===========================================================================
  // ACCOUNT + CONTRACT
  // ===========================================================================

  /** Original: account1 in parent, contract in overlay. */
  @Test
  public void testWorldStateWithAccountInParentAndContractInOverlay() throws IOException {
    final ZkAccount zkAccount2 = makeAccount2Contract();
    MutableZkAccount account = getAccountOne();

    InMemoryWorldStateStorage parentStorage = new InMemoryWorldStateStorage();
    AccountTrieRepositoryWrapper parentRepo = new AccountTrieRepositoryWrapper(parentStorage);
    ZKTrie parentTrie = loadAccountTrie(DEFAULT_TRIE_ROOT, parentRepo);
    final Trace trace =
            parentTrie.putWithTrace(
                    account.getHkey(), account.getAddress(), account.getEncodedBytes());
    parentTrie.commit();
    final Bytes32 parentRoot = parentTrie.getTopRootHash();

    LayeredWorldStateStorage layeredStorage = new LayeredWorldStateStorage(parentStorage);
    AccountTrieRepositoryWrapper layeredRepo = new AccountTrieRepositoryWrapper(layeredStorage);
    ZKTrie layeredTrie = loadAccountTrie(parentRoot, layeredRepo);

    final Trace trace2 =
            layeredTrie.putWithTrace(
                    zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(List.of(trace, trace2)))
            .isEqualToIgnoringWhitespace(
                    getResources("testWorldStateWithAccountAndContract.json"));
  }

  /** Both account and contract written in overlay, empty parent. */
  @Test
  public void testWorldStateWithAccountAndContract_bothInOverlay() throws IOException {
    final ZkAccount zkAccount2 = makeAccount2Contract();
    MutableZkAccount account = getAccountOne();

    ParentState parent = createEmptyParent();

    LayeredWorldStateStorage layeredStorage = new LayeredWorldStateStorage(parent.storage());
    AccountTrieRepositoryWrapper layeredRepo = new AccountTrieRepositoryWrapper(layeredStorage);
    ZKTrie layeredTrie = loadAccountTrie(parent.root(), layeredRepo);

    final Trace trace =
            layeredTrie.putWithTrace(
                    account.getHkey(), account.getAddress(), account.getEncodedBytes());
    final Trace trace2 =
            layeredTrie.putWithTrace(
                    zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(List.of(trace, trace2)))
            .isEqualToIgnoringWhitespace(
                    getResources("testWorldStateWithAccountAndContract.json"));
  }

  // ===========================================================================
  // STORAGE UPDATE
  // ===========================================================================

  /** Original: both accounts in parent, storage update in overlay. */
  @Test
  public void testWorldStateWithStorageUpdateInOverlay() throws IOException {
    final MutableZkAccount zkAccount2 = makeMutableAccount2Contract();
    MutableZkAccount account = getAccountOne();

    InMemoryWorldStateStorage parentStorage = new InMemoryWorldStateStorage();
    AccountTrieRepositoryWrapper parentRepo = new AccountTrieRepositoryWrapper(parentStorage);
    ZKTrie parentTrie = loadAccountTrie(DEFAULT_TRIE_ROOT, parentRepo);
    final Trace trace =
            parentTrie.putWithTrace(
                    account.getHkey(), account.getAddress(), account.getEncodedBytes());
    final Trace trace2 =
            parentTrie.putWithTrace(
                    zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());
    parentTrie.commit();
    final Bytes32 parentRoot = parentTrie.getTopRootHash();

    LayeredWorldStateStorage layeredStorage = new LayeredWorldStateStorage(parentStorage);

    StorageTrieRepositoryWrapper storageRepo =
            new StorageTrieRepositoryWrapper(zkAccount2.hashCode(), layeredStorage);
    ZKTrie account2Storage = loadStorageTrie(zkAccount2.getStorageRoot(), storageRepo);

    final MimcSafeBytes<Bytes32> slotKey = createDumFullBytes(14);
    final Hash slotKeyHash = HashProvider.trieHash(slotKey);
    final MimcSafeBytes<Bytes32> slotValue = createDumFullBytes(18);
    final Trace trace3 = account2Storage.putWithTrace(slotKeyHash, slotKey, slotValue);

    zkAccount2.setStorageRoot(Hash.wrap(account2Storage.getTopRootHash()));
    AccountTrieRepositoryWrapper layeredRepo = new AccountTrieRepositoryWrapper(layeredStorage);
    ZKTrie layeredTrie = loadAccountTrie(parentRoot, layeredRepo);
    final Trace trace4 =
            layeredTrie.putWithTrace(
                    zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(
            JSON_OBJECT_MAPPER.writeValueAsString(List.of(trace, trace2, trace3, trace4)))
            .isEqualToIgnoringWhitespace(
                    getResources("testWorldStateWithUpdateContractStorage.json"));
  }

  /** All writes (accounts + storage) in overlay, empty parent. */
  @Test
  public void testWorldStateWithStorageUpdate_allInOverlay() throws IOException {
    final MutableZkAccount zkAccount2 = makeMutableAccount2Contract();
    MutableZkAccount account = getAccountOne();

    ParentState parent = createEmptyParent();

    LayeredWorldStateStorage layeredStorage = new LayeredWorldStateStorage(parent.storage());
    AccountTrieRepositoryWrapper layeredRepo = new AccountTrieRepositoryWrapper(layeredStorage);
    ZKTrie layeredTrie = loadAccountTrie(parent.root(), layeredRepo);

    final Trace trace =
            layeredTrie.putWithTrace(
                    account.getHkey(), account.getAddress(), account.getEncodedBytes());
    final Trace trace2 =
            layeredTrie.putWithTrace(
                    zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    StorageTrieRepositoryWrapper storageRepo =
            new StorageTrieRepositoryWrapper(zkAccount2.hashCode(), layeredStorage);
    ZKTrie account2Storage = loadStorageTrie(zkAccount2.getStorageRoot(), storageRepo);

    final MimcSafeBytes<Bytes32> slotKey = createDumFullBytes(14);
    final Hash slotKeyHash = HashProvider.trieHash(slotKey);
    final MimcSafeBytes<Bytes32> slotValue = createDumFullBytes(18);
    final Trace trace3 = account2Storage.putWithTrace(slotKeyHash, slotKey, slotValue);

    zkAccount2.setStorageRoot(Hash.wrap(account2Storage.getTopRootHash()));
    final Trace trace4 =
            layeredTrie.putWithTrace(
                    zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(
            JSON_OBJECT_MAPPER.writeValueAsString(List.of(trace, trace2, trace3, trace4)))
            .isEqualToIgnoringWhitespace(
                    getResources("testWorldStateWithUpdateContractStorage.json"));
  }

  /** Account1 in parent, account2 + storage in overlay. */
  @Test
  public void testWorldStateWithStorageUpdate_account1InParentRestInOverlay() throws IOException {
    final MutableZkAccount zkAccount2 = makeMutableAccount2Contract();
    MutableZkAccount account = getAccountOne();

    InMemoryWorldStateStorage parentStorage = new InMemoryWorldStateStorage();
    AccountTrieRepositoryWrapper parentRepo = new AccountTrieRepositoryWrapper(parentStorage);
    ZKTrie parentTrie = loadAccountTrie(DEFAULT_TRIE_ROOT, parentRepo);
    final Trace trace =
            parentTrie.putWithTrace(
                    account.getHkey(), account.getAddress(), account.getEncodedBytes());
    parentTrie.commit();
    final Bytes32 parentRoot = parentTrie.getTopRootHash();

    LayeredWorldStateStorage layeredStorage = new LayeredWorldStateStorage(parentStorage);
    AccountTrieRepositoryWrapper layeredRepo = new AccountTrieRepositoryWrapper(layeredStorage);
    ZKTrie layeredTrie = loadAccountTrie(parentRoot, layeredRepo);

    final Trace trace2 =
            layeredTrie.putWithTrace(
                    zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    StorageTrieRepositoryWrapper storageRepo =
            new StorageTrieRepositoryWrapper(zkAccount2.hashCode(), layeredStorage);
    ZKTrie account2Storage = loadStorageTrie(zkAccount2.getStorageRoot(), storageRepo);

    final MimcSafeBytes<Bytes32> slotKey = createDumFullBytes(14);
    final Hash slotKeyHash = HashProvider.trieHash(slotKey);
    final MimcSafeBytes<Bytes32> slotValue = createDumFullBytes(18);
    final Trace trace3 = account2Storage.putWithTrace(slotKeyHash, slotKey, slotValue);

    zkAccount2.setStorageRoot(Hash.wrap(account2Storage.getTopRootHash()));
    final Trace trace4 =
            layeredTrie.putWithTrace(
                    zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(
            JSON_OBJECT_MAPPER.writeValueAsString(List.of(trace, trace2, trace3, trace4)))
            .isEqualToIgnoringWhitespace(
                    getResources("testWorldStateWithUpdateContractStorage.json"));
  }

  // ===========================================================================
  // DELETE ACCOUNT + UPDATE STORAGE
  // ===========================================================================

  /** Original: both accounts + storage in parent, delete + storage update in overlay. */
  @Test
  public void testDeleteAccountInOverlayAndUpdateStorageInOverlay() throws IOException {
    final MutableZkAccount zkAccount2 = makeMutableAccount2Contract();
    MutableZkAccount account = getAccountOne();

    // --- PARENT: write both accounts + storage ---
    InMemoryWorldStateStorage parentStorage = new InMemoryWorldStateStorage();
    AccountTrieRepositoryWrapper parentRepo = new AccountTrieRepositoryWrapper(parentStorage);
    ZKTrie parentTrie = loadAccountTrie(DEFAULT_TRIE_ROOT, parentRepo);
    parentTrie.putWithTrace(account.getHkey(), account.getAddress(), account.getEncodedBytes());
    parentTrie.putWithTrace(
            zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    final long accountLeafIndex =
            parentTrie.getLeafIndex(zkAccount2.getHkey()).orElse(parentTrie.getNextFreeNode());

    StorageTrieRepositoryWrapper parentStorageRepo =
            new StorageTrieRepositoryWrapper(accountLeafIndex, parentStorage);
    ZKTrie parentAccount2Storage =
            loadStorageTrie(zkAccount2.getStorageRoot(), parentStorageRepo);

    final MimcSafeBytes<Bytes32> slotKey = createDumFullBytes(14);
    final Hash slotKeyHash = HashProvider.trieHash(slotKey);
    final MimcSafeBytes<Bytes32> slotValue = createDumFullBytes(18);
    parentAccount2Storage.putWithTrace(slotKeyHash, slotKey, slotValue);
    parentAccount2Storage.commit();
    final Bytes32 parentStorageRoot = parentAccount2Storage.getTopRootHash();

    zkAccount2.setStorageRoot(Hash.wrap(parentStorageRoot));
    parentTrie.putWithTrace(
            zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());
    parentTrie.commit();
    final Bytes32 parentRoot = parentTrie.getTopRootHash();

    // --- OVERLAY ---
    LayeredWorldStateStorage layeredStorage = new LayeredWorldStateStorage(parentStorage);
    AccountTrieRepositoryWrapper layeredRepo = new AccountTrieRepositoryWrapper(layeredStorage);
    ZKTrie layeredTrie = loadAccountTrie(parentRoot, layeredRepo);

    Trace trace = layeredTrie.removeWithTrace(account.getHkey(), account.getAddress());

    final long accountLayeredLeafIndex =
            layeredTrie.getLeafIndex(zkAccount2.getHkey()).orElse(layeredTrie.getNextFreeNode());

    StorageTrieRepositoryWrapper layeredStorageRepo =
            new StorageTrieRepositoryWrapper(accountLayeredLeafIndex, layeredStorage);
    ZKTrie layeredAccount2Storage = loadStorageTrie(parentStorageRoot, layeredStorageRepo);

    Trace trace2 = layeredAccount2Storage.removeWithTrace(slotKeyHash, slotKey);
    trace2.setLocation(zkAccount2.getAddress().getOriginalUnsafeValue());

    zkAccount2.setStorageRoot(Hash.wrap(layeredAccount2Storage.getTopRootHash()));
    Trace trace3 =
            layeredTrie.putWithTrace(
                    zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    final MimcSafeBytes<Bytes32> newSlotKey = createDumFullBytes(11);
    final Hash newSlotKeyHash = HashProvider.trieHash(newSlotKey);
    final MimcSafeBytes<Bytes32> newSlotValue = createDumFullBytes(78);
    Trace trace4 =
            layeredAccount2Storage.putWithTrace(newSlotKeyHash, newSlotKey, newSlotValue);
    trace4.setLocation(zkAccount2.getAddress().getOriginalUnsafeValue());

    zkAccount2.setStorageRoot(Hash.wrap(layeredAccount2Storage.getTopRootHash()));
    Trace trace5 =
            layeredTrie.putWithTrace(
                    zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(
            JSON_OBJECT_MAPPER.writeValueAsString(
                    List.of(trace, trace2, trace3, trace4, trace5)))
            .isEqualToIgnoringWhitespace(
                    getResources("testWorldStateWithDeleteAccountAndStorage.json"));
  }

  /** Accounts in parent (no storage), storage + delete + new storage all in overlay. */
  @Test
  public void testDeleteAccountAndStorage_accountsInParentAllOpsInOverlay() throws IOException {
    final MutableZkAccount zkAccount2 = makeMutableAccount2Contract();
    MutableZkAccount account = getAccountOne();

    // --- PARENT: write both accounts only (no storage) ---
    InMemoryWorldStateStorage parentStorage = new InMemoryWorldStateStorage();
    AccountTrieRepositoryWrapper parentRepo = new AccountTrieRepositoryWrapper(parentStorage);
    ZKTrie parentTrie = loadAccountTrie(DEFAULT_TRIE_ROOT, parentRepo);
    parentTrie.putWithTrace(account.getHkey(), account.getAddress(), account.getEncodedBytes());
    parentTrie.putWithTrace(
            zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    final long accountLeafIndex =
            parentTrie.getLeafIndex(zkAccount2.getHkey()).orElse(parentTrie.getNextFreeNode());

    // Write storage in parent too so we have the same initial state
    StorageTrieRepositoryWrapper parentStorageRepo =
            new StorageTrieRepositoryWrapper(accountLeafIndex, parentStorage);
    ZKTrie parentAccount2Storage =
            loadStorageTrie(zkAccount2.getStorageRoot(), parentStorageRepo);

    final MimcSafeBytes<Bytes32> slotKey = createDumFullBytes(14);
    final Hash slotKeyHash = HashProvider.trieHash(slotKey);
    final MimcSafeBytes<Bytes32> slotValue = createDumFullBytes(18);
    parentAccount2Storage.putWithTrace(slotKeyHash, slotKey, slotValue);
    parentAccount2Storage.commit();
    final Bytes32 parentStorageRoot = parentAccount2Storage.getTopRootHash();

    zkAccount2.setStorageRoot(Hash.wrap(parentStorageRoot));
    parentTrie.putWithTrace(
            zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());
    parentTrie.commit();
    final Bytes32 parentRoot = parentTrie.getTopRootHash();

    // --- OVERLAY: delete account1, remove storage, add new storage ---
    LayeredWorldStateStorage layeredStorage = new LayeredWorldStateStorage(parentStorage);
    AccountTrieRepositoryWrapper layeredRepo = new AccountTrieRepositoryWrapper(layeredStorage);
    ZKTrie layeredTrie = loadAccountTrie(parentRoot, layeredRepo);

    Trace trace = layeredTrie.removeWithTrace(account.getHkey(), account.getAddress());

    final long accountLayeredLeafIndex =
            layeredTrie.getLeafIndex(zkAccount2.getHkey()).orElse(layeredTrie.getNextFreeNode());

    StorageTrieRepositoryWrapper layeredStorageRepo =
            new StorageTrieRepositoryWrapper(accountLayeredLeafIndex, layeredStorage);
    ZKTrie layeredAccount2Storage = loadStorageTrie(parentStorageRoot, layeredStorageRepo);

    Trace trace2 = layeredAccount2Storage.removeWithTrace(slotKeyHash, slotKey);
    trace2.setLocation(zkAccount2.getAddress().getOriginalUnsafeValue());

    zkAccount2.setStorageRoot(Hash.wrap(layeredAccount2Storage.getTopRootHash()));
    Trace trace3 =
            layeredTrie.putWithTrace(
                    zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    final MimcSafeBytes<Bytes32> newSlotKey = createDumFullBytes(11);
    final Hash newSlotKeyHash = HashProvider.trieHash(newSlotKey);
    final MimcSafeBytes<Bytes32> newSlotValue = createDumFullBytes(78);
    Trace trace4 =
            layeredAccount2Storage.putWithTrace(newSlotKeyHash, newSlotKey, newSlotValue);
    trace4.setLocation(zkAccount2.getAddress().getOriginalUnsafeValue());

    zkAccount2.setStorageRoot(Hash.wrap(layeredAccount2Storage.getTopRootHash()));
    Trace trace5 =
            layeredTrie.putWithTrace(
                    zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(
            JSON_OBJECT_MAPPER.writeValueAsString(
                    List.of(trace, trace2, trace3, trace4, trace5)))
            .isEqualToIgnoringWhitespace(
                    getResources("testWorldStateWithDeleteAccountAndStorage.json"));
  }

  /** Everything in overlay, empty parent. */
  @Test
  public void testDeleteAccountAndStorage_allInOverlay() throws IOException {
    final MutableZkAccount zkAccount2 = makeMutableAccount2Contract();
    MutableZkAccount account = getAccountOne();

    // --- Empty parent ---
    ParentState parent = createEmptyParent();

    // --- OVERLAY: build entire state then do delete + storage ops ---
    LayeredWorldStateStorage layeredStorage = new LayeredWorldStateStorage(parent.storage());
    AccountTrieRepositoryWrapper layeredRepo = new AccountTrieRepositoryWrapper(layeredStorage);
    ZKTrie layeredTrie = loadAccountTrie(parent.root(), layeredRepo);

    // Build initial state in overlay
    layeredTrie.putWithTrace(
            account.getHkey(), account.getAddress(), account.getEncodedBytes());
    layeredTrie.putWithTrace(
            zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    final long accountLeafIndex =
            layeredTrie.getLeafIndex(zkAccount2.getHkey()).orElse(layeredTrie.getNextFreeNode());

    StorageTrieRepositoryWrapper layeredStorageRepo =
            new StorageTrieRepositoryWrapper(accountLeafIndex, layeredStorage);
    ZKTrie layeredAccount2Storage =
            loadStorageTrie(zkAccount2.getStorageRoot(), layeredStorageRepo);

    final MimcSafeBytes<Bytes32> slotKey = createDumFullBytes(14);
    final Hash slotKeyHash = HashProvider.trieHash(slotKey);
    final MimcSafeBytes<Bytes32> slotValue = createDumFullBytes(18);
    layeredAccount2Storage.putWithTrace(slotKeyHash, slotKey, slotValue);

    zkAccount2.setStorageRoot(Hash.wrap(layeredAccount2Storage.getTopRootHash()));
    layeredTrie.putWithTrace(
            zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    // Now do the traced operations
    Trace trace = layeredTrie.removeWithTrace(account.getHkey(), account.getAddress());

    Trace trace2 = layeredAccount2Storage.removeWithTrace(slotKeyHash, slotKey);
    trace2.setLocation(zkAccount2.getAddress().getOriginalUnsafeValue());

    zkAccount2.setStorageRoot(Hash.wrap(layeredAccount2Storage.getTopRootHash()));
    Trace trace3 =
            layeredTrie.putWithTrace(
                    zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    final MimcSafeBytes<Bytes32> newSlotKey = createDumFullBytes(11);
    final Hash newSlotKeyHash = HashProvider.trieHash(newSlotKey);
    final MimcSafeBytes<Bytes32> newSlotValue = createDumFullBytes(78);
    Trace trace4 =
            layeredAccount2Storage.putWithTrace(newSlotKeyHash, newSlotKey, newSlotValue);
    trace4.setLocation(zkAccount2.getAddress().getOriginalUnsafeValue());

    zkAccount2.setStorageRoot(Hash.wrap(layeredAccount2Storage.getTopRootHash()));
    Trace trace5 =
            layeredTrie.putWithTrace(
                    zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(
            JSON_OBJECT_MAPPER.writeValueAsString(
                    List.of(trace, trace2, trace3, trace4, trace5)))
            .isEqualToIgnoringWhitespace(
                    getResources("testWorldStateWithDeleteAccountAndStorage.json"));
  }

  // ===========================================================================
  // DELETE + ADD
  // ===========================================================================

  /** Original: both accounts in parent, delete + add in overlay. */
  @Test
  public void testDeleteInOverlayAndAddInOverlay() throws IOException {
    final ZkAccount zkAccount2 = makeAccount2Contract();
    final ZkAccount zkAccount3 = makeAccount3();
    MutableZkAccount account = getAccountOne();

    InMemoryWorldStateStorage parentStorage = new InMemoryWorldStateStorage();
    AccountTrieRepositoryWrapper parentRepo = new AccountTrieRepositoryWrapper(parentStorage);
    ZKTrie parentTrie = loadAccountTrie(DEFAULT_TRIE_ROOT, parentRepo);
    parentTrie.putWithTrace(
            account.getHkey(), account.getAddress(), account.getEncodedBytes());
    parentTrie.putWithTrace(
            zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());
    parentTrie.commit();
    final Bytes32 parentRoot = parentTrie.getTopRootHash();

    LayeredWorldStateStorage layeredStorage = new LayeredWorldStateStorage(parentStorage);
    AccountTrieRepositoryWrapper layeredRepo = new AccountTrieRepositoryWrapper(layeredStorage);
    ZKTrie layeredTrie = loadAccountTrie(parentRoot, layeredRepo);

    Trace trace = layeredTrie.removeWithTrace(account.getHkey(), account.getAddress());
    Trace trace2 =
            layeredTrie.putWithTrace(
                    zkAccount3.getHkey(), zkAccount3.getAddress(), zkAccount3.getEncodedBytes());

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(List.of(trace, trace2)))
            .isEqualToIgnoringWhitespace(getResources("testAddAndDeleteAccounts.json"));
  }

  /** All in overlay: write account1+2, then delete account1, add account3. */
  @Test
  public void testDeleteAndAdd_allInOverlay() throws IOException {
    final ZkAccount zkAccount2 = makeAccount2Contract();
    final ZkAccount zkAccount3 = makeAccount3();
    MutableZkAccount account = getAccountOne();

    ParentState parent = createEmptyParent();

    LayeredWorldStateStorage layeredStorage = new LayeredWorldStateStorage(parent.storage());
    AccountTrieRepositoryWrapper layeredRepo = new AccountTrieRepositoryWrapper(layeredStorage);
    ZKTrie layeredTrie = loadAccountTrie(parent.root(), layeredRepo);

    // Build initial state in overlay
    layeredTrie.putWithTrace(
            account.getHkey(), account.getAddress(), account.getEncodedBytes());
    layeredTrie.putWithTrace(
            zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    // Now do the traced operations
    Trace trace = layeredTrie.removeWithTrace(account.getHkey(), account.getAddress());
    Trace trace2 =
            layeredTrie.putWithTrace(
                    zkAccount3.getHkey(), zkAccount3.getAddress(), zkAccount3.getEncodedBytes());

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(List.of(trace, trace2)))
            .isEqualToIgnoringWhitespace(getResources("testAddAndDeleteAccounts.json"));
  }

  /** Account1 in parent, account2 in overlay, then delete account1 + add account3 in overlay. */
  @Test
  public void testDeleteAndAdd_account1InParentAccount2InOverlay() throws IOException {
    final ZkAccount zkAccount2 = makeAccount2Contract();
    final ZkAccount zkAccount3 = makeAccount3();
    MutableZkAccount account = getAccountOne();

    // Parent: only account1
    InMemoryWorldStateStorage parentStorage = new InMemoryWorldStateStorage();
    AccountTrieRepositoryWrapper parentRepo = new AccountTrieRepositoryWrapper(parentStorage);
    ZKTrie parentTrie = loadAccountTrie(DEFAULT_TRIE_ROOT, parentRepo);
    parentTrie.putWithTrace(
            account.getHkey(), account.getAddress(), account.getEncodedBytes());
    parentTrie.commit();
    final Bytes32 parentRoot = parentTrie.getTopRootHash();

    // Overlay: add account2, then delete account1, add account3
    LayeredWorldStateStorage layeredStorage = new LayeredWorldStateStorage(parentStorage);
    AccountTrieRepositoryWrapper layeredRepo = new AccountTrieRepositoryWrapper(layeredStorage);
    ZKTrie layeredTrie = loadAccountTrie(parentRoot, layeredRepo);

    // Build state: add account2
    layeredTrie.putWithTrace(
            zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    // Traced operations
    Trace trace = layeredTrie.removeWithTrace(account.getHkey(), account.getAddress());
    Trace trace2 =
            layeredTrie.putWithTrace(
                    zkAccount3.getHkey(), zkAccount3.getAddress(), zkAccount3.getEncodedBytes());

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(List.of(trace, trace2)))
            .isEqualToIgnoringWhitespace(getResources("testAddAndDeleteAccounts.json"));
  }

  @SuppressWarnings({"SameParameterValue", "ConstantConditions", "resource"})
  private String getResources(final String fileName) throws IOException {
    var classLoader = LayeredWorldStateTraceTest.class.getClassLoader();
    return new String(
            classLoader.getResourceAsStream(fileName).readAllBytes(), StandardCharsets.UTF_8);
  }
}
