package zio.profiling.tracing

import java.util.concurrent.atomic.AtomicLong

final private class LocationData(
  val numberOfCalls: AtomicLong,
  val totalTimeNanos: AtomicLong,
  val maxTimeNanos: AtomicLong
) {
  def recordCall(duration: Long): Unit = {
    numberOfCalls.incrementAndGet()
    totalTimeNanos.addAndGet(duration)
    maxTimeNanos.accumulateAndGet(duration, Math.max)
    ()
  }
}

private object LocationData {
  def fromSingleCall(duration: Long): LocationData =
    new LocationData(new AtomicLong(1), new AtomicLong(duration), new AtomicLong(duration))
}
