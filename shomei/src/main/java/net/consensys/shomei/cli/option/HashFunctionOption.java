/*
 * Copyright Consensys Software Inc., 2025
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

import net.consensys.zkevm.HashFunction;
import picocli.CommandLine;

public class HashFunctionOption {

  /**
   * Create Haah Function option.
   *
   * @return the option
   */
  public static HashFunctionOption create() {
    return new HashFunctionOption();
  }

  public static final HashFunction DEFAULT_HASH_FUNCTION = HashFunction.POSEIDON_2;

  @CommandLine.Option(
      names = {"--hash-function"},
      paramLabel = "<CURVE>",
      description = "The hash function to use (POSEIDON_2, KECCAK256) (default: ${DEFAULT-VALUE})",
      arity = "1")
  private HashFunction hashFunction = DEFAULT_HASH_FUNCTION;

  public HashFunction getHashFunction() {
    return hashFunction;
  }

  public static class Builder {
    private final HashFunctionOption hashFunctionOption = new HashFunctionOption();

    public Builder setHashFunction(HashFunction hashFunction) {
      this.hashFunctionOption.hashFunction = hashFunction;
      return this;
    }

    public HashFunctionOption build() {
      return hashFunctionOption;
    }
  }
}
