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
package net.consensys.shomei.fullsync.rules;

import org.hyperledger.besu.datatypes.Hash;

import java.util.Optional;

public class FullSyncRules {

  private boolean isTraceGenerationEnabled;
  private long traceStartBlockNumber;
  private long minConfirmationsBeforeImporting;
  private final boolean enableFinalizedBlockLimit;
  private Optional<Long> finalizedBlockNumberLimit;
  private Optional<Hash> finalizedBlockHashLimit;

  public FullSyncRules(
      final boolean isTraceGenerationEnabled,
      final long traceStartBlockNumber,
      final long minConfirmationsBeforeImporting,
      final boolean enableFinalizedBlockLimit,
      final Optional<Long> finalizedBlockNumberLimit,
      final Optional<Hash> finalizedBlockHashLimit) {
    this.isTraceGenerationEnabled = isTraceGenerationEnabled;
    this.traceStartBlockNumber = traceStartBlockNumber;
    this.minConfirmationsBeforeImporting = minConfirmationsBeforeImporting;
    this.enableFinalizedBlockLimit = enableFinalizedBlockLimit;
    this.finalizedBlockNumberLimit = finalizedBlockNumberLimit;
    this.finalizedBlockHashLimit = finalizedBlockHashLimit;
  }

  public FullSyncRules(
      final boolean isTraceGenerationEnabled,
      final long traceStartBlockNumber,
      final long minConfirmationsBeforeImporting) {
    this.isTraceGenerationEnabled = isTraceGenerationEnabled;
    this.traceStartBlockNumber = traceStartBlockNumber;
    this.minConfirmationsBeforeImporting = minConfirmationsBeforeImporting;
    this.enableFinalizedBlockLimit = false;
    this.finalizedBlockNumberLimit = Optional.empty();
    this.finalizedBlockHashLimit = Optional.empty();
  }

  public boolean isTraceGenerationEnabled() {
    return isTraceGenerationEnabled;
  }

  public long getTraceStartBlockNumber() {
    return traceStartBlockNumber;
  }

  public long getMinConfirmationsBeforeImporting() {
    return minConfirmationsBeforeImporting;
  }

  public boolean isEnableFinalizedBlockLimit() {
    return enableFinalizedBlockLimit;
  }

  public Optional<Long> getFinalizedBlockNumberLimit() {
    return finalizedBlockNumberLimit;
  }

  public Optional<Hash> getFinalizedBlockHashLimit() {
    return finalizedBlockHashLimit;
  }

  public void setTraceStartBlockNumber(final long traceStartBlockNumber) {
    this.traceStartBlockNumber = traceStartBlockNumber;
  }

  public void setMinConfirmationsBeforeImporting(final long minConfirmationsBeforeImporting) {
    this.minConfirmationsBeforeImporting = minConfirmationsBeforeImporting;
  }

  public void setFinalizedBlockNumberLimit(final Optional<Long> finalizedBlockNumberLimit) {
    this.finalizedBlockNumberLimit = finalizedBlockNumberLimit;
  }

  public void setFinalizedBlockHashLimit(final Optional<Hash> finalizedBlockHashLimit) {
    this.finalizedBlockHashLimit = finalizedBlockHashLimit;
  }
}
