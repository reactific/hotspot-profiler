package com.reactific.hsp

import java.lang.management.ManagementFactory

import org.slf4j.Logger
import org.specs2.execute.{Failure, Skipped, Result}

/** Test Tools For Profiler.
  * This trait can be mixed into test case software to obtain some tools that are useful for working with
  * the Profiler in the case of
  */
trait ProfilerTestTools {

  /** Determine if the system is viable for doing timing tests */
  private lazy val (suitableForTimingTests: Boolean,
                    unsuitabilityReason: String) = {
    if (System.getenv("TRAVIS") != null) {
      false → "TRAVIS environment variable is present (Travis CI execution)"
    } else {
      val os = ManagementFactory.getOperatingSystemMXBean
      val processors = os.getAvailableProcessors
      val avg = os.getSystemLoadAverage
      val limit = processors / 2
      (avg < limit) → s"CPU Average ($avg) must be less than $limit (less than 1/2 the machine's CPUs)"
    }
  }

  def ifSuitableForTimingTest(name: String)(func: () ⇒ Result): Result = {
    if (suitableForTimingTests)
      func()
    else
      Skipped(
        s"Test '$name' not run because system is unsuitable for timing tests because $unsuitabilityReason")
  }

  /** Profiler wrapper for doing a timed test */
  def timedTest(
      maxNanoSeconds: Double,
      name: String,
      profiler: Profiler = Profiler,
      logger: Option[Logger] = None,
      print: Boolean = false
  )(func: (Profiler) ⇒ Result): Result = {
    if (suitableForTimingTests) {
      val result: Result = profiler.profile(name) { func(profiler) }
      val (_, time) = Profiler.get_one_item(name)
      logger match {
        case Some(log) ⇒
          profiler.log_profile_summary(log, name)
        case _ ⇒
      }
      if (print) {
        profiler.print_profile_summary(System.out)
        println()
      }
      if (time > maxNanoSeconds) {
        Failure(
          s"Test '$name' took ${time}ns which exceeds the limit of ${maxNanoSeconds}ns")
      } else {
        result
      }
    } else {
      Skipped(
        s"Test '$name' not run because system is unsuitable for timing tests because $unsuitabilityReason")
    }
  }

}
