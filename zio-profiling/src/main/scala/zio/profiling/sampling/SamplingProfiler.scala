package zio.profiling.sampling

import zio.Unsafe.unsafe
import zio._
import zio.profiling.CostCenter

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import scala.collection.immutable.SortedSet
import scala.jdk.CollectionConverters._

final case class SamplingProfiler(
  samplingPeriod: Duration = 10.millis
) {

  /**
   * Profile a program and get the resulting profile data.
   */
  def profile[R, E](zio: ZIO[R, E, Any]): ZIO[R, E, ProfilingResult] = ZIO.scoped[R] {
    for {
      supervisor <- makeSupervisor
      _          <- (zio.forkScoped).supervised(supervisor).flatMap(_.join)
      result     <- supervisor.value
    } yield result
  }

  val makeSupervisor: URIO[Scope, Supervisor[ProfilingResult]] = {
    val fibersRef   = new AtomicReference[SortedSet[Fiber.Runtime[Any, Any]]](SortedSet.empty)
    val costCenters = new ConcurrentHashMap[CostCenter, Long]()

    val sampleFibers = ZIO.succeed {
      fibersRef.get().foreach { fiber =>
        val cc = unsafe(implicit u => CostCenter.getCurrentUnsafe(fiber))
        costCenters.put(cc, costCenters.getOrDefault(cc, 0) + 1)
      }
    }

    val supervisor = new Supervisor[ProfilingResult] {
      def value(implicit trace: Trace): UIO[ProfilingResult] = ZIO.succeed {
        val entries = costCenters.entrySet().asScala.map { entry =>
          ProfilingResult.Entry(entry.getKey(), entry.getValue())
        }
        ProfilingResult(entries.toList)
      }

      def onStart[R, E, A](
        environment: ZEnvironment[R],
        effect: ZIO[R, E, A],
        parent: Option[Fiber.Runtime[Any, Any]],
        fiber: Fiber.Runtime[E, A]
      )(implicit unsafe: Unsafe): Unit =
        onResume(fiber)

      def onEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit =
        onSuspend(fiber)

      override def onResume[E, A](fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit = {
        var loop = true
        while (loop) {
          val original = fibersRef.get
          val updated  = original + fiber
          loop = !fibersRef.compareAndSet(original, updated)
        }
      }

      override def onSuspend[E, A](fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit = {
        var loop = true
        while (loop) {
          val original = fibersRef.get
          val updated  = original - fiber
          loop = !fibersRef.compareAndSet(original, updated)
        }
      }
    }

    sampleFibers.repeat(Schedule.spaced(samplingPeriod)).delay(samplingPeriod).forkScoped.as(supervisor)
  }
}
