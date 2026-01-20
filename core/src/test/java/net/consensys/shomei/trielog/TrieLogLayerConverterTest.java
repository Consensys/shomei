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
package net.consensys.shomei.trielog;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;

import net.consensys.shomei.storage.worldstate.InMemoryWorldStateStorage;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

public class TrieLogLayerConverterTest {

  // fixture generated from besu-shomei-plugin TrieLogFactoryTests.trielogfixture
  private static final String TRIELOG_FIXTURE =
      "0xf8d6a0000000000000000000000000000000000000000000000000000000000000000001f847940000000000000000000000000000000000000000c98086feeddeadbeef8080e6e5a0290decd9548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e56380018080f8699400000000000000000000000000000000deadbeef80f85080f84c80880de0b6b3a7640000a0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470a0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a4708080";
  private static final AccountKey MOCK_ACCOUNT =
      new AccountKey(Address.fromHexString("0xdeadbeef"));

  private final TrieLogLayerConverter converter =
      new TrieLogLayerConverter(new InMemoryWorldStateStorage());

  @Test
  public void assertTrieLogDecoding() {
    var trielog =
        converter.decodeTrieLog(new BytesValueRLPInput(Bytes.fromHexString(TRIELOG_FIXTURE), true));

    assertThat(trielog).isNotNull();
    var mockAccount = trielog.getAccount(MOCK_ACCOUNT);
    assertThat(mockAccount).isPresent();
    assertThat(mockAccount.get().getNonce()).isEqualTo(UInt256.ZERO);
    assertThat(mockAccount.get().getBalance()).isEqualTo(Wei.fromEth(1));
    assertThat(mockAccount.get().getCodeHash().getOriginalUnsafeValue()).isEqualTo(Hash.EMPTY);
  }
}
