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
package net.consensys.shomei.util.bytes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class BytesBuffer {

  final ByteArrayInputStream inputStream;

  private BytesBuffer(final Bytes bytes) throws IOException {
    this.inputStream = new ByteArrayInputStream(bytes.toArrayUnsafe());
  }

  public void close() {
    try {
      inputStream.close();
    } catch (IOException ex) {
      // no op
    }
  }

  public UInt256 readUInt256() {
    try {
      return UInt256.fromBytes(Bytes.of(inputStream.readNBytes(UInt256.SIZE)));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Bytes32 readBytes32() {
    try {
      return Bytes32.wrap(inputStream.readNBytes(Bytes32.SIZE));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T readBytes(Bytes bytes, Function<BytesBuffer, T> inputConsumer) {
    BytesBuffer stream = null;
    try {
      stream = new BytesBuffer(bytes);
      return inputConsumer.apply(stream);
    } catch (IOException ex) {
      throw new RuntimeException("cannot read provided bytes");
    } finally {
      if (stream != null) {
        stream.close();
      }
    }
  }
}
