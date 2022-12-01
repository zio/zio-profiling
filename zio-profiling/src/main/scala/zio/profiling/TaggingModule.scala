package zio.profiling

import zio._

import scala.language.implicitConversions

trait TaggingModule {
  import TaggingModule._

  implicit def zioSyntax[R, E, A](zio: ZIO[R, E, A]): TagZIOSyntax[R, E, A] =
    new TagZIOSyntax(zio)

  /**
   * Run an effect with an adjusted [[zio.profiling.CostCenter]]. This is equivalent to:
   * {{{
   * CostCenter.withCostCenter(_ / name)(zio)
   * }}}
   */
  def withTag(name: String): WithTagPartiallyApplied =
    new WithTagPartiallyApplied(name)

  val Root: CostCenter.Root.type = CostCenter.Root
}

object TaggingModule extends TaggingModule {

  final class WithTagPartiallyApplied(name: String) {
    def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      CostCenter.withCostCenter(_ / name)(zio)
  }

  final class TagZIOSyntax[R, E, A](val zio: ZIO[R, E, A]) extends AnyVal {

    /**
     * Run an effect with an adjusted [[zio.profiling.CostCenter]].
     *
     * {{{
     * for {
     *   _ <- ZIO.succeed(Thread.sleep(20)) <# "short" // code attributed to cost center `Root / "short"`
     *   _ <- ZIO.succeed(Thread.sleep(40)) <# "long" // code attributes to cost center `Root / "long"`
     * } yield ()
     * }}}
     *
     * This is equivalent to:
     * {{{
     * CostCenter.withCostCenter(_ / name)(zio)
     * }}}
     */
    def <#(name: String)(implicit trace: Trace): ZIO[R, E, A] =
      withTag(name)(zio)

    /**
     * A named alias for `<#`.
     */
    def tagged(name: String)(implicit trace: Trace): ZIO[R, E, A] =
      zio <# name
  }
}
