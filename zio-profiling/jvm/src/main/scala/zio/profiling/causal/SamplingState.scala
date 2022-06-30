package zio.profiling.causal

sealed private trait SamplingState

private object SamplingState {
  final case class Warmup(
    until: Long
  ) extends SamplingState

  final case class ExperimentPending(
    iteration: Int,
    results: List[ExperimentResult]
  ) extends SamplingState

  final case class ExperimentInProgress(
    experiment: Experiment,
    iteration: Int,
    results: List[ExperimentResult]
  ) extends SamplingState

  final case class CoolOff(
    until: Long,
    iteration: Int,
    results: List[ExperimentResult]
  ) extends SamplingState

  case object Done extends SamplingState
}
