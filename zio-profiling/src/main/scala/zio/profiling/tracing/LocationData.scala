package zio.profiling.tracing

import java.util.concurrent.atomic.AtomicLong

final private class LocationData(
  val numberOfCalls: AtomicLong,
  val totalTime: AtomicLong,
  val maxTime: AtomicLong
) {
  def recordCall(duration: Long): Unit = {
    numberOfCalls.incrementAndGet()
    totalTime.addAndGet(duration)
    maxTime.accumulateAndGet(duration, Math.max)
    ()
  }
}

private[tracing] object LocationData {
  def fromSingleCall(
    duration: Long
  ): LocationData =
    new LocationData(new AtomicLong(1), new AtomicLong(duration), new AtomicLong(duration))
}
