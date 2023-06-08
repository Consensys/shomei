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

package net.consensys.shomei.trie.proof;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

public class EmptyTrace implements Trace {
  @Override
  public int getType() {
    return -1;
  }

  @Override
  public Bytes getLocation() {
    return Bytes.EMPTY;
  }

  @Override
  public void setLocation(final Bytes location) {
    // no op
  }

  @Override
  public void writeTo(final RLPOutput out) {
    throw new RuntimeException("cannot serialize empty trace");
  }
}
