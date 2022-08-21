/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.profiling.causal

import java.util.concurrent.atomic.AtomicLong

import zio._
import zio.profiling.{CostCenter, CostCenterRef}

final private class FiberState private (
  val localDelay: AtomicLong,
  @volatile var costCenter: CostCenter,
  @volatile var location: Trace,
  @volatile var running: Boolean,
  @volatile var lastEffectWasStateful: Boolean,
  @volatile var inAsync: Boolean,
  @volatile var preAsyncGlobalDelay: Long
) {

  def refreshCostCenter(fiber: Fiber.Runtime[_, _])(implicit unsafe: Unsafe): Unit =
    costCenter = CostCenter.getCurrent(fiber)

  def taggedLocation: CostCenter =
    costCenter #> location
}

private object FiberState {

  def makeFor(fiber: Fiber.Runtime[_, _], inheritedDelay: Long)(implicit unsafe: Unsafe): FiberState = {
    val state = new FiberState(
      new AtomicLong(inheritedDelay),
      CostCenter.getCurrent(fiber),
      Trace.empty,
      true,
      false,
      false,
      0
    )
    state
  }
}
