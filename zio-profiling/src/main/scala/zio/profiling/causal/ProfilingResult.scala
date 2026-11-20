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

import java.nio.file.{Files, Paths}

/**
 * The result of a causal profile: one [[zio.profiling.causal.ExperimentResult]] for each experiment, the most recent
 * first.
 *
 * {{{
 * import zio._
 * import zio.profiling.CostCenter
 *
 * val work = CostCenter.withChildCostCenter("work")(ZIO.succeed(Thread.sleep(10)))
 *
 * val saved: Task[Unit] =
 *   CausalProfiler(iterations = 5, warmUpPeriod = 1.second)
 *     .profileIterations(work)
 *     .flatMap(_.renderToFile("target/profile.coz"))
 * }}}
 *
 * @see
 *   [[https://zio.dev/zio-profiling/ ZIO Profiling]]
 */
final case class ProfilingResult(experiments: List[ExperimentResult]) {

  /**
   * Renders the experiments as text, in the same tab-separated format that [[https://github.com/plasma-umass/coz Coz]]
   * writes to its profile files: the lines of each experiment's `render`, joined with `\n`.
   */
  lazy val render: String =
    experiments.flatMap(_.render).mkString("\n")

  /**
   * Writes `render` to the file at `pathString`, creating missing parent directories and replacing an existing file.
   * The text is encoded with the platform's default charset.
   */
  def renderToFile(pathString: String): Task[Unit] =
    ZIO.attempt {
      val path      = Paths.get(pathString)
      val parentDir = path.getParent()
      if (parentDir ne null) {
        Files.createDirectories(parentDir)
      }
      Files.write(path, render.getBytes())
      ()
    }
}
