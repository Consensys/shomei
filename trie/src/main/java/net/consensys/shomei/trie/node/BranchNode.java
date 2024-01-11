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

package net.consensys.shomei.trie.node;

import net.consensys.zkevm.HashProvider;

import java.io.ByteArrayOutputStream;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeFactory;

public class BranchNode<V> extends org.hyperledger.besu.ethereum.trie.patricia.BranchNode<V>
    implements Node<V> {

  private SoftReference<Bytes32> hash;

  public BranchNode(
      final Bytes location,
      final ArrayList<Node<V>> children,
      final Optional<V> value,
      final NodeFactory<V> nodeFactory,
      final Function<V, Bytes> valueSerializer) {
    super(location, children, value, nodeFactory, valueSerializer);
  }

  public BranchNode(
      final List<Node<V>> children,
      final Optional<V> value,
      final NodeFactory<V> nodeFactory,
      final Function<V, Bytes> valueSerializer) {
    super(children, value, nodeFactory, valueSerializer);
  }

  @Override
  public int maxChild() {
    return 2;
  }

  @Override
  public Bytes getEncodedBytes() {
    if (encodedBytes != null) {
      final Bytes encoded = encodedBytes.get();
      if (encoded != null) {
        return encoded;
      }
    }
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    getChildren()
        .forEach(
            vNode -> {
              if (vNode instanceof NextFreeNode<V>) {
                out.writeBytes(vNode.getEncodedBytes().toArrayUnsafe());
              } else {
                out.writeBytes(vNode.getHash().toArrayUnsafe());
              }
            });
    final Bytes encoded = Bytes.wrap(out.toByteArray());
    encodedBytes = new WeakReference<>(encoded);
    return encoded;
  }

  @Override
  public Bytes32 getHash() {
    if (hash != null) {
      final Bytes32 hashed = hash.get();
      if (hashed != null) {
        return hashed;
      }
    }
    final Bytes32 hashed = HashProvider.trieHash(getEncodedBytes());
    hash = new SoftReference<>(hashed);
    return hashed;
  }

  @Override
  public boolean isReferencedByHash() {
    return true;
  }
}
