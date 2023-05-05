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

import static net.consensys.shomei.trie.ZKTrie.DEFAULT_TRIE_ROOT;
import static net.consensys.shomei.util.bytes.MimcSafeBytes.safeUInt256;

import net.consensys.shomei.MutableZkAccount;
import net.consensys.shomei.ZkAccount;
import net.consensys.shomei.ZkValue;
import net.consensys.shomei.storage.WorldStateRepository;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.proof.Trace;
import net.consensys.shomei.trie.storage.AccountTrieRepositoryWrapper;
import net.consensys.shomei.trie.storage.StorageTrieRepositoryWrapper;
import net.consensys.shomei.trie.storage.TrieRepository;
import net.consensys.shomei.trie.storage.TrieRepository.TrieUpdater;
import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.trielog.StorageSlotKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZKEvmWorldState {

  private static final Logger LOG = LoggerFactory.getLogger(ZKEvmWorldState.class);

  private final ZkEvmWorldStateUpdateAccumulator accumulator;

  private State state;

  private long blockNumber;
  private Hash blockHash;

  private final WorldStateRepository zkEvmWorldStateStorage;

  public ZKEvmWorldState(final WorldStateRepository zkEvmWorldStateStorage) {
    this.state =
        new State(
            zkEvmWorldStateStorage.getWorldStateRootHash().orElse(DEFAULT_TRIE_ROOT),
            new ArrayList<>());
    this.blockNumber = zkEvmWorldStateStorage.getWorldStateBlockNumber().orElse(-1L);
    this.blockHash = zkEvmWorldStateStorage.getWorldStateBlockHash().orElse(Hash.EMPTY);
    this.accumulator = new ZkEvmWorldStateUpdateAccumulator();
    this.zkEvmWorldStateStorage = zkEvmWorldStateStorage;
  }

  public void commit(final long blockNumber, final Hash blockHash, final boolean ignoreTrace) {
    LOG.atDebug()
        .setMessage("Commit world state for block number {} and block hash {}")
        .addArgument(blockNumber)
        .addArgument(blockHash)
        .log();
    long start = System.currentTimeMillis();
    final WorldStateRepository.WorldStateUpdater updater =
        (WorldStateRepository.WorldStateUpdater) zkEvmWorldStateStorage.updater();
    this.state = generateNewState(updater, ignoreTrace);
    this.blockNumber = blockNumber;
    this.blockHash = blockHash;

    updater.setBlockHash(blockHash);
    updater.setBlockNumber(blockNumber);
    updater.saveZkStateRootHash(blockNumber, state.stateRoot);

    if (!ignoreTrace) {
      updater.saveTrace(blockNumber, Trace.serialize(state.traces));
      if (!state.traces.isEmpty()) {
        LOG.atInfo()
            .setMessage("Generated trace for block {}:{} in {} ms")
            .addArgument(blockNumber)
            .addArgument(blockHash)
            .addArgument(System.currentTimeMillis() - start)
            .log();
      } else {
        LOG.atInfo()
            .setMessage("Ignore empty trace for block {}:{}")
            .addArgument(blockNumber)
            .addArgument(blockHash)
            .log();
      }
    }
    // persist
    updater.commit();
    accumulator.reset();
  }

  record State(Hash stateRoot, List<Trace> traces) {}

  private State generateNewState(final TrieUpdater updater, final boolean ignoreTrace) {
    final ZKTrie zkAccountTrie =
        loadAccountTrie(new AccountTrieRepositoryWrapper(zkEvmWorldStateStorage, updater));
    final List<Trace> traces = updateAccounts(zkAccountTrie, updater, ignoreTrace);
    zkAccountTrie.commit();
    return new State(Hash.wrap(zkAccountTrie.getTopRootHash()), traces);
  }

  private List<Trace> updateAccounts(
      final ZKTrie zkAccountTrie, final TrieUpdater updater, final boolean ignoreTrace) {
    final List<Trace> traces = new ArrayList<>();
    accumulator.getAccountsToUpdate().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            (entry) -> {
              final AccountKey accountKey = entry.getKey();
              final ZkValue<ZkAccount> accountValue = entry.getValue();
              traces.addAll(
                  updateAccount(accountKey, accountValue, zkAccountTrie, updater, ignoreTrace));
            });
    return traces;
  }

  private List<Trace> updateAccount(
      final AccountKey accountKey,
      final ZkValue<ZkAccount> accountValue,
      final ZKTrie zkAccountTrie,
      final TrieUpdater updater,
      final boolean ignoreTrace) {
    final List<Trace> traces = new ArrayList<>();

    // check read and read zero for rollfoward (if trace don't needed we skip this step)
    if (!ignoreTrace) {
      if (accountValue.isRollforward()) {
        if (accountValue.isZeroRead() || accountValue.isNonZeroRead()) {
          // read zero or non zero
          traces.add(zkAccountTrie.readAndProve(accountKey.accountHash(), accountKey.address()));
        }
        // only read if the contract already exist
        if (accountValue.getPrior() != null) {
          final long accountLeafIndex =
              zkAccountTrie.getLeafIndex(accountKey.accountHash()).orElseThrow();
          traces.addAll(readSlots(accountKey, accountLeafIndex, accountValue, updater));
        }
      }
    }

    // check if remove needed
    if (accountValue.isCleared()) {
      if (!accountValue.isRollforward()) {
        zkAccountTrie.decrementNextFreeNode();
      }
      if (accountValue.getPrior() != null) {
        traces.add(zkAccountTrie.removeAndProve(accountKey.accountHash(), accountKey.address()));
        // TODO remove all slots from storage : if we not do that the rollback will not work
        // get index leaf from DB and delete all keys starting by HKEY+index in trie branch
      }
    }

    // check selfdestruct
    if (!accountValue.isRollforward()
        && accountValue.isRecreated()) { // decrement again in case of selfdestruct
      zkAccountTrie.decrementNextFreeNode();
      zkAccountTrie.removeAndProve(accountKey.accountHash(), accountKey.address());
      // TODO remove all slots from storage : if we not do that the rollback will not work
      // get index leaf from DB and delete all keys starting by HKEY+index in trie branch
    }

    // check update and insert
    if (accountValue.isCleared() || !accountValue.isUnchanged()) {
      // update account or create
      if (accountValue.getUpdated() != null) {

        final long accountLeafIndex =
            zkAccountTrie
                .getLeafIndex(accountKey.accountHash())
                .orElse(zkAccountTrie.getNextFreeNode());
        // update slots of the account
        traces.addAll(updateSlots(accountKey, accountLeafIndex, accountValue, updater));

        traces.add(
            zkAccountTrie.putAndProve(
                accountValue.getUpdated().getHkey(),
                accountKey.address(),
                accountValue.getUpdated().getEncodedBytes()));
      }
    }
    return traces;
  }

  private List<Trace> readSlots(
      final AccountKey accountKey,
      final long accountLeafIndex,
      final ZkValue<ZkAccount> accountValue,
      final TrieUpdater updater) {
    final List<Trace> traces = new ArrayList<>();
    final Map<StorageSlotKey, ZkValue<UInt256>> storageToRead =
        accumulator.getStorageToUpdate().get(accountKey);
    if (storageToRead != null) {
      // load the account storage trie
      final StorageTrieRepositoryWrapper storageAdapter =
          new StorageTrieRepositoryWrapper(accountLeafIndex, zkEvmWorldStateStorage, updater);
      final ZKTrie zkStorageTrie = loadStorageTrie(accountValue, false, storageAdapter);
      storageToRead.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(
              (storageEntry) -> {
                final StorageSlotKey storageSlotKey = storageEntry.getKey();
                final ZkValue<UInt256> storageValue = storageEntry.getValue();
                if (Objects.equals(storageValue.getPrior(), storageValue.getUpdated())
                    || storageValue.isCleared()) {
                  traces.add(
                      zkStorageTrie.readAndProve(
                          storageSlotKey.slotHash(), storageSlotKey.slotKey()));
                }
              });
    }
    return traces;
  }

  private List<Trace> updateSlots(
      final AccountKey accountKey,
      final long accountLeafIndex,
      final ZkValue<ZkAccount> accountValue,
      final TrieUpdater updater) {
    final List<Trace> traces = new ArrayList<>();
    final Map<StorageSlotKey, ZkValue<UInt256>> storageToUpdate =
        accumulator.getStorageToUpdate().get(accountKey);
    if (storageToUpdate != null) {
      // load the account storage trie
      final StorageTrieRepositoryWrapper storageAdapter =
          new StorageTrieRepositoryWrapper(accountLeafIndex, zkEvmWorldStateStorage, updater);
      final ZKTrie zkStorageTrie =
          loadStorageTrie(accountValue, accountValue.isCleared(), storageAdapter);
      storageToUpdate.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(
              (storageEntry) -> {
                final StorageSlotKey storageSlotKey = storageEntry.getKey();
                final ZkValue<UInt256> storageValue = storageEntry.getValue();
                traces.addAll(
                    updateSlot(accountValue, storageSlotKey, storageValue, zkStorageTrie));
              });
      // update storage root of the account
      final MutableZkAccount mutableZkAccount = new MutableZkAccount(accountValue.getUpdated());
      mutableZkAccount.setStorageRoot(Hash.wrap(zkStorageTrie.getTopRootHash()));
      accountValue.setUpdated(mutableZkAccount);

      zkStorageTrie.commit();
    }
    return traces;
  }

  private List<Trace> updateSlot(
      final ZkValue<ZkAccount> accountValue,
      final StorageSlotKey storageSlotKey,
      final ZkValue<UInt256> storageValue,
      final ZKTrie zkStorageTrie) {
    final List<Trace> traces = new ArrayList<>();
    // check remove needed (not needed in case of selfdestruct contract)
    if (storageValue.isCleared() && !accountValue.isRecreated()) {
      if (!storageValue.isRollforward()) {
        zkStorageTrie.decrementNextFreeNode();
      }
      if (storageValue.getPrior() != null) {
        traces.add(
            zkStorageTrie.removeAndProve(storageSlotKey.slotHash(), storageSlotKey.slotKey()));
      }
    }

    // check update and insert
    if (accountValue.isCleared() || !storageValue.isUnchanged()) {
      // update account or create
      if (storageValue.getUpdated() != null) {
        traces.add(
            zkStorageTrie.putAndProve(
                storageSlotKey.slotHash(),
                storageSlotKey.slotKey(),
                safeUInt256(storageValue.getUpdated())));
      }
    }
    return traces;
  }

  public Hash getStateRootHash() {
    return state.stateRoot;
  }

  public long getBlockNumber() {
    return blockNumber;
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  public List<Trace> getLastTraces() {
    return state.traces;
  }

  @VisibleForTesting
  public ZkEvmWorldStateUpdateAccumulator getAccumulator() {
    return accumulator;
  }

  private ZKTrie loadAccountTrie(final TrieRepository storage) {
    if (state.stateRoot.equals(DEFAULT_TRIE_ROOT)) {
      return ZKTrie.createTrie(storage);
    } else {
      return ZKTrie.loadTrie(state.stateRoot, storage);
    }
  }

  private ZKTrie loadStorageTrie(
      final ZkValue<ZkAccount> zkAccount,
      final boolean forceNewTrie,
      final TrieRepository storage) {
    if (zkAccount.getPrior() == null // new contract
        || zkAccount
            .getPrior()
            .getStorageRoot()
            .equals(DEFAULT_TRIE_ROOT) // first write in the storage
        || forceNewTrie) { // recreate contract
      return ZKTrie.createTrie(storage);
    } else {
      return ZKTrie.loadTrie(zkAccount.getPrior().getStorageRoot(), storage);
    }
  }
}
