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

package net.consensys.shomei.storage;

import net.consensys.shomei.services.storage.api.KeyValueStorage;
import net.consensys.shomei.services.storage.api.KeyValueStorageTransaction;
import net.consensys.shomei.trie.proof.Trace;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.primitives.Longs;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;

public interface TraceManager {
  String ZK_STATE_ROOT_PREFIX = "zkStateRoot";

  Optional<Bytes> getTrace(final long blockNumber);

  TraceManager saveTrace(final long blockNumber, final List<Trace> traces);

  Optional<Hash> getZkStateRootHash(final long blockNumber);

  TraceManager saveZkStateRootHash(final long blockNumber, final Hash stateRoot);

  TraceManager commit();

  class TraceManagerImpl implements TraceManager {
    private final KeyValueStorage traceStorage;
    private final AtomicReference<KeyValueStorageTransaction> traceTx;

    public TraceManagerImpl(final KeyValueStorage traceStorage) {
      this.traceStorage = traceStorage;
      this.traceTx = new AtomicReference<>(traceStorage.startTransaction());
    }

    @Override
    public Optional<Bytes> getTrace(final long blockNumber) {
      // we might not need to read through the transaction, but it probably doesn't hurt
      return traceTx.get().get(Longs.toByteArray(blockNumber)).map(Bytes::wrap);
    }

    @Override
    public TraceManager saveTrace(final long blockNumber, final List<Trace> traces) {
      traceTx.getAndUpdate(
          tx -> {
            tx.put(Longs.toByteArray(blockNumber), Trace.serialize(traces).toArrayUnsafe());
            tx.commit();
            return traceStorage.startTransaction();
          });
      return this;
    }

    @Override
    public Optional<Hash> getZkStateRootHash(final long blockNumber) {
      return traceTx
          .get()
          .get((ZK_STATE_ROOT_PREFIX + blockNumber).getBytes(StandardCharsets.UTF_8))
          .map(Bytes32::wrap)
          .map(Hash::wrap);
    }

    @Override
    public TraceManager saveZkStateRootHash(final long blockNumber, final Hash stateRoot) {
      traceTx
          .get()
          .put(
              (ZK_STATE_ROOT_PREFIX + blockNumber).getBytes(StandardCharsets.UTF_8),
              stateRoot.toArrayUnsafe());
      return this;
    }

    @Override
    public TraceManager commit() {
      traceTx.getAndUpdate(
          tx -> {
            tx.commit();
            return traceStorage.startTransaction();
          });
      return this;
    }
  }
}
