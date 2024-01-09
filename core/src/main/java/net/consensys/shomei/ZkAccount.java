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

package net.consensys.shomei;

import static net.consensys.shomei.util.bytes.MimcSafeBytes.safeByte32;
import static net.consensys.zkevm.HashProvider.keccak256;
import static net.consensys.zkevm.HashProvider.trieHash;

import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.trielog.TrieLogAccountValue;
import net.consensys.shomei.util.bytes.BytesBuffer;
import net.consensys.shomei.util.bytes.MimcSafeBytes;

import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;

/** A ZkAccount is a representation of an Ethereum account in the ZkEvm world. */
public class ZkAccount {

  public static final MimcSafeBytes<Bytes32> EMPTY_KECCAK_CODE_HASH =
      safeByte32(keccak256(Bytes.EMPTY));
  public static final Hash EMPTY_CODE_HASH = trieHash(Bytes32.ZERO);

  protected AccountKey accountKey;
  protected MimcSafeBytes<Bytes32> keccakCodeHash;
  protected Hash mimcCodeHash;

  protected UInt256 codeSize;
  protected UInt256 nonce;
  protected Wei balance;
  protected Hash storageRoot;

  public ZkAccount(
      final AccountKey accountKey,
      final UInt256 nonce,
      final Wei balance,
      final Hash storageRoot,
      final Hash mimcCodeHash,
      final MimcSafeBytes<Bytes32> keccakCodeHash,
      final UInt256 codeSize) {
    this.accountKey = accountKey;
    this.nonce = nonce;
    this.balance = balance;
    this.storageRoot = storageRoot;
    this.keccakCodeHash = keccakCodeHash;
    this.mimcCodeHash = mimcCodeHash;
    this.codeSize = codeSize;
  }

  public ZkAccount(
      final AccountKey accountKey,
      final long nonce,
      final Wei balance,
      final Hash storageRoot,
      final Hash mimcCodeHash,
      final MimcSafeBytes<Bytes32> keccakCodeHash,
      final long codeSize) {
    this(
        accountKey,
        UInt256.valueOf(nonce),
        balance,
        storageRoot,
        mimcCodeHash,
        keccakCodeHash,
        UInt256.valueOf(codeSize));
  }

  public ZkAccount(final AccountKey accountKey, final TrieLogAccountValue accountValue) {
    this(
        accountKey,
        accountValue.getNonce(),
        accountValue.getBalance(),
        accountValue.getStorageRoot(),
        accountValue.getMimcCodeHash(),
        safeByte32(accountValue.getCodeHash()),
        accountValue.getCodeSize());
  }

  public ZkAccount(final ZkAccount toCopy) {
    this(
        toCopy.accountKey,
        toCopy.nonce,
        toCopy.balance,
        toCopy.storageRoot,
        toCopy.mimcCodeHash,
        toCopy.keccakCodeHash,
        toCopy.codeSize);
  }

  public static ZkAccount fromEncodedBytes(final AccountKey accountKey, final Bytes encoded) {
    return BytesBuffer.readBytes(
        encoded,
        bytesInput ->
            new ZkAccount(
                accountKey,
                UInt256.fromBytes(bytesInput.readBytes32()),
                Wei.wrap(UInt256.fromBytes(bytesInput.readBytes32())),
                Hash.wrap(bytesInput.readBytes32()),
                Hash.wrap(bytesInput.readBytes32()),
                safeByte32(bytesInput.readBytes32()),
                UInt256.fromBytes(bytesInput.readBytes32())));
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
  public MimcSafeBytes<Address> getAddress() {
    return accountKey.address();
  }

  /**
   * Returns the account key.
   *
   * @return the account key
   */
  public UInt256 getNonce() {
    return nonce;
  }

  /**
   * Returns the account balance.
   *
   * @return the account balance
   */
  public Wei getBalance() {
    return balance;
  }

  /**
   * Returns the keccak code hash
   *
   * @return the keccak code hash
   */
  public Hash getCodeHash() {
    return Hash.wrap(keccakCodeHash.getOriginalUnsafeValue());
  }

  /**
   * Returns the mimc code hash
   *
   * @return the mimc code hash
   */
  public Hash getMimcCodeHash() {
    return mimcCodeHash;
  }

  /**
   * Returns the code size
   *
   * @return the code size
   */
  public UInt256 getCodeSize() {
    return codeSize;
  }

  /**
   * Returns the zkevm storage root
   *
   * @return the zkevm storage root
   */
  public Hash getStorageRoot() {
    return storageRoot;
  }

  /**
   * Returns the encoded bytes of the account as a safe representation for the mimc algorithm
   *
   * @return
   */
  public MimcSafeBytes<Bytes> getEncodedBytes() {
    return MimcSafeBytes.concatenateSafeElements(
        nonce, balance, storageRoot, mimcCodeHash, keccakCodeHash, codeSize);
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
        && Objects.equals(mimcCodeHash, zkAccount.mimcCodeHash)
        && Objects.equals(codeSize, zkAccount.codeSize)
        && Objects.equals(nonce, zkAccount.nonce)
        && Objects.equals(balance, zkAccount.balance)
        && Objects.equals(storageRoot, zkAccount.storageRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        accountKey, keccakCodeHash, mimcCodeHash, codeSize, nonce, balance, storageRoot);
  }

  @Override
  public String toString() {
    return "ZkAccount{"
        + "accountKey="
        + accountKey
        + ", keccakCodeHash="
        + keccakCodeHash
        + ", mimcCodeHash="
        + mimcCodeHash
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
      if (!Objects.equals(source.balance, account.getBalance())) {
        throw new IllegalStateException(context + ": balances differ");
      }
      if (!Objects.equals(source.storageRoot, account.getStorageRoot())) {
        throw new IllegalStateException(context + ": Storage Roots differ");
      }
    }
  }
}
