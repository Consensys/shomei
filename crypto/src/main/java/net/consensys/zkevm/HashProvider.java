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

import com.sun.jna.Native;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.nativelib.gnark.LibGnark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HashProvider {
  private static final Logger LOG = LoggerFactory.getLogger(HashProvider.class);
  // default to bls12-377
  private static Function<Bytes, Hash> trieHashFunction = HashProvider::mimcBls12377;

  @SuppressWarnings("WeakerAccess")
  public static final boolean ENABLED;

  static {
    boolean enabled;
    try {
      Native.register(LibGnark.class, "gnark_jni");
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

  public static Hash trieHash(final Bytes bytes) {
    return trieHashFunction.apply(bytes);
  }

  public static void setTrieHashFunction(Function<Bytes, Hash> hashFunction) {
    trieHashFunction = hashFunction;
  }

  public static Hash keccak256(final Bytes bytes) {
    return Hash.hash(bytes);
  }

  public static Hash mimcBls12377(final Bytes bytes) {
    final byte[] output = new byte[Bytes32.SIZE];
    LibGnark.computeMimcBls12377(bytes.toArrayUnsafe(), bytes.size(), output);
    return Hash.wrap(Bytes32.wrap(output));
  }

  public static Hash mimcBn254(final Bytes bytes) {
    final byte[] output = new byte[Bytes32.SIZE];
    LibGnark.computeMimcBn254(bytes.toArrayUnsafe(), bytes.size(), output);
    return Hash.wrap(Bytes32.wrap(output));
  }
}
