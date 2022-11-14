package zio.profiling

import zio._

/**
 * A CostCenter allows grouping multiple source code locations into one unit for reporting and targeting purposes.
 * Instead of relying on a function call hierarchy to identify a location, zio-profiling relies on manual tagging.
 *
 * {{{
 * for {
 *   _ <- ZIO.succeed(Thread.sleep(20)) <# "short" // code attributed to cost center `Root / "short"`
 *   _ <- ZIO.succeed(Thread.sleep(40)) <# "long" // code attributes to cost center `Root / "long"`
 * } yield ()
 * }}}
 */
sealed trait CostCenter { self =>
  import CostCenter._

  def isRoot: Boolean = self match {
    case Root => true
    case _    => false
  }

  /**
   * Create a child cost center that is nested under this one.
   */
  def /(name: String): CostCenter =
    Child(self, name)

  /**
   * Attribute a source code location to this cost center.
   */
  def #>(location: Trace): TaggedLocation =
    TaggedLocation(self, location)

  /**
   * Check whether this cost center is a direct child of another cost center.
   *
   * {{{
   * (Root / "foo" / "bar").isChildOf(Root / "foo") // true
   * (Root / "foo" / "bar1" / "bar2").isChildOf(Root / "foo") // false
   * }}}
   */
  def isChildOf(other: CostCenter): Boolean =
    self match {
      case Root             => false
      case Child(parent, _) => parent == other
    }

  /**
   * Check whether this cost center is a direct or transitive child of another cost center.
   */
  def isDescendantOf(other: CostCenter): Boolean =
    self == other || (self match {
      case Root             => false
      case Child(parent, _) => parent.isDescendantOf(other)
    })

  /**
   * Get the cost center that is a parent of this one and a direct child of the other one if it exists.
   *
   * {{{
   * (Root / "foo" / "bar").getOneLevelDownFrom(Root / "foo") // Some(Root / "foo" / "bar")
   * (Root / "foo" / "bar1" / "bar2").getOneLevelDownFrom(Root / "foo") // Some(Root / "foo" / "bar1")
   * }}}
   */
  def getOneLevelDownFrom(other: CostCenter): Option[CostCenter] =
    self match {
      case Root => None
      case Child(parent, _) =>
        if (parent == other) Some(self)
        else parent.getOneLevelDownFrom(other)
    }

  def render: String =
    self match {
      case Root => "<root>"
      case Child(parent, name) =>
        if (parent == Root) name
        else s"${parent.render}>$name"
    }
}

object CostCenter {
  case object Root                                         extends CostCenter
  final case class Child(parent: CostCenter, name: String) extends CostCenter

  /**
   * Get the current cost center this fiber is executing in.
   */
  def getCurrentUnsafe(fiber: Fiber.Runtime[_, _])(implicit unsafe: Unsafe): CostCenter =
    fiber.unsafe.getFiberRefs().getOrDefault(costCenterRef)

  /**
   * Get the current cost center.
   */
  def getCurrent(implicit trace: Trace): UIO[CostCenter] =
    costCenterRef.get

  /**
   * Run an effect with an adjusted cost center.
   */
  def withCostCenter[R, E, A](f: CostCenter => CostCenter)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    costCenterRef.locallyWith(f)(zio)

  private final val costCenterRef: FiberRef[CostCenter] =
    Unsafe.unsafeCompat(implicit u => FiberRef.unsafe.make(CostCenter.Root, identity, (old, _) => old))
}
