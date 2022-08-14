package zio.profiling.tracing

import zio._
import zio.profiling.{Tag, TagRef}

final private class FiberState private (
  @volatile var tag: Tag,
  @volatile var location: Trace,
  @volatile var timestamp: Long,
  @volatile var lastEffectWasStateful: Boolean
) {

  def refreshTag(fiber: Fiber.Runtime[_, _])(implicit unsafe: Unsafe): Unit =
    tag = fiber.unsafe.getFiberRefs().getOrDefault(TagRef)

  def taggedLocation: Tag.TaggedLocation =
    tag #> location

}

private object FiberState {
  def makeFor(fiber: Fiber.Runtime[_, _])(implicit unsafe: Unsafe): FiberState = {
    val state = new FiberState(
      Tag.Root,
      null.asInstanceOf[Trace],
      0,
      false
    )
    state.refreshTag(fiber)
    state
  }

}
