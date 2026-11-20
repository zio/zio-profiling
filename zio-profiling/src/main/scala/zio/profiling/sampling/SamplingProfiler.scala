package zio.profiling.sampling

import zio._

/**
 * A sampling profiler. Every `samplingPeriod`, it adds one to the count of the cost center of each running fiber that
 * it supervises. A fiber counts as running while it executes on a thread: `ZIO.succeed(Thread.sleep(100))` is counted,
 * and a fiber waiting in `ZIO.sleep` is not. Fibers that run in parallel are each counted, so compare cost centers with
 * each other, not with the wall-clock time.
 *
 * {{{
 * import zio._
 * import zio.profiling.CostCenter
 *
 * val fast = CostCenter.withChildCostCenter("fast")(ZIO.succeed(Thread.sleep(100)))
 * val slow = CostCenter.withChildCostCenter("slow")(ZIO.succeed(Thread.sleep(300)))
 *
 * val result: UIO[ProfilingResult] = SamplingProfiler().profile(fast <&> slow)
 * }}}
 *
 * @param samplingPeriod
 *   the time between two samples. The first sample is taken one period after sampling starts.
 *
 * @see
 *   [[https://zio.dev/zio-profiling/ ZIO Profiling]]
 */
final case class SamplingProfiler(
  samplingPeriod: Duration = 10.millis
) {

  /**
   * Runs `zio` in a new fiber with a sampler attached, waits for it, and returns the samples. The value of `zio` is
   * discarded. Fibers that `zio` forks are sampled too. When `zio` fails, dies or is interrupted, the profile fails,
   * dies or is interrupted in the same way.
   *
   * A known bug: all profiles run with the same `SamplingProfiler` value share one supervisor, so the result of a
   * second `profile` call also holds the samples of the first. Create a new `SamplingProfiler` for each profile.
   */
  def profile[R, E](zio: ZIO[R, E, Any]): ZIO[R, E, ProfilingResult] = ZIO.scoped[R] {
    for {
      supervisor <- makeSupervisor
      _          <- (zio.forkScoped).supervised(supervisor).flatMap(_.join)
      result     <- supervisor.value
    } yield result
  }

  /**
   * Starts a sampler in the current scope and returns the supervisor that holds its samples. Sampling stops when the
   * scope closes.
   *
   * Only fibers that start, or resume after a suspension, inside `.supervised(supervisor)` are sampled. A fiber that
   * resumes there without forking stays sampled after it leaves `.supervised`, so fork the profiled effect inside it. A
   * known bug: every run of `makeSupervisor` on the same `SamplingProfiler` value returns the same supervisor, so
   * samples from earlier runs stay in it.
   */
  val makeSupervisor: URIO[Scope, SamplingProfilerSupervisor] = {
    val supervisor = new SamplingProfilerSupervisor()

    ZIO
      .succeed(supervisor.sample())
      .repeat[Any, Long](Schedule.spaced(samplingPeriod))
      .delay(samplingPeriod)
      .forkScoped
      .as(supervisor)
  }

  /**
   * Creates a runtime that samples the fibers of the effects run with it. Get the supervisor with
   * `runtime.environment.get[SamplingProfilerSupervisor]`, and shut the runtime down when done, which stops the
   * sampler. It uses the supervisor of `makeSupervisor`, so the same known bug applies.
   */
  def supervisedRuntime(implicit unsafe: Unsafe): Runtime.Scoped[SamplingProfilerSupervisor] = {
    val layer = ZLayer.scoped[Any](makeSupervisor).flatMap(env => Runtime.addSupervisor(env.get).map(_ => env))
    Runtime.unsafe.fromLayer(layer)
  }

}
