package zio.profiling

import zio._
import zio.stream.ZStream

import scala.util.matching.Regex

/**
 * A CostCenter allows grouping multiple source code locations into one unit for reporting and targeting purposes.
 * Instead of relying on a function call hierarchy to identify a location, zio-profiling relies on manual tagging.
 *
 * {{{
 * for {
 *   _ <- CostCenter.withChildCostCenter("short")(ZIO.succeed(Thread.sleep(20))) // code attributed to cost center `Root / "short"`
 *   _ <- CostCenter.withChildCostCenter("long")(ZIO.succeed(Thread.sleep(40))) // code attributes to cost center `Root / "long"`
 * } yield ()
 * }}}
 */
final case class CostCenter(locations: Chunk[String]) {
  def render: String = locations.mkString(";")

  def isRoot: Boolean = locations.isEmpty

  /**
   * Create a child cost center that is nested under this one.
   */
  def /(location: String): CostCenter = CostCenter(locations :+ location)

  def location: Option[String] = locations.lastOption

  def isChildOf(other: CostCenter): Boolean =
    locations.startsWith(other.locations)

  /**
   * Check whether this cost center has a parent with a given name.
   *
   * {{{
   * (Root / "foo" / "bar").hasParent("foo") // true
   * (Root / "foo" / "bar").hasParent("bar") // true
   * (Root / "foo" / "bar").hasParent("baz") // false
   * }}}
   */
  def hasParent(name: String): Boolean =
    locations.contains(name)

  /**
   * Check whether this cost center has a parent with a name matching the given regex.
   */
  def hasParentMatching(regex: Regex): Boolean =
    locations.exists(regex.findFirstIn(_).nonEmpty)
}

object CostCenter {

  /**
   * The root cost center.
   */
  val Root: CostCenter = CostCenter(Chunk.empty)

  /**
   * Get the current cost center this fiber is executing in.
   */
  def getCurrentUnsafe(fiber: Fiber.Runtime[_, _])(implicit unsafe: Unsafe): CostCenter =
    fiber.unsafe.getFiberRefs().getOrDefault(globalRef)

  /**
   * Get the current cost center.
   */
  def getCurrent(implicit trace: Trace): UIO[CostCenter] =
    globalRef.get

  /**
   * Run an effect with a child cost center nested under the current one.
   */
  def withChildCostCenter[R, E, A](name: String)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    globalRef.locallyWith(_ / name)(zio)

  /**
   * Run an effect with a child cost center nested under the current one.
   */
  def withChildCostCenterStream[R, E, A](name: String)(stream: ZStream[R, E, A])(implicit
    trace: Trace
  ): ZStream[R, E, A] =
    ZStream.scoped[R](globalRef.locallyScopedWith(_ / name)) *> stream

  private final val globalRef: FiberRef[CostCenter] =
    Unsafe.unsafe(implicit u => FiberRef.unsafe.make(CostCenter.Root, identity, (old, _) => old))
}
