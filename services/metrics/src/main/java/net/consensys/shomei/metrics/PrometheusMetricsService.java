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

package net.consensys.shomei.metrics;

import java.util.function.Supplier;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;

public class PrometheusMetricsService extends AbstractVerticle
    implements MetricsService.VertxMetricsService {
  private final PrometheusMeterRegistry prometheusMeterRegistry;
  private final String host;
  private final int port;

  public PrometheusMetricsService(String metricsHost, int metricsPort) {
    this.prometheusMeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    this.host = metricsHost;
    this.port = metricsPort;
  }

  @Override
  public MeterRegistry getRegistry() {
    return prometheusMeterRegistry;
  }

  @Override
  public void addGauge(
      final String name,
      final String description,
      final Iterable<Tag> tags,
      final Supplier<Number> supplier) {

    // Register a gauge to track this value
    Gauge.builder(name, supplier)
        .description(description)
        .tags(tags)
        .register(prometheusMeterRegistry);
  }

  @Override
  public void start(Promise<Void> startPromise) {

    Router router = Router.router(vertx);
    router
        .route("/metrics")
        .handler(
            routingContext -> {
              String metrics = prometheusMeterRegistry.scrape();
              routingContext.response().end(metrics);
            });

    vertx.createHttpServer().requestHandler(router).listen(port, host);
  }
}
