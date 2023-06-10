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

package net.consensys.shomei.trielog;

import static net.consensys.shomei.util.bytes.MimcSafeBytes.safeByte32;

import net.consensys.shomei.ZkAccount;
import net.consensys.shomei.storage.worldstate.WorldStateStorage;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.model.FlattenedLeaf;
import net.consensys.zkevm.HashProvider;

import java.util.Objects;
import java.util.Optional;

import com.google.common.primitives.Longs;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrieLogLayerConverter {

  private static final Logger LOG = LoggerFactory.getLogger(TrieLogLayerConverter.class);

  final WorldStateStorage worldStateStorage;

  public TrieLogLayerConverter(final WorldStateStorage worldStateStorage) {
    this.worldStateStorage = worldStateStorage;
  }

  public TrieLogLayer decodeTrieLog(final RLPInput input) {

    TrieLogLayer trieLogLayer = new TrieLogLayer();

    input.enterList();
    trieLogLayer.setBlockHash(Hash.wrap(input.readBytes32()));
    trieLogLayer.setBlockNumber(input.readLongScalar());

    while (!input.isEndOfCurrentList()) {
      input.enterList();

      final Address address = Address.readFrom(input);
      final AccountKey accountKey = new AccountKey(address);
      final Optional<Bytes> newCode;
      Optional<Long> maybeAccountIndex = Optional.empty();

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
      } else {
        input.enterList();
        final PriorAccount priorAccount = preparePriorTrieLogAccount(accountKey, input);
        maybeAccountIndex = priorAccount.index;
        final ZkAccount newAccountValue =
            TrieLogLayer.nullOrValue(
                input,
                rlpInput -> prepareNewTrieLogAccount(accountKey, newCode, priorAccount, rlpInput));
        final boolean isCleared = TrieLogLayer.defaultOrValue(input, 0, RLPInput::readInt) == 1;
        input.leaveList();
        trieLogLayer.addAccountChange(accountKey, priorAccount.account, newAccountValue, isCleared);
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
            final UInt256 oldValueFound =
                maybeAccountIndex
                    .flatMap(
                        index ->
                            worldStateStorage
                                .getFlatLeaf(
                                    Bytes.concatenate(
                                        Bytes.wrap(Longs.toByteArray(index)),
                                        storageSlotKey.slotHash()))
                                .map(FlattenedLeaf::leafValue)
                                .map(UInt256::fromBytes))
                    .orElse(null);
            LOG.atTrace()
                .setMessage(
                    "storage entry ({} and keccak hash {}) found for account {} with leaf index {} : expected old value {} and found {} with new value {}")
                .addArgument(storageSlotKey)
                .addArgument(keccakSlotHash)
                .addArgument(address)
                .addArgument(maybeAccountIndex)
                .addArgument(oldValueExpected)
                .addArgument(oldValueFound)
                .addArgument(newValue)
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
    input.leaveListLenient();
    trieLogLayer.freeze();

    return trieLogLayer;
  }

  record PriorAccount(ZkAccount account, Hash evmStorageRoot, Optional<Long> index) {}

  public PriorAccount preparePriorTrieLogAccount(final AccountKey accountKey, final RLPInput in) {

    final ZkAccount oldAccountValue;

    final Optional<FlattenedLeaf> flatLeaf =
        worldStateStorage.getFlatLeaf(accountKey.accountHash());

    if (in.nextIsNull() && flatLeaf.isEmpty()) {
      in.skipNext();

      LOG.atTrace()
          .setMessage("no prior account entry found for address({})")
          .addArgument(accountKey)
          .log();

      return new PriorAccount(null, Hash.EMPTY_TRIE_HASH, Optional.empty());
    } else if (!in.nextIsNull() && flatLeaf.isPresent()) {
      oldAccountValue =
          flatLeaf
              .map(value -> ZkAccount.fromEncodedBytes(accountKey, value.leafValue()))
              .orElseThrow();

      in.enterList();

      final UInt256 nonce = UInt256.valueOf(in.readLongScalar());
      final Wei balance = Wei.of(in.readUInt256Scalar());
      final Hash evmStorageRoot;
      if (in.nextIsNull()) {
        evmStorageRoot = Hash.EMPTY_TRIE_HASH;
        in.skipNext();
      } else {
        evmStorageRoot = Hash.wrap(in.readBytes32());
      }
      in.skipNext(); // skip keccak codeHash
      in.leaveList();

      LOG.atTrace()
          .setMessage("prior account entry ({}) : expected old value ({},{},{}) and found ({},{})")
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

  public ZkAccount prepareNewTrieLogAccount(
      final AccountKey accountKey,
      final Optional<Bytes> newCode,
      final PriorAccount priorAccount,
      final RLPInput in) {
    in.enterList();

    final UInt256 nonce = UInt256.valueOf(in.readLongScalar());
    final Wei balance = Wei.of(in.readUInt256Scalar());
    Hash storageRoot;
    if (in.nextIsNull() || priorAccount.account == null) {
      storageRoot = ZKTrie.DEFAULT_TRIE_ROOT;
      in.skipNext();
    } else {
      final Hash newEvmStorageRoot = Hash.wrap(in.readBytes32());
      if (!priorAccount.evmStorageRoot.equals(newEvmStorageRoot)) {
        storageRoot = null;
      } else {
        storageRoot = priorAccount.account.getStorageRoot();
      }
    }
    in.skipNext(); // skip keccak codeHash
    in.leaveList();

    Bytes32 keccakCodeHash;
    Bytes32 mimcCodeHash;
    UInt256 codeSize;

    if (newCode.isEmpty()) {
      if (priorAccount.account == null) {
        keccakCodeHash = ZkAccount.EMPTY_KECCAK_CODE_HASH.getOriginalUnsafeValue();
        mimcCodeHash = ZkAccount.EMPTY_CODE_HASH;
        codeSize = UInt256.ZERO;
      } else {
        keccakCodeHash = priorAccount.account.getCodeHash();
        mimcCodeHash = priorAccount.account.getMimcCodeHash();
        codeSize = priorAccount.account.getCodeSize();
      }
    } else {
      final Bytes code = newCode.get();
      keccakCodeHash = HashProvider.keccak256(code);
      mimcCodeHash = prepareMimcCodeHash(code);
      codeSize = UInt256.valueOf(code.size());
    }

    return new ZkAccount(
        accountKey,
        nonce,
        balance,
        storageRoot,
        Hash.wrap(mimcCodeHash),
        safeByte32(keccakCodeHash),
        codeSize);
  }

  /**
   * The MiMC hasher operates over field elements and the overall operation should be ZK friendly.
   * Each opcode making up the code to hash fit on a single byte. Since it would be too inefficient
   * to use one field element per opcode we group them in “limbs” of 16 bytes (so 16 opcodes per
   * limbs).
   *
   * @param code bytecode
   * @return mimc code hash
   */
  private static Hash prepareMimcCodeHash(final Bytes code) {
    final int sizeChunk = Bytes32.SIZE / 2;
    final int numChunks = (int) Math.ceil((double) code.size() / sizeChunk);
    final MutableBytes mutableBytes = MutableBytes.create(numChunks * Bytes32.SIZE);
    int offset = 0;
    for (int i = 0; i < numChunks; i++) {
      int length = Math.min(sizeChunk, code.size() - offset);
      mutableBytes.set(i * Bytes32.SIZE + (Bytes32.SIZE - length), code.slice(offset, length));
      offset += length;
    }
    return HashProvider.mimc(mutableBytes);
  }
}
