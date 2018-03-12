import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.stream.{ ActorMaterializer, Attributes, DelayOverflowStrategy }
import akka.{ Done, NotUsed }

import net.ruippeixotog.streammon._

trait Helper {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher

  val ident: Flow[Int, Int, NotUsed] =
    Flow[Int]

  val fact: Flow[Int, Int, NotUsed] = {
    def f(n: Int, acc: Int = 1): Int = if (n == 0) acc else f(n - 1, acc * n)
    Flow[Int].map(f(_)).async
  }

  val fib: Flow[Int, Int, NotUsed] = {
    def f(n: Int): Int = if (n <= 1) 1 else f(n - 1) + f(n - 2)
    Flow[Int].map(f).async
  }

  val delaySingle: Flow[Int, Int, NotUsed] = {
    Flow[Int].delay(200.millis, DelayOverflowStrategy.backpressure)
      .addAttributes(Attributes.inputBuffer(1, 1)).async
  }

  val delayWindow: Flow[Int, Int, NotUsed] = {
    Flow[Int].delay(200.millis).async
  }
}

object ThroughputTest extends App with Helper {

  def printStats(stats: ThroughputMonitor.Stats) = {
    println(f"[${stats.timeElapsed}%04d] ${stats.throughput}%.3f ops/s")
  }

  def benchmark(flow: Flow[Int, Int, NotUsed]): Future[Done] =
    Source.fromIterator(() => Iterator.from(0))
      .via(ThroughputMonitor(1.second, printStats))
      .via(flow)
      .runWith(Sink.ignore)

  //  benchmark(ident)
  //  benchmark(fact)
  //  benchmark(fib)
  //  benchmark(delaySingle)
  benchmark(delayWindow)
}

object LatencyTest extends App with Helper {

  def printStats(stats: LatencyMonitor.Stats) = {
    println(f"[${stats.timeElapsed}%04d] ${stats.avgLatency}%.3f ms")
  }

  def benchmark(flow: Flow[Int, Int, NotUsed]): Future[Done] =
    Source.fromIterator(() => Iterator.from(0))
      .via(LatencyMonitor(flow, 1.second, printStats))
      .runWith(Sink.ignore)

  //  benchmark(ident)
  //  benchmark(fact)
  //  benchmark(fib)
  benchmark(delaySingle)
}
