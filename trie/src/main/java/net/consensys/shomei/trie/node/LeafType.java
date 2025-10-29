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
package net.consensys.shomei.trie.node;

import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes;

public enum LeafType {
  VALUE((byte) 0x16),
  NEXT_FREE_NODE((byte) 0x17),

  EMPTY((byte) 0x18);

  final byte terminatorPath;

  LeafType(final byte terminatorPath) {
    this.terminatorPath = terminatorPath;
  }

  public byte getTerminatorPath() {
    return terminatorPath;
  }

  public static LeafType fromBytes(final Bytes path) {
    return Arrays.stream(values())
        .filter(leafType -> Bytes.of(leafType.getTerminatorPath()).equals(path))
        .findFirst()
        .orElse(EMPTY);
  }

  public static boolean isLeafTerminator(final byte b) {
    return Arrays.stream(values()).anyMatch(leafType -> leafType.terminatorPath == b);
  }
}
