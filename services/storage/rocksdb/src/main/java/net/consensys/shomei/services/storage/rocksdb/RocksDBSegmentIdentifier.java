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

package net.consensys.shomei.services.storage.rocksdb;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import services.storage.SegmentIdentifier;

/** The RocksDb segment identifier. */
public class RocksDBSegmentIdentifier implements SegmentIdentifier {

  public enum SegmentNames {
    DEFAULT("default"), // default rocksdb segment
    ZK_ACCOUNT_TRIE("ZK_ACCOUNT_TRIE"),
    ZK_ACCOUNT_STORAGE_TRIE("ZK_ACCOUNT_STORAGE_TRIE"),
    ZK_FLAT_ACCOUNTS("ZK_FLAT_ACCOUNTS"),
    ZK_TRIE_LOG("ZK_TRIE_LOG");

    private final byte[] segmentId;

    SegmentNames(String segmentName) {
      this.segmentId = segmentName.getBytes(UTF_8);
    }

    RocksDBSegmentIdentifier getSegmentIdentifier() {
      return new RocksDBSegmentIdentifier(this);
    }

    public byte[] getId() {
      return segmentId;
    }

    public static SegmentNames fromId(byte[] segmentId) {
      return EnumSet.allOf(SegmentNames.class).stream()
          .filter(z -> Arrays.equals(z.getId(), segmentId))
          .findFirst()
          .orElseThrow(() -> new RuntimeException("Unknown segment id"));
    }
  }

  private static final Map<SegmentNames, RocksDBSegmentIdentifier> ALL_SEGMENTS =
      EnumSet.allOf(SegmentNames.class).stream()
          .collect(Collectors.toMap(s -> s, SegmentNames::getSegmentIdentifier));

  private final SegmentNames segmentName;

  /**
   * Instantiates a new RocksDb segment identifier.
   *
   * @param segmentName the segment name
   */
  public RocksDBSegmentIdentifier(final SegmentNames segmentName) {
    this.segmentName = segmentName;
  }

  @Override
  public String getName() {
    return segmentName.name();
  }

  @Override
  public byte[] getId() {
    return segmentName.getId();
  }

  public static RocksDBSegmentIdentifier fromHandle(ColumnFamilyHandle handle) {
    try {
      return ALL_SEGMENTS.get(SegmentNames.fromId(handle.getName()));
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RocksDBSegmentIdentifier that = (RocksDBSegmentIdentifier) o;
    return Objects.equals(this.segmentName, that.segmentName);
  }

  @Override
  public int hashCode() {
    return this.segmentName.hashCode();
  }
}
