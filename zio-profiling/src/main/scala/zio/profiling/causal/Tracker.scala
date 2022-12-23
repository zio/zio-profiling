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

import zio._

private trait Tracker {
  def progressPoint(name: String): Unit
  def enterLatencyPoint(name: String): Unit
  def exitLatencyPoint(name: String): Unit
}

private object Tracker {
  final val noop: Tracker = new Tracker {
    def progressPoint(name: String): Unit = ()

    def enterLatencyPoint(name: String): Unit = ()

    def exitLatencyPoint(name: String): Unit = ()

  }

  private[causal] val globalRef: FiberRef[Tracker] =
    Unsafe.unsafe(implicit u => FiberRef.unsafe.make(Tracker.noop))
}
