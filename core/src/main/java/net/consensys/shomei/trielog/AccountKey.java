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

import net.consensys.shomei.util.bytes.MimcSafeBytes;
import net.consensys.zkevm.HashProvider;

import java.util.Objects;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.jetbrains.annotations.NotNull;

public record AccountKey(Hash accountHash, MimcSafeBytes<Address> address)
    implements Comparable<AccountKey> {

  public AccountKey(final Hash accountHash, final Address address) {
    this(accountHash, MimcSafeBytes.safeAddress(address));
  }

  public AccountKey(final Address address) {
    this(MimcSafeBytes.safeAddress(address));
  }

  public AccountKey(final MimcSafeBytes<Address> address) {
    this(HashProvider.trieHash(address), address);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AccountKey that = (AccountKey) o;
    return Objects.equals(accountHash, that.accountHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(accountHash.hashCode());
  }

  @Override
  public String toString() {
    return String.format("AccountKey{accountHash=%s, address=%s}", accountHash, address);
  }

  @Override
  public int compareTo(@NotNull final AccountKey other) {
    return this.accountHash.compareTo(other.accountHash);
  }
}
