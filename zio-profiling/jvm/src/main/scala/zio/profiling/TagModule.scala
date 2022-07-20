package zio.profiling

import scala.language.implicitConversions

import zio.{Tag => _, _}

trait TagModule {
  import TagModule._

  implicit def zioSyntax[R, E, A](zio: ZIO[R, E, A]): TagZIOSyntax[R, E, A] =
    new TagZIOSyntax(zio)

  def withTag(name: String): WithTagPartiallyApplied =
    new WithTagPartiallyApplied(name)

  val Root: Tag = Tag.Root
}

object TagModule extends TagModule {

  final class WithTagPartiallyApplied(name: String) {
    def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
      TagRef.locallyWith(_ / name)(zio)
  }

  final class TagZIOSyntax[R, E, A](val zio: ZIO[R, E, A]) extends AnyVal {
    def <#(name: String): ZIO[R, E, A] =
      withTag(name)(zio)
  }
}
