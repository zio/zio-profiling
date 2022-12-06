package zio.profiling.plugins

import scala.tools.nsc

import nsc.Global
import nsc.plugins.Plugin
import nsc.plugins.PluginComponent
import nsc.transform.{Transform, TypingTransformers}

class TaggingPlugin(val global: Global) extends Plugin {
  import global._

  val name                              = "zio-profiling-tagging"
  val description                       = "automatically tag zio effects"
  val components: List[PluginComponent] = List[PluginComponent](TaggingComponent)

  private object TaggingComponent extends PluginComponent with Transform with TypingTransformers {
    val global: TaggingPlugin.this.global.type = TaggingPlugin.this.global

    val runsAfter: List[String] = List("typer")

    override val runsBefore: List[String] = List("patmat")

    val phaseName: String = TaggingPlugin.this.name

    def newTransformer(unit: CompilationUnit): Transformer = new TaggingTransformer(unit)

    class TaggingTransformer(unit: CompilationUnit) extends TypingTransformer(unit) {
      override def transform(tree: Tree): Tree = tree match {
        case valDef @ ValDef(_, _, ZioTypeTree(t1, t2, t3), rhs) =>
          val transformedRhs = tagEffectTree(descriptiveName(tree), rhs, t1, t2, t3)
          val typedRhs       = localTyper.typed(transformedRhs)
          val updated        = treeCopy.ValDef(tree, valDef.mods, valDef.name, valDef.tpt, rhs = typedRhs)
          super.transform(updated)
        case defDef @ DefDef(_, _, _, _, ZioTypeTree(t1, t2, t3), rhs) =>
          val transformedRhs = tagEffectTree(descriptiveName(tree), rhs, t1, t2, t3)
          val typedRhs       = localTyper.typed(transformedRhs)
          val updated =
            treeCopy.DefDef(tree, defDef.mods, defDef.name, defDef.tparams, defDef.vparamss, defDef.tpt, rhs = typedRhs)
          super.transform(updated)
        case _ =>
          super.transform(tree)
      }

      private def descriptiveName(tree: Tree): String = {
        val fullName   = tree.symbol.fullNameString
        val sourceFile = tree.pos.source.file.name
        val sourceLine = tree.pos.line

        s"$fullName($sourceFile:$sourceLine)"
      }

      private def tagEffectTree(name: String, tree: Tree, t1: Type, t2: Type, t3: Type): Tree = {
        val costCenterModule = rootMirror.getRequiredModule("_root_.zio.profiling.CostCenter")
        val traceModule      = rootMirror.getRequiredModule("_root_.zio.Trace")

        q"$costCenterModule.withChildCostCenter[$t1,$t2,$t3](${Literal(Constant(name))})($tree)($traceModule.empty)"
      }

      private object ZioTypeTree {
        private def zioTypeRef: Type =
          rootMirror.getRequiredClass("zio.ZIO").tpe

        def unapply(tpt: Tree): Option[(Type, Type, Type)] =
          tpt.tpe.dealias match {
            case TypeRef(_, sym, t1 :: t2 :: t3 :: Nil) if sym == zioTypeRef.typeSymbol => Some((t1, t2, t3))
            case _                                                                      => None
          }

      }
    }
  }
}
