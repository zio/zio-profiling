package zio.profiling.examples

import zio._
import zio.profiling.causal._

object CausalProfilerToyExample extends ZIOAppDefault {

  def run: Task[Unit] =
    CausalProfiler(iterations = 100).profile {
      val io = for {
        _    <- CausalProfiler.progressPoint("iteration start")
        short = ZIO.blocking(ZIO.succeed(Thread.sleep(40)))
        long  = ZIO.blocking(ZIO.succeed(Thread.sleep(100)))
        _    <- short.zipPar(long)
      } yield ()
      io.forever
    }
      .flatMap[Any, Throwable, Unit](_.renderToFile("profile.coz"))
}
