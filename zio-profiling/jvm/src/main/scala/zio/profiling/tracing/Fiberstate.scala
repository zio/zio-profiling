package zio.profiling.tracing

import zio._
import zio.profiling.CostCenter

final private class FiberState private (
  @volatile var costCenter: CostCenter,
  @volatile var location: Trace,
  @volatile var timestamp: Long,
  @volatile var lastEffectWasStateful: Boolean
) {

  def refreshCostCenter(fiber: Fiber.Runtime[_, _])(implicit unsafe: Unsafe): Unit =
    costCenter = CostCenter.getCurrent(fiber)

  def taggedLocation: CostCenter.TaggedLocation =
    costCenter #> location

}

private object FiberState {
  def makeFor(fiber: Fiber.Runtime[_, _])(implicit unsafe: Unsafe): FiberState = {
    val state = new FiberState(
      CostCenter.getCurrent(fiber),
      null.asInstanceOf[Trace],
      0,
      false
    )
    state
  }

}
