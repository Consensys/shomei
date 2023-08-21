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
import net.consensys.shomei.trie.trace.Trace;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import com.google.common.primitives.Longs;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;

public interface TraceManager {
  String ZK_STATE_ROOT_PREFIX = "zkStateRoot";

  TraceManagerUpdater updater();

  Optional<Bytes> getTrace(final long blockNumber);

  // TODO it's not logical to save the zkstate root hash in the trace manager, we need to change
  // that in the future. but for backward compatibility, we keep it here for now.
  Optional<Hash> getZkStateRootHash(final long blockNumber);

  class TraceManagerImpl implements TraceManager {
    private final KeyValueStorage traceStorage;

    public TraceManagerImpl(final KeyValueStorage traceStorage) {
      this.traceStorage = traceStorage;
    }

    @Override
    public Optional<Bytes> getTrace(final long blockNumber) {
      // we might not need to read through the transaction, but it probably doesn't hurt
      return traceStorage.get(Longs.toByteArray(blockNumber)).map(Bytes::wrap);
    }

    @Override
    public Optional<Hash> getZkStateRootHash(final long blockNumber) {
      return traceStorage
          .get((ZK_STATE_ROOT_PREFIX + blockNumber).getBytes(StandardCharsets.UTF_8))
          .map(Bytes32::wrap)
          .map(Hash::wrap);
    }

    @Override
    public TraceManagerUpdater updater() {
      return new TraceManagerUpdater(traceStorage.startTransaction());
    }
  }

  class TraceManagerUpdater {
    private final KeyValueStorageTransaction transaction;

    public TraceManagerUpdater(final KeyValueStorageTransaction transaction) {
      this.transaction = transaction;
    }

    public TraceManagerUpdater saveTrace(final long blockNumber, final List<Trace> traces) {
      transaction.put(Longs.toByteArray(blockNumber), Trace.serialize(traces).toArrayUnsafe());
      return this;
    }

    public TraceManagerUpdater removeTrace(final long blockNumber) {
      transaction.remove(Longs.toByteArray(blockNumber));
      return this;
    }

    public TraceManagerUpdater saveZkStateRootHash(final long blockNumber, final Hash stateRoot) {
      transaction.put(
          (ZK_STATE_ROOT_PREFIX + blockNumber).getBytes(StandardCharsets.UTF_8),
          stateRoot.toArrayUnsafe());
      return this;
    }

    public void commit() {
      transaction.commit();
    }
  }
}
