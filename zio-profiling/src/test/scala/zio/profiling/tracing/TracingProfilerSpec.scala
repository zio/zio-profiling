package zio.profiling.tracing

import zio._
import zio.profiling.{BaseSpec, CostCenter, TaggedLocation}
import zio.test.Assertion._
import zio.test._

object TracingProfilerSpec extends BaseSpec {

  def spec =
    suite("TracingProfile")(
      test("Should correctly profile simple example program") {
        val program = for {
          _ <- ZIO.succeed(Thread.sleep(20)) <# "short"
          _ <- ZIO.succeed(Thread.sleep(40)) <# "long"
        } yield ()

        TracingProfiler.profile(program).map { result =>
          val sortedEntries = result.entries.sortBy(_.totalTimeNanos).reverse

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

          assert(sortedEntries)(hasSize(equalTo(7))) &&
          assertTrue(isLongEffect(longEffect.location)) && assert(longEffect.totalTimeNanos)(
            isGreaterThan(40_000_000L)
          ) &&
          assertTrue(isShortEffect(shortEffect.location)) && assert(shortEffect.totalTimeNanos)(
            isGreaterThan(20_000_000L)
          )
        }
      }
    )

}
