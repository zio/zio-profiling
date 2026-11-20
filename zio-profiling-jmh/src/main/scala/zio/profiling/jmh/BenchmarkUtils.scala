package zio.profiling.jmh

import zio._
import zio.profiling.sampling.SamplingProfilerSupervisor

import java.util.concurrent.atomic.AtomicReference

/**
 * Runs ZIO effects in JMH benchmarks on the runtime of a running [[JmhZioProfiler]], so that the profiler samples them.
 * When no profiler is running, it uses `Runtime.default`.
 */
object BenchmarkUtils {

  private[jmh] val runtimeRef: AtomicReference[Runtime.Scoped[SamplingProfilerSupervisor]] = new AtomicReference()

  /**
   * Returns the sampling runtime of the running [[JmhZioProfiler]], or `Runtime.default` when no profiler is running:
   * without `-prof zio.profiling.jmh.JmhZioProfiler`, in a run with no warm-up iterations (`-wi 0`), before the first
   * warm-up iteration, and after the last measurement iteration.
   */
  def getRuntime(): Runtime[Any] = {
    val customRt = runtimeRef.get()
    if (customRt ne null) customRt else Runtime.default
  }

  /**
   * Returns the sampling supervisor of the running [[JmhZioProfiler]], or `Supervisor.none` when no profiler is
   * running.
   */
  def getSupervisor(): Supervisor[Any] = {
    val customRt = runtimeRef.get()
    if (customRt ne null) customRt.environment.get[SamplingProfilerSupervisor] else Supervisor.none
  }

  /**
   * Runs `zio` on the runtime from [[getRuntime]] and returns its value. When `zio` fails or dies, it throws a
   * `zio.FiberFailure` that holds the cause.
   */
  def unsafeRun[E, A](zio: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      getRuntime().unsafe.run(zio).getOrThrowFiberFailure()
    }

}
