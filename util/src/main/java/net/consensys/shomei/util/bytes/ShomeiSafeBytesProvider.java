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

import net.consensys.zkevm.HashFunction;
import net.consensys.zkevm.HashProvider;

import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;

public class ShomeiSafeBytesProvider {

    // ------- PUBLIC METHODS -------

    public static ShomeiSafeBytes<UInt256> safeUInt256(final UInt256 delegate) {
        return new ShomeiSafeBytes<>(convertByte32ToSafeFieldElementsSize(delegate), delegate);
    }

    public static ShomeiSafeBytes<Bytes32> safeByte32(final Bytes32 delegate) {
        return new ShomeiSafeBytes<>(convertByte32ToSafeFieldElementsSize(delegate), delegate);
    }

    public static ShomeiSafeBytes<Bytes> safeCode(final Bytes delegate) {
        return new ShomeiSafeBytes<>(convertCodeToSafeFieldElementsSize(delegate), delegate);
    }

    public static ShomeiSafeBytes<Address> safeAddress(final Address delegate) {
        return new ShomeiSafeBytes<>(convertAddressToSafeFieldElementsSize(delegate), delegate);
    }

    public static ShomeiSafeBytes<Bytes> unsafeFromBytes(final Bytes delegate) {
        return new ShomeiSafeBytes<>(delegate, delegate);
    }

    public static ShomeiSafeBytes<Bytes> concatenateSafeElements(final Bytes... values) {
        return new ShomeiSafeBytes<>(Bytes.concatenate(values), concatenateUnsafe(values));
    }

    // ------- PRIVATE METHODS -------

    /**
     * Concatenates unsafe values from the input array of Bytes objects.
     */
    private static Bytes concatenateUnsafe(Bytes... values) {
        if (values.length == 0) {
            return Bytes.EMPTY;
        }

        int size = Arrays.stream(values)
                .mapToInt(value -> (value instanceof ShomeiSafeBytes<?>)
                        ? ((ShomeiSafeBytes<?>) value).getOriginalUnsafeValue().size()
                        : value.size())
                .reduce(0, Math::addExact);

        MutableBytes result = MutableBytes.create(size);
        int offset = 0;

        for (Bytes value : values) {
            Bytes unsafeValue = (value instanceof ShomeiSafeBytes<?>)
                    ? ((ShomeiSafeBytes<?>) value).getOriginalUnsafeValue()
                    : value;

            unsafeValue.copyTo(result, offset);
            offset += unsafeValue.size();
        }

        return result;
    }

    /**
     * Converts Bytes32 into a safe field size depending on the hash function.
     */
    private static Bytes convertByte32ToSafeFieldElementsSize(final Bytes32 value) {
        if (HashProvider.getHashFunction().equals(HashFunction.POSEIDON_2)) {
            return convertToPoseidonSafeFieldElementsSize(value);
        } else {
            return convertToMimcSafeFieldElementsSize(value);
        }
    }

    /**
     * Converts the code into a safe field size depending on the hash function.
     */
    private static Bytes convertCodeToSafeFieldElementsSize(final Bytes value) {
        if (HashProvider.getHashFunction().equals(HashFunction.POSEIDON_2)) {
            return convertToSafePoseidonCode(value);
        } else {
            return convertToSafeMimcCode(value);
        }
    }

    /**
     * Converts the address into a safe field size depending on the hash function.
     */
    private static Bytes convertAddressToSafeFieldElementsSize(final Bytes value) {
        if (HashProvider.getHashFunction().equals(HashFunction.POSEIDON_2)) {
            return convertCodeToSafeFieldElementsSize(value);
        } else {
            return Bytes32.leftPad(value);
        }
    }

    /**
     * Converts Bytes into Poseidon-compatible safe field elements size.
     */
    private static Bytes convertToPoseidonSafeFieldElementsSize(final Bytes value) {
        boolean isOdd = value.size() % 2 != 0;
        int paddedSize = isOdd ? value.size() + 1 : value.size(); // Ensure even size
        int limbCount = paddedSize / 2;
        byte[] output = new byte[limbCount * 4];

        int offset = isOdd ? 1 : 0; // Offset if we need to pad the first byte
        for (int i = 0; i < limbCount; i++) {
            int src = i * 2 - offset; // Adjust source index with padding offset
            int dst = i * 4; // Limbs are 4-byte wide

            output[dst] = 0x00; // Padding byte
            output[dst + 1] = 0x00; // Padding byte

            output[dst + 2] = src < 0 ? 0x00 : value.get(src); // Left byte
            output[dst + 3] = src + 1 < value.size() ? value.get(src + 1) : 0x00; // Right byte
        }

        return Bytes.wrap(output);
    }

    private static Bytes convertToSafePoseidonCode(final Bytes code) {
        return convertToPoseidonSafeFieldElementsSize(code);
    }

    private static Bytes convertToMimcSafeFieldElementsSize(final Bytes32 value) {
        Bytes32 lsb = Bytes32.leftPad(value.slice(16, 16));
        Bytes32 msb = Bytes32.leftPad(value.slice(0, 16));
        return Bytes.concatenate(lsb, msb);
    }

    private static Bytes convertToSafeMimcCode(final Bytes code) {
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
}
