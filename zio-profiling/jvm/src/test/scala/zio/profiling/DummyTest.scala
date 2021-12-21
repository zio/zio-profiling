package zio.profiling

import zio.test._
import zio.test.TestAspect._

object DummyTest extends DefaultRunnableSpec {

  def spec = suite("The DummySpec should")(
    simpleTest
  ) @@ timed

  private val simpleTest = test("simply run")(
    assertTrue(true)
  )
}
