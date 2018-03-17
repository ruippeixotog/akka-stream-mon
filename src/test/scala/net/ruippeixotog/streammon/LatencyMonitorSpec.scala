package net.ruippeixotog.streammon

import scala.concurrent.duration._

import akka.stream.scaladsl.Flow
import akka.stream.{ ActorMaterializer, OverflowStrategy }
import net.ruippeixotog.akka.testkit.specs2.mutable.AkkaSpecification
import org.specs2.concurrent.ExecutionEnv

class LatencyMonitorSpec(implicit ee: ExecutionEnv) extends AkkaSpecification with TestHelpers {
  implicit val materializer = ActorMaterializer()

  def avgLatency(stats: List[LatencyMonitor.Stats]): Double =
    stats.map(_.sumLatency).sum.toDouble / stats.map(_.count).sum

  "A LatencyMonitor" should {

    "provide the average time between two points of a linear flow" in {
      var stats: List[LatencyMonitor.Stats] = Nil

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
  }
}
