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

import java.math.BigInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests that BesuSimulateClient can decode typed (EIP-1559) transaction RLP. Previously, the client
 * used Transaction.readFrom(BytesValueRLPInput) which does not handle the type prefix byte,
 * causing: "Cannot enter a lists, input is fully consumed (at bytes 0-0: [])"
 */
public class BesuSimulateClientTest {

  private static final String TEST_PRIVATE_KEY =
      "0x4b2c1f9a7e3d5068bf12ca9e0d8374a6519f2e7c8b03d61a5f94e72c8d0163ab";

  // Point to a non-listening port so the HTTP call fails fast after decode succeeds
  private static final String BESU_HOST = "127.0.0.1";
  private static final int BESU_PORT = 1; // unlikely to be listening

  private static Vertx vertx;
  private static BesuSimulateClient client;

  @BeforeAll
  static void setUp() {
    vertx = Vertx.vertx();
    client = new BesuSimulateClient(vertx, BESU_HOST, BESU_PORT);
  }

  @AfterAll
  static void tearDown() {
    client.close();
    vertx.close();
  }

  private static KeyPair createTestKeyPair() {
    final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();
    return signatureAlgorithm.createKeyPair(
        signatureAlgorithm.createPrivateKey(
            Bytes.fromHexString(TEST_PRIVATE_KEY).toUnsignedBigInteger()));
  }

  /**
   * Verifies that simulateTransaction successfully decodes an EIP-1559 typed transaction.
   * The HTTP call to Besu will fail (no server), but the decode must succeed — so the error
   * must be a connection error, NOT "Failed to decode transaction RLP".
   */
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

    final String txRlpHex = eip1559Tx.encoded().toHexString();

    final CompletableFuture<String> future = client.simulateTransaction(7L, txRlpHex);

    // The future should fail with a connection error (no Besu server), NOT an RLP decode error
    try {
      future.get();
    } catch (ExecutionException | InterruptedException e) {
      assertThat(e.getCause().getMessage()).doesNotContain("Failed to decode transaction RLP");
      assertThat(e.getCause().getMessage()).doesNotContain("Cannot enter a lists");
    }
  }

  /**
   * Verifies that simulateTransaction successfully decodes a legacy (FRONTIER) transaction.
   */
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

    final String txRlpHex = legacyTx.encoded().toHexString();

    final CompletableFuture<String> future = client.simulateTransaction(7L, txRlpHex);

    try {
      future.get();
    } catch (ExecutionException | InterruptedException e) {
      assertThat(e.getCause().getMessage()).doesNotContain("Failed to decode transaction RLP");
      assertThat(e.getCause().getMessage()).doesNotContain("Cannot enter a lists");
    }
  }
}
