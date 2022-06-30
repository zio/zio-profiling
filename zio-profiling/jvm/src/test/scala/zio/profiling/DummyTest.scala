package zio.profiling

import zio.test._
import zio.test.TestAspect._

// For now just to see if the tests are picked up with mill correctly
object DummyTest extends ZIOSpecDefault {

  def spec = suite("The DummySpec should")(
    simpleTest
  ) @@ timed

  private val simpleTest = test("simply run")(
    assertTrue(true)
  )
}
