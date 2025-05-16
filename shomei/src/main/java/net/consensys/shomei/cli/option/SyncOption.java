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

package net.consensys.shomei.cli.option;

import picocli.CommandLine;

public class SyncOption {
  /**
   * Create Sync option.
   *
   * @return the RPC option
   */
  public static SyncOption create() {
    return new SyncOption();
  }

  @CommandLine.Spec CommandLine.Model.CommandSpec spec;

  static final long DEFAULT_FIRST_GENERATED_BLOCK_NUMBER = 0;

  static final long DEFAULT_MIN_CONFIRMATION = 0;

  @CommandLine.Option(
      names = {"--trace-start-block-number"},
      paramLabel = "<LONG>",
      description =
          "Lowest block number for the trace generation process. Default: ${DEFAULT-VALUE}",
      arity = "1")
  private long traceStartBlockNumber = DEFAULT_FIRST_GENERATED_BLOCK_NUMBER;

  @CommandLine.Option(
      names = {"--enable-trace-generation"},
      paramLabel = "<BOOL>",
      description = "Enable trace generation. Default: ${DEFAULT-VALUE}",
      arity = "1")
  private boolean enableTraceGeneration = true;

  @CommandLine.Option(
      names = {"--min-confirmations-before-importing"},
      paramLabel = "<LONG>",
      description = "Number of confirmations before importing block. Default: ${DEFAULT-VALUE}",
      arity = "1")
  private long minConfirmationsBeforeImporting = DEFAULT_MIN_CONFIRMATION;

  @CommandLine.Option(
      names = {"--enable-finalized-block-limit"},
      paramLabel = "<BOOL>",
      description = "Enable finalized block limit. Default: ${DEFAULT-VALUE}",
      arity = "1")
  private boolean enableFinalizedBlockLimit = false;

  @CommandLine.Option(
      names = {"--use-finalized-block-number"},
      paramLabel = "<LONG>",
      description =
          "Number of the finalized block up to which Shomei will synchronize. After this point, it will pause and wait for further finalized blocks.",
      arity = "1")
  private Long finalizedBlockNumberLimit = null;

  @CommandLine.Option(
      names = {"--use-finalized-block-hash"},
      paramLabel = "<LONG>",
      description =
          "Hash of the finalized block up to which Shomei will synchronize. After this point, it will pause and wait for further finalized blocks.",
      arity = "1")
  private String finalizedBlockHashLimit = null;

  public long getTraceStartBlockNumber() {
    return traceStartBlockNumber;
  }

  public long getMinConfirmationsBeforeImporting() {
    return minConfirmationsBeforeImporting;
  }

  public boolean isEnableFinalizedBlockLimit() {
    return enableFinalizedBlockLimit;
  }

  public Long getFinalizedBlockNumberLimit() {
    return finalizedBlockNumberLimit;
  }

  public String getFinalizedBlockHashLimit() {
    return finalizedBlockHashLimit;
  }

  public boolean isTraceGenerationEnabled() {
    return enableTraceGeneration;
  }

  public static class Builder {
    private final SyncOption syncOption = new SyncOption();

    public Builder setTraceStartBlockNumber(long traceStartBlockNumber) {
      this.syncOption.traceStartBlockNumber = traceStartBlockNumber;
      return this;
    }

    public Builder setEnableTraceGeneration(boolean enableTraceGeneration) {
      this.syncOption.enableTraceGeneration = enableTraceGeneration;
      return this;
    }

    public Builder setMinConfirmationsBeforeImporting(long minConfirmationsBeforeImporting) {
      this.syncOption.minConfirmationsBeforeImporting = minConfirmationsBeforeImporting;
      return this;
    }

    public Builder setEnableFinalizedBlockLimit(boolean enableFinalizedBlockLimit) {
      this.syncOption.enableFinalizedBlockLimit = enableFinalizedBlockLimit;
      return this;
    }

    public Builder setFinalizedBlockNumberLimit(Long finalizedBlockNumberLimit) {
      this.syncOption.finalizedBlockNumberLimit = finalizedBlockNumberLimit;
      return this;
    }

    public Builder setFinalizedBlockHashLimit(String finalizedBlockHashLimit) {
      this.syncOption.finalizedBlockHashLimit = finalizedBlockHashLimit;
      return this;
    }

    public SyncOption build() {
      return syncOption;
    }
  }
}
