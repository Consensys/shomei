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

public class ReadTrace implements Trace {

  private Bytes location;
  private long nextFreeNode;
  public Node<Bytes> subRoot;

  public LeafOpening leaf;

  public TraceProof proof;

  public Bytes key;

  public Bytes value;

  public ReadTrace(
      final Bytes location,
      final long nextFreeNode,
      final Node<Bytes> subRoot,
      final LeafOpening leaf,
      final TraceProof proof,
      final Bytes key,
      final Bytes value) {
    this.location = location;
    this.nextFreeNode = nextFreeNode;
    this.subRoot = subRoot;
    this.leaf = leaf;
    this.proof = proof;
    this.key = key;
    this.value = value;
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

  public LeafOpening getLeaf() {
    return leaf;
  }

  public TraceProof getProof() {
    return proof;
  }

  public Bytes getKey() {
    return key;
  }

  public Bytes getValue() {
    return value;
  }

  @Override
  public int getType() {
    return READ_TRACE_CODE;
  }

  public static ReadTrace readFrom(final RLPInput in) {
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
    final LeafOpening leaf = LeafOpening.readFrom(in.readBytes());
    final TraceProof proof = TraceProof.readFrom(in);
    final Bytes key = in.readBytes();
    final Bytes value = in.readBytes();
    in.leaveList();
    return new ReadTrace(location, newNextFreeNode, subRoot, leaf, proof, key, value);
  }

  @Override
  public void writeTo(final RLPOutput out) {
    out.startList();
    out.writeBytes(location);
    out.writeLongScalar(nextFreeNode);
    out.writeBytes(subRoot.getHash());
    out.writeBytes(leaf.getEncodesBytes());
    proof.writeTo(out);
    out.writeBytes(key);
    out.writeBytes(value);
    out.endList();
  }
}
