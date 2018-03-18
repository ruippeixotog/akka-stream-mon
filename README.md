# akka-stream-mon [![Build Status](https://travis-ci.org/ruippeixotog/akka-stream-mon.svg?branch=master)](https://travis-ci.org/ruippeixotog/akka-stream-mon) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.ruippeixotog/akka-stream-mon_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.ruippeixotog/akka-stream-mon_2.12)

A small library containing graph stages to measure the latency and throughput of Akka Streams.

## Usage

To use akka-stream-mon in an existing SBT project with Scala 2.12, add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "net.ruippeixotog" %% "akka-stream-mon" % "0.1.0"
```

### Throughput

A `ThroughputMonitor` measures the throughput at a given link in a graph. Graphically, it looks like this:

```
+--------+    +-------------------+     +------+
| Source +----> ThroughputMonitor +-----> Sink |
+--------+    +---------+---------+     +------+
                        |
                        |
                    +---v---+
                    | Stats |
                    +-------+
```

In their simplest form, throughput monitors can be set up like this:

```scala
// monitor the throughput at the `source`-`sink` link, emitting stats to
// `statsSink`
source.via(ThroughputMonitor(statsSink)).to(sink)

// monitor the throughput at the `source`-`sink` link, calling
// `onStatsHandler` with stats every 5 seconds
source.via(ThroughputMonitor(5.seconds, onStatsHandler)).to(sink)
```

In more complex graphs, with the help of the `GraphDSL`, the monitor can be used as a graph stage with `in` and `out`
ports for the link to be measured and an additional `statsOut` emitting stats. With `statsOut` connected using any of
the ways shown above, this stage looks and acts just like a no-op `Flow` (a `Flow` letting all elements flow untouched).

### Latency

A `LatencyMonitor` measures the throughput of a linear segment (`Flow`) of a graph. Graphically, it looks like this:

```
              +----------------+
              | LatencyMonitor |
+--------+    |    +------+    |    +------+
| Source +---------> Flow +---------> Sink |
+--------+    |    +------+    |    +------+
              |                |
              +--------+-------+
                       |
                   +---v---+
                   | Stats |
                   +-------+

```

In order for latency measures to be meaningful, the `Flow` being measured is required by this stage to:
- emit exactly one element for each one that it consumes (i.e. it doesn't aggregate, filter or unfold elements, it just
  transforms them);
- emits elements the in the same order it consumes them.

This happens because `LatencyMonitor` doesn't mark or modify elements in any way to recognize them individually on both
endpoints; it just monitors the flow on each side and relies on the 1-to-1 mapping to deduce latencies.

In their simplest form, latency monitors can be set up like this:

```scala
// monitor the latency of `flow`, emitting stats to `statsSink`
source.via(LatencyMonitor(flow, statsSink)(Keep.right)).to(sink)

// monitor the latency of `flow`, calling `onStatsHandler` with stats every
// 5 seconds
source.via(LatencyMonitor(flow, 5.seconds, onStatsHandler)).to(sink)
```

In more complex graphs, with the help of the `GraphDSL`, the monitor can be used as a graph stage that wraps a `flow`,
providing `in` and `out` ports connected to the `flow` ports, while providing an additional `statsOut` emitting stats.
With `statsOut` connected using any of the ways shown above, this stage looks and acts just like the `flow` it monitors.

## Copyright

Copyright (c) 2018 Rui Gonçalves. See LICENSE for details.
