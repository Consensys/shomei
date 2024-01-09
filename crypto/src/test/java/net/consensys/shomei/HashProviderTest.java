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

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.zkevm.HashProvider;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.junit.Test;

public class HashProviderTest {

  @Test
  public void testMimc() {
    MutableBytes input = MutableBytes.of(new byte[Bytes32.SIZE * 16]);
    for (int i = 0; i < 16; i++) {
      input.set(Bytes32.SIZE * (i + 1) - 1, (byte) i);
    }
    assertThat(HashProvider.trieHash(input))
        .isEqualTo(
            Bytes32.fromHexString(
                "0x12900ae41a010e54e3b1ed95efa39071d357ff642aeedd30a2c4e13250409662"));
  }
}
