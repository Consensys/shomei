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

public class UpdateTrace implements Trace {
  private Bytes location;
  private final long newNextFreeNode;
  public Node<Bytes> oldSubRoot;
  public Node<Bytes> newSubRoot;

  public TraceProof proof;

  public Bytes key;
  public Bytes oldValue;
  public Bytes newValue;

  // Value of the leaf opening before being modified
  public LeafOpening priorUpdatedLeaf;

  public UpdateTrace(
      final Bytes location,
      final long newNextFreeNode,
      final Node<Bytes> oldSubRoot,
      final Node<Bytes> newSubRoot,
      final TraceProof proof,
      final Bytes key,
      final Bytes oldValue,
      final Bytes newValue,
      final LeafOpening priorUpdatedLeaf) {
    this.location = location;
    this.newNextFreeNode = newNextFreeNode;
    this.oldSubRoot = oldSubRoot;
    this.newSubRoot = newSubRoot;
    this.proof = proof;
    this.key = key;
    this.oldValue = oldValue;
    this.newValue = newValue;
    this.priorUpdatedLeaf = priorUpdatedLeaf;
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

  public TraceProof getProof() {
    return proof;
  }

  public Bytes getKey() {
    return key;
  }

  public Bytes getOldValue() {
    return oldValue;
  }

  public Bytes getNewValue() {
    return newValue;
  }

  public LeafOpening getPriorUpdatedLeaf() {
    return priorUpdatedLeaf;
  }

  @Override
  public int getType() {
    return UPDATE_TRACE_CODE;
  }

  public static UpdateTrace readFrom(final RLPInput in) {
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
    final TraceProof proof = TraceProof.readFrom(in);
    final Bytes key = in.readBytes();
    final Bytes oldValue = in.readBytes();
    final Bytes newValue = in.readBytes();
    final LeafOpening priorUpdatedLeaf = LeafOpening.readFrom(in.readBytes());
    in.leaveList();
    return new UpdateTrace(
        location,
        newNextFreeNode,
        oldSubRoot,
        newSubRoot,
        proof,
        key,
        oldValue,
        newValue,
        priorUpdatedLeaf);
  }

  @Override
  public void writeTo(final RLPOutput out) {
    out.startList();
    out.writeBytes(location);
    out.writeLongScalar(newNextFreeNode);
    out.writeBytes(oldSubRoot.getHash());
    out.writeBytes(newSubRoot.getHash());
    proof.writeTo(out);
    out.writeBytes(key);
    out.writeBytes(oldValue);
    out.writeBytes(newValue);
    out.writeBytes(priorUpdatedLeaf.getEncodesBytes());
    out.endList();
  }
}
