package zio.profiling.sampling

import zio.Scope
import zio.profiling.{BaseSpec, CostCenter, ProfilerExamples}
import zio.test.Assertion.{hasSize, isGreaterThanEqualTo}
import zio.test._

object PluginSamplingProfilerSpec extends BaseSpec {

  def spec: Spec[Environment with TestEnvironment with Scope, Any] = suite("PluginSamplingProfiler")(
    test("Should correctly profile simple example program") {
      Live.live(SamplingProfiler().profile(ProfilerExamples.zioProgram)).map { result =>
        val sortedEntries = result.entries.sortBy(_.samples).reverse

        def isSlowEffect(location: CostCenter) =
          location.hasParentMatching("zio\\.profiling\\.ProfilerExamples\\.slow\\(.*\\)".r)
        def isFastEffect(location: CostCenter) =
          location.hasParentMatching("zio\\.profiling\\.ProfilerExamples\\.fast\\(.*\\)".r)

        assert(sortedEntries)(hasSize(isGreaterThanEqualTo(2))) &&
        assertTrue(isSlowEffect(sortedEntries(0).costCenter)) &&
        assertTrue(isFastEffect(sortedEntries(1).costCenter))
      }
    },
    test("Should correctly profile simple example streams program") {
      Live.live(SamplingProfiler().profile(ProfilerExamples.zioStreamProgram)).map { result =>
        val sortedEntries = result.entries.sortBy(_.samples).reverse

        def isSlowEffect(location: CostCenter) =
          location.hasParentMatching("zio\\.profiling\\.ProfilerExamples\\.slowStream\\(.*\\)".r)
        def isFastEffect(location: CostCenter) =
          location.hasParentMatching("zio\\.profiling\\.ProfilerExamples\\.fastStream\\(.*\\)".r)

        assert(sortedEntries)(hasSize(isGreaterThanEqualTo(2))) &&
        assertTrue(isSlowEffect(sortedEntries(0).costCenter)) &&
        assertTrue(isFastEffect(sortedEntries(1).costCenter))
      }
    }
  )

}
