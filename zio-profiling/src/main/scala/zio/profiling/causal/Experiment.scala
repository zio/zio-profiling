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

import zio.profiling.CostCenter

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

final private class Experiment(
  val candidate: CostCenter,
  val startTime: Long,
  val duration: Long,
  val speedUp: Float,
  private val progressPoints: ConcurrentHashMap[String, Int],
  private val latencyPointArrivals: ConcurrentHashMap[String, Int],
  private val latencyPointDepartures: ConcurrentHashMap[String, Int]
) {
  @volatile private[this] var delays: Long            = 0
  @volatile private[this] var effectiveDuration: Long = duration

  val endTime: Long =
    startTime + duration

  def trackDelay(amount: Long): Unit = {
    delays += 1
    effectiveDuration -= amount
    ()
  }

  def addProgressPointMeasurement(name: String): Unit = {
    progressPoints.compute(name, (_, v) => v + 1)
    ()
  }

  def addLatencyPointArrival(name: String): Unit = {
    latencyPointArrivals.compute(name, (_, v) => v + 1)
    ()
  }

  def addLatencyPointDeparture(name: String): Unit = {
    latencyPointDepartures.compute(name, (_, v) => v + 1)
    ()
  }

  def toResult(): ExperimentResult =
    ExperimentResult(
      candidate,
      speedUp,
      duration,
      effectiveDuration,
      delays,
      progressPoints.asScala.map { case (name, delta) =>
        ThroughputData(
          name,
          delta
        )
      }.toList,
      latencyPointArrivals.asScala.map { case (name, arrivals) =>
        LatencyData(
          name,
          arrivals,
          latencyPointDepartures.getOrDefault(name, 0)
        )
      }.toList
    )
}
