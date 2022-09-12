package zio.profiling.tracing

import zio.profiling.TaggedLocation
import zio.{UIO, _}

import java.lang.System.nanoTime
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

object TracingProfiler {

  def profile[R, E](zio: ZIO[R, E, Any]): ZIO[R, E, ProfilingResult] =
    supervisor.flatMap { prof =>
      zio
        .withRuntimeFlags(RuntimeFlags.enable(RuntimeFlag.OpSupervision))
        .supervised(prof)
        .zipRight(prof.value)
    }

  def supervisor(implicit trace: Trace): UIO[Supervisor[ProfilingResult]] = ZIO.succeed {
    val fibers       = new ConcurrentHashMap[FiberId, FiberState]()
    val locationData = new ConcurrentHashMap[TaggedLocation, LocationData]()

    def recordCall(location: TaggedLocation, duration: Long): Unit = {
      val data = locationData.get(location)
      if (data != null) {
        data.recordCall(duration)
      } else {
        locationData.put(location, LocationData.fromSingleCall(duration))
        ()
      }
    }
    new Supervisor[ProfilingResult] {
      def value(implicit trace: zio.Trace): UIO[ProfilingResult] =
        ZIO.succeed {
          val entries = locationData.entrySet().asScala.map { entry =>
            val data          = entry.getValue()
            val numberOfCalls = data.numberOfCalls.get()
            val totalTime     = data.totalTime.get()
            val maxTime       = data.maxTime.get()
            ProfilingResult.Entry(
              entry.getKey,
              numberOfCalls,
              totalTime,
              maxTime,
              totalTime / numberOfCalls
            )
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
        val state = fibers.get(fiber.id)
        if ((state ne null) && (state.location ne null)) {
          recordCall(state.taggedLocation, nanoTime() - state.timestamp)
          fibers.remove(fiber.id)
          ()
        }
      }

      override def onSuspend[E, A](fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit = {
        val state = fibers.get(fiber.id)
        if ((state ne null) && (state.location ne null)) {
          recordCall(state.taggedLocation, nanoTime() - state.timestamp)
          state.location = null.asInstanceOf[Trace]
          ()
        }
      }

      override def onEffect[E, A](
        fiber: Fiber.Runtime[E, A],
        effect: ZIO[_, _, _]
      )(implicit unsafe: Unsafe): Unit = {
        var state = fibers.get(fiber.id)

        if (state eq null) {
          state = FiberState.makeFor(fiber)
          fibers.put(fiber.id, state)
          ()
        } else if (state.location ne null) {
          recordCall(state.taggedLocation, nanoTime() - state.timestamp)
          ()
        }

        if (state.lastEffectWasStateful) {
          state.refreshCostCenter(fiber)
          state.lastEffectWasStateful = false
        }

        effect match {
          case ZIO.Stateful(_, _) =>
            state.lastEffectWasStateful = true
          case _ => ()
        }

        state.location = effect.trace
        state.timestamp = nanoTime()
      }
    }
  }
}
