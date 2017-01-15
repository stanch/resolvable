# Installation

Add to your `build.sbt`:

```scala
resolvers ++= Seq(
  "Stanch@bintray" at "http://dl.bintray.com/stanch/maven",
  "JTO@github" at "https://raw.github.com/jto/mvn-repo/master/snapshots"
)

libraryDependencies ++= Seq(
  "org.resolvable" %% "resolvable" % "2.0.0-M4"
)
```