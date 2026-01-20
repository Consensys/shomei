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
package net.consensys.shomei.util.bytes;

import org.hyperledger.besu.datatypes.Address;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt256;

public class PoseidonSafeBytesUtils {

  private interface ConversionStrategy {
    Bytes convertBytes32(Bytes32 value);

    Bytes convertCode(Bytes value);

    Bytes convertAddress(Bytes value);
  }

  // ------- POSEIDON STRATEGY -------
  private static class PoseidonStrategy implements ConversionStrategy {
    @Override
    public Bytes convertBytes32(Bytes32 value) {
      return convertToPoseidonSafeFieldElementsSize(value);
    }

    @Override
    public Bytes convertCode(Bytes value) {
      return convertToPoseidonSafeFieldElementsSize(value);
    }

    @Override
    public Bytes convertAddress(Bytes value) {
      return convertToPoseidonSafeFieldElementsSize(value);
    }
  }

  public static Bytes convertToPoseidonSafeFieldElementsSize(final Bytes value) {
    boolean isOdd = value.size() % 2 != 0;
    int paddedSize = isOdd ? value.size() + 1 : value.size();
    int limbCount = paddedSize / 2;
    byte[] output = new byte[limbCount * 4];
    int offset = 0;

    for (int i = 0; i < limbCount; i++) {
      int src = i * 2 - offset;
      int dst = i * 4;
      output[dst] = 0x00;
      output[dst + 1] = 0x00;

      if (isOdd && i % 8 == 0 && limbCount - i <= 8) {
        offset = 1;
        output[dst + 2] = 0x00;
        output[dst + 3] = value.get(src);
      } else {
        output[4 * i + 2] = value.get(src);
        output[4 * i + 3] = value.get(src + 1);
      }
    }

    return Bytes.wrap(output);
  }

  public static Bytes32 convertBackFromPoseidonSafeFieldElementsForEvenSize(final Bytes value) {
    ByteBuffer cleanedBytesBuf = ByteBuffer.allocate(value.size() / 2 + 1);
    int numLimbs = value.size() / 4;

    for (int i = 0; i < numLimbs; i++) {
      if (i % 8 == 0 && numLimbs - i <= 8 && value.get(4 * i + 2) == 0x00) {
        cleanedBytesBuf.put(value.get(4 * i + 3));
      } else {
        cleanedBytesBuf.put(value.get(4 * i + 2));
        cleanedBytesBuf.put(value.get(4 * i + 3));
      }
    }

    Bytes cleanedBytes =
        Bytes.wrap(Arrays.copyOf(cleanedBytesBuf.array(), cleanedBytesBuf.position()));

    // Convert back to Bytes32 (ensuring a fixed size of 32 bytes)
    return Bytes32.leftPad(cleanedBytes);
  }

  private static final ConversionStrategy POSEIDON_STRATEGY = new PoseidonStrategy();

  private static ConversionStrategy getStrategy() {
    return POSEIDON_STRATEGY;
  }

  public static PoseidonSafeBytes<UInt256> safeUInt256(final UInt256 delegate) {
    return new PoseidonSafeBytes<>(getStrategy().convertBytes32(delegate), delegate);
  }

  public static PoseidonSafeBytes<Bytes32> safeByte32(final Bytes32 delegate) {
    return new PoseidonSafeBytes<>(getStrategy().convertBytes32(delegate), delegate);
  }

  public static PoseidonSafeBytes<Bytes> safeCode(final Bytes delegate) {
    return new PoseidonSafeBytes<>(getStrategy().convertCode(delegate), delegate);
  }

  public static PoseidonSafeBytes<Address> safeAddress(final Address delegate) {
    return new PoseidonSafeBytes<>(getStrategy().convertAddress(delegate), delegate);
  }

  public static PoseidonSafeBytes<Bytes> unsafeFromBytes(final Bytes delegate) {
    return new PoseidonSafeBytes<>(delegate, delegate);
  }

  public static PoseidonSafeBytes<Bytes> concatenateSafeElements(final Bytes... values) {
    return new PoseidonSafeBytes<>(Bytes.concatenate(values), concatenateUnsafe(values));
  }

  private static Bytes concatenateUnsafe(Bytes... values) {
    if (values.length == 0) {
      return Bytes.EMPTY;
    }

    int size =
        Arrays.stream(values)
            .mapToInt(
                value ->
                    (value instanceof PoseidonSafeBytes<?>)
                        ? ((PoseidonSafeBytes<?>) value).getOriginalUnsafeValue().size()
                        : value.size())
            .reduce(0, Math::addExact);

    MutableBytes result = MutableBytes.create(size);
    int offset = 0;

    for (Bytes value : values) {
      Bytes unsafeValue =
          (value instanceof PoseidonSafeBytes<?>)
              ? ((PoseidonSafeBytes<?>) value).getOriginalUnsafeValue()
              : value;
      unsafeValue.copyTo(result, offset);
      offset += unsafeValue.size();
    }
    return result;
  }
}
