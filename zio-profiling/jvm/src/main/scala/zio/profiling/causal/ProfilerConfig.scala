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

final case class ProfilerConfig(
  iterations: Int,
  scope: ScopeSelector,
  reportProgress: Boolean,
  samplingPeriod: Duration,
  minExperimentDuration: Duration,
  experimentTargetSamples: Int,
  warmUpPeriod: Duration,
  coolOffPeriod: Duration,
  zeroSpeedupWeight: Int,
  maxConsideredSpeedUp: Int,
  sleepPrecision: Duration
)

object ProfilerConfig {

  val Default: ProfilerConfig =
    ProfilerConfig(
      iterations = 10,
      scope = ScopeSelector.Default,
      reportProgress = true,
      samplingPeriod = 20.millis,
      minExperimentDuration = 1.second,
      experimentTargetSamples = 30,
      warmUpPeriod = 30.seconds,
      coolOffPeriod = 2.seconds,
      zeroSpeedupWeight = 10,
      maxConsideredSpeedUp = 100,
      sleepPrecision = 10.millis
    )
}
