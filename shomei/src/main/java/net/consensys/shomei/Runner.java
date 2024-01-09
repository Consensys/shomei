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

import net.consensys.shomei.cli.option.DataStorageOption;
import net.consensys.shomei.cli.option.HashFunctionOption;
import net.consensys.shomei.cli.option.JsonRpcOption;
import net.consensys.shomei.cli.option.MetricsOption;
import net.consensys.shomei.cli.option.SyncOption;
import net.consensys.shomei.fullsync.FullSyncDownloader;
import net.consensys.shomei.fullsync.rules.FullSyncRules;
import net.consensys.shomei.metrics.MetricsService;
import net.consensys.shomei.metrics.PrometheusMetricsService;
import net.consensys.shomei.rpc.client.GetRawTrieLogClient;
import net.consensys.shomei.rpc.server.JsonRpcService;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;
import net.consensys.shomei.storage.RocksDBStorageProvider;
import net.consensys.shomei.storage.StorageProvider;
import net.consensys.shomei.storage.ZkWorldStateArchive;
import net.consensys.zkevm.HashProvider;

import java.io.IOException;
import java.util.Optional;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.vertx.core.Vertx;
import org.hyperledger.besu.datatypes.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Runner {

  private static final Logger LOG = LoggerFactory.getLogger(Runner.class);

  private final Vertx vertx;
  private final FullSyncDownloader fullSyncDownloader;
  private final JsonRpcService jsonRpcService;

  private final MetricsService metricsService;

  private final ZkWorldStateArchive worldStateArchive;

  public Runner(
      final DataStorageOption dataStorageOption,
      JsonRpcOption jsonRpcOption,
      final SyncOption syncOption,
      MetricsOption metricsOption,
      HashFunctionOption hashFunctionOption) {
    this.vertx = Vertx.vertx();
    setupHashFunction(hashFunctionOption);
    metricsService = setupMetrics(metricsOption);

    final StorageProvider storageProvider =
        new RocksDBStorageProvider(
            new RocksDBConfigurationBuilder()
                .databaseDir(dataStorageOption.getDataStoragePath())
                .build());

    worldStateArchive = new ZkWorldStateArchive(storageProvider);

    final GetRawTrieLogClient getRawTrieLog =
        new GetRawTrieLogClient(
            worldStateArchive.getTrieLogManager(),
            jsonRpcOption.getBesuRpcHttpHost(),
            jsonRpcOption.getBesuRHttpPort());

    final FullSyncRules fullSyncRules =
        new FullSyncRules(
            syncOption.isTraceGenerationEnabled(),
            syncOption.getTraceStartBlockNumber(),
            syncOption.getMinConfirmationsBeforeImporting(),
            syncOption.isEnableFinalizedBlockLimit(),
            Optional.ofNullable(syncOption.getFinalizedBlockNumberLimit()),
            Optional.ofNullable(syncOption.getFinalizedBlockHashLimit()).map(Hash::fromHexString));

    fullSyncDownloader = new FullSyncDownloader(worldStateArchive, getRawTrieLog, fullSyncRules);

    this.jsonRpcService =
        new JsonRpcService(
            jsonRpcOption.getRpcHttpHost(),
            jsonRpcOption.getRpcHttpPort(),
            Optional.of(jsonRpcOption.getRpcHttpHostAllowList()),
            fullSyncDownloader,
            worldStateArchive);
  }

  private void setupHashFunction(HashFunctionOption hashFunctionOption) {
    HashProvider.setTrieHashFunction(hashFunctionOption.getHashFunction());
  }

  private MetricsService setupMetrics(MetricsOption metricsOption) {
    // use prometheus as metrics service
    MetricsService metricsService =
        new PrometheusMetricsService(
            metricsOption.getMetricsHttpHost(), metricsOption.getMetricsHttpPort());
    MeterRegistry meterRegistry = metricsService.getRegistry();
    MetricsService.MetricsServiceProvider.setMetricsService(metricsService);

    // register JVM metrics here
    new JvmMemoryMetrics().bindTo(meterRegistry);
    new JvmGcMetrics().bindTo(meterRegistry);
    new JvmThreadMetrics().bindTo(meterRegistry);
    return metricsService;
  }

  public void start() {
    vertx.deployVerticle(
        jsonRpcService,
        res -> {
          if (!res.succeeded()) {
            LOG.atError()
                .setMessage("Error occurred when starting the JSON RPC service {}")
                .addArgument(res.cause())
                .log();
          }
        });
    vertx.deployVerticle(
        fullSyncDownloader,
        res -> {
          if (!res.succeeded()) {
            LOG.atError()
                .setMessage("Error occurred when starting the block downloader {}")
                .addArgument(res.cause())
                .log();
          }
        });
    vertx.deployVerticle(
        metricsService,
        res -> {
          if (!res.succeeded()) {
            LOG.atError()
                .setMessage("Error occurred when starting the metrics service {}")
                .addArgument(res.cause())
                .log();
          }
        });
  }

  public void stop() throws IOException {
    worldStateArchive.close();
    vertx.close();
  }
}
