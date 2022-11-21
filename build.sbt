import BuildHelper._
import Dependencies._

inThisBuild(
  List(
    organization := "dev.zio",
    homepage     := Some(url("https://zio.github.io/zio-profiling/")),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer("jdegoes", "John De Goes", "john@degoes.net", url("http://degoes.net")),
      Developer("mschuwalow", "Maxim Schuwalow", "maxim.schuwalow@gmail.com", url("https://github.com/mschuwalow"))
    )
  )
)

addCommandAlias("compileSources", "zioProfiling/Test/compile; zioProfilingExamples/Test/compile")
addCommandAlias("testAll", "zioProfiling/test")

addCommandAlias("check", "fixCheck; fmtCheck")
addCommandAlias("fix", "scalafixAll")
addCommandAlias("fixCheck", "scalafixAll --check")
addCommandAlias("fmt", "all scalafmtSbt scalafmtAll")
addCommandAlias("fmtCheck", "all scalafmtSbtCheck scalafmtCheckAll")
addCommandAlias("prepare", "fix; fmt")

lazy val root = project
  .in(file("."))
  .settings(
    publish / skip := true
  )
  .aggregate(zioProfiling, examples)

lazy val zioProfiling = project
  .in(file("zio-profiling"))
  .settings(stdSettings("zio-profiling"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"                %% "zio"                     % zioVersion,
      "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatVersion,
      "dev.zio"                %% "zio-test"                % zioVersion % Test,
      "dev.zio"                %% "zio-test-sbt"            % zioVersion % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val examples = project
  .in(file("examples"))
  .settings(stdSettings("examples"))
  .settings(
    publish / skip := true
  )
  .dependsOn(zioProfiling)

lazy val docs = project
  .in(file("zio-profiling-docs"))
  .enablePlugins(WebsitePlugin)
  .settings(
    publish / skip := true,
    moduleName     := "zio-profiling-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings"
  )
  .dependsOn(zioProfiling)
