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

import org.hyperledger.besu.datatypes.Hash;

public interface LeafIndexLoader {

  Optional<Long> getKeyIndex(Hash key);

  Range getNearestKeys(Hash key);

  class Range {
    private final Map.Entry<Hash, Long> leftNode;

    private final Optional<Map.Entry<Hash, Long>> centerNode;
    private final Map.Entry<Hash, Long> rightNode;

    public Range(
        final Map.Entry<Hash, Long> leftNode,
        final Optional<Map.Entry<Hash, Long>> centerNode,
        final Map.Entry<Hash, Long> rightNode) {
      this.leftNode = leftNode;
      this.centerNode = centerNode;
      this.rightNode = rightNode;
    }

    public Range(final Map.Entry<Hash, Long> leftNode, final Map.Entry<Hash, Long> rightNode) {
      this(leftNode, Optional.empty(), rightNode);
    }

    public Hash getLeftNodeKey() {
      return leftNode.getKey();
    }

    public Optional<Hash> getCenterNodeKey() {
      return centerNode.map(Map.Entry::getKey);
    }

    public Hash getRightNodeKey() {
      return rightNode.getKey();
    }

    public Long getLeftNodeIndex() {
      return leftNode.getValue();
    }

    public Optional<Long> getCenterNodeIndex() {
      return centerNode.map(Map.Entry::getValue);
    }

    public Long getRightNodeIndex() {
      return rightNode.getValue();
    }
  }
}
