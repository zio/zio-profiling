package zio.profiling.tracing

import zio._
import zio.profiling.CostCenter._
import zio.profiling.{CostCenter, TaggedLocation}

import java.nio.file.{Files, Paths}

final case class ProfilingResult(
  entries: List[ProfilingResult.Entry]
) {

  /**
   * Render the result so it can be transformed into a flamegraph using https://github.com/brendangregg/FlameGraph.
   *
   * Example command: `flamegraph.pl profile.folded > profile.svg`
   */
  def stackCollapse: List[String] = {
    def renderTrace(trace: Trace): String =
      if (trace == Trace.empty) "<unknown>" else trace.toString

    def renderCostCenter(costCenter: CostCenter): String =
      costCenter match {
        case Root                => ""
        case Child(Root, name)   => name
        case Child(parent, name) => s"${renderCostCenter(parent)};$name"
      }

    def renderLocation(taggedLocation: TaggedLocation) =
      if (taggedLocation.costCenter.isRoot)
        renderTrace(taggedLocation.location)
      else
        s"${renderCostCenter(taggedLocation.costCenter)};${renderTrace(taggedLocation.location)}"

    val overallTotaltime = entries.map(_.totalTimeNanos).sum
    entries.map { entry =>
      val percentage = entry.totalTimeNanos.toDouble / overallTotaltime
      s"${renderLocation(entry.location)} ${(percentage * 1000).toInt}"
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
  final case class Entry(
    location: TaggedLocation,
    totalCalls: Long,
    totalTimeNanos: Long,
    maxTimeNanos: Long,
    averageTimeNanos: Long
  )
}
