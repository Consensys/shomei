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

package net.consensys.shomei.worldview;

import net.consensys.shomei.ZkAccount;
import net.consensys.shomei.ZkValue;
import net.consensys.shomei.trielog.TrieLogAccountValue;
import net.consensys.shomei.trielog.TrieLogLayer;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;

public class ZkEvmWorldStateUpdateAccumulator {

  private final Map<Hash, ZkValue<Address, ZkAccount>> accountsToUpdate = new ConcurrentHashMap<>();

  private final Map<Hash, Map<Hash, ZkValue<UInt256, UInt256>>> storageToUpdate =
      new ConcurrentHashMap<>();

  private boolean isAccumulatorStateChanged;

  public ZkEvmWorldStateUpdateAccumulator() {
    this.isAccumulatorStateChanged = false;
  }

  public Map<Hash, ZkValue<Address, ZkAccount>> getAccountsToUpdate() {
    return accountsToUpdate;
  }

  public Map<Hash, Map<Hash, ZkValue<UInt256, UInt256>>> getStorageToUpdate() {
    return storageToUpdate;
  }

  public void rollForward(final TrieLogLayer layer) {
    layer
        .streamAccountChanges()
        .forEach(
            entry ->
                rollAccountChange(
                    entry.getKey(),
                    entry.getValue().getKey(),
                    entry.getValue().getPrior(),
                    entry.getValue().getUpdated(),
                    true));
    layer
        .streamStorageChanges()
        .forEach(
            entry ->
                entry
                    .getValue()
                    .forEach(
                        (storageHash, value) ->
                            rollStorageChange(
                                entry.getKey(),
                                storageHash,
                                value.getKey(),
                                value.getPrior(),
                                value.getUpdated(),
                                true)));
  }

  public void rollBack(final TrieLogLayer layer) {
    layer
        .streamAccountChanges()
        .forEach(
            entry ->
                rollAccountChange(
                    entry.getKey(),
                    entry.getValue().getKey(),
                    entry.getValue().getUpdated(),
                    entry.getValue().getPrior(),
                    false));
    layer
        .streamStorageChanges()
        .forEach(
            entry ->
                entry
                    .getValue()
                    .forEach(
                        (slotHash, value) ->
                            rollStorageChange(
                                entry.getKey(),
                                slotHash,
                                value.getKey(),
                                value.getUpdated(),
                                value.getPrior(),
                                false)));
  }

  private void rollAccountChange(
      final Hash hkey,
      final Address address,
      final TrieLogAccountValue expectedValue,
      final TrieLogAccountValue replacementValue,
      final boolean isRollforward) {
    ZkValue<Address, ZkAccount> accountValue = accountsToUpdate.get(hkey);
    if (accountValue == null && expectedValue != null) {
      accountValue =
          accountsToUpdate.compute(
              hkey,
              (__, zkAccountZkValue) ->
                  new ZkValue<>(
                      address,
                      new ZkAccount(hkey, address, expectedValue),
                      new ZkAccount(hkey, address, expectedValue)));
    }
    if (accountValue == null) {
      accountValue =
          accountsToUpdate.compute(
              hkey,
              (__, zkAccountZkValue) ->
                  new ZkValue<>(address, null, new ZkAccount(hkey, address, replacementValue)));
    } else {
      if (expectedValue == null) {
        if (accountValue.getUpdated() != null) {
          throw new IllegalStateException(
              String.format(
                  "Expected to create account, but the account exists.  Address=%s", hkey));
        }
      } else {
        ZkAccount.assertCloseEnoughForDiffing(
            accountValue.getUpdated(),
            expectedValue,
            "Address=" + hkey + " Prior Value in Rolling Change");
      }
      if (replacementValue == null) {
        accountValue.setUpdated(null);
      } else {
        accountValue.setUpdated(new ZkAccount(hkey, address, replacementValue));
      }
    }
    accountValue.setRollforward(isRollforward);
  }

  private void rollStorageChange(
      final Hash hkey,
      final Hash storageHash,
      final UInt256 storageKey,
      final UInt256 expectedValue,
      final UInt256 replacementValue,
      final boolean isRollforward) {
    if (replacementValue == null && expectedValue != null && expectedValue.isZero()) {
      // corner case on deletes, non-change
      return;
    }
    final Map<Hash, ZkValue<UInt256, UInt256>> storageMap = storageToUpdate.get(hkey);
    ZkValue<UInt256, UInt256> slotValue = storageMap == null ? null : storageMap.get(storageHash);
    if (slotValue == null && expectedValue != null) {
      slotValue =
          new ZkValue<>(
              storageKey,
              expectedValue.isZero() ? null : expectedValue,
              expectedValue.isZero() ? null : expectedValue);
      storageToUpdate
          .computeIfAbsent(hkey, slotHash -> new ConcurrentHashMap<>())
          .put(storageHash, slotValue);
    }
    if (slotValue == null) {
      slotValue = new ZkValue<>(storageKey, null, replacementValue);
      storageToUpdate
          .computeIfAbsent(hkey, slotHash -> new ConcurrentHashMap<>())
          .put(storageHash, slotValue);
    } else {
      final UInt256 existingSlotValue = slotValue.getUpdated();
      if ((expectedValue == null || expectedValue.isZero())
          && existingSlotValue != null
          && !existingSlotValue.isZero()) {
        throw new IllegalStateException(
            String.format(
                "Expected to create slot, but the slot exists. Account=%s SlotHash=%s expectedValue=%s existingValue=%s",
                hkey, storageHash, expectedValue, existingSlotValue));
      }
      if (!isSlotEquals(expectedValue, existingSlotValue)) {
        throw new IllegalStateException(
            String.format(
                "Old value of slot does not match expected value. Account=%s SlotHash=%s Expected=%s Actual=%s",
                hkey,
                storageHash,
                expectedValue == null ? "null" : expectedValue.toShortHexString(),
                existingSlotValue == null ? "null" : existingSlotValue.toShortHexString()));
      }
      slotValue.setUpdated(replacementValue);
    }
    slotValue.setRollforward(isRollforward);
  }

  private boolean isSlotEquals(final UInt256 expectedValue, final UInt256 existingSlotValue) {
    final UInt256 sanitizedExpectedValue = (expectedValue == null) ? UInt256.ZERO : expectedValue;
    final UInt256 sanitizedExistingSlotValue =
        (existingSlotValue == null) ? UInt256.ZERO : existingSlotValue;
    return Objects.equals(sanitizedExpectedValue, sanitizedExistingSlotValue);
  }

  public boolean isAccumulatorStateChanged() {
    return isAccumulatorStateChanged;
  }

  public void resetAccumulatorStateChanged() {
    isAccumulatorStateChanged = false;
  }

  public void reset() {
    storageToUpdate.clear();
    accountsToUpdate.clear();
    resetAccumulatorStateChanged();
  }
}
