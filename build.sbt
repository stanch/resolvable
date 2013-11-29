name := "needs"

organization := "org.needs"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.3"

autoCompilerPlugins := true

scalacOptions += "-Ydebug"

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots")
)

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  compilerPlugin("org.scala-lang.plugins" % "macro-paradise" % "2.0.0-SNAPSHOT" cross CrossVersion.full)
)

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % "2.2.0",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.0" % "compile",
  "com.google.android" % "android" % "4.1.1.4" % "compile",
  "com.loopj.android" % "android-async-http" % "1.4.4" % "compile",
  "org.scala-lang.modules" %% "scala-async" % "0.9.0-M4",
  "org.scalatest" %% "scalatest" % "2.0" % "test"
)