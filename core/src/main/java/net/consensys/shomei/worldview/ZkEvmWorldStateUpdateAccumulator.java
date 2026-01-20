/*
 * Copyright Consensys Software Inc., 2025
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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import net.consensys.shomei.ZkAccount;
import net.consensys.shomei.ZkValue;
import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.trielog.StorageSlotKey;
import net.consensys.shomei.trielog.TrieLogLayer;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * The ZkEvmWorldStateUpdateAccumulator class is responsible for accumulating updates to the EVM
 * world state in a zero-knowledge-friendly manner. It tracks the changes made to account states and
 * storage values within the EVM world state.
 */
public class ZkEvmWorldStateUpdateAccumulator {

  private final Map<AccountKey, ZkValue<ZkAccount>> accountsToUpdate = new ConcurrentHashMap<>();

  private final Map<AccountKey, Map<StorageSlotKey, ZkValue<UInt256>>> storageToUpdate =
      new ConcurrentHashMap<>();

  private boolean isAccumulatorStateChanged;

  public ZkEvmWorldStateUpdateAccumulator() {
    this.isAccumulatorStateChanged = false;
  }

  public Map<AccountKey, ZkValue<ZkAccount>> getAccountsToUpdate() {
    return accountsToUpdate;
  }

  public Map<AccountKey, Map<StorageSlotKey, ZkValue<UInt256>>> getStorageToUpdate() {
    return storageToUpdate;
  }

  public void rollForward(final TrieLogLayer layer) {
    layer
        .streamAccountChanges()
        .forEach(
            entry ->
                rollAccountChange(
                    entry.getKey(),
                    entry.getValue().getPrior(),
                    entry.getValue().getUpdated(),
                    entry.getValue().isCleared(),
                    true));
    layer
        .streamStorageChanges()
        .forEach(
            entry ->
                entry
                    .getValue()
                    .forEach(
                        (storageSlotKey, value) ->
                            rollStorageChange(
                                entry.getKey(),
                                storageSlotKey,
                                value.getPrior(),
                                value.getUpdated(),
                                value.isCleared(),
                                true)));
  }

  public void rollBack(final TrieLogLayer layer) {
    layer
        .streamAccountChanges()
        .forEach(
            entry ->
                rollAccountChange(
                    entry.getKey(),
                    entry.getValue().getUpdated(),
                    entry.getValue().getPrior(),
                    entry.getValue().isCleared(),
                    false));
    layer
        .streamStorageChanges()
        .forEach(
            entry ->
                entry
                    .getValue()
                    .forEach(
                        (storageSlotKey, value) ->
                            rollStorageChange(
                                entry.getKey(),
                                storageSlotKey,
                                value.getUpdated(),
                                value.getPrior(),
                                value.isCleared(),
                                false)));
  }

  private void rollAccountChange(
      final AccountKey accountKey,
      final ZkAccount expectedValue,
      final ZkAccount replacementValue,
      final boolean isCleared,
      final boolean isRollforward) {
    ZkValue<ZkAccount> accountValue = accountsToUpdate.get(accountKey);
    if (accountValue == null && expectedValue != null) {
      accountValue =
          accountsToUpdate.compute(
              accountKey,
              (__, zkAccountZkValue) -> new ZkValue<>(expectedValue, expectedValue, isCleared));
    }
    if (accountValue == null) {
      accountValue =
          accountsToUpdate.compute(
              accountKey, (__, zkAccountZkValue) -> new ZkValue<>(null, replacementValue));
    } else {
      if (expectedValue == null) {
        if (accountValue.getUpdated() != null) {
          throw new IllegalStateException(
              String.format(
                  "Expected to create account, but the account exists.  Address=%s",
                  accountKey.address()));
        }
      } else {
        ZkAccount.assertCloseEnoughForDiffing(
            accountValue.getUpdated(),
            expectedValue,
            "Address=" + accountKey.address() + " Prior Value in Rolling Change");
      }
      if (replacementValue == null) {
        accountValue.setUpdated(null);
      } else {
        accountValue.setUpdated(replacementValue);
      }
    }
    accountValue.setRollforward(isRollforward);
  }

  private void rollStorageChange(
      final AccountKey accountKey,
      final StorageSlotKey storageSlotKey,
      final UInt256 expectedValue,
      final UInt256 replacementValue,
      final boolean isCleared,
      final boolean isRollforward) {
    final Map<StorageSlotKey, ZkValue<UInt256>> storageMap = storageToUpdate.get(accountKey);
    ZkValue<UInt256> slotValue = storageMap == null ? null : storageMap.get(storageSlotKey);
    if (slotValue == null && expectedValue != null) {
      slotValue =
          new ZkValue<>(
              expectedValue.isZero() ? null : expectedValue,
              expectedValue.isZero() ? null : expectedValue,
              isCleared);
      storageToUpdate
          .computeIfAbsent(accountKey, slotHash -> new ConcurrentHashMap<>())
          .put(storageSlotKey, slotValue);
    }
    if (slotValue == null) {
      slotValue = new ZkValue<>(null, replacementValue);
      storageToUpdate
          .computeIfAbsent(accountKey, slotHash -> new ConcurrentHashMap<>())
          .put(storageSlotKey, slotValue);
    } else {
      final UInt256 existingSlotValue = slotValue.getUpdated();
      if ((expectedValue == null || expectedValue.isZero())
          && existingSlotValue != null
          && !existingSlotValue.isZero()) {
        throw new IllegalStateException(
            String.format(
                "Expected to create slot, but the slot exists. Account=%s SlotHash=%s expectedValue=%s existingValue=%s",
                accountKey.address(), storageSlotKey.slotHash(), expectedValue, existingSlotValue));
      }
      if (!isSlotEquals(expectedValue, existingSlotValue)) {
        throw new IllegalStateException(
            String.format(
                "Old value of slot does not match expected value. Account=%s SlotHash=%s Expected=%s Actual=%s",
                accountKey.address(),
                storageSlotKey.slotHash(),
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
