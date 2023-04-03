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

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;

public class LeafIndexManager implements LeafIndexLoader, LeafIndexUpdater {

  TreeMap<Bytes, Long> flatDB;

  public LeafIndexManager(final TreeMap<Bytes, Long> flatDB) {
    this.flatDB = flatDB;
  }

  public LeafIndexManager() {
    this(new TreeMap<>(Comparator.naturalOrder()));
  }

  @Override
  public Optional<Long> getKeyIndex(final Hash key) {
    return Optional.ofNullable(flatDB.get(wrapKey(key)));
  }

  @Override
  public void putKeyIndex(final Hash key, final Long index) {
    flatDB.put(wrapKey(key), index);
  }

  @Override
  public void removeKeyIndex(final Hash key) {
    flatDB.remove(wrapKey(key));
  }

  @Override
  public Range getNearestKeys(final Hash key) {

    final Bytes wrappedKey = wrapKey(key);
    final Iterator<Map.Entry<Bytes, Long>> iterator = flatDB.entrySet().iterator();
    Map.Entry<Bytes, Long> next = Map.entry(Bytes32.ZERO, 0L);
    Map.Entry<Bytes, Long> left = next;
    Optional<Map.Entry<Bytes, Long>> maybeMiddle = Optional.empty();
    int compKeyResult;
    while (iterator.hasNext() && (compKeyResult = next.getKey().compareTo(wrappedKey)) <= 0) {
      if (compKeyResult == 0) {
        maybeMiddle = Optional.of(next);
      } else {
        left = next;
      }
      next = iterator.next();
    }
    return new Range(
        Map.entry(unwrapKey(left.getKey()), left.getValue()),
        maybeMiddle.map(middle -> Map.entry(unwrapKey(middle.getKey()), middle.getValue())),
        Map.entry(unwrapKey(next.getKey()), next.getValue()));
  }

  public Bytes wrapKey(final Hash key) {
    return key;
  }

  public Hash unwrapKey(final Bytes key) {
    return Hash.wrap(Bytes32.wrap(key));
  }
}
