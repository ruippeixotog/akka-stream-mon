package net.ruippeixotog.streammon

import scala.concurrent.duration._

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import net.ruippeixotog.akka.testkit.specs2.mutable.AkkaSpecification
import org.specs2.concurrent.ExecutionEnv

class ThroughputMonitorSpec(implicit ee: ExecutionEnv) extends AkkaSpecification with TestHelpers {
  implicit val materializer = ActorMaterializer()

  def avgThroughput(stats: List[ThroughputMonitor.Stats]): Double =
    stats.map(_.count).sum.toDouble * 1000 / stats.map(_.timeElapsed).sum

  "A ThroughputMonitor" should {

    "provide the throughput at a point of a stream" in {
      var statsBefore: List[ThroughputMonitor.Stats] = Nil
      var statsAfter: List[ThroughputMonitor.Stats] = Nil

      def runThruMonitor(
        flow: Flow[Int, Int, _],
        slowSource: Option[FiniteDuration] = None,
        slowSink: Option[FiniteDuration] = None) = runFlowMonitor(slowSource, slowSink) {

        statsBefore = Nil
        statsAfter = Nil
        Flow[Int]
          .via(ThroughputMonitor(500.millis, { st => statsBefore = st :: statsBefore }))
          .via(flow)
          .via(ThroughputMonitor(500.millis, { st => statsAfter = st :: statsAfter }))
      }

      runThruMonitor(Flow[Int])
      avgThroughput(statsBefore) must beGreaterThan(1000000.0)
      avgThroughput(statsAfter) must beGreaterThan(1000000.0)

      runThruMonitor(delaySync(200.millis))
      avgThroughput(statsBefore) must beCloseTo(5.0 +/- 1.0)
      avgThroughput(statsAfter) must beCloseTo(5.0 +/- 1.0)

      runThruMonitor(Flow[Int], slowSource = Some(200.millis))
      avgThroughput(statsBefore) must beCloseTo(5.0 +/- 1.0)
      avgThroughput(statsAfter) must beCloseTo(5.0 +/- 1.0)

      runThruMonitor(Flow[Int].zipWithIndex.collect { case (n, i) if i % 2 == 0 => n }, slowSource = Some(200.millis))
      avgThroughput(statsBefore) must beCloseTo(5.0 +/- 1.0)
      avgThroughput(statsAfter) must beCloseTo(2.5 +/- 1.0)

      runThruMonitor(Flow[Int].mapConcat(List.fill(2)(_)), slowSource = Some(200.millis))
      avgThroughput(statsBefore) must beCloseTo(5.0 +/- 1.0)
      avgThroughput(statsAfter) must beCloseTo(10.0 +/- 2.0)

      runThruMonitor(Flow[Int].mapConcat(List.fill(2)(_)), slowSink = Some(200.millis))
      avgThroughput(statsBefore) must beCloseTo(2.5 +/- 1.0)
      avgThroughput(statsAfter) must beCloseTo(5.0 +/- 1.0)
    }
  }
}
