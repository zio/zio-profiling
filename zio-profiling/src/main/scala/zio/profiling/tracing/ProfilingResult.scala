package zio.profiling.tracing

import zio._
import zio.profiling.CostCenter._
import zio.profiling.{CostCenter, TaggedLocation}

import java.nio.file.{Files, Paths}

final case class ProfilingResult(
  entries: List[ProfilingResult.Entry]
) {

  def stackCollapse: List[String] = {
    def renderTrace(trace: Trace) =
      if (trace == Trace.empty) "<unknown_location>" else trace.toString

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

    val overallTotaltime = entries.map(_.totalTime).sum
    entries.map { entry =>
      val percentage = entry.totalTime.toDouble / overallTotaltime
      s"${renderLocation(entry.location)} ${(percentage * 1000).toInt}"
    }
  }

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
    totalTime: Long,
    maxTime: Long,
    averageTime: Long
  )
}
