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
package net.consensys.shomei.rpc.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.EncodingContext;
import org.hyperledger.besu.ethereum.core.encoding.TransactionDecoder;
import org.junit.jupiter.api.Test;

/**
 * Tests that BesuSimulateClient can decode typed (EIP-1559) transaction RLP. Previously, the client
 * used Transaction.readFrom(BytesValueRLPInput) which does not handle the type prefix byte,
 * causing: "Cannot enter a lists, input is fully consumed (at bytes 0-0: [])"
 */
public class BesuSimulateClientTest {

  private static final String TEST_PRIVATE_KEY =
      "0x4b2c1f9a7e3d5068bf12ca9e0d8374a6519f2e7c8b03d61a5f94e72c8d0163ab";

  private static KeyPair createTestKeyPair() {
    final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();
    return signatureAlgorithm.createKeyPair(
        signatureAlgorithm.createPrivateKey(
            Bytes.fromHexString(TEST_PRIVATE_KEY).toUnsignedBigInteger()));
  }

  @Test
  public void shouldDecodeEip1559TypedTransactionRlp() {
    final Transaction eip1559Tx =
        Transaction.builder()
            .type(TransactionType.EIP1559)
            .chainId(BigInteger.valueOf(1337))
            .nonce(0)
            .maxPriorityFeePerGas(Wei.of(100_000_000))
            .maxFeePerGas(Wei.of(1_000_000_000))
            .gasLimit(300_000)
            .to(Address.fromHexString("0x3535353535353535353535353535353535353535"))
            .value(Wei.ZERO)
            .payload(Bytes.fromHexString("0x68656c6c6f"))
            .signAndBuild(createTestKeyPair());

    final Bytes encoded = eip1559Tx.encoded();

    assertThatNoException()
        .isThrownBy(
            () ->
                TransactionDecoder.decodeOpaqueBytes(encoded, EncodingContext.POOLED_TRANSACTION));

    final Transaction decoded =
        TransactionDecoder.decodeOpaqueBytes(encoded, EncodingContext.POOLED_TRANSACTION);

    assertThat(decoded).isNotNull();
    assertThat(decoded.getType().getEthSerializedType()).isEqualTo((byte) 0x02);
    assertThat(decoded.getSender()).isEqualTo(eip1559Tx.getSender());
    assertThat(decoded.getTo()).isPresent();
    assertThat(decoded.getMaxFeePerGas()).isPresent();
    assertThat(decoded.getMaxPriorityFeePerGas()).isPresent();
  }

  @Test
  public void shouldDecodeLegacyTransactionRlp() {
    final Transaction legacyTx =
        Transaction.builder()
            .type(TransactionType.FRONTIER)
            .nonce(0)
            .gasPrice(Wei.of(100_000_000))
            .gasLimit(21_000)
            .to(Address.fromHexString("0x3535353535353535353535353535353535353535"))
            .value(Wei.of(1_000_000_000_000L))
            .payload(Bytes.EMPTY)
            .chainId(BigInteger.ONE)
            .signAndBuild(createTestKeyPair());

    final Bytes encoded = legacyTx.encoded();

    assertThatNoException()
        .isThrownBy(
            () ->
                TransactionDecoder.decodeOpaqueBytes(encoded, EncodingContext.POOLED_TRANSACTION));

    final Transaction decoded =
        TransactionDecoder.decodeOpaqueBytes(encoded, EncodingContext.POOLED_TRANSACTION);

    assertThat(decoded).isNotNull();
    assertThat(decoded.getSender()).isEqualTo(legacyTx.getSender());
  }
}
