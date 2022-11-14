package zio.profiling

import zio.Trace

/**
 * A source code location attributed to a particular [[zio.profiling.CostCenter]].
 */
final case class TaggedLocation(costCenter: CostCenter, location: Trace)
