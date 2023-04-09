package zio.profiling.sampling

import zio.Unsafe.unsafe
import zio._
import zio.profiling.CostCenter

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import scala.collection.immutable.SortedSet
import scala.jdk.CollectionConverters._

final class SamplingProfilerSupervisor() extends Supervisor[ProfilingResult] {

  private val fibersRef   = new AtomicReference[SortedSet[Fiber.Runtime[Any, Any]]](SortedSet.empty)
  private val costCenters = new ConcurrentHashMap[CostCenter, Long]()

  def value(implicit trace: Trace): UIO[ProfilingResult] = ZIO.succeed(unsafeValue())

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

  def unsafeValue(): ProfilingResult = {
    val entries = costCenters.entrySet().asScala.map { entry =>
      ProfilingResult.Entry(entry.getKey(), entry.getValue())
    }
    ProfilingResult(entries.toList)
  }

  /**
   * Impure api. Run a single sampling step.
   */
  def sample(): Unit =
    fibersRef.get().foreach { fiber =>
      val cc = unsafe(implicit u => CostCenter.getCurrentUnsafe(fiber))
      costCenters.compute(cc, (_, v) => v + 1)
    }

  /**
   * Impure api. Reset all collected profiling data
   */
  def reset(): Unit = costCenters.clear()

}
