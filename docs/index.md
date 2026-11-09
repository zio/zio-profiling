---
id: index
title: "Introduction to ZIO Profiling"
sidebar_label: "ZIO Profiling"
---

ZIO Profiling is a collection of different profilers for better understanding the runtime behavior of ZIO programs.

@PROJECT_BADGES@

## Introduction

Normal cpu profilers cannot really be used to profile code using an effect system. Profilers operating on a thread level will only see that threads are spending time in the evaluation loop of the effect system, producing profiles that are not useful for application developers.

Instead, profiling a program written using an effect system requires a profiler that is aware of the effect system and can report where the effects were constructed / which user code the effect system is spending time on. ZIO profiling aims to be that library for the ZIO effect system.

The library focuses exclusively on cpu profiling. For heap profiling please consider using other tools such as async-profiler or VisualVM.

## Installation

ZIO Profiling requires you to add the main library to your `build.sbt`. The compiler plugin is optional, but it is what gives the profiles useful source locations (see [Set up the tagging compiler plugin](how-to/setup-tagging-plugin.md)):

```sbt
libraryDependencies += "dev.zio" %% "zio-profiling" % "@VERSION@"
libraryDependencies += compilerPlugin("dev.zio" %% "zio-profiling-tagging-plugin" % "@VERSION@")
```

The library is published for Scala 2.12, Scala 2.13 and Scala 3, and depends on ZIO 2.

## Which profiler do I need?

ZIO Profiling ships two profilers in the `zio-profiling` module, plus a JMH integration:

| I want to...                                                  | Use                                                                                 |
|---------------------------------------------------------------|-------------------------------------------------------------------------------------|
| See where my program spends its CPU time                       | The [sampling profiler](reference/sampling-profiler.md), rendered as a flamegraph   |
| Find which part of my program to speed up for the biggest gain | The [causal profiler](reference/causal-profiler.md), rendered for the Coz visualizer |
| Profile a JMH benchmark                                       | [Profile JMH benchmarks](how-to/profile-jmh-benchmarks.md)                          |

New to the library? Start with the [tutorial](tutorials/first-flamegraph.md), which profiles a small program and reads the flamegraph.

## Where to go next

- **Tutorial:** [Profile a program and read the flamegraph](tutorials/first-flamegraph.md)
- **How-to guides:** [Find a bottleneck with the causal profiler](how-to/find-bottleneck-causal.md), [Set up the tagging compiler plugin](how-to/setup-tagging-plugin.md), [Tag effects by hand](how-to/tag-effects-by-hand.md), [Profile a running application](how-to/profile-running-application.md), [Profile JMH benchmarks](how-to/profile-jmh-benchmarks.md)
- **Reference:** [Sampling profiler](reference/sampling-profiler.md), [Causal profiler](reference/causal-profiler.md), [CostCenter](reference/cost-center.md)
