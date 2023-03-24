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

package net.consensys.shomei;

import net.consensys.shomei.worldview.ZKEvmWorldState;

import java.util.Optional;

import org.hyperledger.besu.datatypes.Hash;

public class ZkWorldStateProvider {

  private final ZKEvmWorldState persistedState;

  public ZkWorldStateProvider(final ZKEvmWorldState persistedState) {
    this.persistedState = persistedState;
  }

  public Optional<ZKEvmWorldState> loadState(final Hash blockHash) {
    if (persistedState.getBlockHash().equals(blockHash)) {
      return Optional.of(persistedState);
    } else {
      // rolling
      return Optional.empty();
    }
  }
}
