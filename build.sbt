import Keys.{`package` => packageTask}

import BuildHelper._
import Dependencies._

import zio.sbt.githubactions.Step

inThisBuild(
  List(
    organization := "dev.zio",
    homepage     := Some(url("https://zio.dev/zio-profiling/")),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers   := List(
      Developer("mschuwalow", "Maxim Schuwalow", "maxim.schuwalow@gmail.com", url("https://github.com/mschuwalow"))
    ),
    versionScheme := Some("early-semver"),
    // CI workflow generation (`sbt ciGenerateGithubWorkflow`)
    ciEnabledBranches     := Seq("master"),
    ciEnableScalaSteward  := false,
    ciTargetJavaVersions  := Seq("17", "21"),
    ciTargetScalaVersions := Map(
      "core"               -> Seq(Scala212, Scala213, Scala3),
      "taggingPluginTests" -> Seq(Scala212, Scala213, Scala3)
    ),
    // `docs/buildWebsite` fails on CI ("Failed to build the website!", with no npm output) when sbt launches
    // `npm run build` itself, although the same command succeeds when run directly. So sbt only installs the website
    // and compiles the docs, and the Docusaurus build runs as a plain shell step.
    ciCheckWebsiteBuildProcess := Seq(
      Step.SingleStep(
        name = "Check website build process",
        run = Some(
          Seq("docs/clean", "docs/installWebsite", "docs/compileDocs")
            .map("sbt --no-colors --batch " + _)
            .mkString("", "; ", "; cd website; npm run build")
        )
      )
    ),
    ciCheckArtifactsCompilationSteps := Seq(
      Step.SingleStep(
        name = "Compile sources",
        run = Some(
          Seq(Scala212, Scala213, Scala3).map(v => s""""++$v! compileSources"""").mkString("sbt --no-colors ", " ", "")
        )
      )
    )
  )
)

addCommandAlias(
  "compileSources",
  "core/Test/compile; taggingPlugin/compile; taggingPluginTests/compile; examples/compile; benchmarks/compile;"
)
addCommandAlias("testAll", "core/test; taggingPluginTests/test")

addCommandAlias("lint", "check")
addCommandAlias("check", "fixCheck; fmtCheck")
addCommandAlias("fix", "scalafixAll")
addCommandAlias("fixCheck", "scalafixAll --check")
addCommandAlias("fmt", "all scalafmtSbt scalafmtAll")
addCommandAlias("fmtCheck", "all scalafmtSbtCheck scalafmtCheckAll")
addCommandAlias("prepare", "fix; fmt")

// Scala 3 already picks the plugin up from the "plugin" dependency configuration; passing -Xplugin again fails with
// "Setting -Xplugin set to ... redundantly".
lazy val taggingPluginOptions = Def.task {
  val converter = fileConverter.value
  val jar       = converter.toPath((taggingPlugin / Compile / packageTask).value).toAbsolutePath
  if (scalaBinaryVersion.value == "3") Seq.empty[String] else Seq(s"-Xplugin:$jar")
}

lazy val root = project
  .in(file("."))
  .settings(publish / skip := true)
  .aggregate(core, jmh, taggingPlugin, taggingPluginTests, examples, benchmarks, docs)

lazy val core = project
  .in(file("zio-profiling"))
  .settings(
    stdSettings("zio-profiling"),
    libraryDependencies ++= Seq(
      "dev.zio"                %% "zio"                     % zioVersion,
      "dev.zio"                %% "zio-streams"             % zioVersion,
      "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatVersion,
      "dev.zio"                %% "zio-test"                % zioVersion % Test,
      "dev.zio"                %% "zio-test-sbt"            % zioVersion % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val jmh = project
  .in(file("zio-profiling-jmh"))
  .dependsOn(core)
  .settings(
    stdSettings("zio-profiling-jmh"),
    libraryDependencies ++= Seq(
      "org.openjdk.jmh" % "jmh-core" % jmhVersion
    )
  )

lazy val taggingPlugin = project
  .in(file("zio-profiling-tagging-plugin"))
  .settings(
    stdSettings("zio-profiling-tagging-plugin"),
    extraSourceDirectorySettings,
    pluginDefinitionSettings
  )

lazy val taggingPluginTests = project
  .in(file("zio-profiling-tagging-plugin-tests"))
  .dependsOn(core, taggingPlugin % "plugin")
  .settings(
    stdSettings("zio-profiling-tagging-plugin-tests"),
    publish / skip := true,
    Compile / scalacOptions ++= taggingPluginOptions.value,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test"     % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val examples = project
  .in(file("examples"))
  .dependsOn(core, taggingPlugin % "plugin")
  .settings(
    stdSettings("examples"),
    publish / skip := true,
    scalacOptions ++= taggingPluginOptions.value
  )

lazy val benchmarks = project
  .in(file("benchmarks"))
  .dependsOn(core)
  .enablePlugins(JmhPlugin)
  .settings(
    stdSettings("benchmarks"),
    publish / skip := true
  )

lazy val docs = project
  .in(file("zio-profiling-docs"))
  .dependsOn(core)
  .enablePlugins(WebsitePlugin)
  .settings(
    moduleName := "zio-profiling-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    projectName                                := "ZIO Profiling",
    mainModuleName                             := (core / moduleName).value,
    projectStage                               := ProjectStage.Concept,
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(core)
  )
