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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.consensys.shomei.rpc.client.model.SimulateV1Response;

import java.math.BigInteger;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.EncodingContext;
import org.hyperledger.besu.ethereum.core.encoding.TransactionEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests that BesuSimulateClient can decode typed (EIP-1559) transaction RLP. Previously, the client
 * used Transaction.readFrom(BytesValueRLPInput) which does not handle the type prefix byte,
 * causing: "Cannot enter a lists, input is fully consumed (at bytes 0-0: [])"
 */
@ExtendWith(MockitoExtension.class)
public class BesuSimulateClientTest {

  private static final String TEST_PRIVATE_KEY =
      "0x4b2c1f9a7e3d5068bf12ca9e0d8374a6519f2e7c8b03d61a5f94e72c8d0163ab";
  private static final String MOCK_TRIELOG = "0xdeadbeef";

  @Mock private Vertx vertx;
  @Mock private WebClient webClient;
  @Mock private HttpRequest<Buffer> httpRequest;
  @Mock private HttpResponse<Buffer> httpResponse;

  private BesuSimulateClient client;

  @BeforeEach
  void setUp() {
    client = new BesuSimulateClient(vertx, webClient, "127.0.0.1", 8545);
  }

  private static KeyPair createTestKeyPair() {
    final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();
    return signatureAlgorithm.createKeyPair(
        signatureAlgorithm.createPrivateKey(
            Bytes.fromHexString(TEST_PRIVATE_KEY).toUnsignedBigInteger()));
  }

  @SuppressWarnings("unchecked")
  private void stubSuccessfulBesuResponse() {
    when(webClient.request(eq(HttpMethod.POST), anyInt(), anyString(), anyString()))
        .thenReturn(httpRequest);
    when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
    when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);

    // Build a valid SimulateV1Response JSON with a trielog
    final String responseJson =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":[{\"trieLog\":\""
            + MOCK_TRIELOG
            + "\",\"calls\":[{\"status\":\"0x1\"}]}]}";

    when(httpResponse.bodyAsJson(SimulateV1Response.class))
        .thenReturn(Json.decodeValue(responseJson, SimulateV1Response.class));

    doAnswer(
            invocation -> {
              final Handler<AsyncResult<HttpResponse<Buffer>>> handler =
                  invocation.getArgument(1);
              handler.handle(
                  new AsyncResult<>() {
                    @Override
                    public HttpResponse<Buffer> result() {
                      return httpResponse;
                    }

                    @Override
                    public Throwable cause() {
                      return null;
                    }

                    @Override
                    public boolean succeeded() {
                      return true;
                    }

                    @Override
                    public boolean failed() {
                      return false;
                    }
                  });
              return null;
            })
        .when(httpRequest)
        .sendJson(any(), any(Handler.class));
  }

  @Test
  public void shouldDecodeEip1559TypedTransactionAndReturnTrielog() throws Exception {
    stubSuccessfulBesuResponse();

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

    final String txRlp =
        TransactionEncoder.encodeOpaqueBytes(eip1559Tx, EncodingContext.POOLED_TRANSACTION)
            .toHexString();
    final CompletableFuture<String> future = client.simulateTransaction(7L, txRlp);

    final String trieLog = future.get();
    assertThat(trieLog).isEqualTo(MOCK_TRIELOG);
    verify(httpRequest).sendJson(any(), any());
  }

  @Test
  public void shouldDecodeLegacyTransactionAndReturnTrielog() throws Exception {
    stubSuccessfulBesuResponse();

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

    final String txRlp =
        TransactionEncoder.encodeOpaqueBytes(legacyTx, EncodingContext.POOLED_TRANSACTION)
            .toHexString();
    final CompletableFuture<String> future = client.simulateTransaction(7L, txRlp);

    final String trieLog = future.get();
    assertThat(trieLog).isEqualTo(MOCK_TRIELOG);
    verify(httpRequest).sendJson(any(), any());
  }
}
