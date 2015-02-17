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

// Shell prompt which show the current project,
// git branch and build version
object ShellPrompt {
  object devnull extends ProcessLogger {
    def info(s: => String) {}

    def error(s: => String) {}

    def buffer[T](f: => T): T = f
  }

  def currBranch = (
    ("git status -sb" lines_! devnull headOption)
      getOrElse "-" stripPrefix "## ")

  val buildShellPrompt = {
    (state: State) =>
    {
      val currProject = Project.extract(state).currentProject.id
      "%s:%s:%s> ".format(
        currProject, currBranch, HSPBuild.hsp_version)
    }
  }
}

object HSPBuild extends Build {
  val name = "hotspot-profiler"
  val hsp_version = "0.1.0-SNAPSHOT"

  val buildSettings : Seq[sbt.Def.Setting[_]] = Defaults.coreDefaultSettings ++
    Seq(
    ) ++ Publish.settings ++ Docs.settings

  lazy val HotSpotProfiler =
    Project(name, file(".")).
      settings(Defaults.coreDefaultSettings:_*).
      settings(
        organization := "com.reactific",
        resolvers := Dependencies.resolvers,
        version := hsp_version,
        scalaVersion := "2.11.5",
        javaOptions in test ++= Seq("-Xmx512m", "-XX:MaxPermSize=512m"),
        scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation", "-target:jvm-1.7"),
        scalacOptions in(Compile, doc) ++=
          Seq("-feature", "-unchecked", "-deprecation", "-diagrams", "-implicits"),
        scalacOptions in(Compile, doc) ++= Opts.doc.title("Reactific HotSpot Profiler API"),
        scalacOptions in(Compile, doc) ++= Opts.doc.version(hsp_version),
        sourceDirectories in Compile := Seq(baseDirectory.value / "src"),
        sourceDirectories in Test := Seq(baseDirectory.value / "test"),
        unmanagedSourceDirectories in Compile := Seq(baseDirectory.value / "src"),
        unmanagedSourceDirectories in Test := Seq(baseDirectory.value / "test"),
        scalaSource in Compile := baseDirectory.value / "src",
        scalaSource in Test := baseDirectory.value / "test",
        javaSource in Compile := baseDirectory.value / "src",
        javaSource in Test := baseDirectory.value / "test",
        resourceDirectory in Compile := baseDirectory.value / "src/resources",
        resourceDirectory in Test := baseDirectory.value / "test/resources",
        shellPrompt := ShellPrompt.buildShellPrompt,
        fork in Test := false,
        parallelExecution in Test := false,
        logBuffered in Test := false,
        ivyScala := ivyScala.value map {_.copy(overrideScalaVersion = true)},
        libraryDependencies ++= Dependencies.all
      ).
      settings(Publish.settings:_*).
      settings(Docs.settings:_*)
}
