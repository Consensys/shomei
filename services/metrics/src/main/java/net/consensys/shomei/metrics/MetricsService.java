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

import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.Verticle;

public interface MetricsService extends Verticle {

  MeterRegistry getRegistry();

  class MetricsServiceProvider {
    private static final AtomicReference<MetricsService> METRICS_SERVICE = new AtomicReference<>();

    public static void setMetricsService(MetricsService service) {
      if (METRICS_SERVICE.get() == null) {
        METRICS_SERVICE.set(service);
      }
    }

    public static MetricsService getMetricsService() {
      // no-op metrics if otherwise unset:
      if (METRICS_SERVICE.get() == null) {
        setMetricsService(new NoOpMetricsService());
      }
      return METRICS_SERVICE.get();
    }
  }
}
