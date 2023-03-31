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

package net.consensys.shomei.trie.storage;

import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;

public interface LeafIndexLoader {

  Optional<Long> getKeyIndex(Hash key);

  Range getNearestKeys(Hash key);

  class Range {
    private final Map.Entry<Hash, UInt256> leftNode;

    private final Optional<Map.Entry<Hash, UInt256>> middleNode;
    private final Map.Entry<Hash, UInt256> rightNode;

    public Range(
        final Map.Entry<Hash, UInt256> leftNode,
        final Optional<Map.Entry<Hash, UInt256>> middleNode,
        final Map.Entry<Hash, UInt256> rightNode) {
      this.leftNode = leftNode;
      this.middleNode = middleNode;
      this.rightNode = rightNode;
    }

    public Range(
        final Map.Entry<Hash, UInt256> leftNode, final Map.Entry<Hash, UInt256> rightNode) {
      this(leftNode, Optional.empty(), rightNode);
    }

    public Hash getLeftNodeKey() {
      return leftNode.getKey();
    }

    public Optional<Hash> getMiddleNodeKey() {
      return middleNode.map(Map.Entry::getKey);
    }

    public Hash getRightNodeKey() {
      return rightNode.getKey();
    }

    public UInt256 getLeftNodeIndex() {
      return leftNode.getValue();
    }

    public Optional<UInt256> getMiddleNodeIndex() {
      return middleNode.map(Map.Entry::getValue);
    }

    public UInt256 getRightNodeIndex() {
      return rightNode.getValue();
    }
  }
}
