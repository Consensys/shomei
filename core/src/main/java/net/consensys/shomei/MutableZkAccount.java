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
package net.consensys.shomei;

import org.hyperledger.besu.datatypes.Hash;

import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.util.bytes.PoseidonSafeBytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/** A MutableZkAccount is a mutable representation of an Ethereum account in the ZkEvm world. */
public class MutableZkAccount extends ZkAccount {

  public MutableZkAccount(
      final AccountKey accountKey,
      final PoseidonSafeBytes<Bytes32> keccakCodeHash,
      final Bytes32 poseidonCodeHash,
      final PoseidonSafeBytes<UInt256> codeSize,
      final PoseidonSafeBytes<UInt256> nonce,
      final PoseidonSafeBytes<UInt256> balance,
      final Bytes32 storageRoot) {
    super(accountKey, nonce, balance, storageRoot, poseidonCodeHash, keccakCodeHash, codeSize);
  }

  public MutableZkAccount(final ZkAccount toCopy) {
    super(toCopy);
  }

  public void setKeccakCodeHash(final PoseidonSafeBytes<Bytes32> keccakCodeHash) {
    this.keccakCodeHash = keccakCodeHash;
  }

  public void setPoseidonCodeHash(final Hash poseidonCodeHash) {
    this.poseidonCodeHash = poseidonCodeHash;
  }

  public void setCodeSize(final PoseidonSafeBytes<UInt256> codeSize) {
    this.codeSize = codeSize;
  }

  public void setNonce(final PoseidonSafeBytes<UInt256> nonce) {
    this.nonce = nonce;
  }

  public void setBalance(final PoseidonSafeBytes<UInt256> balance) {
    this.balance = balance;
  }

  public void setStorageRoot(final Hash storageRoot) {
    this.storageRoot = storageRoot;
  }
}
