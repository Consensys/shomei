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
package net.consensys.shomei.trie.model;

import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import org.apache.tuweni.bytes.Bytes;

/**
 * This class represents a leaf of a trie in a flat format. Each leaf contains the index of the leaf
 * in the trie and its value (a hash or a value).
 */
public record FlattenedLeaf(Long leafIndex, Bytes leafValue) {

  /** The head of the tree (always at the beginning of the trie). */
  public static final FlattenedLeaf HEAD = new FlattenedLeaf(0L, LeafOpening.HEAD.getHval());

  /** The tail of the tree (always at the end of the trie). */
  public static final FlattenedLeaf TAIL = new FlattenedLeaf(1L, LeafOpening.TAIL.getHval());

  public void writeTo(final RLPOutput out) {
    out.startList();
    out.writeLongScalar(leafIndex);
    out.writeBytes(leafValue);
    out.endList();
  }

  public static FlattenedLeaf readFrom(final RLPInput in) {
    in.enterList();
    final long nonce = in.readLongScalar();
    final Bytes value = in.readBytes();
    in.leaveList();
    return new FlattenedLeaf(nonce, value);
  }
}
