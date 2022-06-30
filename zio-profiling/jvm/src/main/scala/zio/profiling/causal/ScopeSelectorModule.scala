package zio.profiling.causal

import scala.language.implicitConversions

import zio.profiling.Tag

trait ScopeSelectorModule {
  import ScopeSelectorModule._

  val *  = Pattern.All
  val ** = Pattern.Recursive

  implicit def tagSyntax(tag: Tag): ScopeSelectorTagSyntax =
    new ScopeSelectorTagSyntax(tag)
}

object ScopeSelectorModule extends ScopeSelectorModule {

  sealed trait Pattern

  object Pattern {

    case object All       extends Pattern
    case object Recursive extends Pattern

  }

  final class ScopeSelectorTagSyntax(val tag: Tag) extends AnyVal {
    def /(pattern: Pattern): ScopeSelector =
      pattern match {
        case Pattern.All       => ScopeSelector.below(tag)
        case Pattern.Recursive => ScopeSelector.belowRecursive(tag)
      }
  }

}
