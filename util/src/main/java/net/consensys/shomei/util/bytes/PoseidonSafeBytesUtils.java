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
    int offset = isOdd ? 1 : 0;

    for (int i = 0; i < limbCount; i++) {
      int src = i * 2 - offset;
      int dst = i * 4;
      output[dst] = 0x00;
      output[dst + 1] = 0x00;
      output[dst + 2] = src < 0 ? 0x00 : value.get(src);
      output[dst + 3] = src + 1 < value.size() ? value.get(src + 1) : 0x00;
    }
    return Bytes.wrap(output);
  }

  public static Bytes32 convertBackFromPoseidonSafeFieldElementsSize(final Bytes value) {
    // Determine the number of limbs (each represented by 4 bytes in the padded structure)
    int limbCount = value.size() / 4;
    // Prepare an array to hold the original Bytes data
    byte[] originalBytes = new byte[limbCount * 2];
    for (int i = 0; i < limbCount; i++) {
      int src = i * 4;
      int dst = i * 2;
      // The actual data is in the 2nd and 3rd bytes of the 4-byte chunks
      originalBytes[dst] = value.get(src + 2);
      originalBytes[dst + 1] = value.get(src + 3);
    }
    // Wrap the original bytes in a Bytes object
    Bytes originalBytesWrapped = Bytes.wrap(originalBytes);
    // Remove any padding that was added (odd size logic from original function)
    Bytes cleanedBytes = originalBytesWrapped.trimLeadingZeros();
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
