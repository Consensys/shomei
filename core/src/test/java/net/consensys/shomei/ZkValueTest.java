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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class ZkValueTest {
  @Test
  public void testGetPrior() {
    ZkValue<Bytes> zkValue =
        new ZkValue<>(Bytes.fromHexString("0x1122"), Bytes.fromHexString("0x3344"));
    assertEquals(Bytes.fromHexString("0x1122"), zkValue.getPrior());
  }

  @Test
  public void testGetUpdated() {
    ZkValue<Bytes> zkValue =
        new ZkValue<>(Bytes.fromHexString("0x1122"), Bytes.fromHexString("0x3344"));
    assertEquals(Bytes.fromHexString("0x3344"), zkValue.getUpdated());
  }

  @Test
  public void testSetPrior() {
    ZkValue<Bytes> zkValue =
        new ZkValue<>(Bytes.fromHexString("0x1122"), Bytes.fromHexString("0x3344"));
    zkValue.setPrior(Bytes.fromHexString("0x5566"));
    assertEquals(Bytes.fromHexString("0x5566"), zkValue.getPrior());
  }

  @Test
  public void testSetUpdated() {
    ZkValue<Bytes> zkValue =
        new ZkValue<>(Bytes.fromHexString("0x1122"), Bytes.fromHexString("0x3344"));
    zkValue.setUpdated(Bytes.fromHexString("0x7788"));
    assertEquals(Bytes.fromHexString("0x7788"), zkValue.getUpdated());
  }

  @Test
  public void testSetCleared() {
    ZkValue<Bytes> zkValue =
        new ZkValue<>(Bytes.fromHexString("0x1122"), Bytes.fromHexString("0x3344"));
    zkValue.setCleared(true);
    assertTrue(zkValue.isCleared());
  }

  @Test
  public void testIsRecreated() {
    ZkValue<Bytes> zkValue =
        new ZkValue<>(Bytes.fromHexString("0x1122"), Bytes.fromHexString("0x3344"));
    zkValue.setCleared(true);
    assertTrue(zkValue.isRecreated());
  }

  @Test
  public void testIsZeroRead() {
    ZkValue<Bytes> zkValue = new ZkValue<>(null, null);
    assertTrue(zkValue.isZeroRead());
  }

  @Test
  public void testIsNonZeroRead() {
    ZkValue<Bytes> zkValue =
        new ZkValue<>(Bytes.fromHexString("0x1122"), Bytes.fromHexString("0x1122"));
    assertTrue(zkValue.isNonZeroRead());
  }

  @Test
  public void testIsUnchanged() {
    ZkValue<Bytes> zkValue =
        new ZkValue<>(Bytes.fromHexString("0x1122"), Bytes.fromHexString("0x1122"));
    assertTrue(zkValue.isUnchanged());
  }

  @Test
  public void testIsRollforward() {
    ZkValue<Bytes> zkValue = new ZkValue<>(Bytes.fromHexString("0x1122"), null);
    zkValue.setRollforward(true);
    assertTrue(zkValue.isRollforward());
  }

  @Test
  public void testWriteRlp() {
    ZkValue<Bytes> zkValue =
        new ZkValue<>(Bytes.fromHexString("0x1122"), Bytes.fromHexString("0x3344"));
    BytesValueRLPOutput output = new BytesValueRLPOutput();
    zkValue.writeRlp(output, RLPOutput::writeBytes);
    Bytes expectedOutput = Bytes.fromHexString("0xc782112282334480");
    assertEquals(expectedOutput, output.encoded());
  }
}
