import ReleaseTransformations._
import scalariform.formatter.preferences._

name := "akka-stream-mon"
organization := "net.ruippeixotog"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka"   %% "akka-stream"          % "2.5.11",
  "net.ruippeixotog"    %% "akka-testkit-specs2"  % "0.2.3"       % "test",
  "org.specs2"          %% "specs2-core"          % "4.0.2"       % "test")

scalariformPreferences := scalariformPreferences.value
  .setPreference(DanglingCloseParenthesis, Prevent)
  .setPreference(DoubleIndentConstructorArguments, true)

publishTo := sonatypePublishTo.value

licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php"))
homepage := Some(url("https://github.com/ruippeixotog/scala-scraper"))
scmInfo := Some(ScmInfo(
  url("https://github.com/ruippeixotog/scala-scraper"),
  "scm:git:https://github.com/ruippeixotog/scala-scraper.git",
  "scm:git:git@github.com:ruippeixotog/scala-scraper.git"))
developers := List(
  Developer("ruippeixotog", "Rui Gonçalves", "ruippeixotog@gmail.com", url("http://www.ruippeixotog.net")))

releaseTagComment := s"Release ${(version in ThisBuild).value}"
releaseCommitMessage := s"Set version to ${(version in ThisBuild).value}"

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("publishSigned"),
  setNextVersion,
  commitNextVersion,
  releaseStepCommandAndRemaining("sonatypeReleaseAll"),
  pushChanges)
