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

import static com.google.common.base.Preconditions.checkState;

import net.consensys.shomei.ZkAccount;
import net.consensys.shomei.ZkValue;
import net.consensys.shomei.util.bytes.MimcSafeBytes;
import net.consensys.zkevm.HashProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

/**
 * This class encapsulates the changes that are done to transition one block to the next. This
 * includes serialization and deserialization tasks for storing this log to off-memory storage.
 *
 * <p>In this particular formulation only the "Leaves" are tracked" Future layers may track patrica
 * trie changes as well.
 */
public class TrieLogLayer {

  private Hash blockHash;

  private long blockNumber;
  private final Map<AccountKey, ZkValue<ZkAccount>> accounts;
  private final Map<AccountKey, Map<StorageSlotKey, ZkValue<UInt256>>> storages;

  private boolean frozen = false;

  public TrieLogLayer() {
    this.accounts = new HashMap<>();
    this.storages = new HashMap<>();
  }

  /** Locks the layer so no new changes can be added; */
  void freeze() {
    frozen = true; // The code never bothered me anyway
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  public TrieLogLayer setBlockHash(final Hash blockHash) {
    checkState(!frozen, "Layer is Frozen");
    this.blockHash = blockHash;
    return this;
  }

  public long getBlockNumber() {
    return blockNumber;
  }

  public TrieLogLayer setBlockNumber(final long blockNumber) {
    this.blockNumber = blockNumber;
    return this;
  }

  public TrieLogLayer addAccountChange(
      final AccountKey accountKey,
      final ZkAccount oldValue,
      final ZkAccount newValue,
      final boolean isCleared) {
    checkState(!frozen, "Layer is Frozen");
    accounts.put(accountKey, new ZkValue<>(oldValue, newValue, isCleared));
    return this;
  }

  public AccountKey addAccountChange(
      final MimcSafeBytes<Address> address,
      final ZkAccount oldValue,
      final ZkAccount newValue,
      final boolean isCleared) {
    final AccountKey accountKey = new AccountKey(HashProvider.trieHash(address), address);
    addAccountChange(accountKey, oldValue, newValue, isCleared);
    return accountKey;
  }

  public AccountKey addAccountChange(
      final MimcSafeBytes<Address> address, final ZkAccount oldValue, final ZkAccount newValue) {
    return addAccountChange(address, oldValue, newValue, false);
  }

  public TrieLogLayer addStorageChange(
      final AccountKey accountKey,
      final StorageSlotKey storageSlotKey,
      final UInt256 oldValue,
      final UInt256 newValue,
      final boolean isCleared) {
    checkState(!frozen, "Layer is Frozen");
    storages
        .computeIfAbsent(accountKey, a -> new TreeMap<>())
        .put(storageSlotKey, new ZkValue<>(oldValue, newValue, isCleared));
    return this;
  }

  public TrieLogLayer addStorageChange(
      final AccountKey accountKey,
      final StorageSlotKey storageKey,
      final UInt256 oldValue,
      final UInt256 newValue) {
    addStorageChange(accountKey, storageKey, oldValue, newValue, false);
    return this;
  }

  public TrieLogLayer addStorageChange(
      final AccountKey accountKey,
      final UInt256 storageKey,
      final UInt256 oldValue,
      final UInt256 newValue) {
    addStorageChange(accountKey, new StorageSlotKey(storageKey), oldValue, newValue, false);
    return this;
  }

  public Stream<Map.Entry<AccountKey, ZkValue<ZkAccount>>> streamAccountChanges() {
    return accounts.entrySet().stream();
  }

  public Stream<Map.Entry<AccountKey, Map<StorageSlotKey, ZkValue<UInt256>>>>
      streamStorageChanges() {
    return storages.entrySet().stream();
  }

  public boolean hasStorageChanges(final AccountKey accountKey) {
    return storages.containsKey(accountKey);
  }

  public Stream<Map.Entry<StorageSlotKey, ZkValue<UInt256>>> streamStorageChanges(
      final AccountKey accountKey) {
    return storages.getOrDefault(accountKey, Map.of()).entrySet().stream();
  }

  public static <T> T nullOrValue(final RLPInput input, final Function<RLPInput, T> reader) {
    if (input.nextIsNull()) {
      input.skipNext();
      return null;
    } else {
      return reader.apply(input);
    }
  }

  public static <T> T defaultOrValue(
      final RLPInput input, final T defaultValue, final Function<RLPInput, T> reader) {
    final T value = nullOrValue(input, reader);
    return value == null ? defaultValue : value;
  }

  Optional<UInt256> getPriorStorage(
      final AccountKey accountKey, final StorageSlotKey storageSlotKey) {
    return Optional.ofNullable(storages.get(accountKey))
        .map(i -> i.get(storageSlotKey))
        .map(ZkValue::getPrior);
  }

  Optional<UInt256> getStorage(final AccountKey accountKey, final StorageSlotKey storageSlotKey) {
    return Optional.ofNullable(storages.get(accountKey))
        .map(i -> i.get(storageSlotKey))
        .map(ZkValue::getUpdated);
  }

  public Optional<ZkAccount> getPriorAccount(final AccountKey accountKey) {
    return Optional.ofNullable(accounts.get(accountKey)).map(ZkValue::getPrior);
  }

  public Optional<ZkAccount> getAccount(final AccountKey accountKey) {
    return Optional.ofNullable(accounts.get(accountKey)).map(ZkValue::getUpdated);
  }

  public String dump() {
    final StringBuilder sb = new StringBuilder();
    sb.append("TrieLogLayer{" + "blockHash=").append(blockHash).append(frozen).append('}');
    sb.append("accounts\n");
    for (final Map.Entry<AccountKey, ZkValue<ZkAccount>> account : accounts.entrySet()) {
      sb.append(" : ").append(account.getKey()).append("\n");
      if (Objects.equals(account.getValue().getPrior(), account.getValue().getUpdated())) {
        sb.append("   = ").append(account.getValue().getUpdated()).append("\n");
      } else {
        sb.append("   - ").append(account.getValue().getPrior()).append("\n");
        sb.append("   + ").append(account.getValue().getUpdated()).append("\n");
      }
    }
    sb.append("Storage").append("\n");
    for (final Map.Entry<AccountKey, Map<StorageSlotKey, ZkValue<UInt256>>> storage :
        storages.entrySet()) {
      sb.append(" : ").append(storage.getKey()).append("\n");
      for (final Map.Entry<StorageSlotKey, ZkValue<UInt256>> slot : storage.getValue().entrySet()) {
        final UInt256 originalValue = slot.getValue().getPrior();
        final UInt256 updatedValue = slot.getValue().getUpdated();
        sb.append("   : ").append(slot.getKey()).append("\n");
        if (Objects.equals(originalValue, updatedValue)) {
          sb.append("     = ")
              .append((originalValue == null) ? "null" : originalValue.toShortHexString())
              .append("\n");
        } else {
          sb.append("     - ")
              .append((originalValue == null) ? "null" : originalValue.toShortHexString())
              .append("\n");
          sb.append("     + ")
              .append((updatedValue == null) ? "null" : updatedValue.toShortHexString())
              .append("\n");
        }
      }
    }
    return sb.toString();
  }
}
