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

package net.consensys.shomei.rpc.client;

import net.consensys.shomei.observer.TrieLogObserver;
import net.consensys.shomei.rpc.client.model.GetRawTrieLogRpcRequest;
import net.consensys.shomei.rpc.client.model.GetRawTrieLogRpcResponse;
import net.consensys.shomei.rpc.model.TrieLogElement;
import net.consensys.shomei.storage.TrieLogManager;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("UnusedVariable")
public class GetRawTrieLogClient {

  private static final String APPLICATION_JSON = "application/json";

  private static final Logger LOG = LoggerFactory.getLogger(GetRawTrieLogClient.class);
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

  private final TrieLogManager trieLogManager;

  public GetRawTrieLogClient(
      final TrieLogManager trieLogManager, final String besuHttpHost, final int besuHttpPort) {
    final Vertx vertx = Vertx.vertx();
    final WebClientOptions options = new WebClientOptions();
    this.webClient = WebClient.create(vertx, options);
    this.trieLogManager = trieLogManager;
    this.besuHttpHost = besuHttpHost;
    this.besuHttpPort = besuHttpPort;
  }

  public CompletableFuture<List<TrieLogObserver.TrieLogIdentifier>> getTrieLog(
      final long startBlockNumber, final long endBlockNumber) {

    final CompletableFuture<List<TrieLogObserver.TrieLogIdentifier>> completableFuture =
        new CompletableFuture<>();
    final int requestId = RANDOM.nextInt();
    final GetRawTrieLogRpcRequest jsonRpcRequest =
        new GetRawTrieLogRpcRequest(requestId, new Object[] {startBlockNumber, endBlockNumber});
    // Send the request to the JSON-RPC service
    webClient
        .request(HttpMethod.POST, besuHttpPort, besuHttpHost, "/")
        .putHeader("Content-Type", APPLICATION_JSON)
        .timeout(TimeUnit.SECONDS.toMillis(30))
        .sendJson(
            jsonRpcRequest,
            response -> {
              if (response.succeeded()) {
                final GetRawTrieLogRpcResponse responseBody =
                    response.result().bodyAsJson(GetRawTrieLogRpcResponse.class);
                LOG.atDebug()
                    .setMessage("response received for getTrieLogsByRange {}")
                    .addArgument(responseBody)
                    .log();

                try {
                  final List<TrieLogObserver.TrieLogIdentifier> trieLogIdentifiers =
                      new ArrayList<>();
                  for (TrieLogElement trieLogElement : responseBody.getResult()) {
                    trieLogManager.saveTrieLog(
                        trieLogElement.getTrieLogIdentifier(),
                        Bytes.fromHexString(trieLogElement.trieLog()));
                    trieLogIdentifiers.add(trieLogElement.getTrieLogIdentifier());
                  }
                  trieLogManager.commitTrieLogStorage();
                  completableFuture.complete(trieLogIdentifiers);

                } catch (RuntimeException e) {
                  LOG.error("failed to handle new TrieLog {}", e.getMessage());
                  LOG.debug("exception handling TrieLog", e);
                  completableFuture.completeExceptionally(e);
                }
              } else {
                LOG.trace(
                    "failed to retrieve trie log from besu {}", response.cause().getMessage());
                completableFuture.completeExceptionally(
                    new RuntimeException("cannot retrieve trielog from besu " + response.result()));
              }
            });

    return completableFuture;
  }
}
