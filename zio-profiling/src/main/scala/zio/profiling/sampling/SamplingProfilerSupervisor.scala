package zio.profiling.sampling

import zio.Unsafe.unsafe
import zio._
import zio.profiling.CostCenter

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import scala.collection.immutable.SortedSet
import scala.jdk.CollectionConverters._

/**
 * The [[zio.Supervisor]] that collects the samples of a [[zio.profiling.sampling.SamplingProfiler]]. It keeps the set
 * of supervised fibers that are running, and counts, for each cost center, how many times `sample` found a running
 * fiber in it. Get one from `SamplingProfiler.makeSupervisor` or from the environment of
 * `SamplingProfiler.supervisedRuntime`.
 */
final class SamplingProfilerSupervisor() extends Supervisor[ProfilingResult] {

  private val fibersRef   = new AtomicReference[SortedSet[Fiber.Runtime[Any, Any]]](SortedSet.empty)
  private val costCenters = new ConcurrentHashMap[CostCenter, Long]()

  /** Returns the samples collected so far. */
  def value(implicit trace: Trace): UIO[ProfilingResult] = ZIO.succeed(unsafeValue())

  /** Adds `fiber` to the set of running fibers. The ZIO runtime calls it when a supervised fiber starts. */
  def onStart[R, E, A](
    environment: ZEnvironment[R],
    effect: ZIO[R, E, A],
    parent: Option[Fiber.Runtime[Any, Any]],
    fiber: Fiber.Runtime[E, A]
  )(implicit unsafe: Unsafe): Unit =
    onResume(fiber)

  /** Removes `fiber` from the set of running fibers. The ZIO runtime calls it when a supervised fiber ends. */
  def onEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit =
    onSuspend(fiber)

  /** Adds `fiber` to the set of running fibers. The ZIO runtime calls it when a supervised fiber resumes. */
  override def onResume[E, A](fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit = {
    var loop = true
    while (loop) {
      val original = fibersRef.get
      val updated  = original + fiber
      loop = !fibersRef.compareAndSet(original, updated)
    }
  }

  /** Removes `fiber` from the set of running fibers. The ZIO runtime calls it when a supervised fiber suspends. */
  override def onSuspend[E, A](fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit = {
    var loop = true
    while (loop) {
      val original = fibersRef.get
      val updated  = original - fiber
      loop = !fibersRef.compareAndSet(original, updated)
    }
  }

  /** Returns the samples collected so far, without running an effect. The entries are in no particular order. */
  def unsafeValue(): ProfilingResult = {
    val entries = costCenters.entrySet().asScala.map { entry =>
      ProfilingResult.Entry(entry.getKey(), entry.getValue())
    }
    ProfilingResult(entries.toList)
  }

  /**
   * Takes one sample now: adds one to the count of the current cost center of each running fiber. The sampler that
   * `SamplingProfiler.makeSupervisor` starts calls it once per sampling period.
   */
  def sample(): Unit =
    fibersRef.get().foreach { fiber =>
      val cc = unsafe(implicit u => CostCenter.getCurrentUnsafe(fiber))
      costCenters.compute(cc, (_, v) => v + 1)
    }

  /**
   * Clears the counts collected so far. The set of running fibers is kept, so the next `sample` counts them again.
   */
  def reset(): Unit = costCenters.clear()

}
