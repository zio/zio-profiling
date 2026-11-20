package zio.profiling.jmh

import org.openjdk.jmh.infra.{BenchmarkParams, IterationParams}
import org.openjdk.jmh.profile.InternalProfiler
import org.openjdk.jmh.results.{IterationResult, Result, TextResult}
import org.openjdk.jmh.runner.IterationType
import zio._
import zio.profiling.sampling.SamplingProfiler

import java.io.{File, PrintWriter, StringWriter}
import java.lang.System
import java.nio.file.Files
import java.util.Collections
import java.{util => ju}
import scala.annotation.unused

/**
 * A JMH profiler that samples the ZIO effects of a benchmark with a sampling profiler and writes the result to a file,
 * one file per benchmark run. Pass its fully qualified name to JMH's `-prof` option. With the sbt-jmh plugin:
 *
 * {{{
 * // no-check: sbt build setting, not Scala code
 * // project/plugins.sbt
 * addSbtPlugin("com.github.sbt" % "sbt-jmh" % "0.4.9")
 *
 * // build.sbt
 * enablePlugins(JmhPlugin)
 * libraryDependencies += "dev.zio" %% "zio-profiling-jmh" % "<version>"
 *
 * // sbt shell
 * Jmh/run -wi 3 -i 5 -f 1 -prof zio.profiling.jmh.JmhZioProfiler bench.SumBenchmarks
 * }}}
 *
 * Only effects that run on the profiler's runtime are sampled, so run them with [[BenchmarkUtils.unsafeRun]]:
 *
 * {{{
 * import org.openjdk.jmh.annotations.{Benchmark, Scope, State}
 * import zio.{Scope => _, _}
 * import zio.profiling.jmh.BenchmarkUtils
 *
 * @State(Scope.Thread)
 * class SumBenchmarks {
 *   private val numbers: List[Int] = (1 to 1000).toList
 *
 *   private def doubledSum: UIO[Int] =
 *     ZIO.foreach(numbers)(n => ZIO.succeed(n * 2)).map(_.sum)
 *
 *   @Benchmark
 *   def sumWithZio(): Int = BenchmarkUtils.unsafeRun(doubledSum)
 * }
 * }}}
 *
 * Sampling starts in the first warm-up iteration, and the samples taken during warm-up are dropped when the first
 * measurement iteration starts. After the last measurement iteration, the profiler writes the samples to
 * `profile.folded`, in the folded-stack format of the sampling profiler's `ProfilingResult.stackCollapse`, and shuts
 * its runtime down. The file is in a directory named after the JMH benchmark id (benchmark, mode and parameters, for
 * example `bench.SumBenchmarks.sumWithZio-Throughput` or `bench.SumBenchmarks.sumWithZio-Throughput-size-100`), inside
 * the working directory of the JVM that runs the benchmark. A run with no warm-up iterations (`-wi 0`) creates the
 * directory but writes no file.
 *
 * The samples are grouped by cost center. Effects that are not tagged, by hand with
 * `zio.profiling.CostCenter.withChildCostCenter` or by the `zio-profiling-tagging-plugin` compiler plugin, share one
 * line with no name.
 *
 * @param initLine
 *   the option text that follows `:` in `-prof zio.profiling.jmh.JmhZioProfiler:<options>`; it is ignored
 * @see
 *   [[https://zio.dev/zio-profiling/ ZIO Profiling]]
 */
class JmhZioProfiler(@unused initLine: String) extends InternalProfiler {
  private val outDir: File                   = new File(System.getProperty("user.dir"))
  private var trialOutDir: Option[File]      = None
  private var warmupStarted: Boolean         = false
  private var measurementStarted: Boolean    = false
  private var measurementIterationCount: Int = 0

  /** Creates a profiler with an empty option text. */
  def this() = this("")

  /** Returns `"zio-profiling profiler provider."`, the text that JMH's `-lprof` option lists for this profiler. */
  def getDescription(): String =
    "zio-profiling profiler provider."

  /**
   * Called by JMH before each iteration. The first call creates the output directory of the benchmark run. The first
   * warm-up iteration starts the sampling runtime that [[BenchmarkUtils]] then uses, and the first measurement
   * iteration drops the samples taken during warm-up.
   */
  def beforeIteration(benchmarkParams: BenchmarkParams, iterationParams: IterationParams): Unit = {
    if (trialOutDir.isEmpty) {
      createTrialOutDir(benchmarkParams);
    }

    if (iterationParams.getType() == IterationType.WARMUP) {
      if (!warmupStarted) {
        start()
        warmupStarted = true
      }
    }

    if (iterationParams.getType() == IterationType.MEASUREMENT) {
      if (!measurementStarted) {
        if (warmupStarted) {
          reset()
        }
        measurementStarted = true
      }
    }
  }

  /**
   * Called by JMH after each iteration. After the last measurement iteration, it writes `profile.folded` and shuts the
   * sampling runtime down, and [[BenchmarkUtils]] goes back to `Runtime.default`.
   *
   * Returns an empty collection, so JMH prints no result for this profiler. A known bug: the result that names the
   * written file is built and then dropped.
   */
  def afterIteration(
    benchmarkParams: BenchmarkParams,
    iterationParams: IterationParams,
    result: IterationResult
  ): ju.Collection[_ <: Result[_]] = {
    if (iterationParams.getType() == IterationType.MEASUREMENT) {
      measurementIterationCount += 1
      if (measurementIterationCount == iterationParams.getCount()) {
        Collections.singletonList(stopAndDump());
      }
    }

    Collections.emptyList();
  }

  private def createTrialOutDir(benchmarkParams: BenchmarkParams): Unit = {
    val fileName = benchmarkParams.id().replace("%", "_")
    trialOutDir = Some(new File(outDir, fileName))
    trialOutDir.foreach(_.mkdirs())
  }

  private def start(): Unit = Unsafe.unsafe { implicit unsafe =>
    BenchmarkUtils.runtimeRef.set(SamplingProfiler().supervisedRuntime)
  }

  private def stopAndDump(): TextResult = {
    val sw = new StringWriter()
    val pw = new PrintWriter(sw)
    val rt = BenchmarkUtils.runtimeRef.getAndSet(null)

    if (rt ne null) {
      val supervisor = rt.environment.get
      val resultPath = writeFile("profile.folded", supervisor.unsafeValue().stackCollapse.mkString("\n"))
      Unsafe.unsafe(implicit u => rt.unsafe.shutdown())

      pw.println("zio-profiler results:")
      pw.println(s"  ${resultPath}")
    }

    pw.flush();
    pw.close();
    new TextResult(sw.toString(), "zio-profiling")
  }

  private def reset(): Unit = Unsafe.unsafe { implicit unsafe =>
    val runtime = BenchmarkUtils.runtimeRef.get()
    if (runtime ne null) {
      runtime.environment.get.reset()
    }
  }

  private def writeFile(name: String, content: String) = {
    val out = new File(trialOutDir.get, name)
    Files.write(out.toPath(), content.getBytes())
  }

}
