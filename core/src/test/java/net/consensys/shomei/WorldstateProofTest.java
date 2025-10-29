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
package net.consensys.shomei;

import static net.consensys.shomei.util.TestFixtureGenerator.createDumDigest;
import static net.consensys.shomei.util.bytes.ShomeiSafeBytesProvider.unsafeFromBytes;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.consensys.shomei.storage.worldstate.InMemoryWorldStateStorage;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.json.JsonTraceParser;
import net.consensys.shomei.trie.proof.MerkleProof;
import net.consensys.shomei.trie.storage.AccountTrieRepositoryWrapper;
import net.consensys.shomei.util.bytes.ShomeiSafeBytes;
import net.consensys.shomei.util.bytes.ShomeiSafeBytesProvider;
import net.consensys.zkevm.HashProvider;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WorldstateProofTest {

  private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();

  @BeforeEach
  public void setup() {
    JSON_OBJECT_MAPPER.registerModules(JsonTraceParser.modules);
  }

  @Test
  public void testGetProofForMissingKey() throws IOException {

    final Bytes32 key = createDumDigest(36);
    final Hash hkey = HashProvider.trieHash(key);

    ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    final MerkleProof proof =
        accountStateTrie.getProof(hkey, ShomeiSafeBytesProvider.safeByte32(key));

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(proof))
        .isEqualToIgnoringWhitespace(getResources("testGetProofForMissingKey.json"));
  }

  @Test
  public void testGetProofForAvailableKey() throws IOException {

    final ShomeiSafeBytes<Bytes> key = unsafeFromBytes(createDumDigest(36));
    final ShomeiSafeBytes<Bytes> value = unsafeFromBytes(createDumDigest(32));
    final Hash hkey = HashProvider.trieHash(key);

    ZKTrie accountStateTrie =
        ZKTrie.createTrie(new AccountTrieRepositoryWrapper(new InMemoryWorldStateStorage()));

    accountStateTrie.putWithTrace(hkey, key, value);

    accountStateTrie.commit();

    final MerkleProof proof = accountStateTrie.getProof(hkey, key);

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(proof))
        .isEqualToIgnoringWhitespace(getResources("testGetProofForAvailableKey.json"));
  }

  @SuppressWarnings({"SameParameterValue", "ConstantConditions", "resource"})
  private String getResources(final String fileName) throws IOException {
    var classLoader = WorldstateProofTest.class.getClassLoader();
    return new String(
        classLoader.getResourceAsStream(fileName).readAllBytes(), StandardCharsets.UTF_8);
  }
}
