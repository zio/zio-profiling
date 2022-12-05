package zio.profiling.sampling

import zio._
import zio.profiling.{BaseSpec, CostCenter}
import zio.test._
import zio.test.Assertion.{isGreaterThanEqualTo, hasSize}

object SamplingProfilerSpec extends BaseSpec {

  def spec =
    suite("SamplingProfiler")(
      test("Should correctly profile simple example program") {
        val program = for {
          _ <- CostCenter.withChildCostCenter("short")(ZIO.succeed(Thread.sleep(200)))
          _ <- CostCenter.withChildCostCenter("long")(ZIO.succeed(Thread.sleep(400)))
        } yield ()

        Live.live(SamplingProfiler().profile(program)).map { result =>
          val sortedEntries = result.entries.sortBy(_.samples).reverse

          def isLongEffect(location: CostCenter) = location.hasParent("long")
          def isShortEffect(location: CostCenter) = location.hasParent("short")

          assert(sortedEntries)(hasSize(isGreaterThanEqualTo(2))) &&
          assertTrue(isLongEffect(sortedEntries(0).costCenter)) &&
          assertTrue(isShortEffect(sortedEntries(1).costCenter))
        }
      }
    )

}
