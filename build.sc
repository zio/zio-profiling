import mill._
import mill.scalalib._
import mill.modules.Jvm

// Scalafix and Scala Format
import mill.scalalib.scalafmt.ScalafmtModule
import $ivy.`com.goyeau::mill-scalafix:0.2.6`
import com.goyeau.mill.scalafix.ScalafixModule

// Add simple mdoc support for mill
import $ivy.`de.wayofquality.blended::de.wayofquality.blended.mill.mdoc::0.0.1-4-0ce9fb`
import de.wayofquality.mill.mdoc.MDocModule

// Add simple docusaurus2 support for mill
import $ivy.`de.wayofquality.blended::de.wayofquality.blended.mill.docusaurus2::0.0.0-4-cc55b8`
import de.wayofquality.mill.docusaurus2.Docusaurus2Module

import mill.define.Sources
import os.Path

// It's convenient to keep the base project directory around
val projectDir = build.millSourcePath

object Deps {
  val scalaVersion = "2.13.7"

  val silencerVersion = "1.7.7"
  val zioVersion = "2.0.0-RC1"

  val silencer = ivy"com.github.ghik:::silencer-lib:$silencerVersion"
  val silencerPlugin = ivy"com.github.ghik:::silencer-lib:$silencerVersion"

  val zio = ivy"dev.zio::zio:$zioVersion"
  val zioTest = ivy"dev.zio::zio-test:$zioVersion"
  val zioTestSbt = ivy"dev.zio::zio-test-sbt:$zioVersion"
}

trait ZIOModule extends SbtModule with ScalafmtModule with ScalafixModule { outer =>
  def scalaVersion = T(Deps.scalaVersion)
  def scalafixScalaBinaryVersion = T("2.13")

  override def scalacOptions = T(Seq(
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
  ))

  override def scalacPluginIvyDeps = T {
    super.scalacPluginIvyDeps() ++
      Agg(Deps.silencerPlugin)
  }

  trait Tests extends super.Tests with ScalafmtModule with ScalafixModule  {
    override def scalaVersion = outer.scalaVersion
    override def scalafixScalaBinaryVersion = outer.scalafixScalaBinaryVersion

    override def testFramework: T[String] = T("zio.test.sbt.ZTestFramework")

    override def ivyDeps = Agg(Deps.zioTest, Deps.zioTestSbt)
  }

}

object zio extends Module {

  object site extends Docusaurus2Module with MDocModule {
    override def scalaVersion = T(Deps.scalaVersion)
    override def mdocSources = T.sources{ projectDir / "docs" }
    override def docusaurusSources = T.sources(
      projectDir / "website",
    )

    override def watchedMDocsDestination: T[Option[Path]] = T(Some(docusaurusBuild().path / "docs"))

    override def compiledMdocs: Sources = T.sources(mdoc().path)
  }

  object profiling extends ZIOModule {

    override def ivyDeps = T { Agg(Deps.zio, Deps.silencer)}

    override def millSourcePath = projectDir / "zio-profiling" / "jvm"
    override def artifactName: T[String] = T{"zio-profiling"}

    object test extends super.Tests {}

    object examples extends ZIOModule {

      def runToyExample = T { runMain("zio.profiling.examples.CausalProfilerToyExample")}
      def runQueueExample = T { runMain("zio.profiling.examples.CausalProfilerProducerConsumerExample")}

      override def moduleDeps = Seq(profiling)

      override def millSourcePath: Path = projectDir / "zio-profiling-examples" / "jvm"
      override def artifactName: T[String] = T("zio-profiling-examples")
    }

  }
}
