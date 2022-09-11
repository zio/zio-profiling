package zio.profiling.causal

import scala.language.implicitConversions

import zio.profiling.CostCenter

trait ScopeSelectorModule {
  import ScopeSelectorModule._

  val *  = Pattern.All
  val ** = Pattern.Recursive

  implicit def tagSyntax(tag: CostCenter): ScopeSelectorTagSyntax =
    new ScopeSelectorTagSyntax(tag)
}

object ScopeSelectorModule extends ScopeSelectorModule {

  sealed trait Pattern

  object Pattern {

    case object All       extends Pattern
    case object Recursive extends Pattern

  }

  final class ScopeSelectorTagSyntax(val tag: CostCenter) extends AnyVal {
    def /(pattern: Pattern): CandidateSelector =
      pattern match {
        case Pattern.All       => CandidateSelector.below(tag)
        case Pattern.Recursive => CandidateSelector.belowRecursive(tag)
      }
  }

}
