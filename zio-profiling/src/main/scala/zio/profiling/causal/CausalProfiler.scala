/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.profiling.causal

import zio.Unsafe.unsafe
import zio._

import java.util.concurrent.ConcurrentHashMap

/**
 * A causal profiler inspired by https://github.com/plasma-umass/coz, that can be used to instrument code and
 * automatically figure out which areas should be optimized to increase overall throughput.
 *
 * The profiler will periodically select regions of code for optimization and will artifically speed them up by slowing
 * down all code that runs concurrently with it. It will determine throughput based on the number of times that
 * [[zio.profiling.causal.CausalProfiler.progressPoint]] has been invoked during an experiment.
 *
 * Only [[zio.profiling.CostCenter]] will be considered for optimization, so make sure to tag the code.
 *
 * @param iterations
 *   the number of experiments to perform
 * @param candidateSelector
 *   when a new experiment is about to be performed, this is used to decide on a candidate for optimization
 * @param samplingPeriod
 *   how often data about fiber locations gets collected. Lower values increase the overhead of the profiler.
 * @param minExperimentDuration
 *   minimum duration of an experiment
 * @param experimentTargetSamples
 *   number of times every progress point should be invoked during an experiment. will be used to adjust duration of the
 *   next experiment.
 * @param warmUpPeriod
 *   period of time to delay start of the profiler.
 * @param coolOffPeriod
 *   period of time to wait between experiments.
 * @param zeroSpeedupWeight
 *   rate of selecting no speedup for the experiment. A `zeroSpeedupWeight` of n means that that every nth experiment
 *   will have no speedup applied.
 * @param maxConsideredSpeedUp
 *   maximum speedup percentage that will be selected for experiments. Values over 100 do not have any practical use.
 * @param sleepPrecision
 *   estimated precision of `Thread.sleep`. The profiler will busy spin once it enters this proximity to the target time
 *   in order to increase accuraccy.
 */
