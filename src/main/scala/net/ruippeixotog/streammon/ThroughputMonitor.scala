package net.ruippeixotog.streammon

import scala.concurrent.duration.FiniteDuration

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl._
import akka.stream.stage._

import net.ruippeixotog.streammon.ThroughputMonitor.Stats

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

  case class Stats(timeElapsed: Long, count: Long) {
    def throughput: Double = count.toDouble * 1000 / timeElapsed
  }

  def apply[A]: Graph[FanOutShape2[A, A, Stats], NotUsed] =
    new ThroughputMonitor[A]

  def apply[A, Mat](statsSink: Sink[Stats, Mat]): Graph[FlowShape[A, A], Mat] = {
    GraphDSL.create(statsSink) { implicit b => sink =>
      import GraphDSL.Implicits._
      val mon = b.add(apply[A])
      mon.out1 ~> sink
      FlowShape(mon.in, mon.out0)
    }
  }

  def apply[A](statsInterval: FiniteDuration, onStats: Stats => Unit): Graph[FlowShape[A, A], NotUsed] =
    apply(Flow.fromGraph(new Pulse[Stats](statsInterval)).to(Sink.foreach(onStats)).mapMaterializedValue(_ => NotUsed))
}
