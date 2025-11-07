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

import static net.consensys.shomei.util.bytes.PoseidonSafeBytesUtils.safeByte32;
import static net.consensys.shomei.util.bytes.PoseidonSafeBytesUtils.safeCode;
import static net.consensys.shomei.util.bytes.PoseidonSafeBytesUtils.safeUInt256;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;

import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.util.bytes.PoseidonSafeBytes;
import net.consensys.zkevm.HashProvider;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

public class ZkAccountTest {

  @Test
  public void testHashZeroAccount() {

    final ZkAccount zkAccount =
        new ZkAccount(
            new AccountKey(Hash.ZERO, Address.ZERO),
            safeUInt256(UInt256.valueOf(0L)),
            safeUInt256(UInt256.valueOf(0L)),
            Hash.ZERO,
            ZKTrie.DEFAULT_TRIE_ROOT,
            safeByte32(Hash.ZERO),
            safeUInt256(UInt256.valueOf(0L)));

    assertThat(zkAccount.getEncodedBytes().hash())
        .isEqualTo(
            Bytes32.fromHexString(
                "0x0be39dd910329801041c54896705cb664779584732a232276e59ce2e7ca1b5a7"));
  }

  @Test
  public void testEOAAccount() {
    final ZkAccount zkAccount =
        new ZkAccount(
            new AccountKey(Hash.ZERO, Address.ZERO),
            safeUInt256(UInt256.valueOf(65L)),
            safeUInt256(UInt256.valueOf(5690L)),
            Hash.fromHexString(
                "0x0b1dfeef3db4956540da8a5f785917ef1ba432e521368da60a0a1ce430425666"),
            Hash.fromHexString(
                "0x729aac4455d43f2c69e53bb75f8430193332a4c32cafd9995312fa8346929e73"),
            safeByte32(
                Hash.fromHexString(
                    "0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")),
            safeUInt256(UInt256.valueOf(0L)));

    assertThat(zkAccount.getEncodedBytes().hash())
        .isEqualTo(
            Bytes32.fromHexString(
                "0x60cef4a3679fc56e66cba6c72cc9e536600ca999026ef7d22fd7595e62f9fdb8"));
  }

  @Test
  public void testAnotherEOAAccount() {
    final ZkAccount zkAccount =
        new ZkAccount(
            new AccountKey(Hash.ZERO, Address.ZERO),
            safeUInt256(UInt256.valueOf(65L)),
            safeUInt256(UInt256.valueOf(835L)),
            Hash.fromHexString(
                "0x1c41acc261451aae253f621857172d6339919d18059f35921a50aafc69eb5c39"),
            Hash.fromHexString(
                "0x7b688b215329825e5b00e4aa4e1857bc17afab503a87ecc063614b9b227106b2"),
            safeByte32(
                Hash.fromHexString(
                    "0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")),
            safeUInt256(UInt256.valueOf(0L)));

    assertThat(zkAccount.getEncodedBytes().hash())
        .isEqualTo(
            Bytes32.fromHexString(
                "0x5e6056aa0619c47d3a01df970de314a04daf224d21e8a73b0003d7a4286a7538"));
  }

  @Test
  public void testEncodedBytesSerialization() {
    AccountKey accountKey =
        new AccountKey(Address.fromHexString("0x742d35Cc6634C0532925a3b844Bc454e4438f44e"));
    PoseidonSafeBytes<UInt256> nonce = safeUInt256(UInt256.valueOf(42L));
    PoseidonSafeBytes<UInt256> balance = safeUInt256(UInt256.fromHexString("0x56bc75e2d63100000"));
    Hash storageRoot = Hash.wrap(Bytes32.random());
    Hash shomeiCodeHash = Hash.wrap(Bytes32.random());
    PoseidonSafeBytes<Bytes32> keccakCodeHash = safeByte32(Bytes32.random());
    PoseidonSafeBytes<UInt256> codeSize = safeUInt256(UInt256.valueOf(100L));

    ZkAccount originalAccount =
        new ZkAccount(
            accountKey, nonce, balance, storageRoot, shomeiCodeHash, keccakCodeHash, codeSize);

    PoseidonSafeBytes<Bytes> encodedBytes = originalAccount.getEncodedBytes();
    ZkAccount deserializedAccount =
        ZkAccount.fromEncodedBytes(accountKey, encodedBytes.getOriginalUnsafeValue());

    assertThat(deserializedAccount).isEqualToComparingFieldByField(originalAccount);
  }

  @Test
  public void testContractCodeEncoding() {
    final Bytes code =
        Bytes.fromHexString("0x495340db00ecc17b5cb435d5731f8d6635e6b3ef42507a8303a068d178a95d22");
    ;
    final PoseidonSafeBytes<Bytes> safeCode = safeCode(code);
    final ZkAccount zkAccount =
        new ZkAccount(
            new AccountKey(Hash.ZERO, Address.ZERO),
            safeUInt256(UInt256.valueOf(65L)),
            safeUInt256(UInt256.valueOf(835L)),
            ZKTrie.DEFAULT_TRIE_ROOT,
            safeCode.hash(),
            safeByte32(HashProvider.keccak256(code)),
            safeUInt256(UInt256.valueOf(0L)));
    assertThat(zkAccount.getEncodedBytes().hash())
        .isEqualTo(
            Bytes32.fromHexString(
                "0x5b4f38da3b5579846022b90a4a9ca1096653055722e75ebc0d4f68ba22d712c9"));
  }
}
