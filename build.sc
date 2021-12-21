import mill._
import mill.define.Target
import scalalib._

val projectDir = build.millSourcePath

object Deps {
  val zio = ivy"dev.zio::zio:2.0.0-RC1"
}

object zio extends Module {

  object profiling extends ScalaModule with SbtModule {
    def scalaVersion = T{ "2.13.7" }

    override def ivyDeps = T { Agg(Deps.zio)}

    override def millSourcePath = projectDir / "zio-profiling" / "jvm"
    override def artifactName: T[String] = T{"zio-profiling"}

    override def scalacOptions = Seq(
      "--deprecation"
    )
  }
}