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

package net.consensys.shomei;

import java.util.Objects;

public class ZkValue<T> {
  private T prior;
  private T updated;
  private boolean cleared;

  public ZkValue(final T prior, final T updated) {
    this.prior = prior;
    this.updated = updated;
    this.cleared = false;
  }

  public ZkValue(final T prior, final T updated, final boolean cleared) {
    this.prior = prior;
    this.updated = updated;
    this.cleared = cleared;
  }

  public T getPrior() {
    return prior;
  }

  public T getUpdated() {
    return updated;
  }

  public ZkValue<T> setPrior(final T prior) {
    this.prior = prior;
    return this;
  }

  public ZkValue<T> setUpdated(final T updated) {
    this.cleared = updated == null;
    this.updated = updated;
    return this;
  }

  public boolean isUnchanged() {
    return Objects.equals(updated, prior);
  }

  public boolean isCleared() {
    return cleared;
  }

  @Override
  public String toString() {
    return "ZkValue{" + "prior=" + prior + ", updated=" + updated + ", cleared=" + cleared + '}';
  }
}
