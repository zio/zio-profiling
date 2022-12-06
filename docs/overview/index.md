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

ZIO Profiling requires you to add both the main library and optionally the compiler plugin to your build.sbt:
```scala
libraryDependencies += "dev.zio" %% "zio-profiling" % zioProfilingVersion
libraryDependencies += compilerPlugin("dev.zio" %% "zio-profiling-tagging-plugin" % zioProfilingVersion)
```

## Profiling an application and displaying a flamegraph

For this example we are going to use the sampling profiler to measure the cpu time used by parts of a zio program.
All needed definitions can be imported using:
```scala
import zio.profiling.sampling._
```

The program we want to instrument simulates performing a short and then a long computation concurrently:
```scala
val fast = ZIO.succeed(Thread.sleep(400))

val slow = ZIO.succeed(Thread.sleep(600))

val program = fast <&> slow
```

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

This time we are using a slightly more complicated example:
```scala
val fast = ZIO.succeed(Thread.sleep(40))

val slow1 = ZIO.succeed(Thread.sleep(20))

val slow2 = ZIO.succeed(Thread.sleep(60))

val slow = slow1 <&> slow2

val program = (fast <&> slow) *>
  CausalProfiler.progressPoint("iteration done")

CausalProfiler(iterations = 100)
  .profile(program.forever)
  .flatMap(_.renderToFile("profile.coz"))
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
As you can see, the profiler correctly tells you that you can get up to a 33% speedup by optimizing the `slow2` effect,
but it's impossible to get a speedup any other way.

## Compiler Plugin

In order to produce actionable output, a profiler not only needs to know which line of code is currently running, but also how that location was reached.

Most profilers rely on the function call hierarchy to determine this information, but the call stack is not really useful for programs using functional effect systems. The reason for this is that the normal function calls are only used to build up the program as a datastructure -- not execute it.

---

It's possible to restore the proper callstack while the effects are actually getting executed (this is the approach taken by the zio.Trace machinery), but that is not the approach taken by ZIO Profiling.

Instead, zio-profiling tracks the current 'callstack' of your program using FiberRefs. For this approach to work every effect should be manually annotated using `zio.profiling.CostCenter.withChildCostCenter`, which will result in a hierarchy of effect tags at runtime.

As this requires modification of large parts of user programs and is bad UX, zio-profiling ships with a compiler plugin (zio-profiling-tagging-plugin) that automates this. Every `def` or `val` that returns a zio effect will be rewritten to be properly tagged. Consider this example for the rewrite that happens:

```scala
val testEffect = ZIO.unit

// gets rewritten to

val testEffect = CostCenter.withChildCostCenter("foo.Foo.testEffect(Foo.scala:12)")(ZIO.unit)
```
