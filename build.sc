import mill._
import mill.scalalib.scalafmt.ScalafmtModule
import scalalib._

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

trait ZIOTestModule extends TestModule {
  override def testFramework: T[String] = "zio.test.sbt.ZTestFramework"

  override def ivyDeps = Agg(Deps.zioTest, Deps.zioTestSbt)
}

object zio extends Module {

  object profiling extends ScalaModule with SbtModule with ScalafmtModule {
    def scalaVersion = T{ "2.13.7" }

    override def ivyDeps = T { Agg(Deps.zio, Deps.silencer)}

    override def millSourcePath = projectDir / "zio-profiling" / "jvm"
    override def artifactName: T[String] = T{"zio-profiling"}

    override def scalacOptions = Seq(
      "--deprecation"
    )

    override def scalacPluginIvyDeps = T {
      Agg(Deps.silencerPlugin)
    }

    object test extends super.Tests with ZIOTestModule {}
  }
}