package com.reactific.hsp

import org.specs2.mutable.Specification

class ProfilerSpec extends Specification {

  sequential

  "Profiler" should {
    "report less than 50 microseconds for empty function" in {
      Profiler.profile("empty") {}
      val (count, sum) = Profiler.get_one_item("empty")
      count must beEqualTo(1)
      sum must beLessThan(50000.0)
    }
    "Profiler overhead should be under one millisecond" in {
      val t0 = System.nanoTime()
      Profiler.profile("a") {}
      val t1 = System.nanoTime()
      val delta = t1 - t0
      delta must beLessThan(1000000L)
    }

    "Nested Profilers Should Not Block" in {
      val fourty_two = Profiler.profile("a") { Profiler.profile("b") { 42 } }
      fourty_two must beEqualTo(42)
    }

    "Extracting In Profiler.profile context is not permitted" in {
      Profiler.profile("oops") { Profiler.format_profile_summary } must throwA[IllegalStateException]
    }
  }

}
