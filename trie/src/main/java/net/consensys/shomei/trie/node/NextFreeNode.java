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

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.trie.NodeFactory;

import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.util.function.Function;

public class NextFreeNode<V> extends org.hyperledger.besu.ethereum.trie.patricia.LeafNode<V> {

  public NextFreeNode(
      final Bytes path,
      final V value,
      final NodeFactory<V> nodeFactory,
      final Function<V, Bytes> valueSerializer) {
    super(path, value, nodeFactory, valueSerializer);
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
    out.writeBytes(valueSerializer.apply(value).toArrayUnsafe());
    final Bytes encoded = Bytes.wrap(out.toByteArray());
    encodedBytes = new WeakReference<>(encoded);
    return encoded;
  }

  @Override
  public boolean isReferencedByHash() {
    return false;
  }
}
