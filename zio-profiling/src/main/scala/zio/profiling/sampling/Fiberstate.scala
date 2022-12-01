package zio.profiling.sampling

import zio._

final case class FiberState(
  val runtime: Fiber.Runtime[_, _],
  @volatile var trace: Trace
)
