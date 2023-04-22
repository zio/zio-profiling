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

class JmhZioProfiler(@unused initLine: String) extends InternalProfiler {
  private val outDir: File                   = new File(System.getProperty("user.dir"))
  private var trialOutDir: Option[File]      = None
  private var warmupStarted: Boolean         = false
  private var measurementStarted: Boolean    = false
  private var measurementIterationCount: Int = 0

  def this() = this("")

  def getDescription(): String =
    "zio-profiling profiler provider."

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
