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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.StoredNode;

import net.consensys.shomei.trie.model.LeafOpening;
import org.apache.tuweni.bytes.Bytes;

public class ReadZeroTrace implements Trace {

  private Bytes location;
  private long nextFreeNode;
  public Node<Bytes> subRoot;

  public LeafOpening leftLeaf;

  public LeafOpening rightLeaf;

  public TraceProof leftProof; // HKEY -
  public TraceProof rightProof; // HKEY +

  public Bytes key;

  public ReadZeroTrace(
      final Bytes location,
      final long nextFreeNode,
      final Node<Bytes> subRoot,
      final LeafOpening leftLeaf,
      final LeafOpening rightLeaf,
      final TraceProof leftProof,
      final TraceProof rightProof,
      final Bytes key) {
    this.location = location;
    this.nextFreeNode = nextFreeNode;
    this.subRoot = subRoot;
    this.leftLeaf = leftLeaf;
    this.rightLeaf = rightLeaf;
    this.leftProof = leftProof;
    this.rightProof = rightProof;
    this.key = key;
  }

  @Override
  public Bytes getLocation() {
    return location;
  }

  @Override
  public void setLocation(final Bytes location) {
    this.location = location;
  }

  public long getNextFreeNode() {
    return nextFreeNode;
  }

  public Node<Bytes> getSubRoot() {
    return subRoot;
  }

  public LeafOpening getLeftLeaf() {
    return leftLeaf;
  }

  public LeafOpening getRightLeaf() {
    return rightLeaf;
  }

  public TraceProof getLeftProof() {
    return leftProof;
  }

  public TraceProof getRightProof() {
    return rightProof;
  }

  public Bytes getKey() {
    return key;
  }

  @Override
  public int getType() {
    return READ_ZERO_TRACE_CODE;
  }

  public static ReadZeroTrace readFrom(final RLPInput in) {
    in.enterList();
    final Bytes location;
    if (in.nextIsNull()) {
      location = Bytes.EMPTY;
      in.skipNext();
    } else {
      location = in.readBytes();
    }
    final long newNextFreeNode = in.readLongScalar();
    final Node<Bytes> subRoot = new StoredNode<>(null, null, Hash.wrap(in.readBytes32()));
    final LeafOpening leftLeaf = LeafOpening.readFrom(in.readBytes());
    final LeafOpening rightLeaf = LeafOpening.readFrom(in.readBytes());
    final TraceProof leftProof = TraceProof.readFrom(in);
    final TraceProof rightProof = TraceProof.readFrom(in);
    final Bytes key = in.readBytes();
    in.leaveList();
    return new ReadZeroTrace(
        location, newNextFreeNode, subRoot, leftLeaf, rightLeaf, leftProof, rightProof, key);
  }

  @Override
  public void writeTo(final RLPOutput out) {
    out.startList();
    out.writeBytes(location);
    out.writeLongScalar(nextFreeNode);
    out.writeBytes(subRoot.getHash());
    out.writeBytes(leftLeaf.getEncodesBytes());
    out.writeBytes(rightLeaf.getEncodesBytes());
    leftProof.writeTo(out);
    rightProof.writeTo(out);
    out.writeBytes(key);
    out.endList();
  }
}
