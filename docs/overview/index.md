---
id: overview_index
title: "Quick Introduction"
---

ZIO Profiling is a collection of different profilers for better understanding the runtime behavior of ZIO programs.

Normal cpu profilers cannot really be used to profile code using an effect system.
Profilers operating on a thread level will only see that threads are spending time in the evaluation loop of the
effect system, producing profiles that are not useful for application developers.

Instead, profiling a program written using an effect system requires a profiler that is aware of the effect system
and can report where the effects were constructed / which user code the effect system is spending time on.
ZIO profiling aims to be that library for the ZIO effect system.

The library focuses exclusively on cpu profiling. For heap profiling please consider using other tools such as async-profiler
or VisualVM.

## Installation

ZIO Profiling currently ships as a single artifact that can be added to your build.sbt like this:
```scala
libraryDependencies += "dev.zio" %% "zio-profiling" % zioProfilingVersion
```

## Profiling an application and displaying a flamegraph

For this example we are going to use the tracing profiler to measure the cpu time used by parts of a zio program.
All needed definitions can be imported using:
```scala
import zio.profiling.tracing._
```

The program we want to instrument simulates performing a short and then a long computation:
```scala
val program = for {
  _ <- ZIO.succeed(Thread.sleep(20)) <# "short"
  _ <- ZIO.succeed(Thread.sleep(40)) <# "long"
} yield ()
```

ZIO Profiling relies on manually annotated effect names instead of deriving cost centers from the function call
hierarchy, so we need to manually name them using the `<#` operator. Effect names are organized in a hierarchy, so
nested effects will be identified by the effect it is nested in.

In order to profile the program, we wrap it with the `profile` method of the tracing profiler. Once the effect has completed
this will yield the profiling result. We can either manipulate the result in Scala or render it in a number of standard
formats. In this case we are going to write it out in a format supported by https://github.com/brendangregg/FlameGraph, so we
can visualize it as a flamegraph.
```scala
TracingProfiler
  .profile(program)
  .flatMap(_.stackCollapseToFile("profile.folded"))
```

The resulting file can be converted to a svg using the flamegraph.pl script ([preview](../../img/example_tracing_profile.svg)):
```bash
flamegraph.pl ./examples/profile.folded > profile.svg
```

## Causal Profiling

ZIO Profiling includes experimental support for causal profiling inspired by [coz](https://github.com/plasma-umass/coz).

Usage is similar to the tracing profiler, but instead of displaying the time spent running the program it will give recommendations
which parts of the program to focus on during performance tuning for biggest effect. It achieves this by iteratively artificially speeding
up parts of the program (by slowing down all parts running concurrently) and measuring the effect on overall runtime.

Check out the paper linked in the coz repository for more details about the idea.

We can bring the causal profiler into scope with the following import:
```scala
import zio.profiling.causal._
```

This time we are using a slightly more complicated example. Note that we still have to manually name effects using the `<#` operator:
```scala
val prog: ZIO[Any, Nothing, Unit] = for {
  done <- Ref.make(false)
  f    <- (doUselessBackgroundWork *> done.get).repeatUntilEquals(true).fork <# "backgroundwork"
  _    <- doRealWork <# "realwork"
  _    <- done.set(true)
  _    <- f.join
  _    <- progressPoint("workDone")
} yield ()

def doRealWork: ZIO[Any, Nothing, Unit] = ZIO.blocking(ZIO.succeed(Thread.sleep(100)))

def doUselessBackgroundWork: ZIO[Any, Nothing, Unit] = ZIO.blocking(ZIO.succeed(Thread.sleep(30)))
```

We also need to weave the `progressPoint` effect into our program. It will be used by the causal profiler to measure progress
of the overall program.

Finally, we can run the program using the causal profiler (notice the use of `program.forever` -- the profiler will automatically interrupt the program, until then it has to keep running)
and save the result to a file.

```scala
CausalProfiler(iterations = 100)
  .profile(prog.forever)
  .flatMap(_.renderToFile("profile.coz"))
```

The file can be viewed using the [Coz Visualizer](https://plasma-umass.org/coz/) ([preview](../../img/example_causal_profile.png)).
As you can see, the profiler correctly tells you that you can get more performance out of tweaking the `realwork` effect, but you can also get
a small speedup by improving `backgroundwork`.
