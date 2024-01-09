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

package net.consensys.shomei.trielog;

import static net.consensys.shomei.util.bytes.MimcSafeBytes.safeUInt256;

import net.consensys.shomei.util.bytes.MimcSafeBytes;
import net.consensys.zkevm.HashProvider;

import java.util.Objects;

import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;
import org.jetbrains.annotations.NotNull;

public record StorageSlotKey(Hash slotHash, MimcSafeBytes<UInt256> slotKey)
    implements Comparable<StorageSlotKey> {

  public StorageSlotKey(final Hash slotHash, final UInt256 slotKey) {
    this(slotHash, safeUInt256(slotKey));
  }

  public StorageSlotKey(final UInt256 slotKey) {
    this(HashProvider.trieHash(safeUInt256(slotKey)), slotKey);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    StorageSlotKey that = (StorageSlotKey) o;
    return Objects.equals(slotHash, that.slotHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slotHash.hashCode());
  }

  @Override
  public String toString() {
    return String.format("StorageSlotKey{slotHash=%s, slotKey=%s}", slotHash, slotKey);
  }

  @Override
  public int compareTo(@NotNull final StorageSlotKey other) {
    return this.slotHash.compareTo(other.slotHash);
  }
}