final case class CausalProfiler(
  iterations: Int = 100,
  candidateSelector: String => Boolean = _ => true,
  samplingPeriod: Duration = 20.millis,
  minExperimentDuration: Duration = 1.second,
  experimentTargetSamples: Int = 30,
  warmUpPeriod: Duration = 30.seconds,
  coolOffPeriod: Duration = 2.seconds,
  zeroSpeedupWeight: Int = 10,
  maxConsideredSpeedUp: Int = 100,
  sleepPrecision: Duration = 10.millis
) {

  private val sleepPrecisionNanos        = sleepPrecision.toNanos()
  private val warmUpPeriodNanos          = warmUpPeriod.toNanos()
  private val coolOffPeriodNanos         = coolOffPeriod.toNanos()
  private val samplingPeriodNanos        = samplingPeriod.toNanos()
  private val minExperimentDurationNanos = minExperimentDuration.toNanos()

  /**
   * Profile a program and get the result. The program must run long enough for the profiler to complete and will be
   * interrupted once the profiling is done.
   *
   * The profiled program must use the `progressPoint` function to signal progress to the profiler.
   */
  def profile[R, E](zio: ZIO[R, E, Nothing])(implicit trace: Trace): ZIO[R, E, ProfilingResult] =
    ZIO.scoped[R] {
      supervisor.flatMap { prof =>
        zio
          .withRuntimeFlags(RuntimeFlags.enable(RuntimeFlag.OpSupervision))
          .supervised(prof)
          .raceEither(prof.value)
          .flatMap(
            _.fold(
              _ => ZIO.dieMessage("Program completed before profiler could collect sufficient data"),
              ZIO.succeed(_)
            )
          )
      }
    }

  /**
   * Profile a zio program by running it in a loop and using completion of an iteration as the progress metric.
   *
   * {{{
   * profileIterations {
   *   val fast = ZIO.succeed(Thread.sleep(20)) <# "fast"
   *   val slow = ZIO.succeed(Thread.sleep(60)) <# "slow"
   *   fast <&> slow
   * }
   * }}}
   */
  def profileIterations[R, E](zio: ZIO[R, E, Any])(implicit trace: Trace): ZIO[R, E, ProfilingResult] =
    profile((zio *> CausalProfiler.progressPoint("iteration done")).forever)

  /**
   * Create a supervisor that can be used to profile a zio program. Getting the `value` of the supervisor will suspend
   * until profiling to complete. For profiling to work correctly, OpSupervision must be enabled for the effect.
   */
  def supervisor(implicit trace: Trace): ZIO[Scope, Nothing, Supervisor[ProfilingResult]] =
    ZIO.withFiberRuntime[Scope, Nothing, Supervisor[ProfilingResult]] { case (runtime, _) =>
      ZIO.suspendSucceed {

        val startTime = java.lang.System.nanoTime()

        val fibers = new ConcurrentHashMap[Int, FiberState]()

        @volatile
        var samplingState: SamplingState = SamplingState.Warmup(startTime + warmUpPeriodNanos)

        @volatile
        var globalDelay = 0L

        def logMessage(msg: => String) =
          unsafe { implicit u: Unsafe =>
            runtime.log(() => msg, Cause.empty, ZIO.someInfo, trace)
          }

        def delayFiber(state: FiberState): Unit =
          samplingState match {
            case SamplingState.Done =>
              ()
            case SamplingState.ExperimentInProgress(_, _, _) =>
              val delay = globalDelay - state.localDelay.get()
              if (delay > 0) {
                val actualSleep = sleepNanos(delay)
                state.localDelay.addAndGet(actualSleep)
                ()
              }
            case _ =>
              state.localDelay.set(globalDelay)
          }

        val tracker = new Tracker {
          def progressPoint(name: String): Unit =
            samplingState match {
              case SamplingState.ExperimentInProgress(experiment, _, _) =>
                experiment.addProgressPointMeasurement(name)
              case _ =>
                ()
            }

          def enterLatencyPoint(name: String): Unit =
            samplingState match {
              case SamplingState.ExperimentInProgress(experiment, _, _) =>
                experiment.addLatencyPointArrival(name)
              case _ =>
                ()
            }

          def exitLatencyPoint(name: String): Unit =
            samplingState match {
              case SamplingState.ExperimentInProgress(experiment, _, _) =>
                experiment.addLatencyPointDeparture(name)
              case _ =>
                ()
            }

        }

        logMessage(s"Warming up for ${warmUpPeriodNanos}ns")

        Tracker.globalRef.locallyScoped(tracker).zipRight {
          Promise.make[Nothing, ProfilingResult].flatMap { resultPromise =>
            val runSamplingStep = ZIO.succeed {
              val now = java.lang.System.nanoTime()
              samplingState match {
                case SamplingState.Warmup(until) =>
                  if (now >= until) {
                    samplingState = SamplingState.ExperimentPending(1, Nil)
                  }

                case SamplingState.ExperimentPending(iteration, results) =>
                  var experiment = null.asInstanceOf[Experiment]
                  val iterator   = fibers.values().iterator()

                  while (experiment == null && iterator.hasNext()) {
                    val fiber = iterator.next()
                    if (fiber.running) {
                      fiber.costCenter.location.filter(candidateSelector).foreach { candidate =>
                        results match {
                          case previous :: _ =>
                            // with a speedup of 100%, we expect the program to take twice as long
                            def compensateSpeedup(original: Int): Int = (original * (2 - previous.speedup)).toInt

                            val minDelta =
                              if (previous.throughputData.isEmpty) 0
                              else compensateSpeedup(previous.throughputData.map(_.delta).min)

                            val nextDuration =
                              if (minDelta < experimentTargetSamples) {
                                previous.duration * 2
                              } else if (
                                minDelta >= experimentTargetSamples * 2 && previous.duration >= minExperimentDurationNanos * 2
                              ) {
                                previous.duration / 2
                              } else {
                                previous.duration
                              }

                            experiment = new Experiment(
                              candidate,
                              now,
                              nextDuration,
                              selectSpeedUp(),
                              new ConcurrentHashMap[String, Int](),
                              new ConcurrentHashMap[String, Int](),
                              new ConcurrentHashMap[String, Int]()
                            )
                          case Nil =>
                            experiment = new Experiment(
                              candidate,
                              now,
                              minExperimentDurationNanos,
                              selectSpeedUp(),
                              new ConcurrentHashMap[String, Int](),
                              new ConcurrentHashMap[String, Int](),
                              new ConcurrentHashMap[String, Int]()
                            )
                        }
                      }
                    }
                  }
                  if (experiment != null) {
                    samplingState = SamplingState.ExperimentInProgress(experiment, iteration, results)
                    logMessage(
                      s"Starting experiment $iteration (costCenter: ${experiment.candidate}, speedUp: ${experiment.speedUp}, duration: ${experiment.duration}ns)"
                    )
                  }

                case SamplingState.ExperimentInProgress(experiment, iteration, results) =>
                  if (now >= experiment.endTime) {
                    val result = experiment.toResult()
                    if (iteration >= iterations) {
                      unsafe { implicit u: Unsafe =>
                        resultPromise.unsafe.done(ZIO.succeed(ProfilingResult(result :: results)))
                      }
                      samplingState = SamplingState.Done
                      logMessage(s"Profiling done. Total duration: ${now - startTime}ns")
                    } else {
                      samplingState = SamplingState.CoolOff(now + coolOffPeriodNanos, iteration, result :: results)
                    }
                  } else {
                    val iterator = fibers.values.iterator()
                    while (iterator.hasNext()) {
                      val fiber = iterator.next()
                      if (fiber.running && fiber.costCenter.hasParent(experiment.candidate)) {
                        val delayAmount = (experiment.speedUp * samplingPeriodNanos).toLong
                        fiber.localDelay.addAndGet(delayAmount)
                        globalDelay += delayAmount
                        experiment.trackDelay(delayAmount)
                      }
                    }
                  }

                case SamplingState.CoolOff(until, iteration, results) =>
                  if (now >= until) {
                    samplingState = SamplingState.ExperimentPending(iteration + 1, results)
                  }

                case SamplingState.Done =>
                  () // nothing to do
              }
            }

            runSamplingStep.repeat[Any, Long](Schedule.spaced(samplingPeriod)).race(resultPromise.await).fork.as {
              new Supervisor[ProfilingResult] {

                // first event in fiber lifecycle. No previous events to consider.
                override def onStart[R, E, A](
                  environment: ZEnvironment[R],
                  effect: ZIO[R, E, A],
                  parent: Option[Fiber.Runtime[Any, Any]],
                  fiber: Fiber.Runtime[E, A]
                )(implicit unsafe: zio.Unsafe): Unit = {
                  val parentDelay =
                    parent.flatMap(f => Option(fibers.get(f.id)).map(_.localDelay.get())).getOrElse(globalDelay)

                  fibers.put(fiber.id.id, FiberState.makeFor(fiber, parentDelay))
                  ()
                }

                // previous events could be {onStart, onResume, onEffect}
                override def onEnd[R, E, A](
                  value: Exit[E, A],
                  fiber: Fiber.Runtime[E, A]
                )(implicit unsafe: zio.Unsafe): Unit = {
                  val id    = fiber.id.id
                  val state = fibers.get(id)
                  if (state ne null) {
                    state.running = false
                    // no need to refresh tag as we are shutting down
                    if (!value.isInterrupted) {
                      delayFiber(state)
                    }
                    fibers.remove(id)
                    ()
                  }
                }

                // previous events could be {onStart, onResume, onEffect}
                override def onEffect[E, A](fiber: Fiber.Runtime[E, A], effect: ZIO[_, _, _])(implicit
                  unsafe: zio.Unsafe
                ): Unit = {
                  val id         = fiber.id.id
                  var state      = fibers.get(id)
                  var freshState = false

                  if (state == null) {
                    state = FiberState.makeFor(fiber, globalDelay)
                    fibers.put(id, state)
                    freshState = true
                  } else if (state.lastEffectWasStateful) {
                    state.refreshCostCenter(fiber)
                    state.lastEffectWasStateful = false
                  }

                  effect match {
                    case ZIO.Stateful(_, _) =>
                      state.lastEffectWasStateful = true
                    case ZIO.Sync(_, _) =>
                      if (!freshState) {
                        state.running = false
                        delayFiber(state)
                        state.running = true
                      }
                    case ZIO.Async(_, _, _) =>
                      state.preAsyncGlobalDelay = globalDelay
                      state.inAsync = true
                    case _ =>
                      ()
                  }
                }

                // previous events could be {onStart, onResume, onEffect}
                override def onSuspend[E, A](fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit = {
                  val id    = fiber.id.id
                  val state = fibers.get(id)
                  if (state ne null) {
                    if (state.lastEffectWasStateful) {
                      state.lastEffectWasStateful = false
                      state.refreshCostCenter(fiber)
                    }
                    state.running = false
                  } else {
                    val newState = FiberState.makeFor(fiber, globalDelay)
                    newState.running = false
                    fibers.put(id, newState)
                    ()
                  }
                }

                // previous event can only be onSuspend
                override def onResume[E, A](fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit = {
                  val id    = fiber.id.id
                  val state = fibers.get(id)
                  if (state ne null) {
                    state.running = true
                    if (state.inAsync) {
                      state.localDelay.addAndGet(globalDelay - state.preAsyncGlobalDelay)
                      state.preAsyncGlobalDelay = 0
                      state.inAsync = false
                    }
                  } else {
                    fibers.put(id, FiberState.makeFor(fiber, globalDelay))
                    ()
                  }
                }

                def value(implicit trace: Trace): UIO[ProfilingResult] = resultPromise.await
              }
            }
          }
        }
      }
    }

  private def selectSpeedUp(): Float =
    if (scala.util.Random.nextInt(zeroSpeedupWeight) == 0) 0L
    else (scala.util.Random.nextInt(maxConsideredSpeedUp) + 1).toFloat / 100

  // http://andy-malakov.blogspot.com/2010/06/alternative-to-threadsleep.html
  private def sleepNanos(nanoDuration: Long): Long = {
    val end      = java.lang.System.nanoTime() + nanoDuration
    var timeLeft = nanoDuration

    while (timeLeft > 0) {
      if (Thread.interrupted()) {
        throw new InterruptedException()
      } else if (timeLeft > sleepPrecisionNanos) {
        Thread.sleep((timeLeft - sleepPrecisionNanos).nanos.toMillis);
      } else {
        // busy spin
      }
      timeLeft = end - java.lang.System.nanoTime()
    }
    nanoDuration - timeLeft
  }
}

object CausalProfiler {

  def progressPoint(name: String)(implicit trace: Trace): UIO[Unit] =
    Tracker.globalRef.get.flatMap { tracker =>
      ZIO.succeed(tracker.progressPoint(name))
    }

  def enterLatencyPoint(name: String)(implicit trace: Trace): UIO[Unit] =
    Tracker.globalRef.get.flatMap { tracker =>
      ZIO.succeed(tracker.enterLatencyPoint(name))
    }

  def exitLatencyPoint(name: String)(implicit trace: Trace): UIO[Unit] =
    Tracker.globalRef.get.flatMap { tracker =>
      ZIO.succeed(tracker.exitLatencyPoint(name))
    }

  def latencyPointScoped(name: String)(implicit trace: Trace): URIO[Scope, Unit] =
    ZIO.acquireRelease(enterLatencyPoint(name))(_ => exitLatencyPoint(name))
}
