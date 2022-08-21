package zio.profiling.tracing

import java.nio.file.{Files, Paths}

import zio._
import zio.profiling.CostCenter
import zio.profiling.CostCenter._

final case class ProfilingResult(
  entries: List[ProfilingResult.Entry]
) {

  def stackCollapse: List[String] = {
    def renderLocation(location: Trace) =
      if (location == Trace.empty) "unknown_location" else location.toString

    def render(location: CostCenter): String =
      location match {
        case Root                           => ""
        case Child(Root, name)              => name
        case Child(parent, name)            => s"${render(parent)};$name"
        case TaggedLocation(Root, location) => renderLocation(location)
        case TaggedLocation(tag, location)  => s"${render(tag)};${renderLocation(location)}"
      }

    val overallTotaltime = entries.map(_.totalTime).sum
    entries.map { entry =>
      val percentage = entry.totalTime.toDouble / overallTotaltime
      s"${render(entry.location)} ${(percentage * 1000).toInt}"
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
    location: CostCenter.TaggedLocation,
    totalCalls: Long,
    totalTime: Long,
    maxTime: Long,
    averageTime: Long
  )
}
