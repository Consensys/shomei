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

package net.consensys.zkevm;

import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.nativelib.common.BesuNativeLibraryLoader;
import org.hyperledger.besu.nativelib.gnark.LibGnark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HashProvider {
  private static final Logger LOG = LoggerFactory.getLogger(HashProvider.class);

  public static final Bytes32 KECCAK_HASH_EMPTY = Bytes32.wrap(Hash.EMPTY.getBytes());
  public static final Bytes32 KECCAK_HASH_ZERO = Bytes32.wrap(Hash.ZERO.getBytes());
  // default to bls12-377
  private static Function<Bytes, Bytes32> trieHashFunction = HashProvider::mimcBls12377;

  @SuppressWarnings("WeakerAccess")
  public static final boolean ENABLED;

  static {
    boolean enabled;
    try {
      BesuNativeLibraryLoader.registerJNA(LibGnark.class, "gnark_jni");
      enabled = true;
    } catch (final Throwable t) {
      LOG.atError()
          .setMessage("Unable to load MIMC native library with error : {}")
          .addArgument(t.getMessage())
          .log();
      enabled = false;
    }
    ENABLED = enabled;
  }

  public static Bytes32 trieHash(final Bytes bytes) {
    return trieHashFunction.apply(bytes);
  }

  public static void setTrieHashFunction(Function<Bytes, Bytes32> hashFunction) {
    trieHashFunction = hashFunction;
  }

  public static Bytes32 keccak256(final Bytes bytes) {
    return Bytes32.wrap(Hash.hash(bytes).getBytes());
  }

  public static Bytes32 mimcBls12377(final Bytes bytes) {
    final byte[] output = new byte[Bytes32.SIZE];
    LibGnark.computeMimcBls12377(bytes.toArrayUnsafe(), bytes.size(), output);
    return Bytes32.wrap(output);
  }

  public static Bytes32 mimcBn254(final Bytes bytes) {
    final byte[] output = new byte[Bytes32.SIZE];
    LibGnark.computeMimcBn254(bytes.toArrayUnsafe(), bytes.size(), output);
    return Bytes32.wrap(output);
  }
}
