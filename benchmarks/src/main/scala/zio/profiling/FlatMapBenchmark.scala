package zio.profiling

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio._
import zio.profiling.sampling.SamplingProfiler

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class FlatMapBenchmark {
  @Param(Array("1000"))
  var size: Int = _

  private[this] val testEffect = {
    def loop(i: Int): UIO[Int] =
      if (i < size) ZIO.succeed[Int](i + 1).flatMap(loop)
      else ZIO.succeed(i)

    ZIO.succeed(0).flatMap[Any, Nothing, Int](loop).unit
  }

  @Benchmark
  def zioFlatMap(): Unit = BenchmarkUtil.unsafeRun(testEffect)

  @Benchmark
  def zioFlatMapOpSupervision(): Unit =
    BenchmarkUtil.unsafeRun(testEffect.withRuntimeFlags(RuntimeFlags.enable(RuntimeFlag.OpSupervision)))

  @Benchmark
  def zioFlatMapProfiledSampling(): Unit = BenchmarkUtil.unsafeRun(SamplingProfiler(10.millis).profile(testEffect).unit)

}
