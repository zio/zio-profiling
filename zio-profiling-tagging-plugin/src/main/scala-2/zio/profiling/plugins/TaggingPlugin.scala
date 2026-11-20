package zio.profiling.plugins

import scala.tools.nsc

import nsc.Global
import nsc.plugins.{Plugin, PluginComponent}
import nsc.transform.{Transform, TypingTransformers}

/**
 * The compiler plugin that tags ZIO effects and streams with cost centers, so that the profilers in `zio-profiling`
 * report them by name. It is published for Scala 2.12, 2.13 and 3, with one implementation for Scala 2 and one for
 * Scala 3.
 *
 * Enable it in each module whose code you want to see by name. The rewritten code calls `zio.profiling.CostCenter`, so
 * the module also needs `zio-profiling`. Without it, compiling a tagged definition fails: Scala 2 reports a
 * `MissingRequirementError` for `zio.profiling.CostCenter`, and Scala 3 reports a compiler crash.
 *
 * {{{
 * // no-check: sbt build setting, not Scala code
 * libraryDependencies += "dev.zio" %% "zio-profiling" % "<version>"
 * libraryDependencies += compilerPlugin("dev.zio" %% "zio-profiling-tagging-plugin" % "<version>")
 * }}}
 *
 * The plugin rewrites the right-hand side of a `def`, `val`, `lazy val` or `var` whose type, after type aliases such as
 * `UIO` or `Task` are expanded, is `ZIO[R, E, A]` or `ZStream[R, E, A]`. The type may be written or inferred, and the
 * definition may be a member of an object, a class, a trait or an anonymous class, or a local definition inside a
 * method. The source files are not changed; the compiler sees the definition as if it were written like this:
 *
 * {{{
 * // no-check: shows the code the compiler sees, not code to write
 * // def total: UIO[Int] = ZIO.succeed(42), on line 12 of Orders.scala in object shop.Orders, becomes
 * def total: UIO[Int] = CostCenter.withChildCostCenter("shop.Orders.total(Orders.scala:12)")(ZIO.succeed(42))
 * }}}
 *
 * A `ZStream` is wrapped with `CostCenter.withChildCostCenterStream` instead. The name is the full name of the
 * definition, then the file name and line in parentheses. A local definition is named after the enclosing class or
 * object (`shop.Orders.subtotal`), without the methods around it. A tagged definition that runs another one gives a
 * nested cost center, such as `shop.Orders.report(Orders.scala:20);shop.Orders.total(Orders.scala:12)`. A default
 * argument of type `ZIO` or `ZStream` in a method is tagged too, under the name of the method the compiler makes for it
 * (`shop.Orders.find\$default\$1(Orders.scala:16)`). Other types are not tagged, for example `ZLayer`, `Exit` and
 * `Option[UIO[Int]]`.
 *
 * To see the rewritten code, compile with `-Xprint:zio-profiling-tagging` on Scala 2 or `-Vprint:zio-profiling-tagging`
 * on Scala 3. To turn the plugin off for a compilation, pass `-Xplugin-disable:zio-profiling-tagging`.
 *
 * Scala 2 and Scala 3 differ in these cases:
 *   - Anonymous functions whose result type is `ZIO` or `ZStream` are tagged on Scala 3, with names such as
 *     `shop.Orders.\$anonfun(Orders.scala:30)`. So in `ZIO.foreach(ids)(id => ZIO.succeed(id))`, each effect from the
 *     function runs in a nested cost center named after the function. On Scala 2 such functions are not tagged, and
 *     their effects run in the cost center that is current where they run; in this example, the cost center of the
 *     enclosing definition.
 *   - A known bug on Scala 2: a `val` or `var` that is a member of a class or object and has a getter is tagged twice
 *     with the same name (`shop.Orders.pending(Orders.scala:14);shop.Orders.pending(Orders.scala:14)`), because both
 *     the field and its getter are rewritten. A `private[this] val` (it has no getter) and a `val` in a trait are
 *     tagged once. On Scala 3 each of them is tagged once, and on both versions a `def` or `lazy val` is tagged once.
 *   - A class parameter declared with `val`, such as a case-class field (`final case class Item(eff: UIO[Int])`), is
 *     tagged on Scala 2 through its getter, with the field's name (`shop.Item.eff(Item.scala:3)`). On Scala 3 it is not
 *     tagged.
 *   - An annotation or modifier on a line above a definition counts as its first line on Scala 3, so the name holds
 *     that line. On Scala 2 it holds the line of the definition's name.
 *   - The plugin takes no options. On Scala 2, `-P:zio-profiling-tagging:<option>` fails the compilation with "Error:
 *     zio-profiling-tagging takes no options"; Scala 3 ignores such options.
 *
 * @see
 *   [[https://zio.dev/zio-profiling/ ZIO Profiling]]
 */
