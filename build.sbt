name := "needs"

organization := "org.needs"

version := "2.0.0-M3"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

scalaVersion := "2.10.3"

autoCompilerPlugins := true

scalacOptions += "-feature"

resolvers ++= Seq(
  "Stanch@bintray" at "http://dl.bintray.com/stanch/maven",
  "JTO snapshots" at "https://raw.github.com/jto/mvn-repo/master/snapshots"
)

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.scalamacros" %% "quasiquotes" % "2.0.0-M7",
  compilerPlugin("org.scalamacros" % "paradise" % "2.0.0-M7" cross CrossVersion.full)
)

libraryDependencies ++= Seq(
  "jto.github.io" %% "validation-core" % "1.0-SNAPSHOT",
  "jto.github.io" %% "validation-json" % "1.0-SNAPSHOT",
  "com.typesafe.play" %% "play-json" % "2.2.0",
  "org.needs" %% "play-functional-extras" % "1.0.0",
  "org.scala-lang.modules" %% "scala-async" % "0.9.1",
  "org.scalatest" %% "scalatest" % "2.1.2" % "test"
)

// http clients
libraryDependencies ++= Seq(
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.0" % "provided",
  "com.google.android" % "android" % "4.1.1.4" % "provided",
  "com.loopj.android" % "android-async-http" % "1.4.4" % "provided"
)