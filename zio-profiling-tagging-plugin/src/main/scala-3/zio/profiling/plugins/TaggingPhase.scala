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

  override def transformValDef(tree: tpd.ValDef)(using Context): tpd.Tree = {
    tree match {
      case ValDef(_, TaggableTypeTree(taggingTarget), rhs) if !tree.rhs.isEmpty =>
        val transformedRhs = tagEffectTree(descriptiveName(tree), tree.rhs, taggingTarget)
        cpy.ValDef(tree)(rhs = transformedRhs)
      case _ =>
        tree
    }
  }

  override def transformDefDef(tree: tpd.DefDef)(using Context): tpd.Tree = tree match {
    case DefDef(_, _, TaggableTypeTree(taggingTarget), rhs) if !tree.rhs.isEmpty =>
      val transformedRhs = tagEffectTree(descriptiveName(tree), tree.rhs, taggingTarget)
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

  private def tagEffectTree(name: String, tree: tpd.Tree, taggingTarget: TaggingTarget)(using Context): tpd.Tree = {
    val costcenterSym = requiredModule("zio.profiling.CostCenter")
    val traceSym = requiredModule("zio.Trace")
    val emptyTraceSym = traceSym.requiredMethodRef("empty")

    taggingTarget match {
      case ZioTaggingTarget(t1, t2, t3) =>
        val withChildCostCenterSym = costcenterSym.requiredMethod("withChildCostCenter")

        tpd.ref(withChildCostCenterSym)
          .appliedToTypes(List(t1, t2, t3))
          .appliedTo(tpd.Literal(Constant(name)))
          .appliedTo(tree)
          .appliedTo(tpd.ref(emptyTraceSym))

      case ZStreamTaggingTarget(t1, t2, t3) =>
        val withChildCostCenterSym = costcenterSym.requiredMethod("withChildCostCenterStream")

        tpd.ref(withChildCostCenterSym)
          .appliedToTypes(List(t1, t2, t3))
          .appliedTo(tpd.Literal(Constant(name)))
          .appliedTo(tree)
          .appliedTo(tpd.ref(emptyTraceSym))
      }
  }

  private sealed trait TaggingTarget

  private case class ZioTaggingTarget(rType: Type, eType: Type, aType: Type)     extends TaggingTarget
  private case class ZStreamTaggingTarget(rType: Type, eType: Type, aType: Type) extends TaggingTarget

  private object TaggableTypeTree {
    private def zioTypeRef(using Context): TypeRef = requiredClassRef("zio.ZIO")

    private def zStreamTypeRef(using Context): TypeRef = requiredClassRef("stream.ZStream")

    def unapply(tp: Tree[Type])(using Context): Option[TaggingTarget] =
      tp.tpe.dealias match {
        case AppliedType(at, t1 :: t2 :: t3 :: Nil) if at.isRef(zioTypeRef.symbol) => Some(ZioTaggingTarget(t1, t2, t3))
        case AppliedType(at, t1 :: t2 :: t3 :: Nil) if at.isRef(zStreamTypeRef.symbol) => Some(ZStreamTaggingTarget(t1, t2, t3))
        case _ => None
      }

  }

}
