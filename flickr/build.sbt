name := "resolvable-flickr"

organization := "org.resolvable"

version := "1.0.0-SNAPSHOT"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

scalaVersion := "2.10.4"

resolvers ++= Seq(
  "Stanch@bintray" at "http://dl.bintray.com/stanch/maven",
  "JTO@github" at "https://raw.github.com/jto/mvn-repo/master/snapshots"
)

libraryDependencies ++= Seq(
  "org.resolvable" %% "resolvable" % "2.0.0-M6",
  "org.scalatest" %% "scalatest" % "2.0" % "test",
  "commons-io" % "commons-io" % "2.4" % "test",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.0" % "test"
)