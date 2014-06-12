name := "resolvable"

organization := "org.resolvable"

version := "2.0.0-M5"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

scalaVersion := "2.10.4"

crossScalaVersions := Seq("2.10.4", "2.11.1")

autoCompilerPlugins := true

scalacOptions ++= Seq("-feature", "-deprecation")

resolvers ++= Seq(
  "Stanch@bintray" at "http://dl.bintray.com/stanch/maven",
  "JTO@github" at "https://raw.github.com/jto/mvn-repo/master/snapshots"
)

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  compilerPlugin("org.scalamacros" % "paradise" % "2.0.0" cross CrossVersion.full)
)

libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
  case Some((2, 10)) ⇒
    Seq("org.scalamacros" %% "quasiquotes" % "2.0.0")
  case _ ⇒
    Seq()
})

libraryDependencies ++= Seq(
  "io.github.jto" %% "validation-core" % "1.0-1c770f4",
  "io.github.jto" %% "validation-json" % "1.0-1c770f4",
  "com.typesafe.play" %% "play-json" % "2.3.0",
  "org.resolvable" %% "play-functional-extras" % "1.0.2",
  "org.scala-lang.modules" %% "scala-async" % "0.9.1",
  "org.scalatest" %% "scalatest" % "2.1.5" % "test"
)

// http clients
libraryDependencies ++= Seq(
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.0" % "provided",
  "com.google.android" % "android" % "4.1.1.4" % "provided",
  "com.loopj.android" % "android-async-http" % "1.4.4" % "provided"
)
