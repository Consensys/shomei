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

package net.consensys.shomei.rpc.server;

import static com.google.common.collect.Streams.stream;

import net.consensys.shomei.fullsync.FullSyncDownloader;
import net.consensys.shomei.metrics.MetricsService;
import net.consensys.shomei.rpc.server.method.LineaGetProof;
import net.consensys.shomei.rpc.server.method.RollupDeleteZkEVMStateMerkleProofByRange;
import net.consensys.shomei.rpc.server.method.RollupForkChoiceUpdated;
import net.consensys.shomei.rpc.server.method.RollupGetZkEVMBlockNumber;
import net.consensys.shomei.rpc.server.method.RollupGetZkEVMStateMerkleProofV0;
import net.consensys.shomei.rpc.server.method.SendRawTrieLog;
import net.consensys.shomei.storage.ZkWorldStateArchive;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import org.hyperledger.besu.ethereum.api.handlers.HandlerFactory;
import org.hyperledger.besu.ethereum.api.handlers.TimeoutOptions;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcServiceException;
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.DefaultAuthenticationService;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.BaseJsonRpcProcessor;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcExecutor;
import org.hyperledger.besu.ethereum.api.jsonrpc.health.HealthService;
import org.hyperledger.besu.ethereum.api.jsonrpc.health.LivenessCheck;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.Logging403ErrorHandler;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AdminChangeLogLevel;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.util.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The JsonRpcService class is responsible for handling JSON-RPC requests and processing them. It
 * provides methods for handling various JSON-RPC methods and generating appropriate JSON-RPC
 * responses.
 */
public class JsonRpcService extends AbstractVerticle {

  private static final Logger LOG = LoggerFactory.getLogger(JsonRpcService.class);
  private static final String APPLICATION_JSON = "application/json";

  private final JsonRpcConfiguration config;
  private final Map<String, JsonRpcMethod> rpcMethods;
  private final int maxActiveConnections;
  private final AtomicInteger activeConnectionsCount = new AtomicInteger();
  private HttpServer httpServer;
  private final HealthService livenessService;
  private final MeterRegistry meterRegistry =
      MetricsService.MetricsServiceProvider.getMetricsService().getRegistry();

  public JsonRpcService(
      final String rpcHttpHost,
      final Integer rpcHttpPort,
      final Optional<List<String>> hostAllowList,
      final FullSyncDownloader fullSyncDownloader,
      final ZkWorldStateArchive worldStateArchive) {
    this.config = JsonRpcConfiguration.createDefault();
    config.setHost(rpcHttpHost);
    config.setPort(rpcHttpPort);
    hostAllowList.ifPresent(config::setHostsAllowlist);
    this.rpcMethods = new HashMap<>();
    this.rpcMethods.putAll(
        mapOf(
            new AdminChangeLogLevel(),
            new SendRawTrieLog(fullSyncDownloader, worldStateArchive.getTrieLogManager()),
            new LineaGetProof(worldStateArchive),
            new RollupGetZkEVMBlockNumber(worldStateArchive),
            new RollupDeleteZkEVMStateMerkleProofByRange(worldStateArchive.getTraceManager()),
            new RollupForkChoiceUpdated(worldStateArchive, fullSyncDownloader),
            new RollupGetZkEVMStateMerkleProofV0(worldStateArchive.getTraceManager())));
    this.maxActiveConnections = config.getMaxActiveConnections();
    this.livenessService = new HealthService(new LivenessCheck());
  }

  @Override
  public void start() {
    LOG.atInfo()
        .setMessage("Starting JSON-RPC service on {}:{}")
        .addArgument(config.getHost())
        .addArgument(config.getPort())
        .log();
    LOG.atDebug()
        .setMessage("max number of active connections {}")
        .addArgument(maxActiveConnections)
        .log();
    final CompletableFuture<?> resultFuture = new CompletableFuture<>();
    try {

      // Create the HTTP server and a router object.
      httpServer = vertx.createHttpServer(getHttpServerOptions());

      httpServer.connectionHandler(connectionHandler());

      httpServer
          .requestHandler(buildRouter())
          .listen(
              res -> {
                if (!res.failed()) {
                  resultFuture.complete(null);
                  config.setPort(httpServer.actualPort());
                  LOG.atInfo()
                      .setMessage("JSON-RPC service started and listening on {}:{}")
                      .addArgument(config.getHost())
                      .addArgument(config.getPort())
                      .log();

                  return;
                }

                httpServer = null;
                resultFuture.completeExceptionally(getFailureException(res.cause()));
              });

    } catch (final JsonRpcServiceException tlsException) {
      httpServer = null;
      resultFuture.completeExceptionally(tlsException);
    } catch (final VertxException listenException) {
      httpServer = null;
      resultFuture.completeExceptionally(
          new JsonRpcServiceException(
              String.format(
                  "JSON-RPC listener failed to start: %s",
                  ExceptionUtils.rootCause(listenException).getMessage())));
    }
  }

  @Override
  public void stop() throws Exception {
    LOG.atInfo().setMessage("Stopping JSON-RPC service").log();
    if (httpServer != null) {
      LOG.atInfo().setMessage("JSON-RPC service stopped").log();
      httpServer.close();
    }
  }

