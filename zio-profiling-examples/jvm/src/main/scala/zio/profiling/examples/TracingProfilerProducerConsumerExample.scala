package zio.profiling.examples

import zio._
import zio.URIO
import zio.profiling.tracing._

object TracingProfilerProducerConsumerExample extends ZIOAppDefault {

  val Items         = 10000
  val QueueSize     = 1
  val ProducerCount = 1
  val ConsumerCount = 5

  def run: URIO[Any, ExitCode] = {
    def program(iterationRef: Ref[Int]) = Queue.bounded[Unit](QueueSize).flatMap { queue =>
      def producer =
        queue
          .offer(())
          .repeatN((Items / ProducerCount) - 1) <# "producer"

      def consumer =
        queue.take
          .repeatN((Items / ConsumerCount) - 1) <# "consumer"

      for {
        iteration <- iterationRef.updateAndGet(_ + 1)
        _         <- ZIO.logInfo(s"Iteration $iteration")
        producers <- ZIO.forkAll(List.fill(ProducerCount)(producer))
        consumers <- ZIO.forkAll(List.fill(ConsumerCount)(consumer))
        _         <- producers.join *> consumers.join
      } yield ()
    }

    TracingProfiler
      .profile(Ref.make(0).flatMap(program(_).repeatN(100)))
      .flatMap(_.stackCollapseToFile("profile.folded"))
      .exitCode
  }
}
