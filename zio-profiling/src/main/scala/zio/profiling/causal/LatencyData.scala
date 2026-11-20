package zio.profiling.causal

/**
 * The arrivals at and departures from one latency point during one experiment of the causal profiler.
 *
 * @param name
 *   the name passed to `enterLatencyPoint`, `exitLatencyPoint` or `latencyPointScoped`.
 * @param arrivals
 *   how many times `enterLatencyPoint` ran for `name` during the experiment.
 * @param departures
 *   how many times `exitLatencyPoint` ran for `name` during the experiment.
 */
final case class LatencyData(
  name: String,
  arrivals: Int,
  departures: Int
) {

  /**
   * Renders a `latency-point` line of Coz's tab-separated profile format, with the fields `name`, `arrivals`,
   * `departures` and `difference` (arrivals minus departures).
   */
  def render: String =
    s"latency-point\tname=$name\tarrivals=$arrivals\tdepartures=$departures\tdifference=${arrivals - departures}"

}
