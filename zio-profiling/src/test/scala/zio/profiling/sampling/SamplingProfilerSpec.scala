package zio.profiling.sampling

import zio._
import zio.profiling.{BaseSpec, CostCenter, TaggedLocation}
import zio.test._

object SamplingProfilerSpec extends BaseSpec {

  def spec =
    suite("SamplingProfiler")(
      test("Should correctly profile simple example program") {
        val program = for {
          _ <- ZIO.succeed(Thread.sleep(200)) <# "short"
          _ <- ZIO.succeed(Thread.sleep(400)) <# "long"
        } yield ()

        SamplingProfiler().profile(program).map { result =>
          val sortedEntries = result.entries.sortBy(_.samples).reverse

          def isLongEffect(location: TaggedLocation) = location match {
            case TaggedLocation(CostCenter.Child(CostCenter.Root, "long"), _) => true
            case _                                                            => false
          }

          def isShortEffect(location: TaggedLocation) = location match {
            case TaggedLocation(CostCenter.Child(CostCenter.Root, "short"), _) => true
            case _                                                             => false
          }

          val longEffect  = sortedEntries(0)
          val shortEffect = sortedEntries(1)

          assertTrue(isLongEffect(longEffect.location)) &&
          assertTrue(isShortEffect(shortEffect.location))
        }
      }
    ) @@ TestAspect.withLiveClock

}
