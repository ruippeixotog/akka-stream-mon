package net.ruippeixotog.streammon

import java.util.concurrent.ConcurrentLinkedQueue

import scala.concurrent.duration.FiniteDuration

import akka.stream._
import akka.stream.scaladsl._
import akka.stream.stage._

import net.ruippeixotog.streammon.LatencyMonitor.{ Stats, TimerContext }

/**
 * A graph stage measuring the latency of a given linear segment (`Flow`) of a graph. The graph wraps an user-provided
 * `Flow`, connecting `in` to its input port and `out` to its output port. A second output port `statsOut` emits
 * statistics of the time taken by elements to go from `in` to `out`.
 *
 * In order for latency measures to be meaningful, the `Flow` being measured is required by this stage to:
 *   - emit exactly one element for each one that it consumes (i.e. it doesn't aggregate, filter or unfold elements,
 *     just transforms them);
 *   - emits elements the in the same order it consumes them.
 *
 * `statsOut` emits continuously as demanded by downstream; the connected `Sink` is responsible for throttling demand,
 * controlling that way the update frequency of the stats (or, equivalently, the size of the buckets they represent).
 *
 * @tparam A the type of the elements passing through this stage
 */
class LatencyMonitor[A](ctx: TimerContext) extends GraphStage[FanOutShape2[A, A, Stats]] {

  val in = Inlet[A]("LatencyMonitor.in")
  val out = Outlet[A]("LatencyMonitor.out")
  val statsOut = Outlet[Stats]("LatencyMonitor.statsOut")

  val shape = new FanOutShape2[A, A, Stats](in, out, statsOut)

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {

    private var lastStatsPull = System.nanoTime()
    private var count = 0L
    private var sumLatency = 0L

    def pushStats(): Unit = {
      val startTime = lastStatsPull
      val endTime = System.nanoTime()
      push(statsOut, Stats((endTime - startTime) / 1000000, count, sumLatency))
      lastStatsPull = endTime
      count = 0L
      sumLatency = 0L
    }

    setHandler(in, new InHandler {
      def onPush() = { count += 1; sumLatency += ctx.stop(); push(out, grab(in)) }
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

object LatencyMonitor {

  private class TimerContext {
    private val queue = new ConcurrentLinkedQueue[Long]

    def start(): Unit = queue.add(System.nanoTime())
    def stop(): Long = (System.nanoTime() - queue.poll()) / 1000000
  }

  /**
   * Aggregate latency metrics of a flow.
   *
   * @param timeElapsed the time elapsed between the measurement start and its end, in milliseconds
   * @param count the number of elements that passed through the flow
   * @param sumLatency the sum of the latencies of all the elements that passed through the flow, in milliseconds
   */
  case class Stats(timeElapsed: Long, count: Long, sumLatency: Long) {

    /**
     * The average latency of the elements that passed through the flow, in milliseconds
     */
    def avgLatency: Double = sumLatency.toDouble / count
  }

  /**
   * Creates a `LatencyMonitor` stage.
   *
   * @param flow the `Flow` to be measured
   * @tparam A the input type of `flow`
   * @tparam B the output type of `flow`
   * @tparam Mat the materialized value of `flow`
   * @return a `LatencyMonitor` stage measuring `flow`.
   */
  def apply[A, B, Mat](flow: Flow[A, B, Mat]): Graph[FanOutShape2[A, B, Stats], Mat] = {
    GraphDSL.create(flow) { implicit b => fl =>
      import GraphDSL.Implicits._
      val ctx = new TimerContext
      val ctxStart = b.add(Flow[A].map { a => ctx.start(); a })
      val ctxEnd = b.add(new LatencyMonitor[B](ctx))

      ctxStart.out ~> fl ~> ctxEnd.in
      new FanOutShape2(ctxStart.in, ctxEnd.out0, ctxEnd.out1)
    }
  }

  /**
   * Creates a `LatencyMonitor` stage with latency stats consumed by a given `Sink`.
   *
   * @param flow the `Flow` to be measured
   * @param statsSink the `Sink` that will consume latency statistics
   * @param combineMat a function combining the materialized values of `flow` and `statsSink`
   * @tparam A the input type of `flow`
   * @tparam B the output type of `flow`
   * @tparam Mat the materialized value of `flow`
   * @tparam Mat2 the materialized value of `statsSink`
   * @tparam Mat3 the materialized value of the returned stage
   * @return a `Flow` that passes all elements through `flow` and emits latency stats to `statsSink`.
   */
  def apply[A, B, Mat, Mat2, Mat3](
    flow: Flow[A, B, Mat],
    statsSink: Sink[Stats, Mat2])(combineMat: (Mat, Mat2) => Mat3): Graph[FlowShape[A, B], Mat3] = {

    GraphDSL.create(apply(flow), statsSink)(combineMat) { implicit b => (mon, sink) =>
      import GraphDSL.Implicits._
      mon.out1 ~> sink
      FlowShape(mon.in, mon.out0)
    }
  }

  /**
   * Creates a `LatencyMonitor` stage with latency stats handled periodically by a callback.
   *
   * @param flow the `Flow` to be measured
   * @param statsInterval the update frequency of the latency stats
   * @param onStats the function to call when a layency stats bucket is available
   * @tparam A the input type of `flow`
   * @tparam B the output type of `flow`
   * @tparam Mat the materialized value of `flow`
   * @return a `Flow` that passes all elements through `flow` and calls `onStats` frequently with latency stats.
   */
  def apply[A, B, Mat](flow: Flow[A, B, Mat], statsInterval: FiniteDuration, onStats: Stats => Unit): Graph[FlowShape[A, B], Mat] =
    apply(flow, Flow.fromGraph(new Pulse[Stats](statsInterval)).to(Sink.foreach(onStats)))(Keep.left)
}
