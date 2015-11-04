package com.reactific.hsp

import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ProfilerSpec extends Specification {

  sequential

  "Profiler" should {
    "report less than 50 microseconds for empty function" in {
      val profiler = Profiler()
      profiler.profile("empty") {}
      val (count, sum) = profiler.get_one_item("empty")
      count must beEqualTo(1)
      sum must beLessThan(50000.0)
    }
    "Profiler overhead should be under one millisecond" in {
      val profiler = Profiler()
      val t0 = System.nanoTime()
      profiler.profile("a") {}
      val t1 = System.nanoTime()
      val delta = t1 - t0
      delta must beLessThan(1000000L)
    }

    "Nested Profilers Should Not Block" in {
      val profiler = Profiler()
      val fourty_two = profiler.profile("a") { profiler.profile("b") { 42 } }
      fourty_two must beEqualTo(42)
    }

    "Extracting In Profiler.profile context is not permitted" in {
      val profiler = Profiler()
      profiler.profile("oops") { profiler.format_profile_summary } must throwA[IllegalStateException]
    }

    "capture wait and processing time of a Future.map" in {
      val profiler = Profiler()
      val future : Future[Double] = profiler.futureMap("test", Future.successful(42)) { x ⇒ x.toDouble * 2.0 }
      val double = Await.result(future, 1.second)
      double must beEqualTo(84.0)
      val (wait_count, wait_sum) = profiler.get_one_item("test.wait")
      val (count, sum) = profiler.get_one_item("test")
      count must beEqualTo(1)
      wait_count must beEqualTo(1)
      sum must beLessThan(100000.0)
      wait_sum must beGreaterThan(sum)
    }
  }

}
