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
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class InMemoryStorage implements TrieStorage, TrieStorage.TrieUpdater {

  private final TreeMap<Bytes, FlattenedLeaf> flatLeafStorage =
      new TreeMap<>(Comparator.naturalOrder());
  private final Map<Bytes, Bytes> trieNodeStorage = new ConcurrentHashMap<>();

  public TreeMap<Bytes, FlattenedLeaf> getFlatLeafStorage() {
    return flatLeafStorage;
  }

  public Map<Bytes, Bytes> getTrieNodeStorage() {
    return trieNodeStorage;
  }

  @Override
  public Optional<FlattenedLeaf> getFlatLeaf(final Bytes hkey) {
    return Optional.ofNullable(flatLeafStorage.get(hkey));
  }

  @Override
  public Range getNearestKeys(final Bytes hkey) {
    final Iterator<Map.Entry<Bytes, FlattenedLeaf>> iterator =
        flatLeafStorage.entrySet().iterator();
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
      next = iterator.next();
    }
    return new Range(
        Map.entry(left.getKey(), left.getValue()),
        maybeMiddle.map(middle -> Map.entry(middle.getKey(), middle.getValue())),
        Map.entry(next.getKey(), next.getValue()));
  }

  @Override
  public Optional<Bytes> getTrieNode(final Bytes location, final Bytes nodeHash) {
    return Optional.ofNullable(trieNodeStorage.get(location));
  }

  @Override
  public void putTrieNode(final Bytes location, final Bytes nodeHash, final Bytes value) {
    trieNodeStorage.put(location, value);
  }

  @Override
  public InMemoryStorage updater() {
    return this;
  }

  @Override
  public void putFlatLeaf(final Bytes key, final FlattenedLeaf value) {
    flatLeafStorage.put(key, value);
  }

  @Override
  public void removeFlatLeafValue(final Bytes key) {
    flatLeafStorage.remove(key);
  }

  @Override
  public void commit() {
    // no-op
  }
}
