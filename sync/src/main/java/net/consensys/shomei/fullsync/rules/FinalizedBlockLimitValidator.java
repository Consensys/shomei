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

public class FinalizedBlockLimitValidator implements BlockImportValidator {

  private final Supplier<Optional<Long>> currentShomeiBlockNumberLimitSupplier;

  private final Supplier<Long> currentShomeiHeadSupplier;

  public FinalizedBlockLimitValidator(
      final Supplier<Optional<Long>> currentShomeiBlockNumberLimitSupplier,
      final Supplier<Long> currentShomeiHeadSupplier) {
    this.currentShomeiBlockNumberLimitSupplier = currentShomeiBlockNumberLimitSupplier;
    this.currentShomeiHeadSupplier = currentShomeiHeadSupplier;
  }

  @Override
  public boolean canImportBlock() {
    return currentShomeiBlockNumberLimitSupplier
        .get()
        .map(limit -> limit > currentShomeiHeadSupplier.get())
        .orElse(true);
  }
}
