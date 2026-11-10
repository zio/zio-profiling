import sbt._
import Keys._
import sbtbuildinfo._
import BuildInfoKeys._
import scalafix.sbt.ScalafixPlugin.autoImport._

object BuildHelper {
  val Scala212 = "2.12.21"
  val Scala213 = "2.13.18"
  val Scala3   = "3.3.5"

  val defaultScalaVersion = Scala213

  def stdSettings(prjName: String) =
    Seq(
      name                     := s"$prjName",
      crossScalaVersions       := List(Scala212, Scala213, Scala3),
      ThisBuild / scalaVersion := defaultScalaVersion,
      scalacOptions            := stdOptions ++ extraOptions(scalaVersion.value, optimize = !isSnapshot.value),
      semanticdbEnabled        := scalaVersion.value == defaultScalaVersion,
      semanticdbOptions ++= (if (scalaVersion.value != Scala3) List("-P:semanticdb:synthetics:on") else Nil),
      semanticdbVersion        := scalafixSemanticdb.revision,
      Compile / fork           := true,
      Test / fork              := true,
      Test / parallelExecution := true,
      incOptions ~= (_.withLogRecompileOnMacro(false)),
      autoAPIMappings := true,
      buildInfoKeys   := Seq(organization, moduleName, name, version, scalaVersion, sbtVersion, isSnapshot).map(
        BuildInfoKey(_)
      ),
      buildInfoPackage := prjName
    )

  def macroDefinitionSettings = Seq(
    scalacOptions += "-language:experimental.macros",
    libraryDependencies ++= {
      if (scalaVersion.value == Scala3) Seq()
      else
        Seq(
          "org.scala-lang" % "scala-reflect"  % scalaVersion.value % "provided",
          "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided"
        )
    }
  )

  def pluginDefinitionSettings = Seq(
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) =>
          Seq("org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided")
        case Some((3, _)) =>
          Seq("org.scala-lang" %% "scala3-compiler" % scalaVersion.value % "provided")
        case _ =>
          Seq.empty
      }
    }
  )

  def extraSourceDirectorySettings = Seq(
    Compile / unmanagedSourceDirectories ++= {
      extraSourceDirectories(
        sourceDirectory.value,
        scalaVersion.value,
        "main"
      )
    },
    Test / unmanagedSourceDirectories ++= {
      extraSourceDirectories(
        sourceDirectory.value,
        scalaVersion.value,
        "test"
      )
    }
  )

  private val stdOptions =
    List("-deprecation", "-encoding", "UTF-8", "-feature", "-unchecked", "-Xfatal-warnings")

  private def extraOptions(scalaVersion: String, optimize: Boolean) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((3, _)) =>
        List("-language:implicitConversions", "-Xignore-scala2-macros")
      case Some((2, 13)) =>
        List("-Ywarn-unused:params,-implicits") ++ extra2xOptions ++ extraOptimizerOptions(optimize)
      case Some((2, 12)) =>
        List(
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          "-Ywarn-unused:_,imports",
          "-Ywarn-unused:imports",
          "-Ypartial-unification",
          "-Yno-adapted-args",
          "-Ywarn-inaccessible",
          "-Ywarn-infer-any",
          "-Ywarn-nullary-override",
          "-Ywarn-nullary-unit",
          "-Ywarn-unused:params,-implicits",
          "-Xfuture",
          "-Xsource:2.13",
          "-Xmax-classfile-name",
          "242"
        ) ++ extra2xOptions ++ extraOptimizerOptions(optimize)
      case _ => Nil
    }

  private val extra2xOptions =
    List(
      "-language:higherKinds",
      "-language:existentials",
      "-explaintypes",
      "-Yrangepos",
      "-Xlint:_,-missing-interpolator,-type-parameter-shadow",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard"
    )

  private def extraOptimizerOptions(optimize: Boolean): List[String] =
    if (optimize) List("-opt:l:inline", "-opt-inline-from:zio.internal.**") else Nil

  private def extraSourceDirectories(baseDirectory: File, scalaVer: String, conf: String) = {
    val versions = CrossVersion.partialVersion(scalaVer) match {
      case Some((2, _)) =>
        List("2")
      case Some((3, _)) =>
        List("3")
      case _ =>
        List()
    }

    for {
      version <- "scala" :: versions.toList.map("scala-" + _)
      result   = baseDirectory.getParentFile / "src" / conf / version
      if result.exists
    } yield result
  }
}
