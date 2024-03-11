package zio.profiling

import zio._
import zio.stream.ZStream

object ProfilerExamples {
  val fast: ZIO[Any, Nothing, Unit] = ZIO.succeed(Thread.sleep(400))

  val slow: ZIO[Any, Nothing, Unit] = ZIO.succeed(Thread.sleep(200)) <&> ZIO.succeed(Thread.sleep(600))

  val zioProgram: ZIO[Any, Nothing, Unit] = fast <&> slow

  val fastStream: ZStream[Any, Nothing, Unit] = ZStream.fromZIO(fast)

  val slowStream: ZStream[Any, Nothing, Unit] = ZStream.fromZIO(slow)

  val zioStreamProgram: ZIO[Any, Nothing, Unit] = (fastStream <&> slowStream).runDrain
}
