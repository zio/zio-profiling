---
id: index
title: "Introduction to ZIO Profiling"
sidebar_label: "Introduction"
---

ZIO Profiling is a collection of different profilers for better understanding the runtime behavior of ZIO programs.

@PROJECT_BADGES@

## Introduction

Normal cpu profilers cannot really be used to profile code using an effect system. Profilers operating on a thread level will only see that threads are spending time in the evaluation loop of the effect system, producing profiles that are not useful for application developers.

Instead, profiling a program written using an effect system requires a profiler that is aware of the effect system and can report where the effects were constructed / which user code the effect system is spending time on. ZIO profiling aims to be that library for the ZIO effect system.

The library focuses exclusively on cpu profiling. For heap profiling please consider using other tools such as async-profiler or VisualVM.

ZIO Profiling ships a [sampling profiler](reference/sampling-profiler.md) that shows where your program spends its time, and a [causal profiler](reference/causal-profiler.md) that shows which part is worth optimizing. To choose between them, see [Which profiler do I need?](concepts/which-profiler.md).

## Installation

All modules are published for Scala 2.12, Scala 2.13 and Scala 3, on the JVM only, and depend on ZIO 2.

```scala
// Both profilers, CostCenter and the profiling results (JVM)
libraryDependencies += "dev.zio" %% "zio-profiling" % "@VERSION@"

// Compiler plugin that tags effects with their source location (JVM)
libraryDependencies += compilerPlugin("dev.zio" %% "zio-profiling-tagging-plugin" % "@VERSION@")

// JMH profiler that writes a flamegraph input file per benchmark (JVM)
libraryDependencies += "dev.zio" %% "zio-profiling-jmh" % "@VERSION@"
```

## Modules

### Tagging compiler plugin

The plugin names each ZIO effect and stream in your code after its source location. Both profilers report per name, so you almost always want it. See [Set up the tagging compiler plugin](how-to/setup-tagging-plugin.md) and the [plugin reference](reference/tagging-plugin.md).

```scala
// Compiler plugin that tags effects with their source location (JVM)
libraryDependencies += compilerPlugin("dev.zio" %% "zio-profiling-tagging-plugin" % "@VERSION@")
```

### JMH integration

The module attaches the sampling profiler to JMH benchmarks. See [Profile JMH benchmarks](how-to/profile-jmh-benchmarks.md) and the [JMH profiler reference](reference/jmh-profiler.md).

```scala
// JMH profiler that writes a flamegraph input file per benchmark (JVM)
libraryDependencies += "dev.zio" %% "zio-profiling-jmh" % "@VERSION@"
```

## Where to go next

New to the library? Follow [Getting Started](getting-started.md), then the [tutorial](tutorials/first-flamegraph.md).
