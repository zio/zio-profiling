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
 * argument of type `ZIO` or `ZStream` is tagged too, under the name of the method the compiler makes for it. For a
 * method parameter that is `shop.Orders.find\$default\$1(Orders.scala:16)` on both versions. For a class or case-class
 * constructor parameter it is `shop.Item.<init>\$default\$1(Item.scala:3)` (or `shop.Item.apply\$default\$1` when the
 * case class is built with `Item()`) on Scala 2, and `shop.Item.\$lessinit\$greater\$default\$1(Item.scala:3)` on Scala 3.
 * Other types are not tagged, for example `ZLayer`, `Exit` and `Option[UIO[Int]]`.
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
class TaggingPlugin extends StandardPlugin {

  /** The plugin name, `zio-profiling-tagging`. It is also the name of its compiler phase. */
  val name: String = "zio-profiling-tagging"

  /** The plugin description, `automatically tag zio effects`. */
  override val description: String = "automatically tag zio effects"

  /** Returns the one compiler phase of the plugin, a new [[TaggingPhase]]. The `options` are ignored. */
  def init(options: List[String]): List[PluginPhase] = List(new TaggingPhase())
}
