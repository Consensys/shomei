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
package net.consensys.shomei.trie;

import static net.consensys.shomei.trie.DigestGenerator.createDumDigest;
import static net.consensys.shomei.util.bytes.ShomeiSafeBytesProvider.unsafeFromBytes;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.consensys.shomei.trie.json.JsonTraceParser;
import net.consensys.shomei.trie.storage.InMemoryStorage;
import net.consensys.shomei.trie.trace.DeletionTrace;
import net.consensys.shomei.trie.trace.InsertionTrace;
import net.consensys.shomei.trie.trace.ReadTrace;
import net.consensys.shomei.trie.trace.ReadZeroTrace;
import net.consensys.shomei.trie.trace.Trace;
import net.consensys.shomei.trie.trace.UpdateTrace;
import net.consensys.shomei.util.bytes.ShomeiSafeBytes;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TraceSerializationTest {

  private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();

  @BeforeEach
  public void setup() {
    JSON_OBJECT_MAPPER.registerModules(JsonTraceParser.modules);
  }

  @Test
  public void testEncodeAndDecodeInsertionTrace() throws JsonProcessingException {
    final InMemoryStorage storage = new InMemoryStorage();
    ZKTrie zkTrie = ZKTrie.createTrie(storage);

    final ShomeiSafeBytes<Bytes> key = unsafeFromBytes(createDumDigest(58));
    final ShomeiSafeBytes<Bytes> value = unsafeFromBytes(createDumDigest(41));
    final Hash hkey = key.hash();

    final InsertionTrace expectedTrace = (InsertionTrace) zkTrie.putWithTrace(hkey, key, value);

    // try encode and decode
    final InsertionTrace decodedTrace =
        InsertionTrace.readFrom(RLP.input(RLP.encode(expectedTrace::writeTo)));

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(decodedTrace))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(expectedTrace));
  }

  @Test
  public void testEncodeAndDecodeUpdateTrace() throws JsonProcessingException {

    final InMemoryStorage storage = new InMemoryStorage();
    ZKTrie zkTrie = ZKTrie.createTrie(storage);

    final ShomeiSafeBytes<Bytes> key = unsafeFromBytes(createDumDigest(58));
    final ShomeiSafeBytes<Bytes> dumValue = unsafeFromBytes(createDumDigest(41));
    final ShomeiSafeBytes<Bytes> newDumValue = unsafeFromBytes(createDumDigest(42));
    final Hash hkey = key.hash();

    zkTrie.putWithTrace(hkey, key, dumValue);

    final UpdateTrace expectedTrace = (UpdateTrace) zkTrie.putWithTrace(hkey, key, newDumValue);

    // try encode and decode
    final UpdateTrace decodedTrace =
        UpdateTrace.readFrom(RLP.input(RLP.encode(expectedTrace::writeTo)));

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(decodedTrace))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(expectedTrace));
  }

  @Test
  public void testEncodeAndDecodeDeletionTrace() throws JsonProcessingException {
    final InMemoryStorage storage = new InMemoryStorage();
    ZKTrie zkTrie = ZKTrie.createTrie(storage);

    final ShomeiSafeBytes<Bytes> key = unsafeFromBytes(createDumDigest(58));
    final ShomeiSafeBytes<Bytes> value = unsafeFromBytes(createDumDigest(41));
    final Hash hkey = key.hash();

    zkTrie.putWithTrace(hkey, key, value);

    final DeletionTrace expectedTrace = (DeletionTrace) zkTrie.removeWithTrace(hkey, key);

    // try encode and decode
    final DeletionTrace decodedTrace =
        DeletionTrace.readFrom(RLP.input(RLP.encode(expectedTrace::writeTo)));

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(decodedTrace))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(expectedTrace));
  }

  @Test
  public void testEncodeAndDecodeReadTrace() throws JsonProcessingException {

    final InMemoryStorage storage = new InMemoryStorage();
    ZKTrie zkTrie = ZKTrie.createTrie(storage);

    final ShomeiSafeBytes<Bytes> key = unsafeFromBytes(createDumDigest(58));
    final ShomeiSafeBytes<Bytes> dumValue = unsafeFromBytes(createDumDigest(41));
    final Hash hkey = key.hash();

    // try read zero trace before inserting the key in the trie
    final ReadZeroTrace expectedReadZeroTrace = (ReadZeroTrace) zkTrie.readWithTrace(hkey, key);

    // try encode and decode
    final ReadZeroTrace decodedReadZeroTrace =
        ReadZeroTrace.readFrom(RLP.input(RLP.encode(expectedReadZeroTrace::writeTo)));

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(decodedReadZeroTrace))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(expectedReadZeroTrace));

    zkTrie.putWithTrace(hkey, key, dumValue);

    // try read trace
    final ReadTrace expectedReadTrace = (ReadTrace) zkTrie.readWithTrace(hkey, key);

    // try encode and decode
    final ReadTrace decodedReadTrace =
        ReadTrace.readFrom(RLP.input(RLP.encode(expectedReadTrace::writeTo)));

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(decodedReadTrace))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(expectedReadTrace));
  }

  @Test
  public void testEncodeAndDecodeListOfTraces() throws JsonProcessingException {
    final InMemoryStorage storage = new InMemoryStorage();
    ZKTrie zkTrie = ZKTrie.createTrie(storage);

    final ShomeiSafeBytes<Bytes> key = unsafeFromBytes(createDumDigest(58));
    final ShomeiSafeBytes<Bytes> dumValue = unsafeFromBytes(createDumDigest(41));
    final ShomeiSafeBytes<Bytes> newDumValue = unsafeFromBytes(createDumDigest(42));
    final Hash hkey = key.hash();

    List<Trace> expectedTraces = new ArrayList<>();
    expectedTraces.add(zkTrie.putWithTrace(hkey, key, dumValue));
    expectedTraces.add(zkTrie.putWithTrace(hkey, key, newDumValue));

    // try encode and decode
    final List<Trace> decodedTraces = Trace.deserialize(RLP.input(Trace.serialize(expectedTraces)));

    assertThat(JSON_OBJECT_MAPPER.writeValueAsString(decodedTraces))
        .isEqualTo(JSON_OBJECT_MAPPER.writeValueAsString(expectedTraces));
  }
}
