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

package net.consensys.shomei.rpc.server;

import java.util.Optional;

import org.hyperledger.besu.util.platform.PlatformDetector;

public class ShomeiVersion {

  public static final String CLIENT_IDENTITY = "shomei";
  public static final String IMPL_VERSION =
      Optional.ofNullable(System.getProperty("shomei.version"))
          .or(() -> Optional.ofNullable(
              ShomeiVersion.class.getPackage().getImplementationVersion()))
          .orElse("UNPUBLISHED_VERSION");
  public static final String TEST_VERSION = "test";
  public static final String OS = PlatformDetector.getOS();
  public static final String VM = PlatformDetector.getVM();

  public static final String VERSION = CLIENT_IDENTITY + "/" + IMPL_VERSION + "/" + OS + "/" + VM;
}
