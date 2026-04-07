/*
 * Copyright Consensys Software Inc., 2023
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
import static net.consensys.shomei.util.bytes.PoseidonSafeBytesUtils.safeByte32;
import static net.consensys.shomei.util.bytes.PoseidonSafeBytesUtils.safeUInt256;
import static net.consensys.shomei.util.bytes.PoseidonSafeBytesUtils.unsafeFromBytes;
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
import net.consensys.shomei.util.bytes.PoseidonSafeBytes;
import net.consensys.zkevm.HashProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
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
            safeUInt256(UInt256.valueOf(42)),
            safeUInt256(UInt256.valueOf(354)),
            DEFAULT_TRIE_ROOT,
            EMPTY_CODE_HASH,
            EMPTY_KECCAK_CODE_HASH,
            safeUInt256(UInt256.ZERO));
  }

  private ZkAccount makeAccount2Contract() {
    return new ZkAccount(
            new AccountKey(createDumAddress(47)),
            safeUInt256(UInt256.valueOf(41)),
            safeUInt256(UInt256.valueOf(15353)),
            DEFAULT_TRIE_ROOT,
            createDumDigest(75),
            safeByte32(createDumFullBytes(15)),
            safeUInt256(UInt256.valueOf(7)));
  }

  private MutableZkAccount makeMutableAccount2Contract() {
    return new MutableZkAccount(
            new AccountKey(createDumAddress(47)),
            safeByte32(createDumFullBytes(15)),
            createDumDigest(75),
            safeUInt256(UInt256.valueOf(7)),
            safeUInt256(UInt256.valueOf(41)),
            safeUInt256(UInt256.valueOf(15353)),
            DEFAULT_TRIE_ROOT);
  }

  private ZkAccount makeAccount3() {
    return new ZkAccount(
            new AccountKey(createDumAddress(120)),
            safeUInt256(UInt256.valueOf(48)),
            safeUInt256(UInt256.valueOf(9835)),
            DEFAULT_TRIE_ROOT,
            createDumDigest(54),
            safeByte32(createDumFullBytes(85)),
            safeUInt256(UInt256.valueOf(19)));
  }

  // ===========================================================================
  // READ ZERO
  // ===========================================================================

  /** Original: empty parent, single overlay, read zero. */
  @Test
  public void testTraceReadZero() throws IOException {
    final Bytes32 key = createDumDigest(36);
    final Bytes32 hkey = HashProvider.trieHash(key);

    InMemoryWorldStateStorage parentStorage = new InMemoryWorldStateStorage();
    AccountTrieRepositoryWrapper parentRepo = new AccountTrieRepositoryWrapper(parentStorage);
    ZKTrie parentTrie = loadAccountTrie(DEFAULT_TRIE_ROOT, parentRepo);
    parentTrie.commit();
    final Bytes32 parentRoot = parentTrie.getTopRootHash();

    LayeredWorldStateStorage layeredStorage = new LayeredWorldStateStorage(parentStorage);
    AccountTrieRepositoryWrapper layeredRepo = new AccountTrieRepositoryWrapper(layeredStorage);
    ZKTrie layeredTrie = loadAccountTrie(parentRoot, layeredRepo);

    Trace trace = layeredTrie.readWithTrace(hkey, safeByte32(key));

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(trace))
            .isEqualToIgnoringWhitespace(getResources("testTraceReadZero.json"));
  }

  // ===========================================================================
  // READ SIMPLE VALUE
  // ===========================================================================

  /** Original: write in parent, read from overlay. */
  @Test
  public void testTraceReadSimpleValueFromParent() throws IOException {
    final PoseidonSafeBytes<Bytes> key = unsafeFromBytes(createDumDigest(36));
    final PoseidonSafeBytes<Bytes> value = unsafeFromBytes(createDumDigest(32));
    final Bytes32 hkey = HashProvider.trieHash(key);

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

    final PoseidonSafeBytes<Bytes32> slotKey = safeByte32(createDumFullBytes(14));
    final Bytes32 slotKeyHash = HashProvider.trieHash(slotKey);
    final PoseidonSafeBytes<Bytes32> slotValue = safeByte32(createDumFullBytes(18));
    final Trace trace3 = account2Storage.putWithTrace(slotKeyHash, slotKey, slotValue);
    trace3.setLocation(zkAccount2.getAddress().getOriginalUnsafeValue());
    zkAccount2.setStorageRoot(account2Storage.getTopRootHash());

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

    final PoseidonSafeBytes<Bytes32> slotKey = safeByte32(createDumFullBytes(14));
    final Bytes32 slotKeyHash = HashProvider.trieHash(slotKey);
    final PoseidonSafeBytes<Bytes32> slotValue = safeByte32(createDumFullBytes(18));
    final Trace trace3 = account2Storage.putWithTrace(slotKeyHash, slotKey, slotValue);
    trace3.setLocation(zkAccount2.getAddress().getOriginalUnsafeValue());

    zkAccount2.setStorageRoot(account2Storage.getTopRootHash());
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

    final PoseidonSafeBytes<Bytes32> slotKey = safeByte32(createDumFullBytes(14));
    final Bytes32 slotKeyHash = HashProvider.trieHash(slotKey);
    final PoseidonSafeBytes<Bytes32> slotValue = safeByte32(createDumFullBytes(18));
    final Trace trace3 = account2Storage.putWithTrace(slotKeyHash, slotKey, slotValue);
    trace3.setLocation(zkAccount2.getAddress().getOriginalUnsafeValue());
    zkAccount2.setStorageRoot(account2Storage.getTopRootHash());

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

    final PoseidonSafeBytes<Bytes32> slotKey = safeByte32(createDumFullBytes(14));
    final Bytes32 slotKeyHash = HashProvider.trieHash(slotKey);
    final PoseidonSafeBytes<Bytes32> slotValue = safeByte32(createDumFullBytes(18));
    parentAccount2Storage.putWithTrace(slotKeyHash, slotKey, slotValue);
    parentAccount2Storage.commit();
    final Bytes32 parentStorageRoot = parentAccount2Storage.getTopRootHash();

    zkAccount2.setStorageRoot(parentStorageRoot);
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

    zkAccount2.setStorageRoot(layeredAccount2Storage.getTopRootHash());
    Trace trace3 =
            layeredTrie.putWithTrace(
                    zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    final PoseidonSafeBytes<Bytes32> newSlotKey = safeByte32(createDumFullBytes(11));
    final Bytes32 newSlotKeyHash = HashProvider.trieHash(newSlotKey);
    final PoseidonSafeBytes<Bytes32> newSlotValue = safeByte32(createDumFullBytes(78));
    Trace trace4 =
            layeredAccount2Storage.putWithTrace(newSlotKeyHash, newSlotKey, newSlotValue);
    trace4.setLocation(zkAccount2.getAddress().getOriginalUnsafeValue());

    zkAccount2.setStorageRoot(layeredAccount2Storage.getTopRootHash());
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

    final PoseidonSafeBytes<Bytes32> slotKey = safeByte32(createDumFullBytes(14));
    final Bytes32 slotKeyHash = HashProvider.trieHash(slotKey);
    final PoseidonSafeBytes<Bytes32> slotValue = safeByte32(createDumFullBytes(18));
    parentAccount2Storage.putWithTrace(slotKeyHash, slotKey, slotValue);
    parentAccount2Storage.commit();
    final Bytes32 parentStorageRoot = parentAccount2Storage.getTopRootHash();

    zkAccount2.setStorageRoot(parentStorageRoot);
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

    zkAccount2.setStorageRoot(layeredAccount2Storage.getTopRootHash());
    Trace trace3 =
            layeredTrie.putWithTrace(
                    zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    final PoseidonSafeBytes<Bytes32> newSlotKey = safeByte32(createDumFullBytes(11));
    final Bytes32 newSlotKeyHash = HashProvider.trieHash(newSlotKey);
    final PoseidonSafeBytes<Bytes32> newSlotValue = safeByte32(createDumFullBytes(78));
    Trace trace4 =
            layeredAccount2Storage.putWithTrace(newSlotKeyHash, newSlotKey, newSlotValue);
    trace4.setLocation(zkAccount2.getAddress().getOriginalUnsafeValue());

    zkAccount2.setStorageRoot(layeredAccount2Storage.getTopRootHash());
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

    final PoseidonSafeBytes<Bytes32> slotKey = safeByte32(createDumFullBytes(14));
    final Bytes32 slotKeyHash = HashProvider.trieHash(slotKey);
    final PoseidonSafeBytes<Bytes32> slotValue = safeByte32(createDumFullBytes(18));
    layeredAccount2Storage.putWithTrace(slotKeyHash, slotKey, slotValue);

    zkAccount2.setStorageRoot(layeredAccount2Storage.getTopRootHash());
    layeredTrie.putWithTrace(
            zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    // Now do the traced operations
    Trace trace = layeredTrie.removeWithTrace(account.getHkey(), account.getAddress());

    Trace trace2 = layeredAccount2Storage.removeWithTrace(slotKeyHash, slotKey);
    trace2.setLocation(zkAccount2.getAddress().getOriginalUnsafeValue());

    zkAccount2.setStorageRoot(layeredAccount2Storage.getTopRootHash());
    Trace trace3 =
            layeredTrie.putWithTrace(
                    zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    final PoseidonSafeBytes<Bytes32> newSlotKey = safeByte32(createDumFullBytes(11));
    final Bytes32 newSlotKeyHash = HashProvider.trieHash(newSlotKey);
    final PoseidonSafeBytes<Bytes32> newSlotValue = safeByte32(createDumFullBytes(78));
    Trace trace4 =
            layeredAccount2Storage.putWithTrace(newSlotKeyHash, newSlotKey, newSlotValue);
    trace4.setLocation(zkAccount2.getAddress().getOriginalUnsafeValue());

    zkAccount2.setStorageRoot(layeredAccount2Storage.getTopRootHash());
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
