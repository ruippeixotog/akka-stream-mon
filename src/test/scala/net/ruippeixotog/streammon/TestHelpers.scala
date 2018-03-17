package net.ruippeixotog.streammon

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.stream._
import akka.stream.scaladsl.{ Flow, Keep, Sink, Source }
import akka.stream.stage.{ GraphStage, InHandler, OutHandler, TimerGraphStageLogic }
import akka.{ Done, NotUsed }
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.SpecificationLike

trait TestHelpers extends SpecificationLike {

  class DelaySync[A](of: FiniteDuration) extends GraphStage[FlowShape[A, A]] {
    val in = Inlet[A]("DelaySync.in")
    val out = Outlet[A]("DelaySync.out")
    val shape = FlowShape.of(in, out)

    def createLogic(inheritedAttributes: Attributes) = new TimerGraphStageLogic(shape) with InHandler with OutHandler {
      var currElem: Option[A] = None

      def pushAndDelay() = {
        currElem = Some(grab(in))
        scheduleOnce((), of)
      }

      def onPush() = { if (currElem.isEmpty) pushAndDelay() }
      def onPull() = { if (currElem.isEmpty) pull(in) }

      override def onTimer(timerKey: Any) = {
        push(out, currElem.get)
        currElem = None
        if (isAvailable(out)) pull(in)
      }

      setHandlers(in, out, this)
    }
  }

  def delaySync(of: FiniteDuration): Flow[Int, Int, NotUsed] =
    Flow.fromGraph(new DelaySync[Int](of))

  def runFlowMonitor(
    slowSource: Option[FiniteDuration],
    slowSink: Option[FiniteDuration])(block: => Flow[Int, Int, _])(implicit ee: ExecutionEnv, materializer: Materializer): Unit = {

    val src: Source[Int, _] = slowSource match {
      case None => Source.repeat(0)
      case Some(of) => Source.repeat(0).via(delaySync(of))
    }
    val sink: Sink[Int, Future[Done]] = slowSink match {
      case None => Sink.ignore
      case Some(of) => Flow[Int].via(delaySync(of)).toMat(Sink.ignore)(Keep.right)
    }

    val fut = src
      .merge(Source.tick(10.seconds, 10.seconds, -1))
      .takeWhile(_ != -1)
      .via(block)
      .runWith(sink)

    fut must beEqualTo(Done).awaitFor(15.seconds)
  }
}
