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
