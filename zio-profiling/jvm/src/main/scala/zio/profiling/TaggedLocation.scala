package zio.profiling

import zio.Trace

final case class TaggedLocation(costCenter: CostCenter, location: Trace)
