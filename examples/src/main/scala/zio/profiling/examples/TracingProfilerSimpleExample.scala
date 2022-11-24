package zio.profiling.examples

import zio._
import zio.profiling.tracing._

object TracingProfilerSimpleExample extends ZIOAppDefault {

  def run: URIO[Any, ExitCode] = {
    val program = for {
      _ <- ZIO.succeed(Thread.sleep(20)) <# "short"
      _ <- ZIO.succeed(Thread.sleep(40)) <# "long"
    } yield ()

    TracingProfiler
      .profile(program)
      .flatMap(_.stackCollapseToFile("profile.folded"))
      .exitCode
  }
}
