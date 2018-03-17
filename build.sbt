import scalariform.formatter.preferences._

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka"   %% "akka-stream"          % "2.5.11",
  "net.ruippeixotog"    %% "akka-testkit-specs2"  % "0.2.3"       % "test",
  "org.specs2"          %% "specs2-core"          % "4.0.2"       % "test")

scalariformPreferences := scalariformPreferences.value
  .setPreference(DanglingCloseParenthesis, Prevent)
  .setPreference(DoubleIndentConstructorArguments, true)
