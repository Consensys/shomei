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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.vertx.core.Context;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public class NoOpMetricsService implements MetricsService {

  private final MeterRegistry meterRegistry;
  private Vertx vertx;

  public NoOpMetricsService() {
    this.meterRegistry = new SimpleMeterRegistry();
  }

  @Override
  public MeterRegistry getRegistry() {
    return meterRegistry;
  }

  @Override
  public Vertx getVertx() {
    return vertx;
  }

  @Override
  public void init(final Vertx vertx, final Context context) {
    this.vertx = vertx;
  }

  @Override
  public void start(final Promise<Void> startPromise) throws Exception {
    // no-op
  }

  @Override
  public void stop(final Promise<Void> stopPromise) throws Exception {
    // no-op
  }
}
