import scalariform.formatter.preferences._

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka"   %% "akka-stream"          % "2.5.11",
  "com.typesafe.akka"   %% "akka-stream-contrib"  % "0.9")

scalariformPreferences := scalariformPreferences.value
  .setPreference(DanglingCloseParenthesis, Prevent)
  .setPreference(DoubleIndentConstructorArguments, true)
