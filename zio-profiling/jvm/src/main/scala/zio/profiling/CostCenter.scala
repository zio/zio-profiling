package zio.profiling

import zio.{Fiber, Trace}
import zio.Unsafe

sealed trait CostCenter { self =>
  import CostCenter._

  def isValid: Boolean = self match {
    case TaggedLocation(_, location) if location == Trace.empty => false
    case _                                                      => true
  }

  def /(name: String): CostCenter =
    Child(self, name)

  def #>(location: Trace): TaggedLocation =
    TaggedLocation(self, location)

  def isChildOf(other: CostCenter): Boolean =
    self match {
      case Root                      => false
      case Child(parent, _)          => parent == other
      case TaggedLocation(parent, _) => parent == other
    }

  def isDescendantOf(other: CostCenter): Boolean =
    self == other || (self match {
      case Root                      => false
      case Child(parent, _)          => parent.isDescendantOf(other)
      case TaggedLocation(parent, _) => parent.isDescendantOf(other)
    })

  def getOneLevelDownFrom(other: CostCenter): Option[CostCenter] =
    other match {
      case Root                      => None
      case Child(parent, _)          =>
        if (self == parent) Some(other)
        else getOneLevelDownFrom(parent)
      case TaggedLocation(parent, _) =>
        if (self == parent) Some(other)
        else getOneLevelDownFrom(parent)
    }

  def render: String = {
    def renderLocation(location: Trace) =
      if (location == Trace.empty) "<unknown_location>" else location.toString

    self match {
      case Root                             => ""
      case Child(parent, name)              =>
        if (parent == Root) name
        else s"${parent.render}>$name"
      case TaggedLocation(parent, location) =>
        if (parent == Root) renderLocation(location)
        else s"${parent.render}@${renderLocation(location)}"
    }
  }
}

object CostCenter {
  object Root                                                          extends CostCenter
  final case class Child(parent: CostCenter, name: String)             extends CostCenter
  final case class TaggedLocation(parent: CostCenter, location: Trace) extends CostCenter

  def getCurrent(fiber: Fiber.Runtime[_, _])(implicit unsafe: Unsafe): CostCenter =
    fiber.unsafe.getFiberRefs().getOrDefault(CostCenterRef)
}
