package zio.profiling.jmh

import zio._
import zio.profiling.sampling.SamplingProfilerSupervisor

import java.util.concurrent.atomic.AtomicReference

object BenchmarkUtils {

  private[jmh] val runtimeRef: AtomicReference[Runtime.Scoped[SamplingProfilerSupervisor]] = new AtomicReference()

  def getRuntime(): Runtime[Any] = {
    val customRt = runtimeRef.get()
    if (customRt ne null) customRt else Runtime.default
  }

  def getSupervisor(): Supervisor[Any] = {
    val customRt = runtimeRef.get()
    if (customRt ne null) customRt.environment.get[SamplingProfilerSupervisor] else Supervisor.none
  }

  def unsafeRun[E, A](zio: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      getRuntime().unsafe.run(zio).getOrThrowFiberFailure()
    }

}
