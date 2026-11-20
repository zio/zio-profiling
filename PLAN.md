# ZIO-200 plan: one `libraryDependencies +=` line per artifact in docs

Problem: some install snippets group artifacts in `libraryDependencies ++= Seq(...)`. Readers copy the whole block even when they need one module.

Goal: every install snippet in docs/ lists one artifact per `libraryDependencies +=` line, each with a one-line `//` comment above it.

Published artifacts (from build.sbt): zio-profiling, zio-profiling-jmh, zio-profiling-tagging-plugin. Not published: tests, examples, benchmarks, docs, root.

Steps:
1. docs/index.md: Installation lists all 3 artifacts; the 3 Modules blocks become single `+=` lines with comments.
2. docs/reference/jmh-profiler.md: Artifact block to `+=` with comment.
3. docs/reference/tagging-plugin.md: Artifact block (compilerPlugin) to `+=` with comment.
4. docs/tutorials/first-flamegraph.md: add comments above lines 19-20.
5. docs/how-to/profile-jmh-benchmarks.md: add comment above line 18.
6. docs/how-to/setup-tagging-plugin.md: add comments above lines 14-15.
7. Verify: grep for `++= Seq` (none), every `libraryDependencies +=` has a comment above, sbt fmt, lint, docs/mdoc.
8. Review git diff origin/master line by line.

Files to touch: docs/index.md, docs/reference/jmh-profiler.md, docs/reference/tagging-plugin.md, docs/tutorials/first-flamegraph.md, docs/how-to/profile-jmh-benchmarks.md, docs/how-to/setup-tagging-plugin.md.

PLAN.md is removed before the PR.
