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

package net.consensys.shomei.util;

import static net.consensys.shomei.util.bytes.FieldElementsUtil.convertToSafeFieldElementsSize;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.hyperledger.besu.datatypes.Address;

public class TestUtil {

  public static Bytes32 createDumAddress(int value) {
    MutableBytes mutableBytes = MutableBytes.create(Address.SIZE);
    mutableBytes.set(0, (byte) value);
    return Bytes32.leftPad(Address.wrap(mutableBytes));
  }

  public static Bytes32 createDumDigest(int value) {
    MutableBytes32 mutableBytes = MutableBytes32.create();
    mutableBytes.set(0, (byte) value);
    return mutableBytes;
  }

  public static Bytes createSafeFieldElementSizeDumDigest(int value) {
    return convertToSafeFieldElementsSize(createDumDigest(value));
  }
}
