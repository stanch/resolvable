name := "needs"

organization := "org.needs"

version := "1.0.0-20131203"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

scalaVersion := "2.10.3"

autoCompilerPlugins := true

scalacOptions ++= Seq(
  "-feature"//,
  //"-Ydebug"
)

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  "Stanch@bintray" at "http://dl.bintray.com/stanch/maven"
)

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  compilerPlugin("org.scala-lang.plugins" % "macro-paradise" % "2.0.0-SNAPSHOT" cross CrossVersion.full)
)

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % "2.2.0",
  "org.needs" %% "play-json-applicative" % "1.0.0",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.0" % "compile",
  "com.google.android" % "android" % "4.1.1.4" % "compile",
  "com.loopj.android" % "android-async-http" % "1.4.4" % "compile",
  "org.scala-lang.modules" %% "scala-async" % "0.9.0-M4",
  "com.typesafe" %% "scalalogging-slf4j" % "1.0.1",
  "org.scalatest" %% "scalatest" % "2.0" % "test",
  "ch.qos.logback" % "logback-classic" % "1.0.13" % "test"
)