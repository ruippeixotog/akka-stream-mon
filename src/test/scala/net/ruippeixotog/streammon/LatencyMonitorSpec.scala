package net.ruippeixotog.streammon

import scala.concurrent.duration._

import akka.stream.scaladsl._
import akka.stream.{ ActorMaterializer, ClosedShape, OverflowStrategy }
import net.ruippeixotog.akka.testkit.specs2.mutable.AkkaSpecification
import net.ruippeixotog.streammon.LatencyMonitor.Stats
import org.specs2.concurrent.ExecutionEnv

class LatencyMonitorSpec(implicit ee: ExecutionEnv) extends AkkaSpecification with TestHelpers {
  implicit val materializer = ActorMaterializer()

  def avgLatency(stats: Seq[Stats]): Double =
    stats.map(_.sumLatency).sum.toDouble / stats.map(_.count).sum

  "A LatencyMonitor" should {

    "provide the average time between two points of a linear flow" in {
      var stats: List[Stats] = Nil

      def runLatencyMonitor(
        flow: Flow[Int, Int, _],
        slowSource: Option[FiniteDuration] = None,
        slowSink: Option[FiniteDuration] = None) = runFlowMonitor(slowSource, slowSink) {

        stats = Nil
        Flow.fromGraph(LatencyMonitor(flow, 500.millis, { st => stats = st :: stats }))
      }

      runLatencyMonitor(Flow[Int])
      avgLatency(stats) must beBetween(0.0, 20.0)

      runLatencyMonitor(delaySync(200.millis))
      avgLatency(stats) must beCloseTo(200.0 +/- 50.0)

      runLatencyMonitor(delaySync(200.millis), slowSource = Some(300.millis))
      avgLatency(stats) must beCloseTo(200.0 +/- 50.0)

      runLatencyMonitor(Flow[Int].buffer(5, OverflowStrategy.backpressure), slowSink = Some(200.millis))
      avgLatency(stats) must beCloseTo(1000.0 +/- 50.0)
    }

    "provide a constructor for a 1-to-2 fan-out stage" in {
      val statsSink = pulse(500.millis).toMat(Sink.seq[Stats])(Keep.right)
      val graph = RunnableGraph.fromGraph(GraphDSL.create(statsSink) { implicit b => statsSink =>
        import GraphDSL.Implicits._

        val src = Source.repeat(0).takeDuring(10.seconds, -1)
        val sink = Sink.ignore
        val mon = b.add(LatencyMonitor(delaySync[Int](200.millis)))

        src ~> mon.in
        mon.out0 ~> sink
        mon.out1 ~> statsSink
        ClosedShape
      })

      graph.run().map(avgLatency) must beCloseTo(200.0 +/- 50.0).awaitFor(15.seconds)
    }

    "provide a constructor for a flow given a stats sink" in {
      val src = Source.repeat(0).takeDuring(10.seconds, -1)
      val sink = Sink.ignore
      val statsSink = pulse(500.millis).toMat(Sink.seq[Stats])(Keep.right)
      val mon = LatencyMonitor(delaySync[Int](200.millis), statsSink)(Keep.right)

      val graph = src.viaMat(mon)(Keep.right).to(sink)
      graph.run().map(avgLatency) must beCloseTo(200.0 +/- 50.0).awaitFor(15.seconds)
    }
  }
}
