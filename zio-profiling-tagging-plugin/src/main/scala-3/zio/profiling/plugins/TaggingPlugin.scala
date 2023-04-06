package zio.profiling.plugins

import dotty.tools.dotc.ast.Trees.*
import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.core.Constants.Constant
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.core.Decorators.*
import dotty.tools.dotc.core.StdNames.*
import dotty.tools.dotc.core.Symbols.*
import dotty.tools.dotc.plugins.{PluginPhase, StandardPlugin}
import dotty.tools.dotc.transform.{Pickler, Staging}

class TaggingPlugin extends StandardPlugin {
  val name: String                 = "zio-profiling-tagging"
  override val description: String = "automatically tag zio effects"

  def init(options: List[String]): List[PluginPhase] = List(TaggingPhase)
}
