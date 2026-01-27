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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
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

    try {
      // Decode the RLP transaction to extract fields
      final Bytes transactionBytes = Bytes.fromHexString(transactionRlp);
      final Transaction transaction =
          Transaction.readFrom(new BytesValueRLPInput(transactionBytes, false));

      // Create block overrides with parent block number
      final Map<String, String> blockOverrides =
          Collections.singletonMap("number", "0x" + Long.toHexString(parentBlockNumber + 1));

      // Extract transaction fields for the call
      final String chainId =
          transaction.getChainId().map(cid -> "0x" + cid.toString(16)).orElse(null);
      final String from = transaction.getSender().toHexString();
      final String to = transaction.getTo().map(address -> address.toHexString()).orElse(null);
      final String gas = "0x" + Long.toHexString(transaction.getGasLimit());
      final String value = transaction.getValue().toHexString();
      final String nonce = "0x" + Long.toHexString(transaction.getNonce());
      final String input = transaction.getPayload().toHexString();

      // Handle gas pricing fields based on transaction type
      String gasPrice = null;
      String maxPriorityFeePerGas = null;
      String maxFeePerGas = null;
      String maxFeePerBlobGas = null;

      if (transaction.getGasPrice().isPresent()) {
        gasPrice = transaction.getGasPrice().get().toHexString();
      }
      if (transaction.getMaxPriorityFeePerGas().isPresent()) {
        maxPriorityFeePerGas = transaction.getMaxPriorityFeePerGas().get().toHexString();
      }
      if (transaction.getMaxFeePerGas().isPresent()) {
        maxFeePerGas = transaction.getMaxFeePerGas().get().toHexString();
      }
      if (transaction.getMaxFeePerBlobGas().isPresent()) {
        maxFeePerBlobGas = transaction.getMaxFeePerBlobGas().get().toHexString();
      }

      // Extract access list if present
      final List<Map<String, Object>> accessList =
          transaction.getAccessList().map(
                  al ->
                      al.stream()
                          .map(
                              entry -> {
                                final Map<String, Object> accessEntry = new HashMap<>();
                                accessEntry.put("address", entry.address().toHexString());
                                accessEntry.put(
                                    "storageKeys",
                                    entry.storageKeys().stream()
                                        .map(Bytes::toHexString)
                                        .collect(Collectors.toList()));
                                return accessEntry;
                              })
                          .collect(Collectors.toList()))
              .orElse(null);

      // Extract blob versioned hashes if present
      final List<String> blobVersionedHashes =
          transaction.getBlobsWithCommitments().isPresent()
              ? transaction.getBlobsWithCommitments().get().getVersionedHashes().stream()
                  .map(vh -> vh.toBytes().toHexString())
                  .collect(Collectors.toList())
              : null;

      // Create transaction call with decoded fields
      final SimulateV1Request.TransactionCall transactionCall =
          new SimulateV1Request.TransactionCall(
              chainId,
              from,
              to,
              gas,
              gasPrice,
              maxPriorityFeePerGas,
              maxFeePerGas,
              maxFeePerBlobGas,
              value,
              nonce,
              input,
              accessList,
              blobVersionedHashes);

      // Create block state call
      final SimulateV1Request.BlockStateCall blockStateCall =
          new SimulateV1Request.BlockStateCall(blockOverrides, List.of(transactionCall));

    // Create params with returnTrieLog=true
    final SimulateV1Request.SimulateV1Params params =
        new SimulateV1Request.SimulateV1Params(List.of(blockStateCall), true, true);

    // Specify the parent block number as the block parameter for simulation context
    final String blockParameter = "0x" + Long.toHexString(parentBlockNumber);

    final SimulateV1Request jsonRpcRequest = new SimulateV1Request(requestId, params, blockParameter);

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

                    // Check if any calls in the block result have errors
                    if (blockResult.getCalls() != null && !blockResult.getCalls().isEmpty()) {
                      final SimulateV1Response.CallResult firstCall =
                          blockResult.getCalls().get(0);
                      if (firstCall.getError() != null) {
                        final String errorMessage =
                            String.format(
                                "Transaction simulation failed [code=%d]: %s",
                                firstCall.getError().getCode(),
                                firstCall.getError().getMessage());
                        LOG.atDebug()
                            .setMessage("eth_simulateV1 call error: {}")
                            .addArgument(errorMessage)
                            .log();
                        completableFuture.completeExceptionally(new RuntimeException(errorMessage));
                        return;
                      }
                    }

                    final String trieLog = blockResult.getTrieLog();

                    if (trieLog != null) {
                      LOG.atInfo()
                          .setMessage("eth_simulateV1 returned trielog: length={}, prefix={}")
                          .addArgument(trieLog.length())
                          .addArgument(trieLog.length() > 100 ? trieLog.substring(0, 100) : trieLog)
                          .log();
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
    } catch (Exception e) {
      LOG.error("Failed to decode transaction RLP: {}", e.getMessage());
      LOG.debug("Exception decoding transaction RLP", e);
      completableFuture.completeExceptionally(
          new RuntimeException("Failed to decode transaction RLP: " + e.getMessage(), e));
    }

    return completableFuture;
  }
}
