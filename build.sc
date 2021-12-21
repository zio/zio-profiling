import mill._
import scalalib._
import mill.scalalib.scalafmt.ScalafmtModule

import $ivy.`com.goyeau::mill-scalafix:0.2.6`
import com.goyeau.mill.scalafix.ScalafixModule

val projectDir = build.millSourcePath

object Deps {
  val silencerVersion = "1.7.7"
  val zioVersion = "2.0.0-RC1"

  val silencer = ivy"com.github.ghik:::silencer-lib:$silencerVersion"
  val silencerPlugin = ivy"com.github.ghik:::silencer-lib:$silencerVersion"

  val zio = ivy"dev.zio::zio:$zioVersion"
  val zioTest = ivy"dev.zio::zio-test:$zioVersion"
  val zioTestSbt = ivy"dev.zio::zio-test-sbt:$zioVersion"
}

trait ZIOModule extends SbtModule with ScalafmtModule with ScalafixModule { outer =>
  def scalaVersion = "2.13.7"
  def scalafixScalaBinaryVersion = "2.13"

  override def scalacOptions = Seq(
    "-deprecation",
    "-Ywarn-unused",
    "-encoding",
    "UTF-8",
    "-feature",
    "-language:higherKinds",
    "-language:existentials",
    "-unchecked",
    "-Wunused:imports",
    "-Wvalue-discard",
    "-Wunused:patvars",
    "-Wunused:privates",
    "-Wvalue-discard"
  )

  override def scalacPluginIvyDeps = T {
    super.scalacPluginIvyDeps() ++
      Agg(Deps.silencerPlugin)
  }

  trait Tests extends super.Tests with ScalafmtModule with ScalafixModule  {
    def scalaVersion = outer.scalaVersion
    def scalafixScalaBinaryVersion = outer.scalafixScalaBinaryVersion

    override def testFramework: T[String] = "zio.test.sbt.ZTestFramework"

    override def ivyDeps = Agg(Deps.zioTest, Deps.zioTestSbt)
  }

}

object zio extends Module {

  object profiling extends ZIOModule {

    override def ivyDeps = T { Agg(Deps.zio, Deps.silencer)}

    override def millSourcePath = projectDir / "zio-profiling" / "jvm"
    override def artifactName: T[String] = T{"zio-profiling"}

    object test extends super.Tests {}
  }
}