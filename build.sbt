/*
 * Copyright © 2015 Reactific Software LLC. All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


import sbt._
import sbt.Keys._
import scala.language.postfixOps

val classesIgnoredByScoverage : String = Seq[String](
  "<empty>", // Avoids warnings from scoverage
  "EmptyTree$/null"
).mkString(";")


lazy val root = sbt.Project("hotspot-profiler", file(".")).
  enablePlugins(ReactificPlugin/*, ScoverageSbtPlugin */).
  settings(
    organization := "com.reactific",
    copyrightHolder := "Reactific Software LLC",
    startYear := Some(2015),
    developerUrl := url("http://reactific.com/"),
    titleForDocs := "Hot Spot Profiler",
    codePackage := "com.reactific.hsp",
    libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.25",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3" % "test",
    libraryDependencies += "org.specs2" %% "specs2-core" % "3.9.5" % "test",
    scalastyleConfig := file("project/scalastyle-config.xml"),
    // coverageFailOnMinimum := true,
    // coverageExcludedPackages := classesIgnoredByScoverage,
    // coverageMinimum := 85,
    // coverallsToken := Some("EXqKGCEQb6SrCQbdzxEWUcJlSkLCOlHHm"),
    logLevel := Level.Info
)
