package zio.profiling

import zio._

object BenchmarkUtil extends Runtime[Any] { self =>
  val environment = Runtime.default.environment

  val fiberRefs = Runtime.default.fiberRefs

  val runtimeFlags = Runtime.default.runtimeFlags

  def repeat[R, E, A](n: Int)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    if (n <= 1) zio
    else zio *> repeat(n - 1)(zio)

  def verify(cond: Boolean)(message: => String): IO[AssertionError, Unit] =
    ZIO.when(!cond)(ZIO.fail(new AssertionError(message))).unit

  def unsafeRun[E, A](zio: ZIO[Any, E, A]): A =
    Unsafe.unsafe(implicit unsafe => self.unsafe.run(zio).getOrThrowFiberFailure())
}
