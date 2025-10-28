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

package net.consensys.shomei.util.bytes;

import net.consensys.zkevm.HashProvider;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.DelegatingBytes;
import org.hyperledger.besu.datatypes.Hash;

/**
 * The fields elements hold on 32 bytes but do not allow to contain 32 bytes entirely. For some
 * keys, we cannot assume that it will always fit on a field element. So we need sometimes to modify
 * the key to be hash friendly.
 */
public class ShomeiSafeBytes<T extends Bytes> extends DelegatingBytes implements Bytes {

  private final T originalUnsafeValue;

  protected ShomeiSafeBytes(final Bytes delegate, final T originalUnsafeValue) {
    super(delegate);
    this.originalUnsafeValue = originalUnsafeValue;
  }


  @Override
  public String toHexString() {
    return originalUnsafeValue.toHexString();
  }

  public T getOriginalUnsafeValue() {
    return originalUnsafeValue;
  }

    public Hash hash() {
      return HashProvider.trieHash(this);
    }
}
