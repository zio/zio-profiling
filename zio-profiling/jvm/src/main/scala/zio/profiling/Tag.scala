package zio.profiling

import zio.Trace

sealed trait Tag { self =>
  import Tag._

  def isValid: Boolean = self match {
    case TaggedLocation(_, location) if location == Trace.empty => false
    case _                                                      => true
  }

  def /(name: String): Tag =
    Child(self, name)

  def #>(location: Trace): TaggedLocation =
    TaggedLocation(self, location)

  def isPrefixOf(other: Tag): Boolean =
    self == other || (other match {
      case Root                      => false
      case Child(parent, _)          => self.isPrefixOf(parent)
      case TaggedLocation(parent, _) => self.isPrefixOf(parent)
    })

  def getDirectDescendant(other: Tag): Option[Tag] =
    other match {
      case Root                      => None
      case Child(parent, _)          =>
        if (self == parent) Some(other)
        else getDirectDescendant(parent)
      case TaggedLocation(parent, _) =>
        if (self == parent) Some(other)
        else getDirectDescendant(parent)
    }

  def render: String = {
    def renderLocation(location: Trace) =
      if (location == Trace.empty) "unknown_location" else location.toString

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

object Tag {
  object Root                                                   extends Tag
  final case class Child(parent: Tag, name: String)             extends Tag
  final case class TaggedLocation(parent: Tag, location: Trace) extends Tag
}
