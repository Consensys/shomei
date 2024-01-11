/*
 * Copyright ConsenSys Software Inc., 2024
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

import net.consensys.zkevm.HashProvider;

import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import picocli.CommandLine;

public class HashFunctionOption {

  enum HashFunction {
    KECCAK256(HashProvider::keccak256),
    MIMC_BN254(HashProvider::mimcBn254),
    MIMC_BLS12_377(HashProvider::mimcBls12377);

    Function<Bytes, Hash> hashFunction;

    HashFunction(Function<Bytes, Hash> hashFunction) {
      this.hashFunction = hashFunction;
    }
  }

  /**
   * Create Haah Function option.
   *
   * @return the option
   */
  public static HashFunctionOption create() {
    return new HashFunctionOption();
  }

  public static final HashFunction DEFAULT_HASH_FUNCTION = HashFunction.MIMC_BLS12_377;

  @CommandLine.Option(
      names = {"--hash-function"},
      paramLabel = "<CURVE>",
      description =
          "The hash function to use (MIMC_BLS12_377, MIMC_BN254, KECCAK256) (default: ${DEFAULT-VALUE})",
      arity = "1")
  private HashFunction hashFunction = DEFAULT_HASH_FUNCTION;

  public Function<Bytes, Hash> getHashFunction() {
    return hashFunction.hashFunction;
  }
}
