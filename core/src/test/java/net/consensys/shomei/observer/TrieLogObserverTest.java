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

package net.consensys.shomei.observer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.consensys.shomei.observer.TrieLogObserver.TrieLogIdentifier;

import java.util.Arrays;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.junit.jupiter.api.Test;

public class TrieLogObserverTest {

  @Test
  public void testOnTrieLogsReceived() {
    TrieLogObserver observer =
        trieLogIds -> {
          assertEquals(2, trieLogIds.size());
          assertEquals((Long) 123L, trieLogIds.get(0).blockNumber());
          assertEquals(Hash.hash(Bytes.of(0)), trieLogIds.get(0).blockHash());
          assertFalse(trieLogIds.get(0).isInitialSync());
          assertEquals((Long) 456L, trieLogIds.get(1).blockNumber());
          assertEquals(Hash.hash(Bytes.of(1)), trieLogIds.get(1).blockHash());
          assertTrue(trieLogIds.get(1).isInitialSync());
        };

    List<TrieLogIdentifier> logIdentifiers =
        Arrays.asList(
            new TrieLogIdentifier(123L, Hash.hash(Bytes.of(0)), false),
            new TrieLogIdentifier(456L, Hash.hash(Bytes.of(1)), true));

    observer.onNewBesuHeadReceived(logIdentifiers);
  }

  @Test
  public void testTrieLogIdentifierCompareTo() {
    TrieLogIdentifier logIdentifier1 = new TrieLogIdentifier(123L, Hash.ZERO, false);
    TrieLogIdentifier logIdentifier2 = new TrieLogIdentifier(456L, Hash.ZERO, false);
    TrieLogIdentifier logIdentifier3 = new TrieLogIdentifier(123L, Hash.ZERO, false);

    assertTrue(logIdentifier1.compareTo(logIdentifier2) < 0);
    assertTrue(logIdentifier2.compareTo(logIdentifier1) > 0);
    assertEquals(0, logIdentifier1.compareTo(logIdentifier3));
  }
}
