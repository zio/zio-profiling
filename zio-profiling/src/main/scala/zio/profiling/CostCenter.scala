package zio.profiling

import zio.{Fiber, Trace, Unsafe}

sealed trait CostCenter { self =>
  import CostCenter._

  def isRoot: Boolean = self match {
    case Root => true
    case _    => false
  }

  def /(name: String): CostCenter =
    Child(self, name)

  def #>(location: Trace): TaggedLocation =
    TaggedLocation(self, location)

  def isChildOf(other: CostCenter): Boolean =
    self match {
      case Root             => false
      case Child(parent, _) => parent == other
    }

  def isDescendantOf(other: CostCenter): Boolean =
    self == other || (self match {
      case Root             => false
      case Child(parent, _) => parent.isDescendantOf(other)
    })

  def getOneLevelDownFrom(other: CostCenter): Option[CostCenter] =
    other match {
      case Root => None
      case Child(parent, _) =>
        if (self == parent) Some(other)
        else getOneLevelDownFrom(parent)
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
  object Root                                              extends CostCenter
  final case class Child(parent: CostCenter, name: String) extends CostCenter

  def getCurrent(fiber: Fiber.Runtime[_, _])(implicit unsafe: Unsafe): CostCenter =
    fiber.unsafe.getFiberRefs().getOrDefault(CostCenterRef)
}
