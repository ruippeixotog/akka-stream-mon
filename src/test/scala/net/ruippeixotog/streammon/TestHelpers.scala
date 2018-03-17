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

  private[this] class DelaySync[A](of: FiniteDuration) extends GraphStage[FlowShape[A, A]] {
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

  implicit class SourceTestOps[A, B, Mat](src: Source[A, Mat]) {
    def delaySync(of: FiniteDuration): Source[A, Mat] =
      src.via(new DelaySync(of))

    def takeDuring(limit: FiniteDuration, endElem: A): Source[A, Mat] =
      src.merge(Source.tick(limit, limit, endElem)).takeWhile(_ != endElem)
  }

  def delaySync[A](of: FiniteDuration): Flow[A, A, NotUsed] =
    Flow.fromGraph(new DelaySync(of))

  def pulse[A](interval: FiniteDuration): Flow[A, A, NotUsed] =
    Flow[A].zipWith(Source.tick(interval, interval, ()))(Keep.left)

  def runFlowMonitor(
    slowSource: Option[FiniteDuration],
    slowSink: Option[FiniteDuration])(block: => Flow[Int, Int, _])(implicit ee: ExecutionEnv, materializer: Materializer): Unit = {

    val src: Source[Int, _] = slowSource match {
      case None => Source.repeat(0)
      case Some(of) => Source.repeat(0).delaySync(of)
    }
    val sink: Sink[Int, Future[Done]] = slowSink match {
      case None => Sink.ignore
      case Some(of) => delaySync(of).toMat(Sink.ignore)(Keep.right)
    }

    val fut = src
      .takeDuring(10.seconds, -1)
      .via(block)
      .runWith(sink)

    fut must beEqualTo(Done).awaitFor(15.seconds)
  }
}
