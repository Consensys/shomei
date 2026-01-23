/*
 * Copyright ConsenSys Software Inc., 2026
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

import net.consensys.shomei.rpc.client.model.SimulateV1Request;
import net.consensys.shomei.rpc.client.model.SimulateV1Response;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BesuSimulateClient {

  private static final String APPLICATION_JSON = "application/json";
  private static final Logger LOG = LoggerFactory.getLogger(BesuSimulateClient.class);
  private static final SecureRandom RANDOM;

  static {
    try {
      RANDOM = SecureRandom.getInstanceStrong();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private final WebClient webClient;
  private final String besuHttpHost;
  private final int besuHttpPort;

  public BesuSimulateClient(final String besuHttpHost, final int besuHttpPort) {
    final Vertx vertx = Vertx.vertx();
    final WebClientOptions options = new WebClientOptions();
    this.webClient = WebClient.create(vertx, options);
    this.besuHttpHost = besuHttpHost;
    this.besuHttpPort = besuHttpPort;
  }

  public CompletableFuture<String> simulateTransaction(
      final long parentBlockNumber, final String transactionRlp) {

    final CompletableFuture<String> completableFuture = new CompletableFuture<>();
    final int requestId = RANDOM.nextInt();

    // Create block overrides with parent block number
    final Map<String, String> blockOverrides =
        Collections.singletonMap("number", "0x" + Long.toHexString(parentBlockNumber + 1));

    // Create transaction call with RLP-encoded transaction
    final SimulateV1Request.TransactionCall transactionCall =
        new SimulateV1Request.TransactionCall(transactionRlp);

    // Create block state call
    final SimulateV1Request.BlockStateCall blockStateCall =
        new SimulateV1Request.BlockStateCall(blockOverrides, List.of(transactionCall));

    // Create params with returnTrieLog=true
    final SimulateV1Request.SimulateV1Params params =
        new SimulateV1Request.SimulateV1Params(List.of(blockStateCall), true, true);

    final SimulateV1Request jsonRpcRequest = new SimulateV1Request(requestId, params);

    // Send the request to the JSON-RPC service
    webClient
        .request(HttpMethod.POST, besuHttpPort, besuHttpHost, "/")
        .putHeader("Content-Type", APPLICATION_JSON)
        .timeout(TimeUnit.SECONDS.toMillis(30))
        .sendJson(
            jsonRpcRequest,
            response -> {
              if (response.succeeded()) {
                try {
                  final SimulateV1Response responseBody =
                      response.result().bodyAsJson(SimulateV1Response.class);
                  LOG.atDebug()
                      .setMessage("response received for eth_simulateV1 {}")
                      .addArgument(responseBody)
                      .log();

                  // Check if the response contains an error
                  if (responseBody.getError() != null) {
                    final String errorMessage =
                        String.format(
                            "eth_simulateV1 error [code=%d]: %s",
                            responseBody.getError().getCode(),
                            responseBody.getError().getMessage());
                    LOG.atDebug()
                        .setMessage("eth_simulateV1 returned error: {}")
                        .addArgument(errorMessage)
                        .log();
                    completableFuture.completeExceptionally(new RuntimeException(errorMessage));
                    return;
                  }

                  if (responseBody.getResult() != null
                      && !responseBody.getResult().isEmpty()) {
                    final SimulateV1Response.BlockResult blockResult =
                        responseBody.getResult().get(0);
                    final String trieLog = blockResult.getTrieLog();

                    if (trieLog != null) {
                      completableFuture.complete(trieLog);
                    } else {
                      completableFuture.completeExceptionally(
                          new RuntimeException("No trielog in eth_simulateV1 response"));
                    }
                  } else {
                    completableFuture.completeExceptionally(
                        new RuntimeException("Empty result in eth_simulateV1 response"));
                  }

                } catch (RuntimeException e) {
                  LOG.error("failed to handle eth_simulateV1 response {}", e.getMessage());
                  LOG.debug("exception handling eth_simulateV1 response", e);
                  completableFuture.completeExceptionally(e);
                }
              } else {
                LOG.trace(
                    "failed to call eth_simulateV1 on besu {}", response.cause().getMessage());
                completableFuture.completeExceptionally(
                    new RuntimeException(
                        "cannot call eth_simulateV1 on besu: " + response.cause().getMessage()));
              }
            });

    return completableFuture;
  }
}
