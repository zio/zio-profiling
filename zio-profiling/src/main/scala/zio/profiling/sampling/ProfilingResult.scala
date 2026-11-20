package zio.profiling.sampling

import zio._
import zio.profiling.CostCenter

import java.nio.file.{Files, Paths}

/**
 * The result of a sampling profile: one [[zio.profiling.sampling.ProfilingResult.Entry]] for each cost center that was
 * sampled at least once, in no particular order.
 *
 * {{{
 * import zio._
 * import zio.profiling.CostCenter
 *
 * val work: UIO[Unit] = CostCenter.withChildCostCenter("work")(ZIO.succeed(Thread.sleep(100)))
 *
 * val saved: Task[Unit] =
 *   SamplingProfiler().profile(work).flatMap(_.stackCollapseToFile("target/profile.folded"))
 * }}}
 *
 * @see
 *   [[https://zio.dev/zio-profiling/ ZIO Profiling]]
 */
final case class ProfilingResult(entries: List[ProfilingResult.Entry]) {

  /**
   * Renders one line for each entry, in the folded-stack format that
   * [[https://github.com/brendangregg/FlameGraph FlameGraph]] turns into a flame graph: the rendered cost center, a
   * space, and the number of samples. The root cost center renders as an empty path, so its line starts with a space.
   *
   * Example command: `flamegraph.pl profile.folded > profile.svg`
   *
   * {{{
   * import zio.profiling.CostCenter
   *
   * val result = ProfilingResult(
   *   List(
   *     ProfilingResult.Entry(CostCenter.Root / "orders" / "parse", 60),
   *     ProfilingResult.Entry(CostCenter.Root, 3)
   *   )
   * )
   * result.stackCollapse // List("orders;parse 60", " 3")
   * }}}
   */
  def stackCollapse: List[String] =
    entries.map(entry => s"${entry.costCenter.render} ${entry.samples}")

  /**
   * Writes the lines of `stackCollapse` to the file at `pathString`, separated by `\n` with no newline at the end. It
   * creates missing parent directories and replaces an existing file. The text is encoded with the platform's default
   * charset.
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

/** Holds [[zio.profiling.sampling.ProfilingResult.Entry]]. */
object ProfilingResult {

  /** The number of samples that found a running fiber in `costCenter`. */
  final case class Entry(costCenter: CostCenter, samples: Long)
}
