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
package net.consensys.shomei.trielog;

import static net.consensys.shomei.trie.storage.AccountTrieRepositoryWrapper.WRAP_ACCOUNT;
import static net.consensys.shomei.util.bytes.PoseidonSafeBytesUtils.safeByte32;
import static net.consensys.shomei.util.bytes.PoseidonSafeBytesUtils.safeCode;
import static net.consensys.shomei.util.bytes.PoseidonSafeBytesUtils.safeUInt256;

import net.consensys.shomei.ZkAccount;
import net.consensys.shomei.storage.worldstate.WorldStateStorage;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.model.FlattenedLeaf;
import net.consensys.shomei.trie.storage.StorageTrieRepositoryWrapper;
import net.consensys.shomei.util.bytes.PoseidonSafeBytes;
import net.consensys.zkevm.HashProvider;

import java.util.Objects;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrieLogLayerConverter {

  private static final Logger LOG = LoggerFactory.getLogger(TrieLogLayerConverter.class);

  final WorldStateStorage headWorldStateStorage;

  public TrieLogLayerConverter(final WorldStateStorage headWorldStateStorage) {
    this.headWorldStateStorage = headWorldStateStorage;
  }

  public TrieLogLayer decodeTrieLog(final RLPInput input) {
    return decodeTrieLog(input, headWorldStateStorage);
  }

  public TrieLogLayer decodeTrieLog(final RLPInput input, WorldStateStorage wss) {

    TrieLogLayer trieLogLayer = new TrieLogLayer();

    input.enterList();
    trieLogLayer.setBlockHash(input.readBytes32());
    trieLogLayer.setBlockNumber(input.readLongScalar());

    // Only process list items (account entries). Stop when encountering non-list items like
    // the optional zkTraceComparisonFeature integer that may be appended by Besu's trielog format
    while (!input.isEndOfCurrentList() && input.nextIsList()) {
      input.enterList();

      final Address address = Address.readFrom(input);
      final AccountKey accountKey = new AccountKey(address);
      final Optional<Bytes> newCode;
      Optional<Long> maybeAccountIndex = Optional.empty();
      boolean isAccountCleared = false;

      if (input.nextIsNull()) {
        input.skipNext();
        newCode = Optional.empty();
      } else {
        input.enterList();
        input.skipNext(); // skip prior code not needed
        newCode = Optional.of(input.readBytes());
        input.skipNext(); // skip is cleared for code
        input.leaveList();
      }

      if (input.nextIsNull()) {
        input.skipNext();
        maybeAccountIndex =
            wss
                .getFlatLeaf(WRAP_ACCOUNT.apply(accountKey.accountHash()))
                .map(FlattenedLeaf::leafIndex);
      } else {
        input.enterList();
        final PriorAccount priorAccount = preparePriorTrieLogAccount(accountKey, input, wss);
        maybeAccountIndex = priorAccount.index;
        final ZkAccount newAccountValue =
            TrieLogLayer.nullOrValue(
                input,
                rlpInput -> prepareNewTrieLogAccount(accountKey, newCode, priorAccount, rlpInput));
        isAccountCleared = TrieLogLayer.defaultOrValue(input, 0, RLPInput::readInt) == 1;
        input.leaveList();
        trieLogLayer.addAccountChange(
            accountKey, priorAccount.account, newAccountValue, isAccountCleared);
      }

      if (input.nextIsNull()) {
        input.skipNext();
      } else {
        input.enterList();
        while (!input.isEndOfCurrentList()) {
          input.enterList();
          final Bytes32 keccakSlotHash = input.readBytes32();
          UInt256 oldValueExpected = TrieLogLayer.nullOrValue(input, RLPInput::readUInt256Scalar);
          final UInt256 newValue = TrieLogLayer.nullOrValue(input, RLPInput::readUInt256Scalar);
          final boolean isCleared = TrieLogLayer.defaultOrValue(input, 0, RLPInput::readInt) == 1;

          if (!input.isEndOfCurrentList()) {
            final StorageSlotKey storageSlotKey =
                new StorageSlotKey(
                    TrieLogLayer.defaultOrValue(input, UInt256.ZERO, RLPInput::readUInt256Scalar));
            final UInt256 oldValueFound;
            if (isAccountCleared) {
              oldValueFound = null;
              oldValueExpected =
                  null; // ignore old value as we will create a new account in the trie
            } else {
              oldValueFound =
                  maybeAccountIndex
                      .flatMap(
                          index -> {
                            return new StorageTrieRepositoryWrapper(index, wss, null)
                                  .getFlatLeaf(storageSlotKey.slotHash())
                                  .map(FlattenedLeaf::leafValue)
                                  .map(UInt256::fromBytes);
                          })
                      .orElse(null);  // Return null for new accounts to match trielog's null representation
            }
            LOG.atTrace()
                .setMessage(
                    "storage entry ({} and keccak hash {}) found for account {} with leaf index {} : expected old value {} and found {} with new value {} is cleared {}")
                .addArgument(storageSlotKey)
                .addArgument(keccakSlotHash)
                .addArgument(address)
                .addArgument(maybeAccountIndex)
                .addArgument(oldValueExpected)
                .addArgument(oldValueFound)
                .addArgument(newValue)
                .addArgument(isCleared)
                .log();
            if (!Objects.equals(
                oldValueExpected, oldValueFound)) { // check consistency between trielog and db
              throw new IllegalStateException("invalid trie log exception");
            }
            trieLogLayer.addStorageChange(
                accountKey, storageSlotKey, oldValueExpected, newValue, isCleared);
          } else {
            LOG.atTrace()
                .setMessage(
                    "storage entry skipped (keccak hash {}) for account {} with leaf index {} : expected old value {} with new value {}")
                .addArgument(keccakSlotHash)
                .addArgument(address)
                .addArgument(maybeAccountIndex)
                .addArgument(oldValueExpected)
                .addArgument(newValue)
                .log();
          }
          input.leaveList();
        }
        input.leaveList();
      }
      // lenient leave list for forward compatible additions.
      input.leaveListLenient();
    }

    // zkTraceComparisonFeature is optional (read as last element in container, before leaving)
    // This is written by Besu's ZkTrieLogFactory when includeMetadata=true
    if (!input.isEndOfCurrentList()) {
      input.readInt(); // consume but don't use
    }

    input.leaveListLenient();
    trieLogLayer.freeze();

    return trieLogLayer;
  }

  record PriorAccount(ZkAccount account, Bytes32 evmStorageRoot, Optional<Long> index) {}

  public PriorAccount preparePriorTrieLogAccount(final AccountKey accountKey, final RLPInput in, final WorldStateStorage wss) {

    final ZkAccount oldAccountValue;

    final Optional<FlattenedLeaf> flatLeaf =
        wss.getFlatLeaf(WRAP_ACCOUNT.apply(accountKey.accountHash()));


    if (in.nextIsNull() && flatLeaf.isEmpty()) {
      in.skipNext();

      LOG.atTrace()
          .setMessage("no prior account entry found for address({})")
          .addArgument(accountKey)
          .log();

      return new PriorAccount(null, Bytes32.wrap(Hash.EMPTY_TRIE_HASH.getBytes()), Optional.empty());
    } else if (!in.nextIsNull() && flatLeaf.isPresent()) {
      oldAccountValue =
          flatLeaf
              .map(value -> ZkAccount.fromEncodedBytes(accountKey, value.leafValue()))
              .orElseThrow();

      in.enterList();
      final UInt256 nonce = UInt256.valueOf(in.readLongScalar());
      final UInt256 balance = in.readUInt256Scalar();
      final Bytes32 evmStorageRoot;
      if (in.nextIsNull()) {
        evmStorageRoot = Bytes32.wrap(Hash.EMPTY_TRIE_HASH.getBytes());
        in.skipNext();
      } else {
        evmStorageRoot = in.readBytes32();
      }
      in.skipNext(); // skip keccak codeHash
      in.leaveList();

      LOG.atTrace()
          .setMessage(
              "prior account entry ({}) : expected old value ({},{},{}) and found ({},{},{})")
          .addArgument(accountKey)
          .addArgument(flatLeaf)
          .addArgument(nonce)
          .addArgument(balance)
          .addArgument(evmStorageRoot)
          .addArgument(oldAccountValue.getNonce())
          .addArgument(oldAccountValue.getBalance())
          .log();

      if (oldAccountValue.getNonce().equals(nonce) // check consistency between trielog and db
          && oldAccountValue.getBalance().equals(balance)) {
        return new PriorAccount(
            oldAccountValue, evmStorageRoot, flatLeaf.map(FlattenedLeaf::leafIndex));
      }
    }

    throw new IllegalStateException("invalid trie log exception");
  }

  /**
   * Decode a trie log for rollback purposes.
   *
   * <p>Unlike {@link #decodeTrieLog(RLPInput, WorldStateStorage)} this method:
   *
   * <ul>
   *   <li>Reads the prior code from the RLP (instead of skipping it) so prior code hashes can be
   *       computed correctly when a contract's code changed in this block.
   *   <li>Skips nonce, balance, and storage-slot consistency checks — the provided {@code wss}
   *       represents the rolling state at block N (after later blocks have already been rolled
   *       back), not the prior state N-1 that the trie log was recorded against.
   *   <li>Constructs the prior account from trie-log values (nonce/balance) combined with rolling-
   *       state values (code hash, ZK storage root) so that {@code rollBack} restores the correct
   *       state.
   * </ul>
   *
   * <p>Must be paired with an interleaved decode-then-rollback loop: decode trie log N against the
   * rolling state at block N, apply rollBack, then commit to advance the rolling state to block
   * N-1 before decoding trie log N-1.
   *
   * @param input the RLP input for this block's trie log
   * @param wss the world-state storage representing the current rolling state (block N)
   * @return a TrieLogLayer suitable for {@code ZkEvmWorldStateUpdateAccumulator.rollBack}
   */
  public TrieLogLayer decodeTrieLogForRollback(
      final RLPInput input, final WorldStateStorage wss) {

    TrieLogLayer trieLogLayer = new TrieLogLayer();

    input.enterList();
    trieLogLayer.setBlockHash(input.readBytes32());
    trieLogLayer.setBlockNumber(input.readLongScalar());

    while (!input.isEndOfCurrentList() && input.nextIsList()) {
      input.enterList();

      final Address address = Address.readFrom(input);
      final AccountKey accountKey = new AccountKey(address);
      Optional<Long> maybeAccountIndex = Optional.empty();
      boolean isAccountCleared = false;

      // For rollback: read BOTH prior code and new code.
      // Prior code is needed to recompute prior code hashes when code changed in this block.
      // New code is needed by prepareNewTrieLogAccountForRollback.
      final Optional<Bytes> priorCode;
      final Optional<Bytes> newCode;
      if (input.nextIsNull()) {
        input.skipNext();
        priorCode = Optional.empty();
        newCode = Optional.empty();
      } else {
        input.enterList();
        if (input.nextIsNull()) {
          input.skipNext();
          priorCode = Optional.empty();
        } else {
          priorCode = Optional.of(input.readBytes());
        }
        if (input.nextIsNull()) {
          input.skipNext();
          newCode = Optional.empty();
        } else {
          newCode = Optional.of(input.readBytes());
        }
        input.skipNext(); // skip is cleared for code
        input.leaveList();
      }

      if (input.nextIsNull()) {
        input.skipNext();
        maybeAccountIndex =
            wss.getFlatLeaf(WRAP_ACCOUNT.apply(accountKey.accountHash()))
                .map(FlattenedLeaf::leafIndex);
      } else {
        input.enterList();
        final PriorAccount priorAccount =
            preparePriorTrieLogAccountForRollback(accountKey, input, wss, priorCode);
        maybeAccountIndex = priorAccount.index;
        final ZkAccount newAccountValue =
            TrieLogLayer.nullOrValue(
                input,
                rlpInput ->
                    prepareNewTrieLogAccountForRollback(accountKey, newCode, priorAccount, rlpInput));
        isAccountCleared = TrieLogLayer.defaultOrValue(input, 0, RLPInput::readInt) == 1;
        input.leaveList();
        trieLogLayer.addAccountChange(
            accountKey, priorAccount.account, newAccountValue, isAccountCleared);

        // Inverted validation: wss (block-N state) must match the trielog's updated nonce/balance.
        // At rollback step for block N, rolling state = block N, trielog.updated = block N values.
        if (newAccountValue != null) {
          wss.getFlatLeaf(WRAP_ACCOUNT.apply(accountKey.accountHash()))
              .map(fl -> ZkAccount.fromEncodedBytes(accountKey, fl.leafValue()))
              .ifPresent(
                  wssAccount -> {
                    if (!wssAccount.getNonce().equals(newAccountValue.getNonce())
                        || !wssAccount.getBalance().equals(newAccountValue.getBalance())) {
                      throw new IllegalStateException("invalid trie log exception");
                    }
                  });
        }
      }

      if (input.nextIsNull()) {
        input.skipNext();
      } else {
        input.enterList();
        while (!input.isEndOfCurrentList()) {
          input.enterList();
          final Bytes32 keccakSlotHash = input.readBytes32();
          final UInt256 oldValueExpected =
              TrieLogLayer.nullOrValue(input, RLPInput::readUInt256Scalar);
          final UInt256 newValue = TrieLogLayer.nullOrValue(input, RLPInput::readUInt256Scalar);
          final boolean isCleared = TrieLogLayer.defaultOrValue(input, 0, RLPInput::readInt) == 1;

          if (!input.isEndOfCurrentList()) {
            final StorageSlotKey storageSlotKey =
                new StorageSlotKey(
                    TrieLogLayer.defaultOrValue(input, UInt256.ZERO, RLPInput::readUInt256Scalar));

            // Inverted validation: wss (block-N state) must match the trielog's updated slot value.
            // At rollback step for block N, rolling state = block N, trielog.updated = block N slot.
            final UInt256 currentValueFound;
            if (isAccountCleared) {
              currentValueFound = null;
            } else {
              currentValueFound =
                  maybeAccountIndex
                      .flatMap(
                          index ->
                              new StorageTrieRepositoryWrapper(index, wss, null)
                                  .getFlatLeaf(storageSlotKey.slotHash())
                                  .map(FlattenedLeaf::leafValue)
                                  .map(UInt256::fromBytes))
                      .orElse(null);
            }

            LOG.atTrace()
                .setMessage(
                    "rollback storage entry ({} keccak {}) account {} leaf {} : prior {} new {} cleared {} current {}")
                .addArgument(storageSlotKey)
                .addArgument(keccakSlotHash)
                .addArgument(address)
                .addArgument(maybeAccountIndex)
                .addArgument(oldValueExpected)
                .addArgument(newValue)
                .addArgument(isCleared)
                .addArgument(currentValueFound)
                .log();

            if (!Objects.equals(newValue, currentValueFound)) {
              throw new IllegalStateException("invalid trie log exception");
            }

            if (!isAccountCleared) {
              trieLogLayer.addStorageChange(
                  accountKey, storageSlotKey, oldValueExpected, newValue, isCleared);
            }
          }
          input.leaveList();
        }
        input.leaveList();
      }

      input.leaveListLenient();
    }

    if (!input.isEndOfCurrentList()) {
      input.readInt();
    }

    input.leaveListLenient();
    trieLogLayer.freeze();
    return trieLogLayer;
  }

  /**
   * Prepare the prior account for rollback decoding.
   *
   * <p>Unlike {@link #preparePriorTrieLogAccount} this method:
   *
   * <ul>
   *   <li>Reads prior nonce and balance from the trie log directly, skipping all consistency
   *       checks against the rolling state.
   *   <li>Uses the rolling state account for code hash and ZK storage root: code hash equals
   *       state N-1 when code did not change in this block; ZK storage root equals state N-1
   *       when storage was not modified, and is overwritten by {@code updateSlots} when it was.
   *   <li>Returns a non-null {@code leafIndex} even when the prior is null (new account), so
   *       storage rollback entries can be processed for the account.
   * </ul>
   */
  private PriorAccount preparePriorTrieLogAccountForRollback(
      final AccountKey accountKey,
      final RLPInput in,
      final WorldStateStorage wss,
      final Optional<Bytes> priorCode) {

    final Optional<FlattenedLeaf> flatLeaf =
        wss.getFlatLeaf(WRAP_ACCOUNT.apply(accountKey.accountHash()));

    if (in.nextIsNull()) {
      in.skipNext();
      // Prior is null: this block created the account.
      // Return the rolling-state leafIndex so storage changes can be undone.
      return new PriorAccount(
          null,
          Bytes32.wrap(Hash.EMPTY_TRIE_HASH.getBytes()),
          flatLeaf.map(FlattenedLeaf::leafIndex));
    }

    // Prior is non-null: existing account modified (or deleted via SELFDESTRUCT) in this block.
    in.enterList();
    final UInt256 priorNonce = UInt256.valueOf(in.readLongScalar());
    final UInt256 priorBalance = in.readUInt256Scalar();
    final Bytes32 priorEvmStorageRoot;
    if (in.nextIsNull()) {
      priorEvmStorageRoot = Bytes32.wrap(Hash.EMPTY_TRIE_HASH.getBytes());
      in.skipNext();
    } else {
      priorEvmStorageRoot = in.readBytes32();
    }
    in.skipNext(); // skip keccak codeHash (derived from prior code or rolling-state below)
    in.leaveList();

    if (flatLeaf.isEmpty()) {
      // Account was deleted in this block AND not re-created by any later block that was
      // already rolled back.  We cannot derive its ZK storage root or code hash from the
      // rolling state.  On Linea mainnet (EIP-6780) SELFDESTRUCT of pre-existing contracts
      // is effectively dead code, so this path is not expected in practice.
      throw new IllegalStateException(
          "Cannot reconstruct prior account during rollback: account "
              + accountKey.address()
              + " is absent from the rolling state. Rolling back across an account deletion"
              + " (SELFDESTRUCT) is not supported.");
    }

    final ZkAccount rollingAccount =
        flatLeaf.map(fl -> ZkAccount.fromEncodedBytes(accountKey, fl.leafValue())).orElseThrow();

    // Prior code hash:
    //   Code changed → compute from prior code read from trie log.
    //   Code unchanged → rolling-state code hash equals state-(N-1) code hash.
    final PoseidonSafeBytes<Bytes32> priorKeccakCodeHash;
    final Bytes32 priorPoseidonCodeHash;
    final PoseidonSafeBytes<UInt256> priorCodeSize;
    if (priorCode.isPresent()) {
      final Bytes code = priorCode.get();
      priorKeccakCodeHash = safeByte32(HashProvider.keccak256(code));
      priorPoseidonCodeHash = safeCode(code).hash();
      priorCodeSize = safeUInt256(UInt256.valueOf(code.size()));
    } else {
      priorKeccakCodeHash = rollingAccount.getCodeHash();
      priorPoseidonCodeHash = rollingAccount.getPoseidonCodeHash();
      priorCodeSize = rollingAccount.getCodeSize();
    }

    // ZK storage root from rolling state (= state-N ZK root).
    //   Storage unchanged: state-N root == state-(N-1) root → correct prior value.
    //   Storage changed:   updateSlots recomputes and overwrites this during commit.
    final Bytes32 priorZkStorageRoot = rollingAccount.getStorageRoot();

    final ZkAccount priorAccount =
        new ZkAccount(
            accountKey,
            safeUInt256(priorNonce),
            safeUInt256(priorBalance),
            priorZkStorageRoot,
            priorPoseidonCodeHash,
            priorKeccakCodeHash,
            priorCodeSize);

    return new PriorAccount(
        priorAccount, priorEvmStorageRoot, flatLeaf.map(FlattenedLeaf::leafIndex));
  }

  /**
   * Prepare the new (post-block) account value for rollback decoding.
   *
   * <p>Identical to {@link #prepareNewTrieLogAccount} except that when storage changed in this
   * block (storageRoot would be {@code null} in the forward path) this method uses
   * {@code priorAccount.account.getStorageRoot()} — the state-N ZK storage root from the rolling
   * state — instead of {@code null}. This is required so that {@code loadStorageTrie} can load
   * the correct state-N storage trie and apply reverse storage operations during rollback.
   */
  private ZkAccount prepareNewTrieLogAccountForRollback(
      final AccountKey accountKey,
      final Optional<Bytes> newCode,
      final PriorAccount priorAccount,
      final RLPInput in) {
    in.enterList();

    final PoseidonSafeBytes<UInt256> nonce = safeUInt256(UInt256.valueOf(in.readLongScalar()));
    final PoseidonSafeBytes<UInt256> balance = safeUInt256(in.readUInt256Scalar());
    Bytes32 storageRoot;
    if (in.nextIsNull() || priorAccount.account == null) {
      storageRoot = ZKTrie.DEFAULT_TRIE_ROOT;
      in.skipNext();
    } else {
      final Bytes32 newEvmStorageRoot = in.readBytes32();
      // For rollback: always use priorAccount.account.getStorageRoot() (= state-N ZK root).
      //   Storage unchanged: state-N root == state-(N-1) root → no-op in loadStorageTrie.
      //   Storage changed:   loadStorageTrie needs state-N root to load the current trie;
      //                      updateSlots will overwrite the root after applying reverse ops.
      // Unlike the forward path, we never emit null here.
      if (!priorAccount.evmStorageRoot.equals(newEvmStorageRoot)) {
        storageRoot = priorAccount.account.getStorageRoot();
      } else {
        storageRoot = priorAccount.account.getStorageRoot();
      }
    }
    in.skipNext(); // skip keccak codeHash
    in.leaveList();

    PoseidonSafeBytes<Bytes32> keccakCodeHash;
    Bytes32 poseidonCodeHash;
    PoseidonSafeBytes<UInt256> codeSize;

    if (newCode.isEmpty()) {
      if (priorAccount.account == null) {
        keccakCodeHash = ZkAccount.EMPTY_KECCAK_CODE_HASH;
        poseidonCodeHash = ZkAccount.EMPTY_CODE_HASH;
        codeSize = safeUInt256(UInt256.ZERO);
      } else {
        keccakCodeHash = priorAccount.account.getCodeHash();
        poseidonCodeHash = priorAccount.account.getPoseidonCodeHash();
        codeSize = priorAccount.account.getCodeSize();
      }
    } else {
      final Bytes code = newCode.get();
      keccakCodeHash = safeByte32(HashProvider.keccak256(code));
      poseidonCodeHash = safeCode(code).hash();
      codeSize = safeUInt256(UInt256.valueOf(code.size()));
    }

    return new ZkAccount(
        accountKey, nonce, balance, storageRoot, poseidonCodeHash, keccakCodeHash, codeSize);
  }

  public ZkAccount prepareNewTrieLogAccount(
      final AccountKey accountKey,
      final Optional<Bytes> newCode,
      final PriorAccount priorAccount,
      final RLPInput in) {
    in.enterList();

    final PoseidonSafeBytes<UInt256> nonce = safeUInt256(UInt256.valueOf(in.readLongScalar()));
    final PoseidonSafeBytes<UInt256> balance = safeUInt256(in.readUInt256Scalar());
    Bytes32 storageRoot;
    if (in.nextIsNull() || priorAccount.account == null) {
      storageRoot = ZKTrie.DEFAULT_TRIE_ROOT;
      in.skipNext();
    } else {
      final Bytes32 newEvmStorageRoot = in.readBytes32();
      if (!priorAccount.evmStorageRoot.equals(newEvmStorageRoot)) {
        storageRoot = null;
      } else {
        storageRoot = priorAccount.account.getStorageRoot();
      }
    }
    in.skipNext(); // skip keccak codeHash
    in.leaveList();

    PoseidonSafeBytes<Bytes32> keccakCodeHash;
    Bytes32 poseidonCodeHash;
    PoseidonSafeBytes<UInt256> codeSize;

    if (newCode.isEmpty()) {
      if (priorAccount.account == null) {
        keccakCodeHash = ZkAccount.EMPTY_KECCAK_CODE_HASH;
        poseidonCodeHash = ZkAccount.EMPTY_CODE_HASH;
        codeSize = safeUInt256(UInt256.ZERO);
      } else {
        keccakCodeHash = priorAccount.account.getCodeHash();
        poseidonCodeHash = priorAccount.account.getPoseidonCodeHash();
        codeSize = priorAccount.account.getCodeSize();
      }
    } else {
      final Bytes code = newCode.get();
      keccakCodeHash = safeByte32(HashProvider.keccak256(code));
      poseidonCodeHash = safeCode(code).hash();
      codeSize = safeUInt256(UInt256.valueOf(code.size()));
    }

    return new ZkAccount(
        accountKey, nonce, balance, storageRoot, poseidonCodeHash, keccakCodeHash, codeSize);
  }
}
