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

import java.util.Optional;
import java.util.function.Supplier;

public class BlockConfirmationMinRequirementValidator implements BlockImportValidator {

  private final Supplier<Long> currentShomeiHeadSupplier;
  private final Supplier<Optional<Long>> currentBesuEstimateHeadSupplier;

  private final Supplier<Long> minimumConfirmationRequired;

  public BlockConfirmationMinRequirementValidator(
      final Supplier<Long> currentShomeiHeadSupplier,
      final Supplier<Optional<Long>> currentBesuEstimateHeadSupplier,
      final Supplier<Long> minimumConfirmationRequired) {
    this.currentShomeiHeadSupplier = currentShomeiHeadSupplier;
    this.currentBesuEstimateHeadSupplier = currentBesuEstimateHeadSupplier;
    this.minimumConfirmationRequired = minimumConfirmationRequired;
  }

  @Override
  public boolean canImportBlock() {
    return currentBesuEstimateHeadSupplier
        .get()
        .map(head -> head - currentShomeiHeadSupplier.get() > minimumConfirmationRequired.get())
        .orElse(false);
  }
}
