import Keys.{`package` => packageTask}

import BuildHelper._
import Dependencies._

import zio.sbt.githubactions.{Job, Step}

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
    // `npm run build` itself, although the same command succeeds when run directly. So sbt only compiles the docs, and
    // `npm install` and the Docusaurus build run as plain shell steps (the website scaffold is committed).
    ciUpdateReadmeJobs         := ciUpdateReadmeJobs.value.map(compileDocsBefore("Generate Readme")),
    ciPostReleaseJobs          := ciPostReleaseJobs.value.map(compileDocsBefore("Publish Docs to NPM Registry")),
    ciCheckWebsiteBuildProcess := Seq(
      Step.SingleStep(
        name = "Check website build process",
        run = Some(
          Seq("docs/clean", "docs/compileDocs")
            .map("sbt --no-colors --batch " + _)
            .mkString("", "; ", "; cd website; npm install; npm run build")
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

// `sbt docs/generateReadme` and `sbt docs/publishToNpm` fail on a fresh checkout with "FileNotFoundException:
// website/docs/index.md" (or package.json), although mdoc reports 0 errors in the same run. They work when mdoc ran
// as its own sbt command before, so CI compiles the docs in a separate step first.
def compileDocsBefore(stepName: String)(job: Job): Job = {
  val compileDocs = Step.SingleStep(name = "Compile docs", run = Some("sbt --no-colors docs/mdoc"))

  def insert(step: Step): Seq[Step] = step match {
    case single: Step.SingleStep if single.name == stepName => Seq(compileDocs, single)
    case sequence: Step.StepSequence                        => Seq(Step.StepSequence(sequence.steps.flatMap(insert)))
    case other                                              => Seq(other)
  }

  job.withSteps(job.steps.flatMap(insert): _*)
}

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
