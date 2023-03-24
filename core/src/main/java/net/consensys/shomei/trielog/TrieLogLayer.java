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

import net.consensys.shomei.ZkValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

/**
 * This class encapsulates the changes that are done to transition one block to the next. This
 * includes serialization and deserialization tasks for storing this log to off-memory storage.
 *
 * <p>In this particular formulation only the "Leaves" are tracked" Future layers may track patrica
 * trie changes as well.
 */
public class TrieLogLayer {

  private Hash blockHash;
  private final Map<Hash, ZkValue<TrieLogAccountValue>> accounts;
  private final Map<Hash, Map<Hash, ZkValue<UInt256>>> storage;

  private boolean frozen = false;

  public TrieLogLayer() {
    this.accounts = new HashMap<>();
    this.storage = new HashMap<>();
  }

  /** Locks the layer so no new changes can be added; */
  void freeze() {
    frozen = true; // The code never bothered me anyway
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  public void setBlockHash(final Hash blockHash) {
    checkState(!frozen, "Layer is Frozen");
    this.blockHash = blockHash;
  }

  public void addAccountChange(
      final Hash hkey, final TrieLogAccountValue oldValue, final TrieLogAccountValue newValue) {
    checkState(!frozen, "Layer is Frozen");
    accounts.put(hkey, new ZkValue<>(oldValue, newValue));
  }

  public void addStorageChange(
      final Hash hkey, final Hash storageKey, final UInt256 oldValue, final UInt256 newValue) {
    checkState(!frozen, "Layer is Frozen");
    storage
        .computeIfAbsent(hkey, a -> new TreeMap<>())
        .put(storageKey, new ZkValue<>(oldValue, newValue));
  }

  public static TrieLogLayer fromBytes(final byte[] bytes) {
    return readFrom(new BytesValueRLPInput(Bytes.wrap(bytes), false));
  }

  public static TrieLogLayer readFrom(final RLPInput input) {
    final TrieLogLayer newLayer = new TrieLogLayer();

    input.enterList();
    newLayer.blockHash = Hash.wrap(input.readBytes32());

    while (!input.isEndOfCurrentList()) {
      input.enterList();
      final Hash hkey = Hash.wrap(input.readBytes32());

      if (input.nextIsNull()) {
        input.skipNext();
      } else {
        input.enterList();
        final TrieLogAccountValue oldValue = nullOrValue(input, TrieLogAccountValue::readFrom);
        final TrieLogAccountValue newValue = nullOrValue(input, TrieLogAccountValue::readFrom);
        input.leaveList();
        newLayer.accounts.put(hkey, new ZkValue<>(oldValue, newValue));
      }

      if (input.nextIsNull()) {
        input.skipNext();
      } else {
        final Map<Hash, ZkValue<UInt256>> storageChanges = new TreeMap<>();
        input.enterList();
        while (!input.isEndOfCurrentList()) {
          input.enterList();
          final Hash slotHash = Hash.wrap(input.readBytes32());
          final UInt256 oldValue = nullOrValue(input, RLPInput::readUInt256Scalar);
          final UInt256 newValue = nullOrValue(input, RLPInput::readUInt256Scalar);
          storageChanges.put(slotHash, new ZkValue<>(oldValue, newValue));
          input.leaveList();
        }
        input.leaveList();
        newLayer.storage.put(hkey, storageChanges);
      }

      // TODO add trie nodes

      // lenient leave list for forward compatible additions.
      input.leaveListLenient();
    }
    input.leaveListLenient();
    newLayer.freeze();

    return newLayer;
  }

  public void writeTo(final RLPOutput output) {
    freeze();

    final Set<Hash> hkeyes = new TreeSet<>();
    hkeyes.addAll(accounts.keySet());
    hkeyes.addAll(storage.keySet());

    output.startList(); // container
    output.writeBytes(blockHash);

    for (final Hash hkey : hkeyes) {
      output.startList(); // this change
      output.writeBytes(hkey);

      final ZkValue<TrieLogAccountValue> accountChange = accounts.get(hkey);
      if (accountChange == null || accountChange.isUnchanged()) {
        output.writeNull();
      } else {
        accountChange.writeRlp(output, (o, sta) -> sta.writeTo(o));
      }

      final Map<Hash, ZkValue<UInt256>> storageChanges = storage.get(hkey);
      if (storageChanges == null) {
        output.writeNull();
      } else {
        output.startList();
        for (final Map.Entry<Hash, ZkValue<UInt256>> storageChangeEntry :
            storageChanges.entrySet()) {
          output.startList();
          output.writeBytes(storageChangeEntry.getKey());
          storageChangeEntry.getValue().writeInnerRlp(output, RLPOutput::writeUInt256Scalar);
          output.endList();
        }
        output.endList();
      }

      output.endList(); // this change
    }
    output.endList(); // container
  }

  public Stream<Map.Entry<Hash, ZkValue<TrieLogAccountValue>>> streamAccountChanges() {
    return accounts.entrySet().stream();
  }

  public Stream<Map.Entry<Hash, Map<Hash, ZkValue<UInt256>>>> streamStorageChanges() {
    return storage.entrySet().stream();
  }

  public boolean hasStorageChanges(final Hash hkey) {
    return storage.containsKey(hkey);
  }

  public Stream<Map.Entry<Hash, ZkValue<UInt256>>> streamStorageChanges(final Hash hkey) {
    return storage.getOrDefault(hkey, Map.of()).entrySet().stream();
  }

  private static <T> T nullOrValue(final RLPInput input, final Function<RLPInput, T> reader) {
    if (input.nextIsNull()) {
      input.skipNext();
      return null;
    } else {
      return reader.apply(input);
    }
  }

  Optional<UInt256> getPriorStorage(final Hash hkey, final Hash key) {
    return Optional.ofNullable(storage.get(hkey)).map(i -> i.get(key)).map(ZkValue::getPrior);
  }

  Optional<UInt256> getStorage(final Hash hkey, final Hash key) {
    return Optional.ofNullable(storage.get(hkey)).map(i -> i.get(key)).map(ZkValue::getUpdated);
  }

  public Optional<TrieLogAccountValue> getPriorAccount(final Hash hkey) {
    return Optional.ofNullable(accounts.get(hkey)).map(ZkValue::getPrior);
  }

  public Optional<TrieLogAccountValue> getAccount(final Hash hkey) {
    return Optional.ofNullable(accounts.get(hkey)).map(ZkValue::getUpdated);
  }

  public String dump() {
    final StringBuilder sb = new StringBuilder();
    sb.append("TrieLogLayer{" + "blockHash=").append(blockHash).append(frozen).append('}');
    sb.append("accounts\n");
    for (final Map.Entry<Hash, ZkValue<TrieLogAccountValue>> account : accounts.entrySet()) {
      sb.append(" : ").append(account.getKey()).append("\n");
      if (Objects.equals(account.getValue().getPrior(), account.getValue().getUpdated())) {
        sb.append("   = ").append(account.getValue().getUpdated()).append("\n");
      } else {
        sb.append("   - ").append(account.getValue().getPrior()).append("\n");
        sb.append("   + ").append(account.getValue().getUpdated()).append("\n");
      }
    }
    sb.append("Storage").append("\n");
    for (final Map.Entry<Hash, Map<Hash, ZkValue<UInt256>>> storage : storage.entrySet()) {
      sb.append(" : ").append(storage.getKey()).append("\n");
      for (final Map.Entry<Hash, ZkValue<UInt256>> slot : storage.getValue().entrySet()) {
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
