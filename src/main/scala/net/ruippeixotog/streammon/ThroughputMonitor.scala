package net.ruippeixotog.streammon

import scala.concurrent.duration.FiniteDuration

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl._
import akka.stream.stage._

import net.ruippeixotog.streammon.ThroughputMonitor.Stats

/**
 * A graph stage measuring the element throughput at a given point in a graph. The stage emits through `out` all
 * elements recieved at `in`, while a second output port `statsOut` emits statistics of the number of elements passing
 * per unit of time at the `in`-`out` edge.
 *
 * `statsOut` emits continuously as demanded by downstream; the connected `Sink` is responsible for throttling demand,
 * controlling that way the update frequency of the stats (or, equivalently, the size of the buckets they represent).
 *
 * @tparam A the type of the elements passing through this stage
 */
class ThroughputMonitor[A] extends GraphStage[FanOutShape2[A, A, Stats]] {

  val in = Inlet[A]("ThroughputMonitor.in")
  val out = Outlet[A]("ThroughputMonitor.out")
  val statsOut = Outlet[Stats]("ThroughputMonitor.statsOut")

  val shape = new FanOutShape2[A, A, Stats](in, out, statsOut)

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {

    private var lastStatsPull = System.nanoTime()
    private var count = 0L

    def pushStats(): Unit = {
      val startTime = lastStatsPull
      val endTime = System.nanoTime()
      push(statsOut, Stats((endTime - startTime) / 1000000, count))
      lastStatsPull = endTime
      count = 0L
    }

    setHandler(in, new InHandler {
      def onPush() = { count += 1; push(out, grab(in)) }
    })

    setHandler(out, new OutHandler {
      def onPull() = pull(in)
    })

    setHandler(statsOut, new OutHandler {
      def onPull() = pushStats()
      override def onDownstreamFinish() = {}
    })
  }
}

object ThroughputMonitor {

  /**
   * Aggregate throughput metrics of a stream.
   *
   * @param timeElapsed the time elapsed between the measurement start and its end, in milliseconds
   * @param count the number of elements that passed through the stream
   */
  case class Stats(timeElapsed: Long, count: Long) {

    /**
     * The number of elements that passed through the stream per second.
     */
    def throughput: Double = count.toDouble * 1000 / timeElapsed
  }

  /**
   * Creates a `ThroughputMonitor` stage.
   *
   * @tparam A the type of the elements passing through this stage
   * @return a `ThroughputMonitor` stage.
   */
  def apply[A]: ThroughputMonitor[A] =
    new ThroughputMonitor[A]

  /**
   * Creates a `ThroughputMonitor` stage with throughput stats consumed by a given `Sink`.
   *
   * @param statsSink the `Sink` that will consume throughput statistics
   * @tparam A the type of the elements passing through this stage
   * @tparam Mat the value materialized by `statsSink`
   * @return a `Flow` that outputs all its inputs and emits throughput stats to `statsSink`.
   */
  def apply[A, Mat](statsSink: Sink[Stats, Mat]): Graph[FlowShape[A, A], Mat] = {
    GraphDSL.create(statsSink) { implicit b => sink =>
      import GraphDSL.Implicits._
      val mon = b.add(apply[A])
      mon.out1 ~> sink
      FlowShape(mon.in, mon.out0)
    }
  }

  /**
   * Creates a `ThroughputMonitor` stage with throughput stats handled periodically by a callback.
   *
   * @param statsInterval the update frequency of the throughput stats
   * @param onStats the function to call when a throughput stats bucket is available
   * @tparam A the type of the elements passing through this stage
   * @return a `Flow` that outputs all its inputs and calls `onStats` frequently with throughput stats.
   */
  def apply[A](statsInterval: FiniteDuration, onStats: Stats => Unit): Graph[FlowShape[A, A], NotUsed] =
    apply(Flow.fromGraph(new Pulse[Stats](statsInterval)).to(Sink.foreach(onStats)).mapMaterializedValue(_ => NotUsed))
}
