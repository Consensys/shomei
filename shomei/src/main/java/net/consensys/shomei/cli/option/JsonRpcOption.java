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

import java.util.Arrays;
import java.util.List;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** The RPC CLI option. */
public class JsonRpcOption {

  /**
   * Create RPC option.
   *
   * @return the RPC option
   */
  public static JsonRpcOption create() {
    return new JsonRpcOption();
  }

  @Spec CommandSpec spec;

  public static final String DEFAULT_JSON_RPC_HOST = "127.0.0.1";

  public static final int BESU_DEFAULT_JSON_RPC_PORT = 8545;
  public static final int SHOMEI_DEFAULT_JSON_RPC_PORT = 8888;
  public static final List<String> SHOMEI_DEFAULT_JSON_RPC_HOST_ALLOW_LIST =
      Arrays.asList("localhost", "127.0.0.1");

  @CommandLine.Option(
      names = {"--rpc-http-host-allow-list"},
      paramLabel = "<ALLOW_HOSTS>",
      description = "Hosts to accept state updates from (default: ${DEFAULT-VALUE})",
      arity = "1")
  private List<String> rpcHttpHostAllowList = SHOMEI_DEFAULT_JSON_RPC_HOST_ALLOW_LIST;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--rpc-http-host"},
      paramLabel = "<HOST>",
      description = "Host for JSON-RPC HTTP to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String rpcHttpHost = DEFAULT_JSON_RPC_HOST;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--rpc-http-port"},
      paramLabel = "<PORT>",
      description = "Port for JSON-RPC HTTP to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private Integer rpcHttpPort = SHOMEI_DEFAULT_JSON_RPC_PORT;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--besu-rpc-http-host"},
      paramLabel = "<HOST>",
      description = "Host for BESU JSON-RPC HTTP (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String besuRpcHttpHost = DEFAULT_JSON_RPC_HOST;

  @CommandLine.Option(
      names = {"--besu-rpc-http-port"},
      paramLabel = "<PORT>",
      description = "Port for Besu JSON-RPC HTTP (default: ${DEFAULT-VALUE})",
      arity = "1")
  private Integer besuRHttpPort = BESU_DEFAULT_JSON_RPC_PORT;

  public String getBesuRpcHttpHost() {
    return besuRpcHttpHost;
  }

  public Integer getBesuRHttpPort() {
    return besuRHttpPort;
  }

  public List<String> getRpcHttpHostAllowList() {
    return rpcHttpHostAllowList;
  }

  public String getRpcHttpHost() {
    return rpcHttpHost;
  }

  public Integer getRpcHttpPort() {
    return rpcHttpPort;
  }
}
