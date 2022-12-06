package zio.profiling.examples

import zio._
import zio.profiling.sampling._

object PresentationExample extends ZIOAppDefault {
  def run: URIO[Any, ExitCode] = {

    val fast = ZIO.succeed(Thread.sleep(400))

    val slow = ZIO.succeed(Thread.sleep(200)) <&> ZIO.succeed(Thread.sleep(600))

    val program = fast <&> slow

    SamplingProfiler()
      .profile(program)
      .flatMap(_.stackCollapseToFile("profile.folded"))
      .exitCode
  }
}
