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
package net.consensys.shomei.exception;

/** Exception thrown when a trie log is missing. */
public class MissingTrieLogException extends Exception {

  /** The block number of the missing trie log. */
  private final long blockNumber;

  public MissingTrieLogException(final long blockNumber) {
    super("Unable to find trie log for block number %d".formatted(blockNumber));
    this.blockNumber = blockNumber;
  }

  /**
   * Returns the block number of the missing trie log.
   *
   * @return the block number of the missing trie log
   */
  public long getBlockNumber() {
    return blockNumber;
  }
}
