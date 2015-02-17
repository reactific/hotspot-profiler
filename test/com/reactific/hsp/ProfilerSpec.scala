package com.reactific.hsp

import org.specs2.mutable.Specification

class ProfilerSpec extends Specification {

  "Profiler" should {
    "report nearly 0 for empty function" in {
      Profiler.profile("empty") {}
      val (count, sum) = Profiler.get_one_item("empty")
      count must beEqualTo(1)
      sum must beLessThan(50000.0)
    }
  }

}
