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

import java.util.concurrent.ConcurrentHashMap

import zio._
import zio.Unsafe.unsafe

object CausalProfiler {
  final class ProfilePartiallyApplied(config: ProfilerConfig) {
    def apply[R, E](zio: ZIO[R, E, Any])(implicit trace: Trace): ZIO[R, E, Result] =
      ZIO.scoped[R] {
        supervisor(config).flatMap { prof =>
          zio
            .withRuntimeFlags(RuntimeFlags.enable(RuntimeFlag.OpSupervision))
            .supervised(prof)
            .fork
            .zipRight(prof.value)
        }
      }
  }

  def profile[R, E](config: ProfilerConfig): ProfilePartiallyApplied =
    new ProfilePartiallyApplied(config)

  def supervisor(config: ProfilerConfig)(implicit trace: Trace): ZIO[Scope, Nothing, Supervisor[Result]] = {
    import config._

    val sleepPrecisionNanos        = sleepPrecision.toNanos()
    val warmUpPeriodNanos          = warmUpPeriod.toNanos()
    val coolOffPeriodNanos         = coolOffPeriod.toNanos()
    val samplingPeriodNanos        = samplingPeriod.toNanos()
    val minExperimentDurationNanos = minExperimentDuration.toNanos()

    val startTime = java.lang.System.nanoTime()

    val fibers = new ConcurrentHashMap[FiberId, FiberState]()

    @volatile
    var samplingState: SamplingState = SamplingState.Warmup(startTime + warmUpPeriodNanos)

    @volatile
    var globalDelay = 0L

    def delayFiber(state: FiberState): Unit =
      samplingState match {
        case SamplingState.Done                          =>
          ()
        case SamplingState.ExperimentInProgress(_, _, _) =>
          val delay = globalDelay - state.localDelay.get()
          if (delay > 0) {
            val actualSleep = sleepNanos(delay)
            state.localDelay.addAndGet(actualSleep)
            ()
          }
        case _                                           =>
          state.localDelay.set(globalDelay)
      }

    def selectSpeedUp(): Float =
      if (scala.util.Random.nextInt(zeroSpeedupWeight) == 0) 0L
      else (scala.util.Random.nextInt(maxConsideredSpeedUp) + 1).toFloat / 100

    // http://andy-malakov.blogspot.com/2010/06/alternative-to-threadsleep.html
    def sleepNanos(nanoDuration: Long): Long = {
      val end      = java.lang.System.nanoTime() + nanoDuration
      var timeLeft = nanoDuration

      while (timeLeft > 0) {
        if (Thread.interrupted()) {
          throw new InterruptedException()
        } else if (timeLeft > sleepPrecisionNanos) {
          Thread.sleep((timeLeft - sleepPrecisionNanos).nanos.toMillis);
        }
        // busy spin
        timeLeft = end - java.lang.System.nanoTime()
      }
      nanoDuration - timeLeft
    }

    val tracker = new Tracker {
      def progressPoint(name: String): Unit =
        samplingState match {
          case SamplingState.ExperimentInProgress(experiment, _, _) =>
            experiment.addProgressPointMeasurement(name)
          case _                                                    =>
            ()
        }
    }

    GlobalTrackerRef.locallyScoped(tracker).zipRight {
      Promise.make[Nothing, Result].flatMap { valuePromise =>
        val runSamplingStep = ZIO.suspendSucceed {
          val now = java.lang.System.nanoTime()
          samplingState match {
            case SamplingState.Warmup(until) =>
              if (now >= until) {
                samplingState = SamplingState.ExperimentPending(1, Nil)
              }
              ZIO.unit

            case SamplingState.ExperimentPending(iteration, results) =>
              var experiment = null.asInstanceOf[Experiment]
              val iterator   = fibers.values().iterator()

              while (experiment == null && iterator.hasNext()) {
                val fiber = iterator.next()
                if (fiber.running) {
                  scope.run(fiber.taggedLocation).foreach { candidate =>
                    results match {
                      case previous :: _ =>
                        val minDelta     =
                          if (previous.throughputData.nonEmpty) previous.throughputData.map(_.delta).min else 0
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
                          new ConcurrentHashMap[String, Int]()
                        )
                      case Nil           =>
                        experiment = new Experiment(
                          candidate,
                          now,
                          minExperimentDurationNanos,
                          selectSpeedUp(),
                          new ConcurrentHashMap[String, Int]()
                        )
                    }
                  }
                }
              }
              if (experiment != null) {
                samplingState = SamplingState.ExperimentInProgress(experiment, iteration, results)
                ZIO.logInfo(
                  s"Starting experiment $iteration (candidate: ${experiment.candidate.render}, speedUp: ${experiment.speedUp}, duration: ${experiment.duration})"
                )
              } else {
                ZIO.unit
              }

            case SamplingState.ExperimentInProgress(experiment, iteration, results) =>
              if (now >= experiment.endTime) {
                val result = experiment.toResult()
                if (iteration >= iterations) {
                  unsafe { implicit u =>
                    valuePromise.unsafe.done(ZIO.succeed(Result(result :: results)))
                  }
                  samplingState = SamplingState.Done
                  ZIO.logInfo(s"Profiling done. Total duration: ${now - startTime}ns")
                } else {
                  samplingState = SamplingState.CoolOff(now + coolOffPeriodNanos, iteration, result :: results)
                  ZIO.unit
                }
              } else {
                val iterator = fibers.values.iterator()
                while (iterator.hasNext()) {
                  val fiber = iterator.next()
                  if (fiber.running && experiment.candidate.isPrefixOf(fiber.taggedLocation)) {
                    val delayAmount = (experiment.speedUp * samplingPeriodNanos).toLong
                    fiber.localDelay.addAndGet(delayAmount)
                    globalDelay += delayAmount
                    experiment.trackDelay(delayAmount)
                  }
                }
                ZIO.unit
              }

            case SamplingState.CoolOff(until, iteration, results) =>
              if (now >= until) {
                samplingState = SamplingState.ExperimentPending(iteration + 1, results)
              }
              ZIO.unit

            case SamplingState.Done =>
              ZIO.unit
          }
        }

        runSamplingStep.repeat(Schedule.spaced(samplingPeriod)).fork.as {
          new Supervisor[Result] {

            // first event in fiber lifecycle. No previous events to consider.
            override def onStart[R, E, A](
              environment: ZEnvironment[R],
              effect: ZIO[R, E, A],
              parent: Option[Fiber.Runtime[Any, Any]],
              fiber: Fiber.Runtime[E, A]
            )(implicit unsafe: zio.Unsafe): Unit = {
              val parentDelay =
                parent.flatMap(f => Option(fibers.get(f.id)).map(_.localDelay.get())).getOrElse(globalDelay)

              fibers.put(fiber.id, FiberState.makeFor(fiber, parentDelay))
              ()
            }

            // previous events could be {onStart, onResume, onEffect}
            override def onEnd[R, E, A](
              value: Exit[E, A],
              fiber: Fiber.Runtime[E, A]
            )(implicit unsafe: zio.Unsafe): Unit = {
              val state = fibers.get(fiber.id)
              if (state ne null) {
                state.running = false
                // no need to refresh tag as we are shutting down
                if (!value.isInterrupted) {
                  delayFiber(state)
                }
                fibers.remove(fiber.id)
                ()
              }
            }

            // previous events could be {onStart, onResume, onEffect}
            override def onEffect[E, A](fiber: Fiber.Runtime[E, A], effect: ZIO[_, _, _])(implicit
              unsafe: zio.Unsafe
            ): Unit = {
              var state      = fibers.get(fiber.id)
              var freshState = false

              if (state == null) {
                state = FiberState.makeFor(fiber, globalDelay)
                fibers.put(fiber.id, state)
                freshState = true
              } else if (state.lastEffectWasStateful) {
                state.refreshTag(fiber)
                state.lastEffectWasStateful = false
              }

              effect match {
                case ZIO.Stateful(_, _) =>
                  state.lastEffectWasStateful = true
                case ZIO.Sync(_, _)     =>
                  if (!freshState) {
                    state.running = false
                    delayFiber(state)
                    state.running = true
                  }
                case ZIO.Async(_, _, _) =>
                  state.preAsyncGlobalDelay = globalDelay
                  state.inAsync = true
                case _                  =>
                  ()
              }
              state.location = effect.trace
            }

            // previous events could be {onStart, onResume, onEffect}
            override def onSuspend[E, A](fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit = {
              val state = fibers.get(fiber.id)
              if (state ne null) {
                if (state.lastEffectWasStateful) {
                  state.lastEffectWasStateful = false
                  state.refreshTag(fiber)
                }
                state.running = false
              } else {
                val newState = FiberState.makeFor(fiber, globalDelay)
                newState.running = false
                fibers.put(fiber.id, newState)
                ()
              }
            }

            // previous event can only be onSuspend
            override def onResume[E, A](fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit = {
              val state = fibers.get(fiber.id)
              if (state ne null) {
                state.running = true
                if (state.inAsync) {
                  state.localDelay.addAndGet(globalDelay - state.preAsyncGlobalDelay)
                  state.preAsyncGlobalDelay = 0
                  state.inAsync = false
                }
              } else {
                fibers.put(fiber.id, FiberState.makeFor(fiber, globalDelay))
                ()
              }
            }

            def value(implicit trace: Trace): UIO[Result] = valuePromise.await
          }
        }
      }
    }

  }
}
