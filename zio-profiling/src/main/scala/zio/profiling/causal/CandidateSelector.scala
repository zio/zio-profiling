/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.profiling.causal

import zio.profiling.CostCenter

/**
 * A `CandidateSelector` is used to decide on a target for optimization based on what code the causal profiler
 * encounters. Whenever a new experiment is about to begin a recently executed line is selected and fed to the selector.
 * The resulting costcenter will be the target of the next experiment.
 */
final case class CandidateSelector(select: CostCenter => Option[CostCenter]) extends AnyVal

object CandidateSelector {

  /**
   * Select all costcenters directly nested under the provided costcenter for optimization.
   */
  def below(scope: CostCenter): CandidateSelector =
    CandidateSelector(_.getOneLevelDownFrom(scope))

  /**
   * Select all costcenters directly or transitively nested under the provided costcenter for optimization.
   */
  def belowRecursive(scope: CostCenter): CandidateSelector =
    CandidateSelector { cc =>
      if (cc.isDescendantOf(scope)) Some(cc) else None
    }

  final val default: CandidateSelector =
    below(CostCenter.Root)

  final val all: CandidateSelector =
    belowRecursive(CostCenter.Root)
}
