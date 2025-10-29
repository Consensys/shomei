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
package net.consensys.shomei.trie.json;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.trie.Node;

import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

@SuppressWarnings("rawtypes")
public class JsonTraceParser {

  public static Module[] modules =
      new Module[] {
        new SimpleModule()
            .addSerializer(
                Node.class,
                new JsonSerializer<>() {
                  @Override
                  public void serialize(
                      Node node, JsonGenerator jsonGen, SerializerProvider serProv)
                      throws IOException {
                    jsonGen.writeString(node.getHash().toHexString());
                  }
                }),
        new SimpleModule()
            .addSerializer(
                UInt256.class,
                new JsonSerializer<>() {
                  @Override
                  public void serialize(
                      UInt256 node, JsonGenerator jsonGen, SerializerProvider serProv)
                      throws IOException {
                    jsonGen.writeString(node.toHexString());
                  }
                }),
        new SimpleModule()
            .addSerializer(
                Hash.class,
                new JsonSerializer<>() {
                  @Override
                  public void serialize(
                      Hash node, JsonGenerator jsonGen, SerializerProvider serProv)
                      throws IOException {
                    jsonGen.writeString(node.toHexString());
                  }
                }),
        new SimpleModule()
            .addSerializer(
                Optional.class,
                new JsonSerializer<>() {
                  @Override
                  public void serialize(
                      Optional optional, JsonGenerator jsonGen, SerializerProvider serProv)
                      throws IOException {
                    if (optional.isPresent() && optional.get() instanceof Bytes) {
                      jsonGen.writeString(optional.get().toString());
                    } else {
                      jsonGen.writeString(optional.toString());
                    }
                  }
                }),
        new SimpleModule()
            .addSerializer(
                Bytes.class,
                new JsonSerializer<>() {
                  @Override
                  public void serialize(
                      Bytes node, JsonGenerator jsonGen, SerializerProvider serProv)
                      throws IOException {
                    jsonGen.writeString(node.toHexString());
                  }
                })
      };
}
