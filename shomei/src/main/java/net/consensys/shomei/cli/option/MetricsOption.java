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

package net.consensys.shomei.cli.option;

import picocli.CommandLine;

public class MetricsOption {
  public static final int PROMETHEUS_DEFAULT_PORT = 9888;
  public static final String PROMETHEUS_DEFAULT_HOST = "localhost";

  public static MetricsOption create() {
    return new MetricsOption();
  }

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--metrics-http-host"},
      paramLabel = "<HOST>",
      description = "Host for prometheus metrics HTTP to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String metricsHttpHost = PROMETHEUS_DEFAULT_HOST;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--metrics-http-port"},
      paramLabel = "<PORT>",
      description = "Port for prometheus metrics HTTP to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private Integer metricsHttpPort = PROMETHEUS_DEFAULT_PORT;

  public String getMetricsHttpHost() {
    return metricsHttpHost;
  }

  public Integer getMetricsHttpPort() {
    return metricsHttpPort;
  }
}
