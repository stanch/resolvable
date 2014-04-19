name := "resolvable"

organization := "org.resolvable"

version := "2.0.0-M4"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

scalaVersion := "2.10.4"

autoCompilerPlugins := true

scalacOptions ++= Seq("-feature", "-deprecation")

resolvers ++= Seq(
  "Stanch@bintray" at "http://dl.bintray.com/stanch/maven",
  "JTO@github" at "https://raw.github.com/jto/mvn-repo/master/snapshots",
  "Typesafe releases" at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.scalamacros" %% "quasiquotes" % "2.0.0-M7",
  compilerPlugin("org.scalamacros" % "paradise" % "2.0.0-M7" cross CrossVersion.full)
)

libraryDependencies ++= Seq(
  "io.github.jto" %% "validation-core" % "1.0-SNAPSHOT",
  "io.github.jto" %% "validation-json" % "1.0-SNAPSHOT",
  "com.typesafe.play" %% "play-json" % "2.2.2",
  "org.resolvable" %% "play-functional-extras" % "1.0.1",
  "org.scala-lang.modules" %% "scala-async" % "0.9.1",
  "org.scalatest" %% "scalatest" % "2.1.3" % "test"
)

// http clients
libraryDependencies ++= Seq(
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.0" % "provided",
  "com.google.android" % "android" % "4.1.1.4" % "provided",
  "com.loopj.android" % "android-async-http" % "1.4.4" % "provided"
)
