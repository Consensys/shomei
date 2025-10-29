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
package net.consensys.shomei.trie.trace;

import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.StoredNode;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;

public class TraceProof {

  public long leafIndex;
  public List<Node<Bytes>> siblings;

  public TraceProof(final long leafIndex, final List<Node<Bytes>> siblings) {
    this.leafIndex = leafIndex;
    this.siblings = siblings;
  }

  public long getLeafIndex() {
    return leafIndex;
  }

  public List<Node<Bytes>> getSiblings() {
    return siblings;
  }

  public static TraceProof readFrom(final RLPInput in) {
    in.enterList();
    final long leafIndex = in.readLongScalar();
    final List<Node<Bytes>> siblings =
        in.readList(rlpInput -> new StoredNode<>(null, null, rlpInput.readBytes32()));
    in.leaveList();
    return new TraceProof(leafIndex, siblings);
  }

  public void writeTo(final RLPOutput out) {
    out.startList();
    out.writeLongScalar(leafIndex);
    out.writeList(siblings, (node, rlpOutput) -> rlpOutput.writeBytes(node.getHash()));
    out.endList();
  }
}
