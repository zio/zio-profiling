import Keys.{`package` => packageTask}

import BuildHelper._
import Dependencies._

inThisBuild(
  List(
    organization := "dev.zio",
    homepage     := Some(url("https://zio.dev/zio-profiling/")),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer("jdegoes", "John De Goes", "john@degoes.net", url("http://degoes.net")),
      Developer("mschuwalow", "Maxim Schuwalow", "maxim.schuwalow@gmail.com", url("https://github.com/mschuwalow"))
    )
  )
)

addCommandAlias("compileSources", "core/Test/compile; taggingPlugin/compile; examples/compile")
addCommandAlias("testAll", "core/test")

addCommandAlias("check", "fixCheck; fmtCheck")
addCommandAlias("fix", "scalafixAll")
addCommandAlias("fixCheck", "scalafixAll --check")
addCommandAlias("fmt", "all scalafmtSbt scalafmtAll")
addCommandAlias("fmtCheck", "all scalafmtSbtCheck scalafmtCheckAll")
addCommandAlias("prepare", "fix; fmt")

lazy val root = project
  .in(file("."))
  .settings(publish / skip := true)
  .aggregate(core, taggingPlugin, examples, benchmarks)

lazy val core = project
  .in(file("zio-profiling"))
  .settings(
    stdSettings("zio-profiling"),
    libraryDependencies ++= Seq(
      "dev.zio"                %% "zio"                     % zioVersion,
      "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatVersion,
      "dev.zio"                %% "zio-test"                % zioVersion % Test,
      "dev.zio"                %% "zio-test-sbt"            % zioVersion % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val taggingPlugin = project
  .in(file("zio-profiling-tagging-plugin"))
  .settings(
    stdSettings("zio-profiling-tagging-plugin"),
    extraSourceDirectorySettings,
    pluginDefinitionSettings
  )

lazy val taggingPluginJar = taggingPlugin / Compile / packageTask

lazy val examples = project
  .in(file("examples"))
  .dependsOn(core, taggingPlugin % "plugin")
  .settings(
    stdSettings("examples"),
    publish / skip := true,
    scalacOptions += s"-Xplugin:${taggingPluginJar.value.getAbsolutePath}"
  )

lazy val benchmarks = project
  .in(file("benchmarks"))
  .dependsOn(core)
  .enablePlugins(JmhPlugin)
  .settings(
    stdSettings("examples"),
    publish / skip := true
  )

lazy val docs = project
  .in(file("zio-profiling-docs"))
  .dependsOn(core)
  .enablePlugins(WebsitePlugin)
  .settings(
    publish / skip := true,
    moduleName     := "zio-profiling-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    projectName := "ZIO Profiling",
    badgeInfo := Some(
      BadgeInfo(
        artifact = "zio-profiling_2.12",
        projectStage = ProjectStage.Concept
      )
    ),
    docsPublishBranch := "master"
  )
