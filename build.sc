import mill._
import mill.modules.Jvm
import scalalib._
import mill.scalalib.scalafmt.ScalafmtModule
import $ivy.`com.goyeau::mill-scalafix:0.2.6`
import com.goyeau.mill.scalafix.ScalafixModule
import mill.define.Sources
import os.Path

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

trait MDocModule extends ScalaModule {

  def scalaMdocVersion : T[String] = "2.2.24"

  def scalaMDocDep : T[Dep] = ivy"org.scalameta::mdoc:${scalaMdocVersion()}"

  def watchedMDocsDestination: T[Option[Path]] = T(None)

  override def ivyDeps: T[Agg[Dep]] = T {
    super.ivyDeps() ++ Agg(scalaMDocDep())
  }

  // where do the mdoc sources live ?
  def mdocSources = T.sources { super.millSourcePath }

  def mdoc : T[PathRef] = T {

    val cp = runClasspath().map(_.path)

    val dir = T.dest.toIO.getAbsolutePath()
    val dirParams = mdocSources().map(pr => Seq(s"--in", pr.path.toIO.getAbsolutePath, "--out",  dir)).iterator.flatten.toSeq

    Jvm.runLocal("mdoc.Main", cp, dirParams)

    PathRef(T.dest)
  }

  def mdocWatch() = T.command {

    watchedMDocsDestination().foreach{ p =>
      val cp = runClasspath().map(_.path)
      val dirParams = mdocSources().map(pr => Seq(s"--in", pr.path.toIO.getAbsolutePath, "--out",  p.toIO.getAbsolutePath)).iterator.flatten.toSeq
      Jvm.runLocal("mdoc.Main", cp, dirParams ++ Seq("--watch"))
    }

  }
}

trait DocusaurusModule extends Module {

  def docusaurusSources : Sources
  def compiledMdocs : Sources

  def yarnInstall : T[PathRef] = T {
    val baseDir = T.dest

    docusaurusSources().foreach{ pr =>
      os.list(pr.path).foreach(p => os.copy.into(p, baseDir, true, true, true, true, false))
    }

    val process = Jvm.spawnSubprocess(
      commandArgs = Seq(
        "yarn", "install", "--check-files"
      ),
      envArgs = Map.empty,
      workingDir = T.dest
    )
    process.join()
    T.log.info(new String(process.stdout.bytes))
    PathRef(T.dest)
  }

  def docusaurusBuild : T[PathRef] = T {
    val workDir = T.dest
    val yarnSetup = yarnInstall().path

    os.list(workDir).foreach(os.remove.all)
    os.list(yarnSetup).foreach { p =>
      os.copy.into(p, workDir, followLinks = true, replaceExisting = true, copyAttributes = true, createFolders = true, mergeFolders = false)
    }

    val docsDir = workDir / "docs"
    os.makeDir.all(docsDir)
    os.list(docsDir).foreach(os.remove.all)

    docusaurusSources().foreach { pr =>
      val bd = pr.path
      os.walk(pr.path / "docs").foreach { p =>
        val relPath = p.relativeTo(bd / "docs")
        T.log.info(relPath.toString())
        if (p.toIO.isFile) {
          os.copy.over(p, docsDir / relPath)
        }
      }
    }

    compiledMdocs().foreach{ pr =>
      os.list(pr.path).foreach(p => os.copy.into(p, docsDir, followLinks = true, replaceExisting = true, copyAttributes = true, createFolders = true, mergeFolders = true))
    }

    val p1 = Jvm.spawnSubprocess(
      commandArgs = Seq(
        "yarn", "install", "--force"
      ),
      envArgs = Map.empty,
      workingDir = workDir
    )

    p1.join()

    val p2 = Jvm.spawnSubprocess(
      commandArgs = Seq(
        "yarn", "build"
      ),
      envArgs = Map.empty,
      workingDir = workDir
    )

    p2.join()

    PathRef(workDir)
  }
}

trait ZIOModule extends SbtModule with ScalafmtModule with ScalafixModule { outer =>
  def scalaVersion = Deps.scalaVersion
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

  object site extends DocusaurusModule with MDocModule {
    def scalaVersion = Deps.scalaVersion
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
      override def artifactName: T[String] = "zio-profiling-examples"
    }

  }
}
