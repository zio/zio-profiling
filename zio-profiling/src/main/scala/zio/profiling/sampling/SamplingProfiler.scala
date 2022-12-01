package zio.profiling.sampling

import zio.Unsafe.unsafe
import zio._
import zio.profiling.{CostCenter, TaggedLocation}

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

final case class SamplingProfiler(
  samplingPeriod: Duration = 10.millis
) {

  /**
   * Profile a program and get the resulting profile data.
   */
  def profile[R, E](zio: ZIO[R, E, Any]): ZIO[R, E, ProfilingResult] = ZIO.scoped[R] {
    supervisor.flatMap { prof =>
      zio
        .withRuntimeFlags(RuntimeFlags.enable(RuntimeFlag.OpSupervision))
        .supervised(prof)
        .zipRight(prof.value)
    }
  }

  /**
   * Create a supervisor that can be used to profile a zio program. For profiling to work correctly, OpSupervision must
   * be enabled for the effect.
   */
  def supervisor(implicit trace: Trace): URIO[Scope, Supervisor[ProfilingResult]] = ZIO.suspendSucceed {
    val fibers       = new ConcurrentHashMap[Int, FiberState]()
    val locationData = new ConcurrentHashMap[TaggedLocation, Long]()

    def isValidLocation(trace: Trace): Boolean =
      (trace ne null) && trace != Trace.empty

    val sampleFibers = ZIO.succeed {
      fibers.values().forEach { state =>
        val location = state.trace
        if (isValidLocation(location)) {
          val costCenter     = unsafe(implicit u => CostCenter.getCurrentUnsafe(state.runtime))
          val taggedLocation = costCenter #> location
          locationData.put(taggedLocation, locationData.getOrDefault(taggedLocation, 0) + 1)
          ()
        }
      }
    }.repeat(Schedule.spaced(samplingPeriod)).delay(samplingPeriod)

    val supervisor = new Supervisor[ProfilingResult] {
      def value(implicit trace: zio.Trace): UIO[ProfilingResult] =
        ZIO.succeed {
          val entries = locationData.entrySet().asScala.map { entry =>
            ProfilingResult.Entry(entry.getKey(), entry.getValue())
          }
          ProfilingResult(entries.toList)
        }

      def onStart[R, E, A](
        environment: ZEnvironment[R],
        effect: ZIO[R, E, A],
        parent: Option[Fiber.Runtime[Any, Any]],
        fiber: Fiber.Runtime[E, A]
      )(implicit unsafe: Unsafe): Unit = ()

      def onEnd[R, E, A](
        value: Exit[E, A],
        fiber: Fiber.Runtime[E, A]
      )(implicit unsafe: Unsafe): Unit = {
        fibers.remove(fiber.id.id)
        ()
      }

      override def onSuspend[E, A](fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit = {
        fibers.remove(fiber.id.id)
        ()
      }

      override def onEffect[E, A](
        fiber: Fiber.Runtime[E, A],
        effect: ZIO[_, _, _]
      )(implicit unsafe: Unsafe): Unit = {
        val id    = fiber.id.id
        var state = fibers.get(id)

        if (state eq null) {
          state = FiberState(fiber, effect.trace)
          fibers.put(id, state)
          ()
        } else {
          state.trace = effect.trace
        }
      }
    }

    sampleFibers.forkScoped.as(supervisor)
  }
}
