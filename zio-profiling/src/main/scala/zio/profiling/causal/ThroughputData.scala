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

/**
 * The number of visits to one progress point during one experiment of the causal profiler.
 *
 * @param name
 *   the name passed to `progressPoint`.
 * @param delta
 *   how many times the progress point ran during the experiment.
 */
final case class ThroughputData(name: String, delta: Int) {

  /** Renders a `throughput-point` line of Coz's tab-separated profile format, with the fields `name` and `delta`. */
  lazy val render: String =
    s"throughput-point\tname=$name\tdelta=$delta"
}