class TaggingPlugin(val global: Global) extends Plugin {
  import global._

  /** The plugin name, `zio-profiling-tagging`. It is also the name of its compiler phase. */
  val name = "zio-profiling-tagging"

  /** The plugin description, `automatically tag zio effects`. */
  val description = "automatically tag zio effects"

  /**
   * The one compiler phase of the plugin, named `zio-profiling-tagging`. It runs after `typer` and before `patmat`, and
   * rewrites the definitions as described on [[TaggingPlugin]].
   */
  val components: List[PluginComponent] = List[PluginComponent](TaggingComponent)

  private object TaggingComponent extends PluginComponent with Transform with TypingTransformers {
    val global: TaggingPlugin.this.global.type = TaggingPlugin.this.global

    val runsAfter: List[String] = List("typer")

    override val runsBefore: List[String] = List("patmat")

    val phaseName: String = TaggingPlugin.this.name

    def newTransformer(unit: CompilationUnit): Transformer = new TaggingTransformer(unit)

    class TaggingTransformer(unit: CompilationUnit) extends TypingTransformer(unit) {
      override def transform(tree: Tree): Tree = tree match {
        case valDef @ ValDef(_, _, TaggableTypeTree(taggingTarget), rhs) if rhs.nonEmpty =>
          val transformedRhs = tagEffectTree(descriptiveName(tree), rhs, taggingTarget)
          val typedRhs       = localTyper.typed(transformedRhs)
          val updated        = treeCopy.ValDef(tree, valDef.mods, valDef.name, valDef.tpt, rhs = typedRhs)

          super.transform(updated)
        case defDef @ DefDef(_, _, _, _, TaggableTypeTree(taggingTarget), rhs) if rhs.nonEmpty =>
          val transformedRhs = tagEffectTree(descriptiveName(tree), rhs, taggingTarget)
          val typedRhs       = localTyper.typed(transformedRhs)
          val updated        =
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

      private def tagEffectTree(name: String, tree: Tree, taggingTarget: TaggingTarget): Tree = {
        val costCenterModule = rootMirror.getRequiredModule("_root_.zio.profiling.CostCenter")
        val traceModule      = rootMirror.getRequiredModule("_root_.zio.Trace")

        taggingTarget match {
          case ZioTaggingTarget(t1, t2, t3) =>
            q"$costCenterModule.withChildCostCenter[$t1,$t2,$t3]($name)($tree)($traceModule.empty)"
          case ZStreamTaggingTarget(t1, t2, t3) =>
            q"$costCenterModule.withChildCostCenterStream[$t1,$t2,$t3]($name)($tree)($traceModule.empty)"
        }

      }

      private sealed trait TaggingTarget

      private case class ZioTaggingTarget(rType: Type, eType: Type, aType: Type)     extends TaggingTarget
      private case class ZStreamTaggingTarget(rType: Type, eType: Type, aType: Type) extends TaggingTarget

      private object TaggableTypeTree {
        private def zioTypeRef: Type = rootMirror.getRequiredClass("_root_.zio.ZIO").tpe

        private def zStreamTypeRef: Type = rootMirror.getRequiredClass("_root_.zio.stream.ZStream").tpe

        def unapply(tpt: Tree): Option[TaggingTarget] =
          tpt.tpe.dealias match {
            case TypeRef(_, sym, t1 :: t2 :: t3 :: Nil) if sym == zioTypeRef.typeSymbol =>
              Some(ZioTaggingTarget(t1, t2, t3))
            case TypeRef(_, sym, t1 :: t2 :: t3 :: Nil) if sym == zStreamTypeRef.typeSymbol =>
              Some(ZStreamTaggingTarget(t1, t2, t3))
            case _ => None
          }

      }
    }
  }
}
