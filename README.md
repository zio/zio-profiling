# zio-profiling

| Project Stage | CI | Release | Snapshot | Discord |
| --- | --- | --- | --- | --- |
| [![Project stage][Badge-Stage]][Link-Stage-Page] | ![CI][Badge-CI] | [![Release Artifacts][Badge-SonatypeReleases]][Link-SonatypeReleases] | [![Snapshot Artifacts][Badge-SonatypeSnapshots]][Link-SonatypeSnapshots] | [![Badge-Discord]][Link-Discord] |

# Summary
ZIO Profiling is a collection of various profilers for cpu profiling of ZIO programs.

To get started add the required dependencies:
```scala
libraryDependencies += "dev.zio" %% "zio-profiling" % zioProfilingVersion
libraryDependencies += compilerPlugin("dev.zio" %% "zio-profiling-tagging-plugin" % zioProfilingVersion)
```

Run your program with the profiler:
```scala
package zio.profiling.examples

import zio._
import zio.profiling.sampling._

object SamplingProfilerSimpleExample extends ZIOAppDefault {
  def run: URIO[Any, ExitCode] = {

    val fast = ZIO.succeed(Thread.sleep(400))

    val slow = ZIO.succeed(Thread.sleep(200)) <&> ZIO.succeed(Thread.sleep(600))

    val program = fast <&> slow

    SamplingProfiler()
      .profile(program)
      .flatMap(_.stackCollapseToFile("profile.folded"))
      .exitCode
  }
}
```

And visualize your application performance in a number of different ways:
![Tracing Profiler Output](./website/static/img/example_tracing_profile.svg?raw=true")

# Documentation
[zio-profiling Microsite](https://zio.github.io/zio-profiling/)

# Contributing
[Documentation for contributors](https://zio.github.io/zio-profiling/docs/about/about_contributing)

## Code of Conduct

See the [Code of Conduct](https://zio.github.io/zio-profiling/docs/about/about_coc)

## Support

Come chat with us on [![Badge-Discord]][Link-Discord].


# License
[License](LICENSE)

[Badge-SonatypeReleases]: https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-profiling_2.12.svg "Sonatype Releases"
[Badge-SonatypeSnapshots]: https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-profiling_2.12.svg "Sonatype Snapshots"
[Badge-Discord]: https://img.shields.io/discord/629491597070827530?logo=discord "chat on discord"
[Badge-CI]: https://github.com/zio/zio-profiling/workflows/CI/badge.svg
[Link-SonatypeReleases]: https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-profiling_2.12/ "Sonatype Releases"
[Link-SonatypeSnapshots]: https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-profiling_2.12/ "Sonatype Snapshots"
[Link-Discord]: https://discord.gg/2ccFBr4 "Discord"
[Badge-Stage]: https://img.shields.io/badge/Project%20Stage-Concept-red.svg
[Link-Stage-Page]: https://github.com/zio/zio/wiki/Project-Stages

