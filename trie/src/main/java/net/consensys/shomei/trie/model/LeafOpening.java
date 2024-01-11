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

import net.consensys.shomei.util.bytes.BytesBuffer;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;

public class LeafOpening {

  public static final LeafOpening HEAD =
      new LeafOpening(0, 1, Hash.wrap(Bytes32.ZERO), Bytes32.ZERO);

  public static final LeafOpening TAIL =
      new LeafOpening(
          0,
          1,
          Hash.fromHexString("12ab655e9a2ca55660b44d1e5c37b00159aa76fed00000010a11800000000000"),
          Bytes32.ZERO);

  private final Hash hkey;

  private Bytes hval;

  private long prevLeaf;

  private long nextLeaf;

  public LeafOpening(final Hash hkey, final UInt256 hval) {
    this.hkey = hkey;
    this.hval = hval;
    this.prevLeaf = 0;
    this.nextLeaf = 1;
  }

  public LeafOpening(final long prevLeaf, final long nextLeaf, final Hash hkey, final Bytes hval) {
    this.hkey = hkey;
    this.hval = hval;
    this.prevLeaf = prevLeaf;
    this.nextLeaf = nextLeaf;
  }

  public LeafOpening(final LeafOpening stateLeafValue) {
    this.hkey = stateLeafValue.hkey;
    this.hval = stateLeafValue.hval;
    this.prevLeaf = stateLeafValue.prevLeaf;
    this.nextLeaf = stateLeafValue.nextLeaf;
  }

  public long getPrevLeaf() {
    return prevLeaf;
  }

  public long getNextLeaf() {
    return nextLeaf;
  }

  public void setPrevLeaf(final long prevLeaf) {
    this.prevLeaf = prevLeaf;
  }

  public void setNextLeaf(final long nextLeaf) {
    this.nextLeaf = nextLeaf;
  }

  public Hash getHkey() {
    return hkey;
  }

  public Bytes getHval() {
    return hval;
  }

  public void setHval(final Bytes hval) {
    this.hval = hval;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LeafOpening that = (LeafOpening) o;
    return prevLeaf == that.prevLeaf
        && nextLeaf == that.nextLeaf
        && Objects.equals(hkey, that.hkey)
        && Objects.equals(hval, that.hval);
  }

  @Override
  public int hashCode() {
    return Objects.hash(hkey, hval, prevLeaf, nextLeaf);
  }

  public static LeafOpening readFrom(final Bytes encodedBytes) {
    return BytesBuffer.readBytes(
        encodedBytes,
        bytesInput ->
            new LeafOpening(
                bytesInput.readUInt256().toLong(),
                bytesInput.readUInt256().toLong(),
                Hash.wrap(bytesInput.readBytes32()),
                bytesInput.readBytes32()));
  }

  @JsonIgnore
  public Bytes getEncodesBytes() {
    return Bytes.concatenate(
        UInt256.valueOf(prevLeaf), // Prev
        UInt256.valueOf(nextLeaf), // Next ,
        hkey, // HKEY
        hval); // HVALUE
  }

  @Override
  public String toString() {
    return "LeafOpening{"
        + "hkey="
        + hkey
        + ", hval="
        + hval.toHexString()
        + ", prevLeaf="
        + prevLeaf
        + ", nextLeaf="
        + nextLeaf
        + '}';
  }
}
