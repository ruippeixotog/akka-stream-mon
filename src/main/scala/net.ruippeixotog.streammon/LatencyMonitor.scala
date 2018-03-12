package net.ruippeixotog.streammon

import java.util.concurrent.ConcurrentLinkedQueue

import scala.concurrent.duration.FiniteDuration

import akka.stream._
import akka.stream.contrib.Pulse
import akka.stream.scaladsl.{ Flow, GraphDSL, Keep, Sink }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import net.ruippeixotog.streammon.LatencyMonitor.{ Stats, TimerContext }

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

  case class Stats(timeElapsed: Long, count: Long, sumLatency: Long) {
    def avgLatency: Double = sumLatency.toDouble / count
  }

  private class TimerContext {
    private val queue = new ConcurrentLinkedQueue[Long]

    def start(): Unit = queue.add(System.nanoTime())
    def stop(): Long = (System.nanoTime() - queue.poll()) / 1000000
  }

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

  def apply[A, B, Mat, Mat2, Mat3](
    flow: Flow[A, B, Mat],
    statsSink: Sink[Stats, Mat2])(combineMat: (Mat, Mat2) => Mat3): Graph[FlowShape[A, B], Mat3] = {

    GraphDSL.create(apply(flow), statsSink)(combineMat) { implicit b => (mon, sink) =>
      import GraphDSL.Implicits._
      mon.out1 ~> sink
      FlowShape(mon.in, mon.out0)
    }
  }

  def apply[A, B, Mat](flow: Flow[A, B, Mat], statsInterval: FiniteDuration, onStats: Stats => Unit): Graph[FlowShape[A, B], Mat] =
    apply(flow, Flow.fromGraph(new Pulse[Stats](statsInterval)).to(Sink.foreach(onStats)))(Keep.left)
}
