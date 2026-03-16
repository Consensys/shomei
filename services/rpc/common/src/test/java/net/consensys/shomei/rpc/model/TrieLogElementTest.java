/*
 * Copyright Consensys Software Inc., 2026
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
package net.consensys.shomei.rpc.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class TrieLogElementTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void deserializeFromJson() throws Exception {
    String json =
        "{\"blockNumber\":29524257,"
            + "\"blockHash\":\"0x2ff25b3771a2d67c2cf9237972c0f182ff60971ff979447fd7c80815d62b7f5d\","
            + "\"trieLog\":\"0xdeadbeef\"}";

    TrieLogElement element = MAPPER.readValue(json, TrieLogElement.class);

    assertThat(element.blockNumber()).isEqualTo(29524257L);
    assertThat(element.blockHash())
        .isEqualTo(
            Bytes32.fromHexString(
                "0x2ff25b3771a2d67c2cf9237972c0f182ff60971ff979447fd7c80815d62b7f5d"));
    assertThat(element.trieLog()).isEqualTo("0xdeadbeef");
  }

  @Test
  void deserializeViaConvertValue() throws Exception {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("blockNumber", 29524257);
    map.put(
        "blockHash",
        "0x2ff25b3771a2d67c2cf9237972c0f182ff60971ff979447fd7c80815d62b7f5d");
    map.put("trieLog", "0xdeadbeef");

    TrieLogElement element = MAPPER.convertValue(map, TrieLogElement.class);

    assertThat(element.blockNumber()).isEqualTo(29524257L);
    assertThat(element.blockHash())
        .isEqualTo(
            Bytes32.fromHexString(
                "0x2ff25b3771a2d67c2cf9237972c0f182ff60971ff979447fd7c80815d62b7f5d"));
    assertThat(element.trieLog()).isEqualTo("0xdeadbeef");
  }

  @Test
  void trieLogIdentifierHasCorrectValues() throws Exception {
    String json =
        "{\"blockNumber\":100,"
            + "\"blockHash\":\"0x0000000000000000000000000000000000000000000000000000000000000001\","
            + "\"trieLog\":\"0x\"}";

    TrieLogElement element = MAPPER.readValue(json, TrieLogElement.class);
    var identifier = element.getTrieLogIdentifier();

    assertThat(identifier.blockNumber()).isEqualTo(100L);
    assertThat(identifier.blockHash())
        .isEqualTo(
            Bytes32.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"));
  }
}
