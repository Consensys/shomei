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

package net.consensys.shomei.rpc.model;

import net.consensys.shomei.observer.TrieLogObserver.TrieLogIdentifier;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.hyperledger.besu.datatypes.Hash;

public class TrieLogElement {
  private Long blockNumber;
  private String blockHash;
  private boolean isInitialSync;
  private String trieLog;

  public TrieLogElement() {}

  @JsonCreator
  public TrieLogElement(
      @JsonProperty("blockNumber") final Long blockNumber,
      @JsonProperty("blockHash") final String blockHash,
      @JsonProperty("trieLog") final String trieLog) {
    this.blockNumber = blockNumber;
    this.blockHash = blockHash;
    this.isInitialSync = false; // Default to false since syncing field is not in response
    this.trieLog = trieLog;
  }

  public Long blockNumber() {
    return blockNumber;
  }

  public String blockHash() {
    return blockHash;
  }

  public boolean isInitialSync() {
    return isInitialSync;
  }

  public String trieLog() {
    return trieLog;
  }

  public TrieLogIdentifier getTrieLogIdentifier() {
    return new TrieLogIdentifier(blockNumber, Hash.fromHexString(blockHash), isInitialSync);
  }

  @Override
  public String toString() {
    return "SendRawTrieLogParameter{"
        + "blockNumber="
        + blockNumber
        + ", blockHash="
        + blockHash
        + ", isInitialSync="
        + isInitialSync
        + ", trieLog='"
        + trieLog
        + '\''
        + '}';
  }
}
