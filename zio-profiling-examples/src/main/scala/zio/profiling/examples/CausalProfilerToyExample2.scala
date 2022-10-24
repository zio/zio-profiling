package zio.profiling.examples

import zio.profiling.causal._
import zio.{URIO, _}

object CausalProfilerToyExample2 extends ZIOAppDefault {
  def run: URIO[Any, ExitCode] =
    CausalProfiler(iterations = 100)
      .profile(prog.forever)
      .flatMap(_.writeToFile("profile.coz"))
      .exitCode

  val prog: ZIO[Any, Nothing, Unit] = for {
    done <- Ref.make(false)
    f    <- (doUselessBackgroundWork *> done.get).repeatUntilEquals(true).fork <# "backgroundwork"
    _    <- doRealWork <# "realwork"
    _    <- done.set(true)
    _    <- f.join
    _    <- progressPoint("workDone")
  } yield ()

  def doRealWork: ZIO[Any, Nothing, Unit] = ZIO.blocking(ZIO.succeed(Thread.sleep(100)))

  def doUselessBackgroundWork: ZIO[Any, Nothing, Unit] = ZIO.blocking(ZIO.succeed(Thread.sleep(30)))
}
