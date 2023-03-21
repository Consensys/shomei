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

package net.consensys.shomei.trie;

import net.consensys.shomei.trie.node.LeafType;
import net.consensys.shomei.trie.util.PathGenerator;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.StoredMerkleTrie;

@SuppressWarnings({"DoNotInvokeMessageDigestDirectly", "unused"})
public class PathResolver {

  private static final Bytes NEXT_FREE_NODE_PATH = Bytes.of(0);
  private static final Bytes SUB_TRIE_ROOT_PATH = Bytes.of(1);

  private final int trieDepth;
  private final MerkleTrie<Bytes, Bytes> trie;

  private Long nextFreeNode;

  public PathResolver(final int trieDepth, final StoredMerkleTrie<Bytes, Bytes> trie) {
    this.trieDepth = trieDepth;
    this.trie = trie;
  }

  public Bytes getAndIncrementNextFreeLeafPath() {
    return getLeafPath(getAndIncrementNextFreeLeafIndex());
  }

  public Long getAndIncrementNextFreeLeafIndex() {
    final long foundFreeNode = getNextFreeLeafIndex();
    nextFreeNode = foundFreeNode + 1;
    trie.putPath(getNextFreeNodePath(), formatNodeIndex(nextFreeNode));
    return foundFreeNode;
  }

  private Long getNextFreeLeafIndex() {
    if (nextFreeNode == null) {
      nextFreeNode =
          trie.getPath(getNextFreeNodePath())
              .map(bytes -> new BigInteger(bytes.toArrayUnsafe()).longValue())
              .orElse(0L);
    }
    return nextFreeNode;
  }

  public Bytes geRootPath() {
    return SUB_TRIE_ROOT_PATH;
  }

  public Bytes getLeafPath(final Long nodeIndex) {
    return Bytes.concatenate(
        SUB_TRIE_ROOT_PATH,
        PathGenerator.bytesToLeafPath(nodeIndexToBytes(nodeIndex), LeafType.VALUE));
  }

  public Bytes getNextFreeNodePath() {
    return Bytes.concatenate(
        NEXT_FREE_NODE_PATH, Bytes.of(LeafType.NEXT_FREE_NODE.getTerminatorPath()));
  }

  private Bytes nodeIndexToBytes(final long nodeIndex) {
    return Bytes.fromHexString(
        String.format("%" + trieDepth + "s", Long.toBinaryString(nodeIndex)).replace(' ', '0'));
  }

  private Bytes formatNodeIndex(final long nodeIndex) {
    final MutableBytes32 mutableBytes32 = MutableBytes32.create();
    Bytes arrayView = Bytes.wrap(BigInteger.valueOf(nodeIndex).toByteArray());
    mutableBytes32.set(MutableBytes32.SIZE - arrayView.size(), arrayView);
    return mutableBytes32;
  }
}
