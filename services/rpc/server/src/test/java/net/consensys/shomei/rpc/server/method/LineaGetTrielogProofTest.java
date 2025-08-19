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

  private LineaGetTrielogProof method;
  private Hash testParentBlockHash;
  private Hash testBlockHash;
  private Address testAddress;
  private AccountKey testAccountKey;
  private ZkAccount testAccount;
  private TrieLogLayer testTrieLogLayer;

  @BeforeEach
  public void setup() {
    // Setup test data
    testParentBlockHash = Hash.fromHexString("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
    testBlockHash = Hash.fromHexString("0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890");
    testAddress = Address.fromHexString("0x1000000000000000000000000000000000000001");
    testAccountKey = new AccountKey(testAddress);
    
    // Create test account (this represents the UPDATED state)
    testAccount = new ZkAccount(
        testAccountKey,
        UInt256.valueOf(2), // Updated nonce
        Wei.of(2000),      // Updated balance
        Hash.wrap(EMPTY_TRIE.getTopRootHash()),
        Hash.ZERO,
        safeByte32(Hash.ZERO),
        UInt256.ZERO
    );

    // Create prior account state (this represents the PRE-STATE we want to prove)
    ZkAccount priorAccount = new ZkAccount(
        testAccountKey,
        UInt256.valueOf(1), // Prior nonce
        Wei.of(1000),      // Prior balance
        Hash.wrap(EMPTY_TRIE.getTopRootHash()),
        Hash.ZERO,
        safeByte32(Hash.ZERO),
        UInt256.ZERO
    );

    // Create test TrieLogLayer
    testTrieLogLayer = new TrieLogLayer();
    testTrieLogLayer.setBlockHash(testBlockHash);
    testTrieLogLayer.setBlockNumber(100L);
    // Account existed before (priorAccount) and was updated (testAccount)
    testTrieLogLayer.addAccountChange(testAccountKey, priorAccount, testAccount, false);
    // Storage slot had a prior value and was updated
    testTrieLogLayer.addStorageChange(testAccountKey, new StorageSlotKey(UInt256.valueOf(1)), UInt256.valueOf(10), UInt256.valueOf(42), false);

    // Setup mocks
    final ZkEvmWorldState zkEvmWorldState = mock(ZkEvmWorldState.class);
    final WorldStateStorage worldStateStorage = mock(WorldStateStorage.class);
    
    lenient().when(worldStateArchive.getCachedWorldState(eq(testParentBlockHash)))
        .thenReturn(Optional.of(zkEvmWorldState));
    lenient().when(worldStateArchive.getTrieLogLayerConverter())
        .thenReturn(trieLogLayerConverter);
    lenient().when(zkEvmWorldState.getZkEvmWorldStateStorage()).thenReturn(worldStateStorage);
    lenient().when(zkEvmWorldState.getStateRootHash()).thenReturn(Hash.wrap(EMPTY_TRIE.getTopRootHash()));
    lenient().when(worldStateStorage.getTrieNode(any(Bytes.class), any(Bytes.class)))
        .thenReturn(Optional.of(EMPTY_TRIE.getTopRootHash()));
    lenient().when(worldStateStorage.getNearestKeys(any(Bytes.class)))
        .thenReturn(
            new TrieStorage.Range(
                Map.entry(Bytes.of(0x01), FlattenedLeaf.HEAD),
                Optional.empty(),
                Map.entry(Bytes.of(0x02), FlattenedLeaf.TAIL)));

    method = new LineaGetTrielogProof(worldStateArchive, trieLogLayerConverter);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("linea_getTrielogProof");
  }

  @Test
  public void shouldReturnErrorWhenParentBlockMissing() {
    // Given
    when(worldStateArchive.getCachedWorldState(eq(testParentBlockHash)))
        .thenReturn(Optional.empty());
    
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
    // Given
    final String serializedTrieLog = createRealisticSerializedTrieLog();
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
    // Should have one storage proof for the storage slot we added
    assertThat(proof.getStorageProofs()).hasSize(1);
  }

  @Test
  public void shouldReturnProofsForTrielogWithOnlyAccountChanges() {
    // Given
    ZkAccount priorAccount = new ZkAccount(
        testAccountKey,
        UInt256.valueOf(1),
        Wei.of(1000),
        Hash.wrap(EMPTY_TRIE.getTopRootHash()),
        Hash.ZERO,
        safeByte32(Hash.ZERO),
        UInt256.ZERO
    );
    
    TrieLogLayer accountOnlyTrieLog = new TrieLogLayer();
    accountOnlyTrieLog.setBlockHash(testBlockHash);
    accountOnlyTrieLog.setBlockNumber(100L);
    accountOnlyTrieLog.addAccountChange(testAccountKey, priorAccount, testAccount, false);
    // No storage changes
    
    final String serializedTrieLog = createRealisticSerializedTrieLog();
    when(trieLogLayerConverter.decodeTrieLog(any())).thenReturn(accountOnlyTrieLog);
    
    final JsonRpcRequestContext request = createRequest(serializedTrieLog, testParentBlockHash.toHexString());

    // When
    final JsonRpcResponse response = method.response(request);

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
    // Given
    TrieLogLayer emptyTrieLog = new TrieLogLayer();
    emptyTrieLog.setBlockHash(testBlockHash);
    emptyTrieLog.setBlockNumber(100L);
    // No account or storage changes
    
    final String serializedTrieLog = createRealisticSerializedTrieLog();
    when(trieLogLayerConverter.decodeTrieLog(any())).thenReturn(emptyTrieLog);
    
    final JsonRpcRequestContext request = createRequest(serializedTrieLog, testParentBlockHash.toHexString());

    // When
    final JsonRpcResponse response = method.response(request);

    // Then
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    
    @SuppressWarnings("unchecked")
    List<MerkleAccountProof> accountProofs = (List<MerkleAccountProof>) successResponse.getResult();
    
    assertThat(accountProofs).isEmpty();
  }

  @Test
  public void shouldHandleMultipleAccountsAndStorageSlots() {
    // Given
    TrieLogLayer multiAccountTrieLog = new TrieLogLayer();
    multiAccountTrieLog.setBlockHash(testBlockHash);
    multiAccountTrieLog.setBlockNumber(100L);
    
    // Add multiple accounts with proper prior states
    Address address2 = Address.fromHexString("0x2000000000000000000000000000000000000002");
    AccountKey accountKey2 = new AccountKey(address2);
    
    ZkAccount priorAccount1 = new ZkAccount(
        testAccountKey,
        UInt256.valueOf(1),
        Wei.of(1000),
        Hash.wrap(EMPTY_TRIE.getTopRootHash()),
        Hash.ZERO,
        safeByte32(Hash.ZERO),
        UInt256.ZERO
    );
    
    ZkAccount priorAccount2 = new ZkAccount(
        accountKey2,
        UInt256.valueOf(1),
        Wei.of(1500),
        Hash.wrap(EMPTY_TRIE.getTopRootHash()),
        Hash.ZERO,
        safeByte32(Hash.ZERO),
        UInt256.ZERO
    );
    
    ZkAccount account2 = new ZkAccount(
        accountKey2,
        UInt256.valueOf(3),
        Wei.of(3000),
        Hash.wrap(EMPTY_TRIE.getTopRootHash()),
        Hash.ZERO,
        safeByte32(Hash.ZERO),
        UInt256.ZERO
    );
    
    multiAccountTrieLog.addAccountChange(testAccountKey, priorAccount1, testAccount, false);
    multiAccountTrieLog.addAccountChange(accountKey2, priorAccount2, account2, false);
    
    // Add multiple storage slots for first account (with proper prior values)
    multiAccountTrieLog.addStorageChange(testAccountKey, new StorageSlotKey(UInt256.valueOf(1)), UInt256.valueOf(5), UInt256.valueOf(42), false);
    multiAccountTrieLog.addStorageChange(testAccountKey, new StorageSlotKey(UInt256.valueOf(2)), UInt256.valueOf(10), UInt256.valueOf(52), false);
    
    // Add storage slot for second account (with proper prior value)
    multiAccountTrieLog.addStorageChange(accountKey2, new StorageSlotKey(UInt256.valueOf(1)), UInt256.valueOf(20), UInt256.valueOf(100), false);
    
    final String serializedTrieLog = createRealisticSerializedTrieLog();
    when(trieLogLayerConverter.decodeTrieLog(any())).thenReturn(multiAccountTrieLog);
    
    final JsonRpcRequestContext request = createRequest(serializedTrieLog, testParentBlockHash.toHexString());

    // When
    final JsonRpcResponse response = method.response(request);

    // Then
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    
    @SuppressWarnings("unchecked")
    List<MerkleAccountProof> accountProofs = (List<MerkleAccountProof>) successResponse.getResult();
    
    assertThat(accountProofs).hasSize(2);
    
    // Find proofs for each account
    Optional<MerkleAccountProof> proof1 = accountProofs.stream()
        .filter(p -> p.getAccountProof().getKey().equals(testAccountKey.accountHash()))
        .findFirst();
    Optional<MerkleAccountProof> proof2 = accountProofs.stream()
        .filter(p -> p.getAccountProof().getKey().equals(accountKey2.accountHash()))
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

  private String createRealisticSerializedTrieLog() {
    // Create a minimal but realistic RLP-encoded trielog
    // For testing purposes, we'll create a simple hex string that represents encoded trielog data
    // In a real scenario, this would be actual RLP-encoded TrieLogLayer data
    
    // This is a simplified representation - in practice, the TrieLogLayerConverter would handle the actual encoding/decoding
    return "0x" + Bytes.random(64).toHexString().substring(2); // Remove the 0x prefix and add it back
  }
}
