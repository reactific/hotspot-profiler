// Comment to get more information during initialization
logLevel := Level.Info

// Scala Version for SBT compilation
scalaVersion := "2.10.4"

// Options for SBT compilation
scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-Xlint")


resolvers += "Sonatype respository" at "https://oss.sonatype.org/content/repositories/releases/"

libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.12"

addSbtPlugin("com.reactific" % "sbt-project" % "0.3.0")
