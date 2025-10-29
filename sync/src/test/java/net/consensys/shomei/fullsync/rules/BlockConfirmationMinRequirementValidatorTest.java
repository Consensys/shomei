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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BlockConfirmationMinRequirementValidatorTest {
  @Test
  public void canImportWhenBlockMinConfirmationNotConfigured() {
    final BlockConfirmationMinRequirementValidator blockConfirmationMinRequirementValidator =
        new BlockConfirmationMinRequirementValidator(() -> 1L, () -> Optional.of(2L), () -> 0L);
    assertThat(blockConfirmationMinRequirementValidator.canImportBlock()).isTrue();
  }

  @Test
  public void canImportWhenBlockMinConfirmationReached() {
    final BlockConfirmationMinRequirementValidator blockConfirmationMinRequirementValidator =
        new BlockConfirmationMinRequirementValidator(() -> 1L, () -> Optional.of(3L), () -> 1L);
    assertThat(blockConfirmationMinRequirementValidator.canImportBlock()).isTrue();
  }

  @Test
  public void cannotImportWhenBlockMinConfirmationNotReached() {
    final BlockConfirmationMinRequirementValidator blockConfirmationMinRequirementValidator =
        new BlockConfirmationMinRequirementValidator(() -> 1L, () -> Optional.of(2L), () -> 1L);
    assertThat(blockConfirmationMinRequirementValidator.canImportBlock()).isFalse();
  }
}
