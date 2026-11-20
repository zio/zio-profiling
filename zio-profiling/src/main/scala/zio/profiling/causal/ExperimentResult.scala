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

/**
 * The result of one experiment of the causal profiler.
 *
 * @param selected
 *   the cost center that was sped up during the experiment.
 * @param speedup
 *   the speedup that was applied, as a fraction: `0.25` means 25%, and `0` marks a baseline experiment.
 * @param duration
 *   the planned length of the experiment, in nanoseconds.
 * @param effectiveDuration
 *   `duration` minus the delays that the profiler added during the experiment, in nanoseconds.
 * @param selectedSamples
 *   the number of delay steps during the experiment: one for each running fiber in the selected cost center, or in one
 *   nested under it, at each sampling step.
 * @param throughputData
 *   one entry for each progress point that ran during the experiment.
 * @param latencyData
 *   one entry for each latency point that had at least one arrival during the experiment.
 */
final case class ExperimentResult(
  selected: CostCenter,
  speedup: Float,
  duration: Long,
  effectiveDuration: Long,
  selectedSamples: Long,
  throughputData: List[ThroughputData],
  latencyData: List[LatencyData]
) {

  /**
   * Renders the experiment as lines of Coz's tab-separated profile format: an `experiment` line, then one line for each
   * entry of `throughputData` and of `latencyData`. The `duration` field of the `experiment` line holds
   * `effectiveDuration`.
   */
  def render: List[String] =
    s"experiment\tselected=${selected.render}\tspeedup=$speedup\tduration=$effectiveDuration\tselected-samples=$selectedSamples" ::
      throughputData.map(_.render) ++
      latencyData.map(_.render)
}
