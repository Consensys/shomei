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

package net.consensys.shomei.trie.model;

import net.consensys.shomei.util.bytes.BytesInput;

import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;

/** Represents the raw values associated with a leaf in the flat database. */
public class StateLeafValue {

  public static final StateLeafValue HEAD =
      new StateLeafValue(UInt256.ZERO, UInt256.valueOf(1), Hash.wrap(Bytes32.ZERO), Bytes32.ZERO);

  public static final StateLeafValue TAIL =
      new StateLeafValue(
          UInt256.ZERO,
          UInt256.valueOf(1),
          Hash.fromHexString("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
          Bytes32.ZERO);

  private final Hash hkey;

  private Bytes value;

  private UInt256 prevLeaf;

  private UInt256 nextLeaf;

  public StateLeafValue(final Hash hkey, final UInt256 value) {
    this.hkey = hkey;
    this.value = value;
    this.prevLeaf = UInt256.ZERO;
    this.nextLeaf = UInt256.valueOf(1);
  }

  public StateLeafValue(
      final UInt256 prevLeaf, final UInt256 nextLeaf, final Hash hkey, final Bytes value) {
    this.hkey = hkey;
    this.value = value;
    this.prevLeaf = prevLeaf;
    this.nextLeaf = nextLeaf;
  }

  public StateLeafValue(final StateLeafValue stateLeafValue) {
    this.hkey = stateLeafValue.hkey;
    this.value = stateLeafValue.value;
    this.prevLeaf = stateLeafValue.prevLeaf;
    this.nextLeaf = stateLeafValue.nextLeaf;
  }

  public UInt256 getPrevLeaf() {
    return prevLeaf;
  }

  public UInt256 getNextLeaf() {
    return nextLeaf;
  }

  public void setPrevLeaf(final UInt256 prevLeaf) {
    this.prevLeaf = prevLeaf;
  }

  public void setNextLeaf(final UInt256 nextLeaf) {
    this.nextLeaf = nextLeaf;
  }

  public Hash getHkey() {
    return hkey;
  }

  public Bytes getValue() {
    return value;
  }

  public void setValue(Bytes value) {
    this.value = value;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    StateLeafValue that = (StateLeafValue) o;
    return Objects.equals(hkey, that.hkey)
        && Objects.equals(value, that.value)
        && Objects.equals(prevLeaf, that.prevLeaf)
        && Objects.equals(nextLeaf, that.nextLeaf);
  }

  @Override
  public int hashCode() {
    return Objects.hash(hkey, value, prevLeaf, nextLeaf);
  }

  public static StateLeafValue readFrom(final Bytes encodedBytes) {
    return BytesInput.readBytes(
        encodedBytes,
        bytesInput ->
            new StateLeafValue(
                bytesInput.readUInt256(),
                bytesInput.readUInt256(),
                Hash.wrap(bytesInput.readBytes32()),
                bytesInput.readBytes32()));
  }

  public Bytes getEncodesBytes() {
    return Bytes.concatenate(
        prevLeaf, // Prev
        nextLeaf, // Next ,
        hkey, // HKEY
        value); // VALUE
  }
}
