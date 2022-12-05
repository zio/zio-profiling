package zio.profiling.plugins

import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.ast.Trees.*
import dotty.tools.dotc.core.Constants.Constant
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.core.Decorators.*
import dotty.tools.dotc.core.StdNames.*
import dotty.tools.dotc.core.Symbols.*
import dotty.tools.dotc.core.Types.*
import dotty.tools.dotc.plugins.{PluginPhase, StandardPlugin}
import dotty.tools.dotc.transform.{Pickler, Staging}
import dotty.tools.dotc.report
import dotty.tools.dotc.core.Types.TypeRef
import dotty.tools.dotc.ast.tpd.{TreeOps, Literal}
import dotty.tools.dotc.ast.untpd.Mod.Given.apply

object TaggingPhase extends PluginPhase {

  val phaseName = "zio-profiling-tagging"

  override val runsAfter = Set(Pickler.name)
  override val runsBefore = Set(Staging.name)

  override def transformValDef(tree: tpd.ValDef)(using Context): tpd.Tree = tree match {
    case ValDef(_, ZioTypeTree(t1, t2, t3), _) =>
      val transformedRhs = tagEffectTree(descriptiveName(tree), tree.rhs, t1, t2, t3)
      cpy.ValDef(tree)(rhs = transformedRhs)
    case _ =>
      tree
  }

  override def transformDefDef(tree: tpd.DefDef)(using Context): tpd.Tree = tree match {
    case DefDef(_, _, tpt @ ZioTypeTree(t1, t2, t3), _) =>
      val transformedRhs = tagEffectTree(descriptiveName(tree), tree.rhs, t1, t2, t3)
      cpy.DefDef(tree)(rhs = transformedRhs)
    case _ =>
      tree
  }

  private def descriptiveName(tree: tpd.Tree)(using ctx: Context): String = {
    val fullName = ctx.printer.fullNameString(tree.symbol)
    val sourceFile = tree.source.file.name
    val sourceLine = tree.sourcePos.startLine + 1

    s"$fullName($sourceFile:$sourceLine)"
  }

  private def tagEffectTree(name: String, tree: tpd.Tree, t1: Type, t2: Type, t3: Type)(using Context): tpd.Tree = {
    val costcenterSym = requiredModule("zio.profiling.CostCenter")
    val withChildCostCenterSym = costcenterSym.requiredMethod("withChildCostCenter")

    val traceSym = requiredModule("zio.Trace")
    val emptyTraceSym = traceSym.requiredMethodRef("empty")

    tpd.ref(withChildCostCenterSym)
      .appliedToTypes(List(t1, t2, t3))
      .appliedTo(tpd.Literal(Constant(name)))
      .appliedTo(tree)
      .appliedTo(tpd.ref(emptyTraceSym))
  }

  private object ZioTypeTree {
    private def zioTypeRef(using Context): TypeRef =
      requiredClassRef("zio.ZIO")

    def unapply(tp: Tree[Type])(using Context): Option[(Type, Type, Type)] =
      tp.tpe.dealias match {
        case AppliedType(at, t1 :: t2 :: t3 :: Nil) if at.isRef(zioTypeRef.symbol) => Some((t1, t2, t3))
        case _ => None
      }

  }

}
