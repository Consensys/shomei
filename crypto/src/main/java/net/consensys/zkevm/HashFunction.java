/*
 * Copyright ConsenSys Software Inc., 2025
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

package net.consensys.zkevm;

import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;

public enum HashFunction {
    KECCAK256(HashProvider::keccak256),
    MIMC_BN254(HashProvider::mimcBn254),
    MIMC_BLS12_377(HashProvider::mimcBls12377),
      POSEIDON_2(HashProvider::poseidon2);

    final Function<Bytes, Hash> hashFunction;

    HashFunction(Function<Bytes, Hash> hashFunction) {
      this.hashFunction = hashFunction;
    }

    public Function<Bytes, Hash> getHashFunction() {
        return hashFunction;
    }
}
