package zio.profiling

import zio.test._
import zio.{Runtime, ZLayer}

abstract class BaseSpec extends ZIOSpecDefault {
  override val bootstrap: ZLayer[Any, Any, TestEnvironment] = testEnvironment ++ Runtime.removeDefaultLoggers
}
