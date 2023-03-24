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
import org.hyperledger.besu.datatypes.Hash;

public class ZkEvmWorldStateUpdateAccumulator {

  private final Map<Hash, ZkValue<ZkAccount>> accountsToUpdate = new ConcurrentHashMap<>();

  private final Map<Hash, Map<Hash, ZkValue<UInt256>>> storageToUpdate = new ConcurrentHashMap<>();

  private boolean isAccumulatorStateChanged;

  public ZkEvmWorldStateUpdateAccumulator() {
    this.isAccumulatorStateChanged = false;
  }

  public Map<Hash, ZkValue<ZkAccount>> getAccountsToUpdate() {
    return accountsToUpdate;
  }

  public Map<Hash, Map<Hash, ZkValue<UInt256>>> getStorageToUpdate() {
    return storageToUpdate;
  }

  public void rollForward(final TrieLogLayer layer) {
    layer
        .streamAccountChanges()
        .forEach(
            entry ->
                rollAccountChange(
                    entry.getKey(), entry.getValue().getPrior(), entry.getValue().getUpdated()));
    layer
        .streamStorageChanges()
        .forEach(
            entry ->
                entry
                    .getValue()
                    .forEach(
                        (key, value) ->
                            rollStorageChange(
                                entry.getKey(), key, value.getPrior(), value.getUpdated())));
  }

  public void rollBack(final TrieLogLayer layer) {
    layer
        .streamAccountChanges()
        .forEach(
            entry ->
                rollAccountChange(
                    entry.getKey(), entry.getValue().getUpdated(), entry.getValue().getPrior()));
    layer
        .streamStorageChanges()
        .forEach(
            entry ->
                entry
                    .getValue()
                    .forEach(
                        (slotHash, value) ->
                            rollStorageChange(
                                entry.getKey(), slotHash, value.getUpdated(), value.getPrior())));
  }

  private void rollAccountChange(
      final Hash hkey,
      final TrieLogAccountValue expectedValue,
      final TrieLogAccountValue replacementValue) {
    if (Objects.equals(expectedValue, replacementValue)) {
      // non-change, a cached read.
      return;
    }
    ZkValue<ZkAccount> accountValue = accountsToUpdate.get(hkey);
    if (accountValue == null && expectedValue != null) {
      accountValue =
          accountsToUpdate.compute(
              hkey,
              (hkey1, zkAccountZkValue) ->
                  new ZkValue<>(
                      new ZkAccount(hkey, expectedValue), new ZkAccount(hkey, expectedValue)));
    }
    if (accountValue == null) {
      accountsToUpdate.put(hkey, new ZkValue<>(null, new ZkAccount(hkey, replacementValue)));
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
        if (accountValue.getPrior() == null) {
          accountsToUpdate.remove(hkey);
        } else {
          accountValue.setUpdated(null);
        }
      } else {
        accountValue.setUpdated(new ZkAccount(hkey, replacementValue));
      }
    }
  }

  private void rollStorageChange(
      final Hash hkey,
      final Hash key,
      final UInt256 expectedValue,
      final UInt256 replacementValue) {
    if (Objects.equals(expectedValue, replacementValue)) {
      // non-change, a cached read.
      return;
    }
    if (replacementValue == null && expectedValue != null && expectedValue.isZero()) {
      // corner case on deletes, non-change
      return;
    }
    final Map<Hash, ZkValue<UInt256>> storageMap = storageToUpdate.get(hkey);
    ZkValue<UInt256> slotValue = storageMap == null ? null : storageMap.get(key);
    if (slotValue == null && expectedValue != null) {
      slotValue = new ZkValue<>(expectedValue, expectedValue);
      storageToUpdate.computeIfAbsent(hkey, hkey1 -> new ConcurrentHashMap<>()).put(key, slotValue);
    }
    if (slotValue == null) {
      maybeCreateStorageMap(storageMap, hkey).put(key, new ZkValue<>(null, replacementValue));
    } else {
      final UInt256 existingSlotValue = slotValue.getUpdated();
      if ((expectedValue == null || expectedValue.isZero())
          && existingSlotValue != null
          && !existingSlotValue.isZero()) {
        throw new IllegalStateException(
            String.format(
                "Expected to create slot, but the slot exists. Account=%s SlotHash=%s expectedValue=%s existingValue=%s",
                hkey, key, expectedValue, existingSlotValue));
      }
      if (!isSlotEquals(expectedValue, existingSlotValue)) {
        throw new IllegalStateException(
            String.format(
                "Old value of slot does not match expected value. Account=%s SlotHash=%s Expected=%s Actual=%s",
                hkey,
                key,
                expectedValue == null ? "null" : expectedValue.toShortHexString(),
                existingSlotValue == null ? "null" : existingSlotValue.toShortHexString()));
      }
      if (replacementValue == null && slotValue.getPrior() == null) {
        final Map<Hash, ZkValue<UInt256>> thisStorageUpdate =
            maybeCreateStorageMap(storageMap, hkey);
        thisStorageUpdate.remove(key);
        if (thisStorageUpdate.isEmpty()) {
          storageToUpdate.remove(hkey);
        }
      } else {
        slotValue.setUpdated(replacementValue);
      }
    }
  }

  private Map<Hash, ZkValue<UInt256>> maybeCreateStorageMap(
      final Map<Hash, ZkValue<UInt256>> storageMap, final Hash hkey) {
    if (storageMap == null) {
      Map<Hash, ZkValue<UInt256>> newMap = new ConcurrentHashMap<>();
      storageToUpdate.put(hkey, newMap);
      return newMap;
    } else {
      return storageMap;
    }
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
