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
package net.consensys.shomei.rpc.server.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class RollupGetZkEvmStateV0ParameterTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  public void parsesJsonWithAllFields() throws Exception {
    final String json =
        """
        {
          "startBlockNumber": "1",
          "endBlockNumber": "2",
          "zkStateManagerVersion": "v1"
        }
        """;

    final RollupGetZkEvmStateV0Parameter param =
        MAPPER.readValue(json, RollupGetZkEvmStateV0Parameter.class);

    assertThat(param.getStartBlockNumber()).isEqualTo(1L);
    assertThat(param.getEndBlockNumber()).isEqualTo(2L);
    assertThat(param.getZkStateManagerVersion()).isEqualTo("v1");
  }

  @Test
  public void parsesJsonWhenZkStateManagerVersionIsOmitted() throws Exception {
    final String json =
        """
        {
          "startBlockNumber": "0",
          "endBlockNumber": "10"
        }
        """;

    final RollupGetZkEvmStateV0Parameter param =
        MAPPER.readValue(json, RollupGetZkEvmStateV0Parameter.class);

    assertThat(param.getStartBlockNumber()).isZero();
    assertThat(param.getEndBlockNumber()).isEqualTo(10L);
    assertThat(param.getZkStateManagerVersion()).isNull();
  }

  @Test
  public void parsesJsonWhenZkStateManagerVersionIsEmptyString() throws Exception {
    final String json =
        """
        {
          "startBlockNumber": "0",
          "endBlockNumber": "0",
          "zkStateManagerVersion": ""
        }
        """;

    final RollupGetZkEvmStateV0Parameter param =
        MAPPER.readValue(json, RollupGetZkEvmStateV0Parameter.class);

    assertThat(param.getZkStateManagerVersion()).isEmpty();
  }

  @Test
  public void parsesJsonWhenZkStateManagerVersionIsExplicitlyNull() throws Exception {
    final String json =
        """
        {
          "startBlockNumber": "0",
          "endBlockNumber": "0",
          "zkStateManagerVersion": null
        }
        """;

    final RollupGetZkEvmStateV0Parameter param =
        MAPPER.readValue(json, RollupGetZkEvmStateV0Parameter.class);

    assertThat(param.getZkStateManagerVersion()).isNull();
  }

  @Test
  public void parsesHexEncodedBlockNumbers() throws Exception {
    final String json =
        """
        {
          "startBlockNumber": "0x0",
          "endBlockNumber": "0xA",
          "zkStateManagerVersion": "x"
        }
        """;

    final RollupGetZkEvmStateV0Parameter param =
        MAPPER.readValue(json, RollupGetZkEvmStateV0Parameter.class);

    assertThat(param.getStartBlockNumber()).isZero();
    assertThat(param.getEndBlockNumber()).isEqualTo(10L);
  }
}
