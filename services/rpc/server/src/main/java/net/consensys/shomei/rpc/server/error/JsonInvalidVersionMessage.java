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
package net.consensys.shomei.rpc.server.error;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("unused")
public record JsonInvalidVersionMessage(
    @JsonProperty("requestedVersion") String requestedVersion,
    @JsonProperty("supportedVersion") String supportedVersion) {

  public JsonInvalidVersionMessage(final String requestedVersion, final String supportedVersion) {
    this.requestedVersion = requestedVersion;
    this.supportedVersion = supportedVersion;
  }

  @Override
  public String requestedVersion() {
    return requestedVersion;
  }

  @Override
  public String supportedVersion() {
    return supportedVersion;
  }
}
