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

final case class ProfilingResult(experiments: List[ExperimentResult]) {

  /**
   * Render the experiment results so they can be uploaded to https://plasma-umass.org/coz/ for visualization.
   */
  lazy val render: String =
    experiments.flatMap(_.render).mkString("\n")

  /**
   * Render the experiment results and write them to file.
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
