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
 * A causal profiler, inspired by [[https://github.com/plasma-umass/coz Coz]], that estimates how much the throughput of
 * a program would change if one part of it were faster.
 *
 * The profiler runs a series of experiments. Each experiment selects a cost center and a speedup, then delays the
 * fibers that are not in that cost center or in one nested under it, by an amount that grows with the speedup, so that
 * the selected code runs relatively faster. Throughput is the number of times
 * [[zio.profiling.causal.CausalProfiler.progressPoint]] runs during an experiment.
 *
 * Only code in a [[zio.profiling.CostCenter]] other than the root is selected, so tag the code you want to measure.
 * With the default parameters a profile takes more than five minutes; lower `warmUpPeriod`, `coolOffPeriod` and
 * `iterations` for a short run.
 *
 * {{{
 * import zio._
 * import zio.profiling.CostCenter
 *
 * val parse = CostCenter.withChildCostCenter("parse")(ZIO.succeed(Thread.sleep(5)))
 * val save  = CostCenter.withChildCostCenter("save")(ZIO.succeed(Thread.sleep(15)))
 *
 * val result: UIO[ProfilingResult] =
 *   CausalProfiler(iterations = 10, warmUpPeriod = 5.seconds)
 *     .profile((parse *> save *> CausalProfiler.progressPoint("request done")).forever)
 * }}}
 *
 * @param iterations
 *   the number of experiments to run. At least one experiment runs, even when it is `0`.
 * @param candidateSelector
 *   decides which cost centers can be selected for an experiment. It receives the last location name of a cost center:
 *   for `Root / "outer" / "slow"` it receives `"slow"`.
 * @param samplingPeriod
 *   how often the profiler runs a step. A step ends the warm-up, an experiment or a cool-off when its time is up, looks
 *   for a candidate when an experiment is pending, and during an experiment adds `speedup` times `samplingPeriod` to
 *   the delay of the other fibers for each running fiber in the selected cost center.
 * @param minExperimentDuration
 *   the planned length of the first experiment, and the shortest planned length of the later ones.
 * @param experimentTargetSamples
 *   the number of times each progress point should run during an experiment. The profiler doubles or halves the length
 *   of the next experiment to get closer to it.
 * @param warmUpPeriod
 *   the time to wait after the profile starts before the first experiment can start. The program runs during this time.
 * @param coolOffPeriod
 *   the time to wait between two experiments.
 * @param zeroSpeedupWeight
 *   how often an experiment applies no speedup, which gives a baseline: each experiment has a chance of 1 in
 *   `zeroSpeedupWeight` to get a speedup of `0`. It must be positive; with `0`, no experiment starts and the profile
 *   does not complete.
 * @param maxConsideredSpeedUp
 *   the largest speedup, as a percentage, that an experiment can select; speedups are drawn at random from 1 to this
 *   value. Values over 100 do not have any practical use. It must be positive; with `0`, the profile does not complete
 *   once an experiment misses the 1 in `zeroSpeedupWeight` draw for a speedup of `0`.
 * @param sleepPrecision
 *   the estimated precision of `Thread.sleep`. When a delay is this close to its end, the profiler busy-waits instead
 *   of sleeping, to make the delay more accurate.
 *
 * @see
 *   [[https://zio.dev/zio-profiling/ ZIO Profiling]]
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
   * Runs `zio` with the profiler attached until the profile is complete, then interrupts `zio` and returns the result.
   * `zio` must not end on its own, so wrap it in `forever`, and call
   * [[zio.profiling.causal.CausalProfiler.progressPoint]] in it to signal progress. Without progress points, the
   * experiments have no throughput data, and with a positive `experimentTargetSamples` each experiment is planned twice
   * as long as the one before.
   *
   * The first experiment starts only when a fiber runs in a cost center that `candidateSelector` accepts; until then
   * the profile does not complete. A known bug: when `zio` fails or dies, the profile does not fail with that error. If
   * this happens before the last experiment, the profile does not complete; if it happens during the last experiment,
   * the profile succeeds with the result.
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
   * Runs `zio` in a loop and profiles the loop with `profile`, with a progress point named `"iteration done"` after
   * each run. Use it when one run of `zio` is one unit of work.
   *
   * {{{
   * import zio._
   * import zio.profiling.CostCenter
   *
   * val result: UIO[ProfilingResult] =
   *   CausalProfiler(iterations = 10, warmUpPeriod = 5.seconds).profileIterations {
   *     val fast = CostCenter.withChildCostCenter("fast")(ZIO.succeed(Thread.sleep(20)))
   *     val slow = CostCenter.withChildCostCenter("slow")(ZIO.succeed(Thread.sleep(60)))
   *     fast <&> slow
   *   }
   * }}}
   */
  def profileIterations[R, E](zio: ZIO[R, E, Any])(implicit trace: Trace): ZIO[R, E, ProfilingResult] =
    profile((zio *> CausalProfiler.progressPoint("iteration done")).forever)

  /**
   * Creates the profiler as a [[zio.Supervisor]], for programs that `profile` can't wrap. Reading its `value` waits
   * until the profile is complete.
   *
   * The profiled effects must run with `RuntimeFlag.OpSupervision` enabled: without it, the profiler does not see the
   * cost center changes of a fiber after it starts, and the profile may not complete. Progress points count only in
   * fibers that inherit the profiler from the fiber that ran `supervisor`, such as the fibers it forks inside the
   * scope.
   *
   * {{{
   * import zio._
   * import zio.profiling.CostCenter
   *
   * val program: UIO[Nothing] =
   *   (CostCenter.withChildCostCenter("step")(ZIO.succeed(Thread.sleep(5))) *>
   *     CausalProfiler.progressPoint("step done")).forever
   *
   * val result: UIO[ProfilingResult] =
   *   ZIO.scoped {
   *     CausalProfiler(iterations = 5, warmUpPeriod = 1.second).supervisor.flatMap { profiler =>
   *       program
   *         .withRuntimeFlags(RuntimeFlags.enable(RuntimeFlag.OpSupervision))
   *         .supervised(profiler)
   *         .forkScoped *> profiler.value
   *     }
   *   }
   * }}}
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
                    val fiber     = iterator.next()
                    val candidate = fiber.costCenter
                    if (fiber.running && !candidate.isRoot && candidate.location.fold(false)(candidateSelector)) {
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
                  if (experiment != null) {
                    samplingState = SamplingState.ExperimentInProgress(experiment, iteration, results)
                    logMessage(
                      s"Starting experiment $iteration (costCenter: ${experiment.candidate.render}, speedUp: ${experiment.speedUp}, duration: ${experiment.duration}ns)"
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
                      if (fiber.running && fiber.costCenter.isChildOf(experiment.candidate)) {
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

            runSamplingStep
              .repeat[Any, Long](Schedule.spaced(samplingPeriod))
              .race[Any, Throwable, Any](resultPromise.await)
              .fork
              .as {
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

/**
 * Functions that mark progress and latency in a program profiled by [[zio.profiling.causal.CausalProfiler]]. They do
 * nothing when no experiment is running.
 */
object CausalProfiler {

  /**
   * Counts one visit of the progress point `name` in the running experiment. The profiler measures throughput as the
   * number of visits during each experiment.
   */
  def progressPoint(name: String)(implicit trace: Trace): UIO[Unit] =
    Tracker.globalRef.get.flatMap { tracker =>
      ZIO.succeed(tracker.progressPoint(name))
    }

  /**
   * Counts one arrival at the latency point `name` in the running experiment. Pair it with `exitLatencyPoint`, or use
   * `latencyPointScoped`.
   */
  def enterLatencyPoint(name: String)(implicit trace: Trace): UIO[Unit] =
    Tracker.globalRef.get.flatMap { tracker =>
      ZIO.succeed(tracker.enterLatencyPoint(name))
    }

  /**
   * Counts one departure from the latency point `name` in the running experiment.
   */
  def exitLatencyPoint(name: String)(implicit trace: Trace): UIO[Unit] =
    Tracker.globalRef.get.flatMap { tracker =>
      ZIO.succeed(tracker.exitLatencyPoint(name))
    }

  /**
   * Calls `enterLatencyPoint(name)` now and `exitLatencyPoint(name)` when the scope closes.
   *
   * {{{
   * import zio._
   *
   * val request: UIO[Unit] =
   *   ZIO.scoped(CausalProfiler.latencyPointScoped("request") *> ZIO.succeed(Thread.sleep(10)))
   * }}}
   */
  def latencyPointScoped(name: String)(implicit trace: Trace): URIO[Scope, Unit] =
    ZIO.acquireRelease(enterLatencyPoint(name))(_ => exitLatencyPoint(name))
}
