package zio.profiling.examples

import zio._
import zio.profiling.causal._

object PresentationExample extends ZIOAppDefault {

  def run: URIO[Any, ExitCode] = {
    val fast = ZIO.succeed(Thread.sleep(40)) <# "fast"

    val slow = ZIO
      .succeed(Thread.sleep(20))
      .tagged("slow-1")
      .zipPar(ZIO.succeed(Thread.sleep(60)).tagged("slow-2"))
      .tagged("slow")

    val program = for {
      _ <- fast <&> slow <# "program"
      _ <- progressPoint("iteration done")
    } yield ()

    CausalProfiler(iterations = 100, candidateSelector = CandidateSelector.all)
      .profile(program.forever)
      .flatMap(_.renderToFile("profile.coz"))
      .exitCode
  }
}
