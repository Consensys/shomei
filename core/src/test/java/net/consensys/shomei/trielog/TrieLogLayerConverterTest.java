/*
 * Copyright ConsenSys Software Inc., 2025
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

package net.consensys.shomei.trielog;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.storage.worldstate.InMemoryWorldStateStorage;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.junit.jupiter.api.Test;

public class TrieLogLayerConverterTest {

  private final String trieLogFixture = "0xf90189a097beef3f7046e4d64d51d848e95bf487bbfd7f6949c506419a6f8a6a6d0591dd831c4f77f8ba94407c019cb116c67ed58b27fe51801a14f185ed3d80f8a1f84e822108880688220d56c402a6a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470f84e822109880688220d56c1c32ca056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a4708080f8a694f58b398cecb1aa5424ed0e37797da0ec734eeb3d80f88df8440180a054de39a823f8be0ff8700960aeb1d6c9e592e1ab4bddfb79461e584ab44b6e11a0ad680f3433e6e75823323e35afeef01b50e767225c37b70f663f1b091b4e6d78f8440180a054de39a823f8be0ff8700960aeb1d6c9e592e1ab4bddfb79461e584ab44b6e11a0ad680f3433e6e75823323e35afeef01b50e767225c37b70f663f1b091b4e6d788080";
  private final TrieLogLayerConverter converter = new TrieLogLayerConverter(new InMemoryWorldStateStorage());


  @Test
  public void assertTrieLogDecoding() {
    var trielog = converter.decodeTrieLog(new BytesValueRLPInput(
        Bytes.fromHexString(trieLogFixture), true));

    assertThat(trielog).isNotNull();

  }
}
