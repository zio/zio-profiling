package zio.profiling

import zio._

object BenchmarkUtil extends Runtime[Any] { self =>
  val environment = Runtime.default.environment

  val fiberRefs = Runtime.default.fiberRefs

  val runtimeFlags = Runtime.default.runtimeFlags

  def repeat[R, E, A](n: Int)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    if (n <= 1) zio
    else zio *> repeat(n - 1)(zio)

  def verify(cond: Boolean)(message: => String)(implicit trace: Trace): IO[AssertionError, Unit] =
    ZIO.when(!cond)(ZIO.fail(new AssertionError(message))).unit

  def unsafeRun[E, A](zio: ZIO[Any, E, A])(implicit trace: Trace): A =
    Unsafe.unsafe(u => self.unsafe.run(zio)(trace, u).getOrThrowFiberFailure()(u))
}
