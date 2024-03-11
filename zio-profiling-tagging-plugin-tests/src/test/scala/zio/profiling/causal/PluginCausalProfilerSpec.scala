package zio.profiling.causal

import zio._
import zio.profiling.{BaseSpec, CostCenter, ProfilerExamples}
import zio.test.Assertion.isTrue
import zio.test._

object PluginSamplingProfilerSpec extends BaseSpec {

  def spec = suite("PluginCausalProfiler")(
    test("Should correctly profile simple example program") {
      val profiler = CausalProfiler(
        iterations = 10,
        warmUpPeriod = 0.seconds,
        minExperimentDuration = 1.second,
        experimentTargetSamples = 1
      )
      val program = (ProfilerExamples.zioProgram *> CausalProfiler.progressPoint("done")).forever
      Live.live(profiler.profile(program)).map { result =>
        def isSlowEffect(location: CostCenter) =
          location.hasParentMatching("zio\\.profiling\\.ProfilerExamples\\.slow\\(.*\\)".r)
        def isFastEffect(location: CostCenter) =
          location.hasParentMatching("zio\\.profiling\\.ProfilerExamples\\.fast\\(.*\\)".r)

        val hasSlow = result.experiments.exists(e => isSlowEffect(e.selected))
        val hasFast = result.experiments.exists(e => isFastEffect(e.selected))

        assert(hasSlow)(isTrue) && assert(hasFast)(isTrue)
      }
    }
  )
}
