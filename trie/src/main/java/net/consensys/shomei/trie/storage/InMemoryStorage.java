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

package net.consensys.shomei.trie.storage;

import net.consensys.shomei.trie.model.FlattenedLeaf;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class InMemoryStorage implements TrieStorage, TrieStorage.TrieUpdater {

  private final TreeMap<Bytes, Optional<FlattenedLeaf>> flatLeafStorage =
          new TreeMap<>(Comparator.naturalOrder());
  private final Map<Bytes, Optional<Bytes>> trieNodeStorage = new ConcurrentHashMap<>();

public TreeMap<Bytes, Optional<FlattenedLeaf>> getFlatLeafStorage() {
    return flatLeafStorage;
  }

  public Map<Bytes, Optional<Bytes>> getTrieNodeStorage() {
    return trieNodeStorage;
  }

  @Override
  public Optional<FlattenedLeaf> getFlatLeaf(final Bytes hkey) {
    final Optional<FlattenedLeaf> value = flatLeafStorage.get(hkey);
    return Objects.requireNonNullElseGet(value, Optional::empty);
  }

  @Override
  public Range getNearestKeys(final Bytes hkey) {
    final Iterator<Map.Entry<Bytes, Optional<FlattenedLeaf>>> iterator =
            flatLeafStorage.entrySet().stream()
                    .filter(e -> e.getValue().isPresent())
                    .iterator();
    Map.Entry<Bytes, FlattenedLeaf> next = Map.entry(Bytes32.ZERO, FlattenedLeaf.HEAD);
    Map.Entry<Bytes, FlattenedLeaf> left = next;
    Optional<Map.Entry<Bytes, FlattenedLeaf>> maybeMiddle = Optional.empty();
    int compKeyResult;
    while (iterator.hasNext() && (compKeyResult = next.getKey().compareTo(hkey)) <= 0) {
      if (compKeyResult == 0) {
        maybeMiddle = Optional.of(next);
      } else {
        left = next;
      }
      var rawEntry = iterator.next();
      next = Map.entry(rawEntry.getKey(), rawEntry.getValue().get());
    }
    return new Range(
        Map.entry(left.getKey(), left.getValue()),
        maybeMiddle.map(middle -> Map.entry(middle.getKey(), middle.getValue())),
        Map.entry(next.getKey(), next.getValue()));
  }


  @Override
  public Optional<Bytes> getTrieNode(final Bytes location, final Bytes nodeHash) {
    final Optional<Bytes> value = trieNodeStorage.get(location);
    if (value == null) {
      // Key was never inserted
      return Optional.empty();
    }
    // Returns the Optional: present = exists, empty = deleted
    return value;
  }

  @Override
  public void putTrieNode(final Bytes location, final Bytes nodeHash, final Bytes value) {
    trieNodeStorage.put(location, Optional.of(value));
  }

  @Override
  public TrieUpdater updater() {
    return this;
  }

  @Override
  public void putFlatLeaf(final Bytes key, final FlattenedLeaf value) {
    flatLeafStorage.put(key, Optional.of(value));
  }

  @Override
  public void removeFlatLeafValue(final Bytes key) {
    flatLeafStorage.put(key, Optional.empty());
  }

  @Override
  public void commit() {
    // no-op
  }
}
