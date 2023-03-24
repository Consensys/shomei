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

package net.consensys.shomei.worldview;

import net.consensys.shomei.ZkAccount;
import net.consensys.shomei.ZkValue;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.storage.InMemoryLeafIndexManager;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZKEvmWorldState {

  private static final Logger LOG = LoggerFactory.getLogger(ZKEvmWorldState.class);

  private final ZkEvmWorldStateUpdateAccumulator accumulator;
  private Hash rootHash;
  private Hash blockHash;

  // TODO change with rocksdb
  private final InMemoryLeafIndexManager inMemoryLeafIndexManager = new InMemoryLeafIndexManager();
  private final Map<Bytes, Bytes> storage = new ConcurrentHashMap<>();

  public ZKEvmWorldState(final Hash rootHash, final Hash blockHash) {
    this.rootHash = rootHash; // read from database
    this.blockHash = blockHash; // read from database
    this.accumulator = new ZkEvmWorldStateUpdateAccumulator();
  }

  public void commit(final BlockHeader blockHeader) {
    final Optional<BlockHeader> maybeBlockHeader = Optional.ofNullable(blockHeader);
    LOG.atDebug().setMessage("Commit world state for block {}").addArgument(maybeBlockHeader).log();
    rootHash = calculateRootHash();
    blockHash = maybeBlockHeader.map(BlockHeader::getHash).orElse(null);
    // persist
  }

  private Hash calculateRootHash() {
    final ZKTrie zkAccountTrie = loadAccountTrie();
    accumulator
        .getAccountsToUpdate()
        .forEach(
            (address, accountValue) -> {
              final Map<Hash, ZkValue<UInt256>> storageToUpdate =
                  accumulator.getStorageToUpdate().get(address);
              if (storageToUpdate != null) {
                final ZKTrie zkStorageTrie = loadStorageTrie(accountValue);
                storageToUpdate.forEach(
                    (slotKey, storageValue) -> {
                      if (storageValue.getUpdated() == null) {
                        zkStorageTrie.remove(slotKey);
                      } else {
                        zkStorageTrie.put(slotKey, storageValue.getUpdated());
                      }
                    });
              }
              if (accountValue.getUpdated() == null) {
                zkAccountTrie.remove(accountValue.getPrior().getHkey());
              } else {
                zkAccountTrie.put(
                    accountValue.getUpdated().getHkey(),
                    accountValue.getUpdated().serializeAccount());
              }
            });
    return Hash.wrap(zkAccountTrie.getTopRootHash());
  }

  public Hash getRootHash() {
    return rootHash;
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  public ZkEvmWorldStateUpdateAccumulator getAccumulator() {
    return accumulator;
  }

  private ZKTrie loadAccountTrie() {
    if (rootHash.equals(ZkAccount.EMPTY_TRIE_ROOT)) {
      return ZKTrie.createTrie(
          inMemoryLeafIndexManager,
          inMemoryLeafIndexManager,
          (location, hash) -> Optional.ofNullable(storage.get(hash)),
          (location, hash, value) -> storage.put(hash, value));
    } else {
      return ZKTrie.loadTrie(
          rootHash,
          inMemoryLeafIndexManager,
          inMemoryLeafIndexManager,
          (location, hash) -> Optional.ofNullable(storage.get(hash)),
          (location, hash, value) -> storage.put(hash, value));
    }
  }

  private ZKTrie loadStorageTrie(final ZkValue<ZkAccount> zkAccount) {
    if (zkAccount.getPrior() == null
        || zkAccount.getPrior().getStorageRoot().equals(ZkAccount.EMPTY_TRIE_ROOT)) {
      return ZKTrie.createTrie(
          inMemoryLeafIndexManager,
          inMemoryLeafIndexManager,
          (location, hash) -> Optional.ofNullable(storage.get(hash)),
          (location, hash, value) -> storage.put(hash, value));
    } else {
      return ZKTrie.loadTrie(
          zkAccount.getPrior().getStorageRoot(),
          inMemoryLeafIndexManager,
          inMemoryLeafIndexManager,
          (location, hash) -> Optional.ofNullable(storage.get(hash)),
          (location, hash, value) -> storage.put(hash, value));
    }
  }
}
