package zio.profiling.examples

import zio._
import zio.profiling.causal._

object CausalProfilerToyExample2 extends ZIOAppDefault {
  def run =
    CausalProfiler
      .profile(ProfilerConfig.Default.copy(iterations = 100)) { prog.forever }
      .flatMap(_.writeToFile("profile.coz"))
      .exitCode

  val prog = for {
    done <- Ref.make(false)
    f    <- (doUselessBackgroundWork *> done.get).repeatUntilEquals(true).fork <# "backgroundwork"
    _    <- doRealWork <# "realwork"
    _    <- done.set(true)
    _    <- f.join
    _    <- progressPoint("workDone")
  } yield ()

  def doRealWork = ZIO.blocking(ZIO.succeed({ Thread.sleep(100); }))

  def doUselessBackgroundWork = ZIO.blocking(ZIO.succeed({ Thread.sleep(30); }))
}
