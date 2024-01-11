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

import static net.consensys.shomei.util.bytes.MimcSafeBytes.safeByte32;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.util.bytes.MimcSafeBytes;
import net.consensys.zkevm.HashProvider;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.Test;

public class ZkAccountTest {

  @Test
  public void testHashZeroAccount() {
    final ZkAccount zkAccount =
        new ZkAccount(
            new AccountKey(Hash.ZERO, Address.ZERO),
            0L,
            Wei.ZERO,
            Hash.ZERO,
            Hash.ZERO,
            safeByte32(Hash.ZERO),
            0L);

    assertThat(HashProvider.trieHash(zkAccount.getEncodedBytes()))
        .isEqualTo(
            Bytes32.fromHexString(
                "0x0f170eaef9275fd6098a06790c63a141e206e0520738a4cf5cf5081d495e8682"));
  }

  @Test
  public void testEncodedBytesSerialization() {
    AccountKey accountKey =
        new AccountKey(Address.fromHexString("0x742d35Cc6634C0532925a3b844Bc454e4438f44e"));
    UInt256 nonce = UInt256.valueOf(42);
    Wei balance = Wei.fromHexString("0x56bc75e2d63100000");
    Hash storageRoot = Hash.wrap(Bytes32.random());
    Hash mimcCodeHash = Hash.wrap(Bytes32.random());
    MimcSafeBytes<Bytes32> keccakCodeHash = safeByte32(Bytes32.random());
    UInt256 codeSize = UInt256.valueOf(100);

    ZkAccount originalAccount =
        new ZkAccount(
            accountKey, nonce, balance, storageRoot, mimcCodeHash, keccakCodeHash, codeSize);

    MimcSafeBytes<Bytes> encodedBytes = originalAccount.getEncodedBytes();
    ZkAccount deserializedAccount =
        ZkAccount.fromEncodedBytes(accountKey, encodedBytes.getOriginalUnsafeValue());

    assertThat(deserializedAccount).isEqualToComparingFieldByField(originalAccount);
  }
}
