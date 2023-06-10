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

import java.util.List;

import org.hyperledger.besu.datatypes.Hash;

/** An observer that is notified when trie logs are received. */
public interface TrieLogObserver {

  /**
   * Called when trie logs are received.
   *
   * @param trieLogIds the trie log identifiers
   */
  void onTrieLogsReceived(final List<TrieLogIdentifier> trieLogIds);

  /** A trie log identifier. */
  record TrieLogIdentifier(Long blockNumber, Hash blockHash, boolean isInitialSync)
      implements Comparable<TrieLogIdentifier> {

    public TrieLogIdentifier(final Long blockNumber, final Hash blockHash) {
      this(blockNumber, blockHash, false);
    }

    @Override
    public int compareTo(TrieLogIdentifier other) {
      return this.blockNumber.compareTo(other.blockNumber);
    }
  }
}
