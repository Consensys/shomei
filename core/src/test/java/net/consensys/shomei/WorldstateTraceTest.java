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
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.json.JsonTraceParser;
import net.consensys.shomei.trie.storage.AccountTrieRepositoryWrapper;
import net.consensys.shomei.trie.storage.StorageTrieRepositoryWrapper;
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
import org.junit.Before;
import org.junit.Test;

public class WorldstateTraceTest {

  private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();

  @Before
  public void setup() {
    JSON_OBJECT_MAPPER.registerModules(JsonTraceParser.modules);
  }

  @Test
  public void testTraceReadZero() throws IOException {

    final Bytes32 key = createDumDigest(36);
    final Hash hkey = HashProvider.trieHash(key);

    ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    Trace trace = accountStateTrie.readWithTrace(hkey, MimcSafeBytes.safeByte32(key));

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(trace))
        .isEqualToIgnoringWhitespace(getResources("testTraceReadZero.json"));
  }

  @Test
  public void testTraceReadSimpleValue() throws IOException {

    final MimcSafeBytes<Bytes> key = unsafeFromBytes(createDumDigest(36));
    final MimcSafeBytes<Bytes> value = unsafeFromBytes(createDumDigest(32));
    final Hash hkey = HashProvider.trieHash(key);

    ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    accountStateTrie.putWithTrace(hkey, key, value);

    Trace trace = accountStateTrie.readWithTrace(hkey, key);

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(trace))
        .isEqualToIgnoringWhitespace(getResources("testTraceReadSimpleValue.json"));
  }

  @Test
  public void testTraceReadAccount() throws IOException {

    ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    MutableZkAccount account = getAccountOne();
    accountStateTrie.putWithTrace(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());

    Trace trace = accountStateTrie.readWithTrace(account.getHkey(), account.getAddress());

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(trace))
        .isEqualToIgnoringWhitespace(getResources("testTraceReadAccount.json"));
  }

  @Test
  public void testTraceStateWithAnAccount() throws IOException {

    ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    MutableZkAccount account = getAccountOne();
    Trace trace =
        accountStateTrie.putWithTrace(
            account.getHkey(), account.getAddress(), account.getEncodedBytes());

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(List.of(trace)))
        .isEqualToIgnoringWhitespace(getResources("testTraceStateWithAnAccount.json"));
  }

  @Test
  public void testWorldStateWithTwoAccounts() throws IOException {

    final ZkAccount zkAccount2 =
        new ZkAccount(
            new AccountKey(createDumAddress(41)),
            42,
            Wei.of(354),
            DEFAULT_TRIE_ROOT,
            EMPTY_CODE_HASH,
            EMPTY_KECCAK_CODE_HASH,
            0L);

    ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    MutableZkAccount account = getAccountOne();
    Trace trace =
        accountStateTrie.putWithTrace(
            account.getHkey(), account.getAddress(), account.getEncodedBytes());
    Trace trace2 =
        accountStateTrie.putWithTrace(
            zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(List.of(trace, trace2)))
        .isEqualToIgnoringWhitespace(getResources("testWorldStateWithTwoAccount.json"));
  }

  @Test
  public void testWorldStateWithAccountAndContract() throws IOException {

    final ZkAccount zkAccount2 =
        new ZkAccount(
            new AccountKey(createDumAddress(47)),
            41,
            Wei.of(15353),
            DEFAULT_TRIE_ROOT,
            Hash.wrap(createDumDigest(75)),
            createDumFullBytes(15),
            7L);

    ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    MutableZkAccount account = getAccountOne();
    final Trace trace =
        accountStateTrie.putWithTrace(
            account.getHkey(), account.getAddress(), account.getEncodedBytes());
    final Trace trace2 =
        accountStateTrie.putWithTrace(
            zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(List.of(trace, trace2)))
        .isEqualToIgnoringWhitespace(getResources("testWorldStateWithAccountAndContract.json"));
  }

  @Test
  public void testWorldStateWithUpdateContractStorage() throws IOException {
    final MutableZkAccount zkAccount2 =
        new MutableZkAccount(
            new AccountKey(createDumAddress(47)),
            createDumFullBytes(15),
            Hash.wrap(createDumDigest(75)),
            7L,
            41,
            Wei.of(15353),
            DEFAULT_TRIE_ROOT);

    final ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    MutableZkAccount account = getAccountOne();
    final Trace trace =
        accountStateTrie.putWithTrace(
            account.getHkey(),
            account.getAddress(),
            account.getEncodedBytes()); // not retest already tested trace
    final Trace trace2 =
        accountStateTrie.putWithTrace(
            zkAccount2.getHkey(),
            zkAccount2.getAddress(),
            zkAccount2.getEncodedBytes()); // not retest already tested trace

    // Write something in the storage of B
    final ZKTrie account2Storage =
        ZKTrie.createTrie(
            new StorageTrieRepositoryWrapper(
                zkAccount2.hashCode(), new InMemoryWorldStateStorage()));
    final MimcSafeBytes<Bytes32> slotKey = createDumFullBytes(14);
    final Hash slotKeyHash = HashProvider.trieHash(slotKey);
    final MimcSafeBytes<Bytes32> slotValue = createDumFullBytes(18);
    final Trace trace3 = account2Storage.putWithTrace(slotKeyHash, slotKey, slotValue);

    zkAccount2.setStorageRoot(Hash.wrap(account2Storage.getTopRootHash()));
    final Trace trace4 =
        accountStateTrie.putWithTrace(
            zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(List.of(trace, trace2, trace3, trace4)))
        .isEqualToIgnoringWhitespace(getResources("testWorldStateWithUpdateContractStorage.json"));
  }

  @Test
  public void testWorldStateWithDeleteAccountAndStorage() throws IOException {

    final MutableZkAccount zkAccount2 =
        new MutableZkAccount(
            new AccountKey(createDumAddress(47)),
            createDumFullBytes(15),
            Hash.wrap(createDumDigest(75)),
            7L,
            41,
            Wei.of(15353),
            DEFAULT_TRIE_ROOT);

    final ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    MutableZkAccount account = getAccountOne();
    accountStateTrie.putWithTrace(
        account.getHkey(),
        account.getAddress(),
        account.getEncodedBytes()); // not retest already tested trace
    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(),
        zkAccount2.getAddress(),
        zkAccount2.getEncodedBytes()); // not retest already tested trace

    // Write something in the storage of B
    final ZKTrie account2Storage =
        ZKTrie.createTrie(
            new StorageTrieRepositoryWrapper(
                zkAccount2.hashCode(), new InMemoryWorldStateStorage()));
    final MimcSafeBytes<Bytes32> slotKey = createDumFullBytes(14);
    final Hash slotKeyHash = HashProvider.trieHash(slotKey);
    final MimcSafeBytes<Bytes32> slotValue = createDumFullBytes(18);
    account2Storage.putWithTrace(slotKeyHash, slotKey, slotValue);
    zkAccount2.setStorageRoot(Hash.wrap(account2Storage.getTopRootHash()));
    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    // Delete account 1
    Trace trace = accountStateTrie.removeWithTrace(account.getHkey(), account.getAddress());

    // clean storage B
    Trace trace2 = account2Storage.removeWithTrace(slotKeyHash, slotKey);
    trace2.setLocation(zkAccount2.getAddress().getOriginalUnsafeValue());

    zkAccount2.setStorageRoot(Hash.wrap(account2Storage.getTopRootHash()));
    Trace trace3 =
        accountStateTrie.putWithTrace(
            zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    // Write again, somewhere else
    final MimcSafeBytes<Bytes32> newSlotKey = createDumFullBytes(11);
    final Hash newSlotKeyHash = HashProvider.trieHash(newSlotKey);
    final MimcSafeBytes<Bytes32> newSlotValue = createDumFullBytes(78);
    Trace trace4 = account2Storage.putWithTrace(newSlotKeyHash, newSlotKey, newSlotValue);
    trace4.setLocation(zkAccount2.getAddress().getOriginalUnsafeValue());

    zkAccount2.setStorageRoot(Hash.wrap(account2Storage.getTopRootHash()));
    Trace trace5 =
        accountStateTrie.putWithTrace(
            zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.getEncodedBytes());

    assertThat(
            JSON_OBJECT_MAPPER.writeValueAsString(List.of(trace, trace2, trace3, trace4, trace5)))
        .isEqualToIgnoringWhitespace(
            getResources("testWorldStateWithDeleteAccountAndStorage.json"));
  }

  @Test
  public void testAddAndDeleteAccounts() throws IOException {

    final ZkAccount zkAccount2 =
        new ZkAccount(
            new AccountKey(createDumAddress(47)),
            41,
            Wei.of(15353),
            DEFAULT_TRIE_ROOT,
            Hash.wrap(createDumDigest(75)),
            createDumFullBytes(15),
            7L);

    final ZkAccount zkAccount3 =
        new ZkAccount(
            new AccountKey(createDumAddress(120)),
            48,
            Wei.of(9835),
            DEFAULT_TRIE_ROOT,
            Hash.wrap(createDumDigest(54)),
            createDumFullBytes(85),
            19L);

    final ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    MutableZkAccount account = getAccountOne();
    accountStateTrie.putWithTrace(
        account.getHkey(),
        account.getAddress(),
        account.getEncodedBytes()); // not retest already tested trace
    accountStateTrie.putWithTrace(
        zkAccount2.getHkey(),
        zkAccount2.getAddress(),
        zkAccount2.getEncodedBytes()); // not retest already tested trace

    Trace trace = accountStateTrie.removeWithTrace(account.getHkey(), account.getAddress());

    Trace trace2 =
        accountStateTrie.putWithTrace(
            zkAccount3.getHkey(), zkAccount3.getAddress(), zkAccount3.getEncodedBytes());

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(List.of(trace, trace2)))
        .isEqualToIgnoringWhitespace(getResources("testAddAndDeleteAccounts.json"));
  }

  @SuppressWarnings({"SameParameterValue", "ConstantConditions", "resource"})
  private String getResources(final String fileName) throws IOException {
    var classLoader = WorldstateTraceTest.class.getClassLoader();
    return new String(
        classLoader.getResourceAsStream(fileName).readAllBytes(), StandardCharsets.UTF_8);
  }
}
