package zio.profiling.examples

import zio.profiling.causal._
import zio.{URIO, _}

object CausalProfilerToyExample extends ZIOAppDefault {

  def run: URIO[Any, ExitCode] =
    CausalProfiler(iterations = 100).profile {
      val io = for {
        _    <- CausalProfiler.progressPoint("iteration start")
        short = ZIO.blocking(ZIO.succeed(Thread.sleep(40)))
        long  = ZIO.blocking(ZIO.succeed(Thread.sleep(100)))
        _    <- short.zipPar(long)
      } yield ()
      io.forever
    }
      .flatMap(_.renderToFile("profile.coz"))
      .exitCode
}
