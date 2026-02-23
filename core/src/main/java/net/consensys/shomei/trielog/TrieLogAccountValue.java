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

import net.consensys.shomei.ZkAccount;
import net.consensys.shomei.trie.ZKTrie;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

public class TrieLogAccountValue {

  private final UInt256 nonce;
  private final Wei balance;
  private final Bytes32 storageRoot;
  private final Bytes32 codeHash;

  private final Bytes32 mimcCodeHash;

  private final UInt256 codeSize;

  public TrieLogAccountValue(
      final UInt256 nonce,
      final Wei balance,
      final Bytes32 storageRoot,
      final Bytes32 codeHash,
      final Bytes32 mimcCodeHash,
      final UInt256 codeSize) {
    this.nonce = nonce;
    this.balance = balance;
    this.storageRoot = storageRoot;
    this.codeHash = codeHash;
    this.mimcCodeHash = mimcCodeHash;
    this.codeSize = codeSize;
  }

  public TrieLogAccountValue(final ZkAccount zkAccount) {
    this.nonce = zkAccount.getNonce();
    this.balance = zkAccount.getBalance();
    this.storageRoot = zkAccount.getStorageRoot();
    this.codeHash = zkAccount.getCodeHash();
    this.mimcCodeHash = zkAccount.getMimcCodeHash();
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
  public Bytes32 getStorageRoot() {
    return storageRoot;
  }

  /**
   * The hash of the EVM bytecode associated with this account.
   *
   * @return the hash of the account code (which may be {@link Hash#EMPTY}).
   */
  public Bytes32 getCodeHash() {
    return codeHash;
  }

  /**
   * The mimc hash of the EVM bytecode associated with this account.
   *
   * @return the mimc hash of the account code.
   */
  public Bytes32 getMimcCodeHash() {
    return mimcCodeHash;
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
    out.writeBytes(mimcCodeHash);
    out.writeUInt256Scalar(codeSize);
    out.endList();
  }

  public static TrieLogAccountValue readFrom(final RLPInput in) {
    in.enterList();

    final UInt256 nonce = UInt256.valueOf(in.readLongScalar());
    final Wei balance = Wei.of(in.readUInt256Scalar());
    Bytes32 storageRoot;
    Bytes32 keccakCodeHash;
    Bytes32 mimcCodeHash;
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
      mimcCodeHash = ZkAccount.EMPTY_CODE_HASH;
      in.skipNext();
    } else {
      mimcCodeHash = in.readBytes32();
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
        storageRoot,
        keccakCodeHash,
        mimcCodeHash,
        codeSize);
  }
}
