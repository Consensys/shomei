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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import picocli.CommandLine;

public class DataStorageOption {
  /**
   * Create RPC option.
   *
   * @return the RPC option
   */
  public static DataStorageOption create() {
    return new DataStorageOption();
  }

  @CommandLine.Spec CommandLine.Model.CommandSpec spec;

  static final String DEFAULT_DATA_STORAGE_PATH = "./build/data";

  @CommandLine.Option(
      names = {"--data-path"},
      paramLabel = "<PATH>",
      description = "Path to use for persisted storage",
      arity = "1")
  private String dataStoragePath = DEFAULT_DATA_STORAGE_PATH;

  public Path getDataStoragePath() {
    Path path = Path.of(dataStoragePath);
    try {
      Files.createDirectories(path);
    } catch (IOException e) {
      System.err.println("Failed to get or create directories: " + e.getMessage());
    }
    return path;
  }

  public static class Builder {
    private final DataStorageOption dataStorageOption = new DataStorageOption();

    public Builder setDataStoragePath(Path dataStoragePath) {
      this.dataStorageOption.dataStoragePath = dataStoragePath.toString();
      return this;
    }

    public DataStorageOption build() {
      return dataStorageOption;
    }
  }
}
