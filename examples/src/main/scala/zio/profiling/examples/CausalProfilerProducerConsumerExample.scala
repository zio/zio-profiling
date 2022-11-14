package zio.profiling.examples

import zio._
import zio.profiling.causal._

object CausalProfilerProducerConsumerExample extends ZIOAppDefault {

  val Items         = 10000
  val QueueSize     = 10
  val ProducerCount = 4
  val ConsumerCount = 20

  def run: URIO[Any, ExitCode] = {
    val program = Queue.bounded[Unit](QueueSize).flatMap { queue =>
      def producer =
        queue.offer(()).repeatN((Items / ProducerCount) - 1) <# "producer"

      def consumer =
        (queue.take).repeatN((Items / ConsumerCount) - 1) <# "consumer"

      for {
        producers <- ZIO.forkAll(List.fill(ProducerCount)(producer))
        consumers <- ZIO.forkAll(List.fill(ConsumerCount)(consumer))
        _         <- producers.join *> consumers.join
        _         <- progressPoint("iteration")
      } yield ()
    }

    CausalProfiler(iterations = 30, experimentTargetSamples = 5)
      .profile(program.forever)
      .flatMap(_.renderToFile("profile.coz"))
      .exitCode
  }
}
