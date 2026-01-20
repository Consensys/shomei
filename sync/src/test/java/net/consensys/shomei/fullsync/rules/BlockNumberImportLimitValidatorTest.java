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
public class BlockNumberImportLimitValidatorTest {

  @Test
  public void canImportWhenBlockLimitNotConfigured() {
    final FinalizedBlockLimitValidator blockNumberImportLimitValidator =
        new FinalizedBlockLimitValidator(() -> Optional.empty(), () -> 1L);
    assertThat(blockNumberImportLimitValidator.canImportBlock()).isTrue();
  }

  @Test
  public void canImportWhenBlockLimitNotReached() {
    final FinalizedBlockLimitValidator finalizedBlockNumberLimitValidator =
        new FinalizedBlockLimitValidator(() -> Optional.of(2L), () -> 1L);
    assertThat(finalizedBlockNumberLimitValidator.canImportBlock()).isTrue();
  }

  @Test
  public void cannotImportWhenBlockLimitReached() {
    final FinalizedBlockLimitValidator finalizedBlockNumberLimitValidator =
        new FinalizedBlockLimitValidator(() -> Optional.of(1L), () -> 1L);
    assertThat(finalizedBlockNumberLimitValidator.canImportBlock()).isFalse();
  }
}
