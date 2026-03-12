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

package net.consensys.shomei.util.bytes;

import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.DelegatingBytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;

/**
 * The fields elements hold on 32 bytes but do not allow to contain 32 bytes entirely. For some
 * keys, we cannot assume that it will always fit on a field element. So we need sometimes to modify
 * the key to be mimc friendly.
 */
public class MimcSafeBytes<T extends Bytes> extends DelegatingBytes implements Bytes {

  private final T originalUnsafeValue;

  private MimcSafeBytes(final Bytes delegate, final T originalUnsafeValue) {
    super(delegate);
    this.originalUnsafeValue = originalUnsafeValue;
  }

  public static MimcSafeBytes<UInt256> safeUInt256(final UInt256 delegate) {
    return new MimcSafeBytes<>(convertToSafeFieldElementsSize(delegate), delegate);
  }

  public static MimcSafeBytes<Bytes32> safeByte32(final Bytes32 delegate) {
    return new MimcSafeBytes<>(convertToSafeFieldElementsSize(delegate), delegate);
  }

  public static MimcSafeBytes<Bytes> safeAddress(final Address delegate) {
    return new MimcSafeBytes<>(Bytes32.leftPad(delegate.getBytes()), delegate.getBytes());
  }

  public static MimcSafeBytes<Bytes> unsafeFromBytes(final Bytes delegate) {
    return new MimcSafeBytes<>(delegate, delegate);
  }

  public static MimcSafeBytes<Bytes> concatenateSafeElements(final Bytes... values) {
    return new MimcSafeBytes<>(Bytes.concatenate(values), concatenateUnSafe(values));
  }

  @Override
  public String toHexString() {
    return originalUnsafeValue.toHexString();
  }

  public T getOriginalUnsafeValue() {
    return originalUnsafeValue;
  }

  private static Bytes concatenateUnSafe(Bytes... values) {
    if (values.length == 0) {
      return EMPTY;
    }

    int size;
    try {
      size =
          Arrays.stream(values)
              .mapToInt(
                  value ->
                      (value instanceof MimcSafeBytes<?>)
                          ? ((MimcSafeBytes<?>) value).getOriginalUnsafeValue().size()
                          : value.size())
              .reduce(0, Math::addExact);
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException(
          "Combined length of values is too long (> Integer.MAX_VALUE)");
    }

    MutableBytes result = MutableBytes.create(size);
    int offset = 0;
    for (Bytes value : values) {
      Bytes unsafeValue =
          (value instanceof MimcSafeBytes<?>)
              ? ((MimcSafeBytes<?>) value).getOriginalUnsafeValue()
              : value;
      unsafeValue.copyTo(result, offset);
      offset += unsafeValue.size();
    }
    return result;
  }

  private static Bytes convertToSafeFieldElementsSize(final Bytes32 value) {
    Bytes32 lsb = Bytes32.leftPad(value.slice(16, 16));
    Bytes32 msb = Bytes32.leftPad(value.slice(0, 16));
    return Bytes.concatenate(lsb, msb);
  }
}
