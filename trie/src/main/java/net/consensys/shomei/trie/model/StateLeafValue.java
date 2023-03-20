/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package net.consensys.shomei.trie.model;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/** Represents the raw values associated with a leaf in the flat database. */
public class StateLeafValue {


  public static final StateLeafValue HEAD = new StateLeafValue(
          UInt256.ZERO,UInt256.valueOf(1), Bytes32.ZERO, Bytes32.ZERO
  );

  public static final StateLeafValue TAIL = new StateLeafValue(
          UInt256.ZERO,UInt256.valueOf(1), Bytes32.fromHexString("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"), Bytes32.ZERO
  );

  private final Bytes32 hkey;

  private Bytes value;

  private UInt256 prevLeaf;

  private UInt256 nextLeaf;

  public StateLeafValue(final Bytes32 hkey, final UInt256 value) {
    this.hkey = hkey;
    this.value = value;
    this.prevLeaf = UInt256.ZERO;
    this.nextLeaf = UInt256.valueOf(1);
  }

  public StateLeafValue(
          final UInt256 prevLeaf, final UInt256 nextLeaf, final Bytes32 hkey, final Bytes value) {
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



  public Bytes32 getHkey() {
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
    return Objects.equals(hkey, that.hkey) && Objects.equals(value, that.value)
            && Objects.equals(prevLeaf, that.prevLeaf) && Objects.equals(nextLeaf, that.nextLeaf);
  }

  @Override
  public int hashCode() {
    return Objects.hash(hkey, value, prevLeaf, nextLeaf);
  }

  public static StateLeafValue readFrom(final Bytes encodedBytes){
    final ByteArrayInputStream stream = new ByteArrayInputStream(encodedBytes.toArrayUnsafe());
    try {
      return new StateLeafValue( UInt256.fromBytes(Bytes.of(stream.readNBytes(UInt256.SIZE))), UInt256.fromBytes(Bytes.of(stream.readNBytes(UInt256.SIZE))), Bytes32.wrap(stream.readNBytes(Bytes32.SIZE)), Bytes32.wrap(stream.readNBytes(Bytes32.SIZE)));
    } catch (IOException e) {
      System.out.println("error reading");
    }
    return null;
  }

  public Bytes getEncodesBytes() {
    return Bytes.concatenate(
            prevLeaf, // Prev
            nextLeaf, // Next ,
            hkey, //HKEY
            value); // VALUE
  }

}
