package zio.profiling.sampling

import zio._

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
   * Create a runtime that will profile all effects executed with it. Use `runtime.environment.get` in order to get a
   * reference to the supervisor. Make sure to shut down the runtime when down.
   */
  def supervisedRuntime(implicit unsafe: Unsafe): Runtime.Scoped[SamplingProfilerSupervisor] = {
    val layer = ZLayer.scoped[Any](makeSupervisor).flatMap(env => Runtime.addSupervisor(env.get).map(_ => env))
    Runtime.unsafe.fromLayer(layer)
  }

}
