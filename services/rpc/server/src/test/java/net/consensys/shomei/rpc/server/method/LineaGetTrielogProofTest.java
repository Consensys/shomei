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

package net.consensys.shomei.rpc.server.method;

import static net.consensys.shomei.trie.ZKTrie.EMPTY_TRIE;

import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.storage.InMemoryStorageProvider;
import net.consensys.shomei.storage.TraceManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.consensys.shomei.ZkAccount;
import net.consensys.shomei.proof.MerkleAccountProof;
import net.consensys.shomei.rpc.server.error.ShomeiJsonRpcErrorResponse;
import net.consensys.shomei.storage.ZkWorldStateArchive;
import net.consensys.shomei.storage.worldstate.WorldStateStorage;
import net.consensys.shomei.trie.model.FlattenedLeaf;
import net.consensys.shomei.trie.storage.TrieStorage;
import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.trielog.StorageSlotKey;
import net.consensys.shomei.trielog.TrieLogLayer;
import net.consensys.shomei.trielog.TrieLogLayerConverter;
import net.consensys.shomei.util.bytes.MimcSafeBytes;

import static net.consensys.shomei.util.bytes.MimcSafeBytes.safeByte32;
import net.consensys.shomei.worldview.ZkEvmWorldState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class LineaGetTrielogProofTest {

  @Mock public ZkWorldStateArchive worldStateArchive;
  @Mock public TrieLogLayerConverter trieLogLayerConverter;

  private Hash testParentBlockHash;
  private Hash testBlockHash;
  private Address testAddress;
  private AccountKey testAccountKey;
  private ZkAccount testAccount, testPriorAccount;

  @BeforeEach
  public void setup() {
    // Setup common test data used across tests
    testParentBlockHash = Hash.fromHexString("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
    testBlockHash = Hash.fromHexString("0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890");
    testAddress = Address.fromHexString("0x1000000000000000000000000000000000000001");
    testAccountKey = new AccountKey(testAddress);
    
    // Create shared test accounts (used by multiple tests)
    testPriorAccount = new ZkAccount(
        testAccountKey,
        UInt256.valueOf(1), // Prior nonce
        Wei.of(1000),      // Prior balance
        ZKTrie.DEFAULT_TRIE_ROOT, // Will be updated when we add storage
        Hash.ZERO,
        safeByte32(Hash.ZERO),
        UInt256.ZERO
    );
    
    testAccount = new ZkAccount(
        testAccountKey,
        UInt256.valueOf(2), // Updated nonce
        Wei.of(2000),      // Updated balance
        ZKTrie.DEFAULT_TRIE_ROOT, // Will be updated when we add storage
        Hash.ZERO,
        safeByte32(Hash.ZERO),
        UInt256.ZERO
    );

    // Setup basic mocks used by all tests
    lenient().when(worldStateArchive.getTrieLogLayerConverter())
        .thenReturn(trieLogLayerConverter);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    final LineaGetTrielogProof method = new LineaGetTrielogProof(worldStateArchive, trieLogLayerConverter);
    assertThat(method.getName()).isEqualTo("linea_getTrielogProof");
  }

  @Test
  public void shouldReturnErrorWhenParentBlockMissing() {
    // Given
    when(worldStateArchive.getCachedWorldState(eq(testParentBlockHash)))
        .thenReturn(Optional.empty());
    
    final LineaGetTrielogProof method = new LineaGetTrielogProof(worldStateArchive, trieLogLayerConverter);
    final String serializedTrieLog = "0x1234"; // dummy serialized trielog
    final JsonRpcRequestContext request = createRequest(serializedTrieLog, testParentBlockHash.toHexString());

    // When
    final JsonRpcResponse response = method.response(request);

    // Then
    final JsonRpcResponse expectedResponse =
        new ShomeiJsonRpcErrorResponse(
            null, RpcErrorType.INVALID_REQUEST, "BLOCK_MISSING_IN_CHAIN - parent block is missing");
    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnErrorWhenTrielogSerializationInvalid() {
    // Given
    final LineaGetTrielogProof method = new LineaGetTrielogProof(worldStateArchive, trieLogLayerConverter);
    final String invalidSerializedTrieLog = "0xinvalid";
    final JsonRpcRequestContext request = createRequest(invalidSerializedTrieLog, testParentBlockHash.toHexString());

    // When
    final JsonRpcResponse response = method.response(request);

    // Then
    assertThat(response).isInstanceOf(ShomeiJsonRpcErrorResponse.class);
    ShomeiJsonRpcErrorResponse errorResponse = (ShomeiJsonRpcErrorResponse) response;
    assertThat(errorResponse.getJsonError().message()).contains("INVALID_TRIELOG_SERIALIZATION");
  }

  @Test
  public void shouldReturnProofsForValidTrielogWithAccountAndStorageChanges() {
    // Given - create real world state with the account and storage for the parent state
    final StorageSlotKey storageSlot = new StorageSlotKey(UInt256.valueOf(1));
    final ZkEvmWorldState realWorldState = createRealWorldState(Map.of(
        testPriorAccount, Map.of(storageSlot, UInt256.valueOf(10))
    ));

    // Create test TrieLogLayer with real state changes
    final TrieLogLayer testTrieLogLayer = new TrieLogLayer();
    testTrieLogLayer.setBlockHash(testBlockHash);
    testTrieLogLayer.setBlockNumber(100L);
    // Account existed before (priorAccount) and was updated (testAccount)
    testTrieLogLayer.addAccountChange(testAccountKey, testPriorAccount, testAccount, false);
    // Storage slot had a prior value and was updated
    testTrieLogLayer.addStorageChange(testAccountKey, new StorageSlotKey(UInt256.valueOf(1)), UInt256.valueOf(10), UInt256.valueOf(42), false);

    // Setup method with real world state
    final LineaGetTrielogProof method = new LineaGetTrielogProof(worldStateArchive, trieLogLayerConverter);
    lenient().when(worldStateArchive.getCachedWorldState(eq(testParentBlockHash)))
        .thenReturn(Optional.of(realWorldState));

    // Use fake hex string and mock the decode method
    final String serializedTrieLog = "0x1234567890abcdef"; // dummy serialized trielog
    when(trieLogLayerConverter.decodeTrieLog(any())).thenReturn(testTrieLogLayer);
    
    final JsonRpcRequestContext request = createRequest(serializedTrieLog, testParentBlockHash.toHexString());

    // When
    final JsonRpcResponse response = method.response(request);

    // Then
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    
    @SuppressWarnings("unchecked")
    List<MerkleAccountProof> accountProofs = (List<MerkleAccountProof>) successResponse.getResult();
    
    assertThat(accountProofs).isNotEmpty();
    assertThat(accountProofs).hasSize(1);
    
    // Verify that the proof contains the expected account
    MerkleAccountProof proof = accountProofs.get(0);
    assertThat(proof).isNotNull();
    assertThat(proof.getAccountProof()).isNotNull();
    assertThat(proof.getStorageProofs()).isNotNull();
    
    
    // With real world state containing actual storage, we should now get the storage proof
    assertThat(proof.getStorageProofs()).hasSize(1);
  }

  @Test
  public void shouldReturnProofsForTrielogWithOnlyAccountChanges() {
    // Given - create prior account with empty storage (DEFAULT_TRIE_ROOT means no storage)
    ZkAccount priorAccountNoStorage = new ZkAccount(
        testAccountKey,
        UInt256.valueOf(1),
        Wei.of(1000),
        Hash.wrap(EMPTY_TRIE.getTopRootHash()),
        Hash.ZERO,
        safeByte32(Hash.ZERO),
        UInt256.ZERO
    );
    
    // Create real world state with account but no storage
    final ZkEvmWorldState worldStateForAccountOnly = createRealWorldState(Map.of(
        priorAccountNoStorage, Map.of()
    ));
    
    // Create trielog for account-only changes
    TrieLogLayer accountOnlyTrieLog = new TrieLogLayer();
    accountOnlyTrieLog.setBlockHash(testBlockHash);
    accountOnlyTrieLog.setBlockNumber(100L);
    accountOnlyTrieLog.addAccountChange(testAccountKey, priorAccountNoStorage, testAccount, false);
    // No storage changes
    
    // Setup method with real world state
    final LineaGetTrielogProof methodForAccountOnly = new LineaGetTrielogProof(worldStateArchive, trieLogLayerConverter);
    lenient().when(worldStateArchive.getCachedWorldState(eq(testParentBlockHash)))
        .thenReturn(Optional.of(worldStateForAccountOnly));
    
    // Use fake hex string and mock the decode method
    final String serializedTrieLog = "0x1234567890abcdef"; // dummy serialized trielog
    when(trieLogLayerConverter.decodeTrieLog(any())).thenReturn(accountOnlyTrieLog);
    
    final JsonRpcRequestContext request = createRequest(serializedTrieLog, testParentBlockHash.toHexString());

    // When
    final JsonRpcResponse response = methodForAccountOnly.response(request);

    // Then
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    
    @SuppressWarnings("unchecked")
    List<MerkleAccountProof> accountProofs = (List<MerkleAccountProof>) successResponse.getResult();
    
    assertThat(accountProofs).hasSize(1);
    MerkleAccountProof proof = accountProofs.get(0);
    assertThat(proof.getStorageProofs()).isEmpty(); // No storage proofs expected
  }

  @Test
  public void shouldReturnEmptyProofsForEmptyTrielog() {
    // Given - create empty real world state
    final ZkEvmWorldState emptyWorldState = createRealWorldState(Map.of());
    
    // Create empty trielog  
    TrieLogLayer emptyTrieLog = new TrieLogLayer();
    emptyTrieLog.setBlockHash(testBlockHash);
    emptyTrieLog.setBlockNumber(100L);
    // No account or storage changes
    
    // Setup method with empty world state
    final LineaGetTrielogProof methodForEmptyTest = new LineaGetTrielogProof(worldStateArchive, trieLogLayerConverter);
    lenient().when(worldStateArchive.getCachedWorldState(eq(testParentBlockHash)))
        .thenReturn(Optional.of(emptyWorldState));
    
    // Use fake hex string and mock the decode method
    final String serializedTrieLog = "0x1234567890abcdef"; // dummy serialized trielog
    when(trieLogLayerConverter.decodeTrieLog(any())).thenReturn(emptyTrieLog);
    
    final JsonRpcRequestContext request = createRequest(serializedTrieLog, testParentBlockHash.toHexString());

    // When
    final JsonRpcResponse response = methodForEmptyTest.response(request);

    // Then
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    
    @SuppressWarnings("unchecked")
    List<MerkleAccountProof> accountProofs = (List<MerkleAccountProof>) successResponse.getResult();
    
    assertThat(accountProofs).isEmpty();
  }

  @Test
  public void shouldHandleMultipleAccountsAndStorageSlots() {
    // Given - create accounts with proper prior states and storage
    Address address2 = Address.fromHexString("0x2000000000000000000000000000000000000002");
    AccountKey accountKey2 = new AccountKey(address2);
    
    ZkAccount priorAccount2 = new ZkAccount(
        accountKey2,
        UInt256.valueOf(1),
        Wei.of(1500),
        ZKTrie.DEFAULT_TRIE_ROOT, // Will be updated when we add storage
        Hash.ZERO,
        safeByte32(Hash.ZERO),
        UInt256.ZERO
    );
    
    ZkAccount account2 = new ZkAccount(
        accountKey2,
        UInt256.valueOf(3),
        Wei.of(3000),
        ZKTrie.DEFAULT_TRIE_ROOT, // Will be updated when we add storage
        Hash.ZERO,
        safeByte32(Hash.ZERO),
        UInt256.ZERO
    );
    
    // Create real world state with both accounts and their storage
    final ZkEvmWorldState multiAccountWorldState = createRealWorldState(Map.of(
        testPriorAccount, Map.of(
            new StorageSlotKey(UInt256.valueOf(1)), UInt256.valueOf(5),
            new StorageSlotKey(UInt256.valueOf(2)), UInt256.valueOf(10)
        ),
        priorAccount2, Map.of(new StorageSlotKey(UInt256.valueOf(1)), UInt256.valueOf(20))
    ));
    
    // Create trielog for multi-account changes
    TrieLogLayer multiAccountTrieLog = new TrieLogLayer();
    multiAccountTrieLog.setBlockHash(testBlockHash);
    multiAccountTrieLog.setBlockNumber(100L);
    
    multiAccountTrieLog.addAccountChange(testAccountKey, testPriorAccount, testAccount, false);
    multiAccountTrieLog.addAccountChange(accountKey2, priorAccount2, account2, false);
    
    // Add multiple storage slots for first account (with proper prior values)
    multiAccountTrieLog.addStorageChange(testAccountKey, new StorageSlotKey(UInt256.valueOf(1)), UInt256.valueOf(5), UInt256.valueOf(42), false);
    multiAccountTrieLog.addStorageChange(testAccountKey, new StorageSlotKey(UInt256.valueOf(2)), UInt256.valueOf(10), UInt256.valueOf(52), false);
    
    // Add storage slot for second account (with proper prior value)
    multiAccountTrieLog.addStorageChange(accountKey2, new StorageSlotKey(UInt256.valueOf(1)), UInt256.valueOf(20), UInt256.valueOf(100), false);
    
    // Setup method with multi-account world state
    final LineaGetTrielogProof methodForMultiTest = new LineaGetTrielogProof(worldStateArchive, trieLogLayerConverter);
    lenient().when(worldStateArchive.getCachedWorldState(eq(testParentBlockHash)))
        .thenReturn(Optional.of(multiAccountWorldState));
    
    // Use fake hex string and mock the decode method
    final String serializedTrieLog = "0x1234567890abcdef"; // dummy serialized trielog
    when(trieLogLayerConverter.decodeTrieLog(any())).thenReturn(multiAccountTrieLog);
    
    final JsonRpcRequestContext request = createRequest(serializedTrieLog, testParentBlockHash.toHexString());

    // When
    final JsonRpcResponse response = methodForMultiTest.response(request);

    // Then
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    
    @SuppressWarnings("unchecked")
    List<MerkleAccountProof> accountProofs = (List<MerkleAccountProof>) successResponse.getResult();
    
    assertThat(accountProofs).hasSize(2);
    
    // Find proofs for each account (using address as key, not account hash)
    Optional<MerkleAccountProof> proof1 = accountProofs.stream()
        .filter(p -> p.getAccountProof().getKey().equals(testAddress))
        .findFirst();
    Optional<MerkleAccountProof> proof2 = accountProofs.stream()
        .filter(p -> p.getAccountProof().getKey().equals(address2))
        .findFirst();
    
    assertThat(proof1).isPresent();
    assertThat(proof2).isPresent();
    
    // First account should have 2 storage proofs
    assertThat(proof1.get().getStorageProofs()).hasSize(2);
    // Second account should have 1 storage proof
    assertThat(proof2.get().getStorageProofs()).hasSize(1);
  }

  private JsonRpcRequestContext createRequest(final String serializedTrieLog, final String parentBlockHash) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest(
            "2.0",
            "linea_getTrielogProof",
            new Object[] {
              serializedTrieLog,
              parentBlockHash
            }));
  }

  /**
   * Creates a real ZkEvmWorldState initialized with the specified accounts and their storage.
   * This eliminates the need for complex mocks and provides a reliable foundation for testing.
   */
  private ZkEvmWorldState createRealWorldState(Map<ZkAccount, Map<StorageSlotKey, UInt256>> accountsWithStorage) {
    // Create a real world state using InMemoryStorageProvider
    final InMemoryStorageProvider storageProvider = new InMemoryStorageProvider();
    final TraceManager traceManager = storageProvider.getTraceManager();
    final ZkEvmWorldState worldState = new ZkEvmWorldState(storageProvider.getWorldStateStorage(), traceManager);
    
    // Create a TrieLogLayer to set up the initial state
    final TrieLogLayer setupLayer = new TrieLogLayer();
    setupLayer.setBlockHash(Hash.ZERO);
    setupLayer.setBlockNumber(0L);
    
    // Add each account and its storage to the setup layer
    for (Map.Entry<ZkAccount, Map<StorageSlotKey, UInt256>> entry : accountsWithStorage.entrySet()) {
      final ZkAccount account = entry.getKey();
      final Map<StorageSlotKey, UInt256> storage = entry.getValue();
      final AccountKey accountKey = new AccountKey(account.getAddress());
      
      // Add the account
      setupLayer.addAccountChange(accountKey, null, account, false);
      
      // Add each storage slot
      for (Map.Entry<StorageSlotKey, UInt256> storageEntry : storage.entrySet()) {
        setupLayer.addStorageChange(accountKey, storageEntry.getKey(), null, storageEntry.getValue(), false);
      }
    }
    
    // Roll forward to create the state and commit
    worldState.getAccumulator().rollForward(setupLayer);
    worldState.commit(0L, Hash.ZERO, true);
    
    return worldState;
  }

}
