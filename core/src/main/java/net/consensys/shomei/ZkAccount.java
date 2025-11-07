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

import static net.consensys.shomei.util.bytes.PoseidonSafeBytesUtils.safeByte32;
import static net.consensys.shomei.util.bytes.PoseidonSafeBytesUtils.safeUInt256;
import static net.consensys.zkevm.HashProvider.keccak256;
import static net.consensys.zkevm.HashProvider.trieHash;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;

import java.util.Objects;

import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.util.bytes.BytesBuffer;
import net.consensys.shomei.util.bytes.PoseidonSafeBytes;
import net.consensys.shomei.util.bytes.PoseidonSafeBytesUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/** A ZkAccount is a representation of an Ethereum account in the ZkEvm world. */
public class ZkAccount {

  public static final PoseidonSafeBytes<Bytes32> EMPTY_KECCAK_CODE_HASH =
      safeByte32(keccak256(Bytes.EMPTY));
  public static final Hash EMPTY_CODE_HASH = trieHash(Bytes32.ZERO);

  protected AccountKey accountKey;
  protected PoseidonSafeBytes<Bytes32> keccakCodeHash;
  protected Bytes32 poseidonCodeHash;

  protected PoseidonSafeBytes<UInt256> codeSize;
  protected PoseidonSafeBytes<UInt256> nonce;
  protected PoseidonSafeBytes<UInt256> balance;
  protected Bytes32 storageRoot;

  public ZkAccount(
      final AccountKey accountKey,
      final PoseidonSafeBytes<UInt256> nonce,
      final PoseidonSafeBytes<UInt256> balance,
      final Bytes32 storageRoot,
      final Bytes32 poseidonCodeHash,
      final PoseidonSafeBytes<Bytes32> keccakCodeHash,
      final PoseidonSafeBytes<UInt256> codeSize) {
    this.accountKey = accountKey;
    this.nonce = nonce;
    this.balance = balance;
    this.storageRoot = storageRoot;
    this.keccakCodeHash = keccakCodeHash;
    this.poseidonCodeHash = poseidonCodeHash;
    this.codeSize = codeSize;
  }

  public ZkAccount(final ZkAccount toCopy) {
    this(
        toCopy.accountKey,
        toCopy.nonce,
        toCopy.balance,
        toCopy.storageRoot,
        toCopy.poseidonCodeHash,
        toCopy.keccakCodeHash,
        toCopy.codeSize);
  }

  public static ZkAccount fromEncodedBytes(final AccountKey accountKey, final Bytes encoded) {
    return BytesBuffer.readBytes(
        encoded,
        bytesInput ->
            new ZkAccount(
                accountKey,
                safeUInt256(bytesInput.readBytesUINT256()),
                safeUInt256(bytesInput.readBytesUINT256()),
                bytesInput.readBytes32(),
                bytesInput.readBytes32(),
                safeByte32(bytesInput.readBytes32()),
                safeUInt256(bytesInput.readBytesUINT256())));
  }

  /**
   * Returns the account key.
   *
   * @return the account key
   */
  public Hash getHkey() {
    return accountKey.accountHash();
  }

  /**
   * Returns the account address.
   *
   * @return the account address
   */
  public PoseidonSafeBytes<Address> getAddress() {
    return accountKey.address();
  }

  /**
   * Returns the account key.
   *
   * @return the account key
   */
  public UInt256 getNonce() {
    return nonce.getOriginalUnsafeValue();
  }

  /**
   * Returns the account balance.
   *
   * @return the account balance
   */
  public UInt256 getBalance() {
    return balance.getOriginalUnsafeValue();
  }

  /**
   * Returns the keccak code hash
   *
   * @return the keccak code hash
   */
  public PoseidonSafeBytes<Bytes32> getCodeHash() {
    return keccakCodeHash;
  }

  /**
   * Returns the Shomei code hash
   *
   * @return the Shomei code hash
   */
  public Bytes32 getPoseidonCodeHash() {
    return poseidonCodeHash;
  }

  /**
   * Returns the code size
   *
   * @return the code size
   */
  public PoseidonSafeBytes<UInt256> getCodeSize() {
    return codeSize;
  }

  /**
   * Returns the zkevm storage root
   *
   * @return the zkevm storage root
   */
  public Bytes32 getStorageRoot() {
    return storageRoot;
  }

  /**
   * Returns the encoded bytes of the account as a safe representation for the hash algorithm
   *
   * @return encoded bytes
   */
  public PoseidonSafeBytes<Bytes> getEncodedBytes() {
    return PoseidonSafeBytesUtils.concatenateSafeElements(
        nonce, balance, storageRoot, poseidonCodeHash, keccakCodeHash, codeSize);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ZkAccount zkAccount = (ZkAccount) o;
    return Objects.equals(accountKey, zkAccount.accountKey)
        && Objects.equals(keccakCodeHash, zkAccount.keccakCodeHash)
        && Objects.equals(poseidonCodeHash, zkAccount.poseidonCodeHash)
        && Objects.equals(codeSize, zkAccount.codeSize)
        && Objects.equals(nonce, zkAccount.nonce)
        && Objects.equals(balance, zkAccount.balance)
        && Objects.equals(storageRoot, zkAccount.storageRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        accountKey, keccakCodeHash, poseidonCodeHash, codeSize, nonce, balance, storageRoot);
  }

  @Override
  public String toString() {
    return "ZkAccount{"
        + "accountKey="
        + accountKey
        + ", keccakCodeHash="
        + keccakCodeHash
        + ", poseidonCodeHash="
        + poseidonCodeHash
        + ", codeSize="
        + codeSize
        + ", nonce="
        + nonce
        + ", balance="
        + balance
        + ", storageRoot="
        + storageRoot
        + '}';
  }

  public static void assertCloseEnoughForDiffing(
      final ZkAccount source, final ZkAccount account, final String context) {
    if (source == null) {
      throw new IllegalStateException(context + ": source is null but target isn't");
    } else {
      if (!Objects.equals(source.getNonce(), account.getNonce())) {
        throw new IllegalStateException(context + ": nonces differ");
      }
      if (!Objects.equals(source.getBalance(), account.getBalance())) {
        throw new IllegalStateException(context + ": balances differ");
      }
      if (!Objects.equals(source.getStorageRoot(), account.getStorageRoot())) {
        throw new IllegalStateException(context + ": Storage Roots differ");
      }
    }
  }
}
