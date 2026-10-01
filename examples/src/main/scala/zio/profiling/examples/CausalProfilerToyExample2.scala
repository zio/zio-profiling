package zio.profiling.examples

import zio._
import zio.profiling.causal._

object CausalProfilerToyExample2 extends ZIOAppDefault {

  val prog: ZIO[Any, Nothing, Unit] = for {
    done <- Ref.make(false)
    f    <- (doUselessBackgroundWork *> done.get).repeatUntilEquals(true).fork
    _    <- doRealWork
    _    <- done.set(true)
    _    <- f.join
    _    <- CausalProfiler.progressPoint("workDone")
  } yield ()

  def doRealWork: ZIO[Any, Nothing, Unit] = ZIO.succeed(Thread.sleep(100))

  def doUselessBackgroundWork: ZIO[Any, Nothing, Unit] = ZIO.succeed(Thread.sleep(30))

  def run: Task[Unit] =
    CausalProfiler(iterations = 100)
      .profile(prog.forever)
      .flatMap[Any, Throwable, Unit](_.renderToFile("profile.coz"))
}
