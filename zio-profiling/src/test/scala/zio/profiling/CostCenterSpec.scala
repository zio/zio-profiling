package zio.profiling

import zio.Scope
import zio.test.Assertion._
import zio.test._

object CostCenterSpec extends BaseSpec {

  def spec: Spec[Environment with TestEnvironment with Scope, Any] = suite("CostCenter")(
    suite("#isChildOf")(
      test("should return true for direct child") {
        val parent = CostCenter.Root / "foo"
        val child  = parent / "bar"
        assert(child.isChildOf(parent))(isTrue)
      },
      test("should return false for unrelated cost centers") {
        val parent = CostCenter.Root / "foo"
        val child  = CostCenter.Root / "bar"
        assert(child.isChildOf(parent))(isFalse)
      },
      test("should return false for transitive child") {
        val parent = CostCenter.Root / "foo"
        val child  = parent / "bar1" / "bar2"
        assert(child.isChildOf(parent))(isFalse)
      }
    ),
    suite("#isDescendantOf")(
      test("should return true for direct child") {
        val parent = CostCenter.Root / "foo"
        val child  = parent / "bar"
        assert(child.isDescendantOf(parent))(isTrue)
      },
      test("should return false for unrelated cost centers") {
        val parent = CostCenter.Root / "foo"
        val child  = CostCenter.Root / "bar"
        assert(child.isDescendantOf(parent))(isFalse)
      },
      test("should return true for transitive child") {
        val parent = CostCenter.Root / "foo"
        val child  = parent / "bar1" / "bar2"
        assert(child.isDescendantOf(parent))(isTrue)
      }
    ),
    suite("#getOneLevelDownFrom")(
      test("should return itself if direct child") {
        val parent = CostCenter.Root / "foo"
        val child  = parent / "bar"
        assert(child.getOneLevelDownFrom(parent))(isSome(equalTo(child)))
      },
      test("should return none if unrelated") {
        val parent = CostCenter.Root / "foo"
        val child  = CostCenter.Root / "bar"
        assert(child.getOneLevelDownFrom(parent))(isNone)
      },
      test("should return one level down if it is a descendant") {
        val parent     = CostCenter.Root / "foo"
        val child      = parent / "bar1"
        val descendant = child / "bar2"
        assert(descendant.getOneLevelDownFrom(parent))(isSome(equalTo(child)))
      }
    ),
    test("should propagate and nest cost centers") {
      val effect = CostCenter.withCostCenter(_ / "foo") {
        CostCenter.withCostCenter(_ / "bar") {
          CostCenter.getCurrent
        }
      }
      assertZIO(effect.exit)(succeeds(equalTo(CostCenter.Root / "foo" / "bar")))
    }
  )

}
