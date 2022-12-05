package zio.profiling.sampling

import zio._
import zio.profiling.CostCenter

import java.nio.file.{Files, Paths}

final case class ProfilingResult(entries: List[ProfilingResult.Entry]) {

  /**
   * Render the result so it can be transformed into a flamegraph using https://github.com/brendangregg/FlameGraph.
   *
   * Example command: `flamegraph.pl profile.folded > profile.svg`
   */
  def stackCollapse: List[String] = {
    def renderCostCenter(costCenter: CostCenter): String = {
      import CostCenter._
      costCenter match {
        case Root                => ""
        case Child(Root, name)   => name
        case Child(parent, name) => s"${renderCostCenter(parent)};$name"
      }
    }

    entries.map { entry =>
      s"${renderCostCenter(entry.costCenter)} ${entry.samples}"
    }
  }

  /**
   * Convenience method to render the result using `stackCollapse` and write it to a file.
   */
  def stackCollapseToFile(pathString: String): Task[Unit] =
    ZIO.attempt {
      val path      = Paths.get(pathString)
      val parentDir = path.getParent()
      if (parentDir ne null) {
        Files.createDirectories(parentDir)
      }
      Files.write(path, stackCollapse.mkString("\n").getBytes())
      ()
    }

}

object ProfilingResult {
  final case class Entry(costCenter: CostCenter, samples: Long)
}
