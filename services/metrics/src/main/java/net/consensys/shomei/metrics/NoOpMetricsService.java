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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class NoOpMetricsService implements MetricsService {

  private final MeterRegistry meterRegistry;

  public NoOpMetricsService() {
    this.meterRegistry = new SimpleMeterRegistry();
  }

  @Override
  public MeterRegistry getRegistry() {
    return meterRegistry;
  }

  @Override
  public void addGauge(
      final String name,
      final String description,
      final Iterable<Tag> tags,
      final Supplier<Number> supplier) {
    // no-op
  }
}
