package zio.profiling.tracing

import _root_.zio.profiling.Tag

final case class ProfilingResult(
  entries: List[ProfilingResult.Entry]
)

object ProfilingResult {
  final case class Entry(
    location: Tag.TaggedLocation,
    totalCalls: Long,
    totalTime: Long,
    maxTime: Long,
    averageTime: Long,
  )
}
