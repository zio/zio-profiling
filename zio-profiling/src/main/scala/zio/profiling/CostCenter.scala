package zio.profiling

import zio._
import zio.stream.ZStream

import scala.util.matching.Regex

/**
 * A `CostCenter` groups source code locations into one named unit for reporting and for causal experiments. It is a
 * path of location names, from the outermost tagged effect to the innermost one. The sampling profiler counts its
 * samples per cost center, and the causal profiler selects cost centers for its experiments.
 *
 * Instead of the call stack, zio-profiling uses tags: wrap an effect in
 * [[zio.profiling.CostCenter.withChildCostCenter]], or let the tagging compiler plugin do it. Code that runs outside
 * any tag is in [[zio.profiling.CostCenter.Root]]. The active cost center is kept in a `FiberRef`, so a forked fiber
 * starts in the cost center of its parent.
 *
 * {{{
 * import zio._
 *
 * val program: UIO[Unit] =
 *   for {
 *     _ <- CostCenter.withChildCostCenter("short")(ZIO.succeed(Thread.sleep(20))) // runs in Root / "short"
 *     _ <- CostCenter.withChildCostCenter("long")(ZIO.succeed(Thread.sleep(40)))  // runs in Root / "long"
 *   } yield ()
 * }}}
 *
 * @see
 *   [[https://zio.dev/zio-profiling/ ZIO Profiling]]
 */
final case class CostCenter(locations: Chunk[String]) {

  /**
   * Returns the location names joined with `;`, the folded-stack format that flame graph tools read. The root cost
   * center renders as the empty string.
   */
  def render: String = locations.mkString(";")

  /** Returns `true` when this is the root cost center, which has no location names. */
  def isRoot: Boolean = locations.isEmpty

  /**
   * Returns a child cost center nested under this one, with `location` added as its last name.
   *
   * {{{
   * val path = CostCenter.Root / "orders" / "parse"
   * path.render // "orders;parse"
   * }}}
   */
  def /(location: String): CostCenter = CostCenter(locations :+ location)

  /** Returns the last location name, or `None` for the root cost center. */
  def location: Option[String] = locations.lastOption

  /**
   * Returns `true` when the location names of `other` are a prefix of the names of this cost center. A cost center is a
   * child of itself, and every cost center is a child of the root.
   */
  def isChildOf(other: CostCenter): Boolean =
    locations.startsWith(other.locations)

  /**
   * Returns `true` when `name` is one of the location names of this cost center, including its own last name. Names are
   * compared whole.
   *
   * {{{
   * val path = CostCenter.Root / "foo" / "bar"
   * path.hasParent("foo") // true
   * path.hasParent("bar") // true
   * path.hasParent("baz") // false
   * path.hasParent("fo")  // false
   * }}}
   */
  def hasParent(name: String): Boolean =
    locations.contains(name)

  /**
   * Returns `true` when `regex` finds a match in one of the location names of this cost center, including its own last
   * name. The match can be anywhere in a name: `"a".r` matches `"bar"`. Always `false` for the root cost center.
   */
  def hasParentMatching(regex: Regex): Boolean =
    locations.exists(regex.findFirstIn(_).nonEmpty)
}

/**
 * Holds the root cost center and the functions that read the active cost center or run code in a child one.
 */
object CostCenter {

  /**
   * The root cost center, with no location names. Code runs in it when no tag is active.
   */
  val Root: CostCenter = CostCenter(Chunk.empty)

  /**
   * Returns the cost center that `fiber` is in, without running an effect. The profilers use it to read the cost center
   * of a fiber they supervise.
   */
  def getCurrentUnsafe(fiber: Fiber.Runtime[_, _])(implicit unsafe: Unsafe): CostCenter =
    fiber.unsafe.getFiberRefs().getOrDefault(globalRef)

  /**
   * Returns the cost center of the fiber that runs this effect.
   */
  def getCurrent(implicit trace: Trace): UIO[CostCenter] =
    globalRef.get

  /**
   * Runs `zio` in a child cost center named `name`, nested under the current one. The current cost center is restored
   * when `zio` ends, whether it succeeds or fails, and a fiber that `zio` forks starts in the child cost center.
   *
   * {{{
   * import zio._
   *
   * val current: UIO[String] =
   *   CostCenter.withChildCostCenter("outer") {
   *     CostCenter.withChildCostCenter("inner")(CostCenter.getCurrent.map(_.render))
   *   }
   * // "outer;inner"
   * }}}
   */
  def withChildCostCenter[R, E, A](name: String)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    globalRef.locallyWith(_ / name)(zio)

  /**
   * Runs `stream` in a child cost center named `name`, nested under the current one. Stages added after this call, such
   * as a `mapZIO` on the returned stream, run in the child cost center too. The current cost center is restored when
   * the stream ends.
   *
   * {{{
   * import zio._
   * import zio.stream.ZStream
   *
   * val names: UIO[Chunk[String]] =
   *   CostCenter
   *     .withChildCostCenterStream("numbers")(ZStream(1, 2))
   *     .mapZIO(_ => CostCenter.getCurrent.map(_.render))
   *     .runCollect
   * // Chunk("numbers", "numbers")
   * }}}
   */
  def withChildCostCenterStream[R, E, A](name: String)(stream: ZStream[R, E, A])(implicit
    trace: Trace
  ): ZStream[R, E, A] =
    ZStream.scoped[R](globalRef.locallyScopedWith(_ / name)) *> stream

  private final val globalRef: FiberRef[CostCenter] =
    Unsafe.unsafe(implicit u => FiberRef.unsafe.make(CostCenter.Root, identity, (old, _) => old))
}
