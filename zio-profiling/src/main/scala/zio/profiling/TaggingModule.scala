package zio.profiling

import zio._

import scala.language.implicitConversions

trait TaggingModule {
  import TaggingModule._

  implicit def zioSyntax[R, E, A](zio: ZIO[R, E, A]): TagZIOSyntax[R, E, A] =
    new TagZIOSyntax(zio)

  def withTag(name: String): WithTagPartiallyApplied =
    new WithTagPartiallyApplied(name)

  val Root: CostCenter.Root.type = CostCenter.Root
}

object TaggingModule extends TaggingModule {

  final class WithTagPartiallyApplied(name: String) {
    def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      CostCenterRef.locallyWith(_ / name)(zio)
  }

  final class TagZIOSyntax[R, E, A](val zio: ZIO[R, E, A]) extends AnyVal {
    def <#(name: String)(implicit trace: Trace): ZIO[R, E, A] =
      withTag(name)(zio)
  }
}
