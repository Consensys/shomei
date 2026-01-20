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
package net.consensys.shomei.trie.trace;

import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;

public interface Trace {

  int READ_TRACE_CODE = 0;
  int READ_ZERO_TRACE_CODE = 1;
  int INSERTION_TRACE_CODE = 2;
  int UPDATE_TRACE_CODE = 3;
  int DELETION_TRACE_CODE = 4;

  int getType();

  Bytes getLocation();

  void setLocation(final Bytes location);

  void writeTo(final RLPOutput out);

  static Bytes serialize(final List<Trace> traces) {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.writeList(
        traces,
        (trace, rlpOutput) -> {
          out.startList();
          out.writeInt(trace.getType());
          trace.writeTo(out);
          out.endList();
        });
    return out.encoded();
  }

  static List<Trace> deserialize(final RLPInput in) {
    return in.readList(
        rlpInput -> {
          rlpInput.enterList();
          final int traceType = rlpInput.readInt();
          final Trace trace;
          switch (traceType) {
            case DELETION_TRACE_CODE -> trace = DeletionTrace.readFrom(in);
            case UPDATE_TRACE_CODE -> trace = UpdateTrace.readFrom(in);
            case INSERTION_TRACE_CODE -> trace = InsertionTrace.readFrom(in);
            case READ_TRACE_CODE -> trace = ReadTrace.readFrom(in);
            case READ_ZERO_TRACE_CODE -> trace = ReadZeroTrace.readFrom(in);
            default -> throw new IllegalStateException("Unexpected trace type: " + traceType);
          }
          rlpInput.leaveList();
          return trace;
        });
  }
}
