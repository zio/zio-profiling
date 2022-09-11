package zio.profiling.examples

import zio.profiling.causal._
import zio.{URIO, _}

object CausalProfilerProducerConsumerExample extends ZIOAppDefault {

  val Items         = 10000
  val QueueSize     = 1
  val ProducerCount = 1
  val ConsumerCount = 5

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

    CausalProfiler
      .profile(ProfilerConfig.Default.copy(iterations = 200)) {
        program.forever
      }
      .flatMap(_.writeToFile("profile.coz"))
      .exitCode
  }
}
