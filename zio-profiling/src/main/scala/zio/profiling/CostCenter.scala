package zio.profiling

import zio._
import zio.stream.ZStream

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
sealed trait CostCenter { self =>
  import CostCenter._

  final def location: Option[String] = self match {
    case Root              => None
    case Child(_, current) => Some(current)
  }

  final def isRoot: Boolean = self match {
    case Root => true
    case _    => false
  }

  /**
   * Create a child cost center that is nested under this one.
   */
  final def /(location: String): CostCenter = self match {
    case Root => Child(Root, location)
    case Child(_, current) =>
      if (current == location)
        self
      else
        Child(self, location)
  }

  /**
   * Check whether this cost center has a parent with a given name.
   *
   * {{{
   * (Root / "foo" / "bar").hasParent("foo") // true
   * (Root / "foo" / "bar").hasParent("bar") // true
   * (Root / "foo" / "bar").hasParent("baz") // false
   * }}}
   */
  final def hasParent(name: String): Boolean = self match {
    case Root                   => false
    case Child(parent, current) => current == name || parent.hasParent(name)
  }
}

object CostCenter {
  case object Root                                            extends CostCenter
  final case class Child(parent: CostCenter, current: String) extends CostCenter

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
