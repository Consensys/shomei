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

import net.consensys.zkevm.HashProvider;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt256;

public class ShomeiSafeBytesProvider {

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

    private Bytes convertToPoseidonSafeFieldElementsSize(final Bytes value) {
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
  }

  // ------- MIMC STRATEGY -------
  private static class MimcStrategy implements ConversionStrategy {
    @Override
    public Bytes convertBytes32(Bytes32 value) {
      Bytes32 lsb = Bytes32.leftPad(value.slice(16, 16));
      Bytes32 msb = Bytes32.leftPad(value.slice(0, 16));
      return Bytes.concatenate(lsb, msb);
    }

    @Override
    public Bytes convertCode(Bytes code) {
      final int sizeChunk = Bytes32.SIZE / 2;
      final int numChunks = (int) Math.ceil((double) code.size() / sizeChunk);
      final MutableBytes mutableBytes = MutableBytes.create(numChunks * Bytes32.SIZE);
      int offset = 0;

      for (int i = 0; i < numChunks; i++) {
        int length = Math.min(sizeChunk, code.size() - offset);
        mutableBytes.set(i * Bytes32.SIZE + (Bytes32.SIZE - length), code.slice(offset, length));
        offset += length;
      }
      return mutableBytes;
    }

    @Override
    public Bytes convertAddress(Bytes value) {
      return Bytes32.leftPad(value);
    }
  }

  private static final ConversionStrategy POSEIDON_STRATEGY = new PoseidonStrategy();
  private static final ConversionStrategy MIMC_STRATEGY = new MimcStrategy();

  private static ConversionStrategy getStrategy() {
    return HashProvider.isPoseidonHashFunction() ? POSEIDON_STRATEGY : MIMC_STRATEGY;
  }

  public static ShomeiSafeBytes<UInt256> safeUInt256(final UInt256 delegate) {
    return new ShomeiSafeBytes<>(getStrategy().convertBytes32(delegate), delegate);
  }

  public static ShomeiSafeBytes<Bytes32> safeByte32(final Bytes32 delegate) {
    return new ShomeiSafeBytes<>(getStrategy().convertBytes32(delegate), delegate);
  }

  public static ShomeiSafeBytes<Bytes> safeCode(final Bytes delegate) {
    return new ShomeiSafeBytes<>(getStrategy().convertCode(delegate), delegate);
  }

  public static ShomeiSafeBytes<Address> safeAddress(final Address delegate) {
    return new ShomeiSafeBytes<>(getStrategy().convertAddress(delegate), delegate);
  }

  public static ShomeiSafeBytes<Bytes> unsafeFromBytes(final Bytes delegate) {
    return new ShomeiSafeBytes<>(delegate, delegate);
  }

  public static ShomeiSafeBytes<Bytes> concatenateSafeElements(final Bytes... values) {
    return new ShomeiSafeBytes<>(Bytes.concatenate(values), concatenateUnsafe(values));
  }

  private static Bytes concatenateUnsafe(Bytes... values) {
    if (values.length == 0) {
      return Bytes.EMPTY;
    }

    int size =
        Arrays.stream(values)
            .mapToInt(
                value ->
                    (value instanceof ShomeiSafeBytes<?>)
                        ? ((ShomeiSafeBytes<?>) value).getOriginalUnsafeValue().size()
                        : value.size())
            .reduce(0, Math::addExact);

    MutableBytes result = MutableBytes.create(size);
    int offset = 0;

    for (Bytes value : values) {
      Bytes unsafeValue =
          (value instanceof ShomeiSafeBytes<?>)
              ? ((ShomeiSafeBytes<?>) value).getOriginalUnsafeValue()
              : value;
      unsafeValue.copyTo(result, offset);
      offset += unsafeValue.size();
    }
    return result;
  }
}
