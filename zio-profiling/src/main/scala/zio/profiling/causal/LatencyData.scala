package zio.profiling.causal

final case class LatencyData(
  name: String,
  arrivals: Int,
  departures: Int
) {

  def render: String =
    s"latency-point\tname=$name\tarrivals=$arrivals\tdepartures=$departures\tdifference=${arrivals - departures}"

}
