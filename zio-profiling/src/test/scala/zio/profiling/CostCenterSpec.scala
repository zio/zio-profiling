package zio.profiling

import zio.Scope
import zio.test.Assertion._
import zio.test._

object CostCenterSpec extends BaseSpec {

  def spec: Spec[Environment with TestEnvironment with Scope, Any] =
    suite("CostCenter")(
    suite("#hasParent")(
      test("should return true for parent") {
        val cc = CostCenter.Root / "foo" / "bar"
        assert(cc.hasParent("foo"))(isTrue)
      },
      test("should return true for current location") {
        val cc = CostCenter.Root / "foo" / "bar"
        assert(cc.hasParent("bar"))(isTrue)
      },
      test("should return false for unrelated cost centers") {
        val cc = CostCenter.Root / "foo" / "bar"
        assert(cc.hasParent("baz"))(isFalse)
      }
    ),
    test("should propagate and nest cost centers") {
      val effect = CostCenter.withChildCostCenter("foo") {
        CostCenter.withChildCostCenter("bar") {
          CostCenter.getCurrent
        }
      }
      assertZIO(effect.exit)(succeeds(equalTo(CostCenter.Root / "foo" / "bar")))
    }
  )

}
