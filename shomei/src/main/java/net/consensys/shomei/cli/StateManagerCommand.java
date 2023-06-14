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

package net.consensys.shomei.cli;

import net.consensys.shomei.Runner;
import net.consensys.shomei.cli.error.ExecutionExceptionHandler;
import net.consensys.shomei.cli.error.ParameterExceptionHandler;
import net.consensys.shomei.cli.option.DataStorageOption;
import net.consensys.shomei.cli.option.JsonRpcOption;
import net.consensys.shomei.cli.option.LoggingLevelOption;
import net.consensys.shomei.cli.option.MetricsOption;
import net.consensys.shomei.util.logging.LoggingConfiguration;

import org.apache.logging.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(
    name = "shomei",
    subcommands = {
      CommandLine.HelpCommand.class,
    },
    showDefaultValues = true,
    abbreviateSynopsis = true,
    description = "Run the Shomei linea state manager",
    mixinStandardHelpOptions = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%n@|bold Description:|@%n%n",
    optionListHeading = "%n@|bold Options:|@%n",
    footerHeading = "%n",
    footer = "Shomei linea state manager is licensed under the Apache License 2.0")
public class StateManagerCommand implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(StateManagerCommand.class);

  @Mixin(name = "Data storage configuration")
  private final DataStorageOption dataStorageOption = DataStorageOption.create();

  @Mixin(name = "Logging level")
  private final LoggingLevelOption loggingLevelOption = LoggingLevelOption.create();

  @Mixin(name = "JSON RPC configuration")
  private final JsonRpcOption jsonRpcOption = JsonRpcOption.create();

  @Mixin(name = "Metrics configuration")
  private final MetricsOption metricsOption = MetricsOption.create();

  public StateManagerCommand() {}

  public int parse(final String... args) {
    return new CommandLine(this)
        .setCaseInsensitiveEnumValuesAllowed(true)
        .setParameterExceptionHandler(
            new ParameterExceptionHandler(loggingLevelOption::getLogLevel))
        .setExecutionExceptionHandler(new ExecutionExceptionHandler())
        .execute(args);
  }

  public void configureLogging() {
    // set log level per CLI flags
    final Level logLevel = loggingLevelOption.getLogLevel();
    if (logLevel != null) {
      LOG.atInfo().setMessage("Change log level to {}").addArgument(logLevel).log();
      LoggingConfiguration.setAllLevelsSilently("", logLevel);
    }
  }

  public LoggingLevelOption getLoggingLevelOption() {
    return loggingLevelOption;
  }

  public JsonRpcOption getJsonRpcOption() {
    return jsonRpcOption;
  }

  @Override
  public void run() {
    try {
      configureLogging();
      final Runner runner = new Runner(dataStorageOption, jsonRpcOption, metricsOption);
      addShutdownHook(runner);
      runner.start();
    } catch (final Exception e) {
      throw new CommandLine.ParameterException(new CommandLine(this), e.getMessage(), e);
    }
  }

  private static void addShutdownHook(final Runner runner) {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    runner.stop();
                  } catch (final Exception e) {
                    LOG.error("Failed to stop Shomei");
                  }
                },
                "Command-Shutdown-Hook"));
  }
}