  private Handler<HttpConnection> connectionHandler() {

    return connection -> {
      if (activeConnectionsCount.get() >= maxActiveConnections) {
        // disallow new connections to prevent DoS
        LOG.warn(
            "Rejecting new connection from {}. Max {} active connections limit reached.",
            connection.remoteAddress(),
            activeConnectionsCount.getAndIncrement());
        connection.close();
      } else {
        LOG.debug(
            "Opened connection from {}. Total of active connections: {}/{}",
            connection.remoteAddress(),
            activeConnectionsCount.incrementAndGet(),
            maxActiveConnections);
      }
      connection.closeHandler(
          c ->
              LOG.debug(
                  "Connection closed from {}. Total of active connections: {}/{}",
                  connection.remoteAddress(),
                  activeConnectionsCount.decrementAndGet(),
                  maxActiveConnections));
    };
  }

  private Router buildRouter() {
    // Handle json rpc requests
    final Router router = Router.router(vertx);

    // Verify Host header to avoid rebind attack.
    router.route().handler(checkAllowlistHostHeader());
    router.errorHandler(403, new Logging403ErrorHandler());
    router
        .route()
        .handler(
            CorsHandler.create(buildCorsRegexFromConfig())
                .allowedHeader("*")
                .allowedHeader("content-type"));
    router.route().handler(BodyHandler.create().setDeleteUploadedFilesOnEnd(true));
    router.route("/").method(HttpMethod.GET).handler(this::handleEmptyRequest);
    router
        .route(HealthService.LIVENESS_PATH)
        .method(HttpMethod.GET)
        .handler(livenessService::handleRequest);
    Route mainRoute = router.route("/").method(HttpMethod.POST).produces(APPLICATION_JSON);
    mainRoute
        .handler(measureRequest())
        .handler(HandlerFactory.jsonRpcParser())
        .handler(HandlerFactory.timeout(new TimeoutOptions(config.getHttpTimeoutSec()), rpcMethods))
        .blockingHandler(
            JsonRpcExecutorHandler.handler(
                new JsonRpcExecutor(new BaseJsonRpcProcessor(), rpcMethods), null),
            false);
    router
        .route("/login")
        .method(HttpMethod.POST)
        .produces(APPLICATION_JSON)
        .handler(DefaultAuthenticationService::handleDisabledLogin);

    return router;
  }

  private Handler<RoutingContext> measureRequest() {
    return routingContext -> {
      long start = System.nanoTime();

      routingContext.addBodyEndHandler(
          v -> {
            long duration = System.nanoTime() - start;

            // Parse the request body to get the method name
            JsonObject requestBody = routingContext.getBodyAsJson();
            String methodName = requestBody.getString("method");

            Timer.builder("http.request")
                .tag("endpoint", "/")
                .tag("method", methodName)
                .register(meterRegistry)
                .record(duration, TimeUnit.NANOSECONDS);
          });

      routingContext.next();
    };
  }

  private HttpServerOptions getHttpServerOptions() {
    return new HttpServerOptions()
        .setHost(config.getHost())
        .setPort(config.getPort())
        .setHandle100ContinueAutomatically(true)
        .setCompressionSupported(true);
  }

  private Throwable getFailureException(final Throwable listenFailure) {

    JsonRpcServiceException servFail =
        new JsonRpcServiceException(
            String.format(
                "Failed to bind Ethereum JSON-RPC listener to %s:%s: %s",
                config.getHost(), config.getPort(), listenFailure.getMessage()));
    servFail.initCause(listenFailure);

    return servFail;
  }

  private Handler<RoutingContext> checkAllowlistHostHeader() {
    return event -> {
      final Optional<String> hostHeader = getAndValidateHostHeader(event);
      if (config.getHostsAllowlist().contains("*")
          || (hostHeader.isPresent() && hostIsInAllowlist(hostHeader.get()))) {
        event.next();
      } else {
        final HttpServerResponse response = event.response();
        if (!response.closed()) {
          response
              .setStatusCode(403)
              .putHeader("Content-Type", "application/json; charset=utf-8")
              .end("{\"message\":\"Host not authorized.\"}");
        }
      }
    };
  }

  private Optional<String> getAndValidateHostHeader(final RoutingContext event) {
    String hostname =
        event.request().getHeader(HttpHeaders.HOST) != null
            ? event.request().getHeader(HttpHeaders.HOST)
            : event.request().host();
    final Iterable<String> splitHostHeader = Splitter.on(':').split(hostname);
    final long hostPieces = stream(splitHostHeader).count();
    if (hostPieces > 1) {
      // If the host contains a colon, verify the host is correctly formed - host [ ":" port ]
      if (hostPieces > 2 || !Iterables.get(splitHostHeader, 1).matches("\\d{1,5}+")) {
        return Optional.empty();
      }
    }
    return Optional.ofNullable(Iterables.get(splitHostHeader, 0));
  }

  private boolean hostIsInAllowlist(final String hostHeader) {
    if (config.getHostsAllowlist().stream()
        .anyMatch(allowlistEntry -> allowlistEntry.equalsIgnoreCase(hostHeader))) {
      return true;
    } else {
      LOG.trace("Host not in allowlist: '{}'", hostHeader);
      return false;
    }
  }

  private void handleEmptyRequest(final RoutingContext routingContext) {
    routingContext.response().setStatusCode(201).end();
  }

  private String buildCorsRegexFromConfig() {
    if (config.getCorsAllowedDomains().isEmpty()) {
      return "";
    }
    if (config.getCorsAllowedDomains().contains("*")) {
      return ".*";
    } else {
      final StringJoiner stringJoiner = new StringJoiner("|");
      config.getCorsAllowedDomains().stream().filter(s -> !s.isEmpty()).forEach(stringJoiner::add);
      return stringJoiner.toString();
    }
  }

  private Map<String, JsonRpcMethod> mapOf(final JsonRpcMethod... methods) {
    return Arrays.stream(methods)
        .collect(Collectors.toMap(JsonRpcMethod::getName, method -> method));
  }
}
