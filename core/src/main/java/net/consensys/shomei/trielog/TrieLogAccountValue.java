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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import net.consensys.shomei.ZkAccount;
import net.consensys.shomei.trie.ZKTrie;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class TrieLogAccountValue {

  private final UInt256 nonce;
  private final Wei balance;
  private final Hash storageRoot;
  private final Hash codeHash;

  private final Hash shomeiCodeHash;

  private final UInt256 codeSize;

  public TrieLogAccountValue(
      final UInt256 nonce,
      final Wei balance,
      final Hash storageRoot,
      final Hash codeHash,
      final Hash shomeiCodeHash,
      final UInt256 codeSize) {
    this.nonce = nonce;
    this.balance = balance;
    this.storageRoot = storageRoot;
    this.codeHash = codeHash;
    this.shomeiCodeHash = shomeiCodeHash;
    this.codeSize = codeSize;
  }

  public TrieLogAccountValue(final ZkAccount zkAccount) {
    this.nonce = zkAccount.getNonce();
    this.balance = zkAccount.getBalance();
    this.storageRoot = zkAccount.getStorageRoot();
    this.codeHash = zkAccount.getCodeHash();
    this.shomeiCodeHash = zkAccount.getShomeiCodeHash();
    this.codeSize = zkAccount.getCodeSize();
  }

  /**
   * The account nonce, that is the number of transactions sent from that account.
   *
   * @return the account nonce.
   */
  public UInt256 getNonce() {
    return nonce;
  }

  /**
   * The available balance of that account.
   *
   * @return the balance, in Wei, of the account.
   */
  public Wei getBalance() {
    return balance;
  }

  /**
   * The hash of the root of the storage trie associated with this account.
   *
   * @return the hash of the root node of the storage trie.
   */
  public Hash getStorageRoot() {
    return storageRoot;
  }

  /**
   * The hash of the EVM bytecode associated with this account.
   *
   * @return the hash of the account code (which may be {@link Hash#EMPTY}).
   */
  public Hash getCodeHash() {
    return codeHash;
  }

  /**
   * The shomei hash of the EVM bytecode associated with this account.
   *
   * @return the shomei hash of the account code.
   */
  public Hash getShomeiCodeHash() {
    return shomeiCodeHash;
  }

  /**
   * The size of the code
   *
   * @return code size
   */
  public UInt256 getCodeSize() {
    return codeSize;
  }

  public void writeTo(final RLPOutput out) {
    out.startList();

    out.writeUInt256Scalar(nonce);
    out.writeUInt256Scalar(balance);
    out.writeBytes(storageRoot);
    out.writeBytes(codeHash);
    out.writeBytes(shomeiCodeHash);
    out.writeUInt256Scalar(codeSize);
    out.endList();
  }

  public static TrieLogAccountValue readFrom(final RLPInput in) {
    in.enterList();

    final UInt256 nonce = UInt256.valueOf(in.readLongScalar());
    final Wei balance = Wei.of(in.readUInt256Scalar());
    Bytes32 storageRoot;
    Bytes32 keccakCodeHash;
    Bytes32 shomeiCodeHash;
    UInt256 codeSize;
    if (in.nextIsNull()) {
      storageRoot = ZKTrie.DEFAULT_TRIE_ROOT;
      in.skipNext();
    } else {
      storageRoot = in.readBytes32();
    }
    if (in.nextIsNull()) {
      keccakCodeHash = ZkAccount.EMPTY_KECCAK_CODE_HASH.getOriginalUnsafeValue();
      in.skipNext();
    } else {
      keccakCodeHash = in.readBytes32();
    }

    if (in.nextIsNull()) {
      shomeiCodeHash = ZkAccount.EMPTY_CODE_HASH;
      in.skipNext();
    } else {
      shomeiCodeHash = in.readBytes32();
    }

    if (in.nextIsNull()) {
      codeSize = UInt256.ZERO;
      in.skipNext();
    } else {
      codeSize = UInt256.valueOf(in.readLongScalar());
    }

    in.leaveList();

    return new TrieLogAccountValue(
        nonce,
        balance,
        Hash.wrap(storageRoot),
        Hash.wrap(keccakCodeHash),
        Hash.wrap(shomeiCodeHash),
        codeSize);
  }
}
