import sbt._
import Keys._
import sbtbuildinfo._
import BuildInfoKeys._
import scalafix.sbt.ScalafixPlugin.autoImport._
import Dependencies.{silencerVersion, organizeImportsVersion}

object BuildHelper {
  private val versions: Map[String, String] = {
    import org.snakeyaml.engine.v2.api.{Load, LoadSettings}

    import java.util.{List => JList, Map => JMap}
    import scala.jdk.CollectionConverters._

    val doc = new Load(LoadSettings.builder().build())
      .loadFromReader(scala.io.Source.fromFile(".github/workflows/ci.yml").bufferedReader())

    val yaml = doc.asInstanceOf[JMap[String, JMap[String, JMap[String, JMap[String, JMap[String, JList[String]]]]]]]

    val list = yaml.get("jobs").get("test").get("strategy").get("matrix").get("scala").asScala

    list.map { v =>
      val vs  = v.split('.')
      val len = if (vs(0) == "2") 2 else 1

      (vs.take(len).mkString("."), v)
    }.toMap
  }

  val Scala212 = versions("2.12")
  val Scala213 = versions("2.13")
  val Scala3   = versions("3")

  def stdSettings(prjName: String) =
    Seq(
      name                     := s"$prjName",
      crossScalaVersions       := List(Scala212, Scala213, Scala3),
      ThisBuild / scalaVersion := Scala213,
      scalacOptions            := stdOptions ++ extraOptions(scalaVersion.value, optimize = !isSnapshot.value),
      libraryDependencies ++= {
        if (scalaVersion.value == Scala3)
          Seq(
            "com.github.ghik" % s"silencer-lib_$Scala213" % silencerVersion % Provided
          )
        else
          Seq(
            "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full,
            compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full)
          )
      },
      semanticdbEnabled := scalaVersion.value != Scala3,
      semanticdbOptions += "-P:semanticdb:synthetics:on",
      semanticdbVersion                                          := scalafixSemanticdb.revision,
      ThisBuild / scalafixScalaBinaryVersion                     := CrossVersion.binaryScalaVersion(scalaVersion.value),
      ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % organizeImportsVersion,
      Compile / fork                                             := true,
      Test / fork                                                := true,
      Test / parallelExecution                                   := true,
      incOptions ~= (_.withLogRecompileOnMacro(false)),
      autoAPIMappings  := true,
      buildInfoKeys    := Seq[BuildInfoKey](organization, moduleName, name, version, scalaVersion, sbtVersion, isSnapshot),
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
        case Some((3, _))  =>
          Seq("org.scala-lang" %% "scala3-compiler" % scalaVersion.value % "provided")
        case _             =>
          Seq.empty
      }
    }
  )

  def extraSourceDirectorySettings = Seq(
    Compile / unmanagedSourceDirectories ++= {
      extraSourceDirectories(
        sourceDirectory.value,
        scalaVersion.value,
        "main",
      )
    },
    Test / unmanagedSourceDirectories ++= {
      extraSourceDirectories(
        sourceDirectory.value,
        scalaVersion.value,
        "test",
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
      case Some((3, _))  =>
        List("3")
      case _             =>
        List()
    }

    for {
      version  <- "scala" :: versions.toList.map("scala-" + _)
      result    = baseDirectory.getParentFile / "src" / conf / version
      if result.exists
    } yield result
  }
}
