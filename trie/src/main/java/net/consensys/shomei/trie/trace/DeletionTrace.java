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

public class DeletionTrace implements Trace {

  private Bytes location;

  private long newNextFreeNode;

  public Node<Bytes> oldSubRoot;
  public Node<Bytes> newSubRoot;

  private Bytes deletedValue;

  // `New` correspond to the inserted leaf
  public TraceProof leftProof; // HKEY -
  public TraceProof deletedProof; // hash(k)
  public TraceProof rightProof; // HKEY +
  public Bytes key;

  // Value of the leaf opening before being modified
  public LeafOpening priorLeftLeaf;
  public LeafOpening priorDeletedLeaf;
  public LeafOpening priorRightLeaf;

  public DeletionTrace(
      final Bytes location,
      final long newNextFreeNode,
      final Node<Bytes> oldSubRoot,
      final Node<Bytes> newSubRoot,
      final TraceProof leftProof,
      final TraceProof deletedProof,
      final TraceProof rightProof,
      final Bytes key,
      final Bytes deletedValue,
      final LeafOpening priorLeftLeaf,
      final LeafOpening priorDeletedLeaf,
      final LeafOpening priorRightLeaf) {
    this.location = location;
    this.newNextFreeNode = newNextFreeNode;
    this.oldSubRoot = oldSubRoot;
    this.newSubRoot = newSubRoot;
    this.leftProof = leftProof;
    this.deletedProof = deletedProof;
    this.rightProof = rightProof;
    this.key = key;
    this.deletedValue = deletedValue;
    this.priorLeftLeaf = priorLeftLeaf;
    this.priorDeletedLeaf = priorDeletedLeaf;
    this.priorRightLeaf = priorRightLeaf;
  }

  public DeletionTrace(final Node<Bytes> oldSubRoot) {
    this.oldSubRoot = oldSubRoot;
  }

  @Override
  public int getType() {
    return DELETION_TRACE_CODE;
  }

  @Override
  public Bytes getLocation() {
    return location;
  }

  @Override
  public void setLocation(final Bytes location) {
    this.location = location;
  }

  public long getNewNextFreeNode() {
    return newNextFreeNode;
  }

  public Node<Bytes> getOldSubRoot() {
    return oldSubRoot;
  }

  public Node<Bytes> getNewSubRoot() {
    return newSubRoot;
  }

  public TraceProof getLeftProof() {
    return leftProof;
  }

  public Bytes getDeletedValue() {
    return deletedValue;
  }

  public TraceProof getDeletedProof() {
    return deletedProof;
  }

  public TraceProof getRightProof() {
    return rightProof;
  }

  public Bytes getKey() {
    return key;
  }

  public LeafOpening getPriorLeftLeaf() {
    return priorLeftLeaf;
  }

  public LeafOpening getPriorDeletedLeaf() {
    return priorDeletedLeaf;
  }

  public LeafOpening getPriorRightLeaf() {
    return priorRightLeaf;
  }

  public static DeletionTrace readFrom(final RLPInput in) {
    in.enterList();

    final Bytes location;
    if (in.nextIsNull()) {
      location = Bytes.EMPTY;
      in.skipNext();
    } else {
      location = in.readBytes();
    }
    final long newNextFreeNode = in.readLongScalar();
    final Node<Bytes> oldSubRoot = new StoredNode<>(null, null, Hash.wrap(in.readBytes32()));
    final Node<Bytes> newSubRoot = new StoredNode<>(null, null, Hash.wrap(in.readBytes32()));
    final TraceProof leftProof = TraceProof.readFrom(in);
    final TraceProof deletedProof = TraceProof.readFrom(in);
    final TraceProof rightProof = TraceProof.readFrom(in);
    final Bytes key = in.readBytes();
    final Bytes deletedValue = in.readBytes();
    final LeafOpening priorLeftLeaf = LeafOpening.readFrom(in.readBytes());
    final LeafOpening priorDeletedLeaf = LeafOpening.readFrom(in.readBytes());
    final LeafOpening priorRightLeaf = LeafOpening.readFrom(in.readBytes());
    in.leaveList();
    return new DeletionTrace(
        location,
        newNextFreeNode,
        oldSubRoot,
        newSubRoot,
        leftProof,
        deletedProof,
        rightProof,
        key,
        deletedValue,
        priorLeftLeaf,
        priorDeletedLeaf,
        priorRightLeaf);
  }

  @Override
  public void writeTo(final RLPOutput out) {
    out.startList();
    out.writeBytes(location);
    out.writeLongScalar(newNextFreeNode);
    out.writeBytes(oldSubRoot.getHash());
    out.writeBytes(newSubRoot.getHash());
    leftProof.writeTo(out);
    deletedProof.writeTo(out);
    rightProof.writeTo(out);
    out.writeBytes(key);
    out.writeBytes(deletedValue);
    out.writeBytes(priorLeftLeaf.getEncodesBytes());
    out.writeBytes(priorDeletedLeaf.getEncodesBytes());
    out.writeBytes(priorRightLeaf.getEncodesBytes());
    out.endList();
  }
}
