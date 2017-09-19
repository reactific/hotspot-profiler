package com.reactific.hsp

import org.slf4j.LoggerFactory
import org.specs2.mutable.Specification
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import org.slf4j.Logger

class ProfilerSpec extends Specification with ProfilerTestTools {

  val logger: Logger = LoggerFactory.getLogger("com.reactific.hsp.ProfilerSpec")
  logger.debug("ProfilerSpec instantiated")

  sequential

  "Profiler" should {
    "report less than 50 microseconds for empty function" in ifSuitableForTimingTest(
      "50 µs maximum"
    ) { () ⇒
      val profiler = Profiler("empty function")
      val attempts = 1000
      for (i ← 1 to attempts) {
        profiler.profile("empty") {}
      }
      profiler.log_profile_summary(logger, "empty function")
      val (count, sum) = profiler.get_one_item("empty")
      count must beEqualTo(attempts)
      (sum must beLessThan(attempts * 50000.0)).toResult
    }

    "have profile overhead < 10 μs" in ifSuitableForTimingTest("10 μs max") {
      () ⇒
        val profiler = Profiler("overhead")
        val attempts = 1000
        var sum = 0.0D
        for (_ ← 1 to attempts) {
          val t0 = System.nanoTime()
          profiler.profile("a") {}
          val t1 = System.nanoTime()
          sum += t1 - t0
        }
        val avg = sum / attempts
        val microseconds = avg / 1000.0D
        logger.debug(s"Average profiler overhead = $microseconds μs")
        (microseconds must beLessThan(10.0)).toResult
    }

    "nested profilers should not block" in {
      val profiler = Profiler("non-blocking")
      val fourty_two = profiler.profile("a") { profiler.profile("b") { 42 } }
      fourty_two must beEqualTo(42)
    }

    "reset_profile_data works" in {
      val profiler = Profiler("reset_profile_data")
      val fourty_two = profiler.profile("forty-two") { 42 }
      fourty_two must beEqualTo(42)
      profiler.format_one_item("forty-two").contains("count=1") must beTrue
      profiler.reset_profile_data()
      profiler.format_one_item("forty-two").contains("count=0") must beTrue
    }

    "format_one_item on disabled Profiler should return empty" in {
      val profiler = Profiler("format_one_item", enabled = false)
      val fourty_two = profiler.profile("item") { 42 }
      fourty_two must beEqualTo(42)
      profiler.format_one_item("item").isEmpty must beTrue
    }

    "get_one_item on disabled Profiler should return zeros" in {
      val profiler = Profiler("get_one_item", enabled = false)
      val fourty_two = profiler.profile("item") { 42 }
      fourty_two must beEqualTo(42)
      profiler.get_one_item("item") must beEqualTo(0 → 0.0)
    }

    "log_profile_summary works" in {
      val profiler = Profiler("log_profile_summary", enabled = false)
      val fourty_two = profiler.profile("item") { 42 }
      fourty_two must beEqualTo(42)
      profiler.log_profile_summary(logger)
      success
    }

    "format_profile_data works" in {
      val profiler = Profiler("format_profile_data")
      val fourty_two = profiler.profile("a") { profiler.profile("b") { 42 } }
      fourty_two must beEqualTo(42)
      val msg = profiler.format_profile_data.toString
      logger.info(s"format_profile_data yielded:\n$msg")
      msg.contains("a\n") must beTrue
      msg.contains("b\n") must beTrue
    }

    "log_profile_data works" in {
      val profiler = Profiler("log_profile_data")
      val fourty_two = profiler.profile("a") { profiler.profile("b") { 42 } }
      fourty_two must beEqualTo(42)
      profiler.log_profile_data(logger)
      success
    }
    "print_profile_data works" in {
      val profiler = Profiler("print_profile_data")
      val fourty_two = profiler.profile("a") { profiler.profile("b") { 42 } }
      fourty_two must beEqualTo(42)
      profiler.print_profile_data(System.out)
      success
    }

    "Extracting In Profiler.profile context is not permitted" in {
      val profiler = Profiler()
      profiler.profile("oops") { profiler.format_profile_summary } must throwA[
        IllegalStateException
      ]
    }

    "capture wait and processing time of a Future.map" in {
      val profiler = Profiler()
      val attempts = 1000
      for (i ← 1 to attempts) {
        val future: Future[Double] =
          profiler.futureMap("test", Future.successful(42)) { x ⇒
            x.toDouble * 2.0
          }
        val double = Await.result(future, 1.second)
        double must beEqualTo(84.0)
      }
      val (wait_count, _) = profiler.get_one_item("test.wait")
      val (count, sum) = profiler.get_one_item("test")
      count must beEqualTo(attempts)
      wait_count must beEqualTo(attempts)
      profiler.log_profile_summary(logger)
      sum must beLessThan(100000.0 * attempts)
    }

    "process a Future.map with profiling disabled" in {
      val profiler = Profiler(enabled = false)
      val future: Future[Double] =
        profiler.futureMap("test", Future.successful(42)) { x ⇒
          x.toDouble * 2.0
        }
      val double = Await.result(future, 1.second)
      double must beEqualTo(84.0)
    }

    "handle async timing" in {
      val profiler = Profiler("async")
      val start = profiler.asyncStart
      start must beGreaterThan(0L)
      profiler.asyncEnd("must", start)
      val (count, sum) = profiler.get_one_item("must")
      count must beEqualTo(1)
      sum must beGreaterThan(0.0D)
    }

    "handle a Future" in {
      val profiler = Profiler("of a Future")
      val future = profiler.profileF("future") {
        Future[Unit] {
          Thread.sleep(42)
          ()
        }
      }
      Await.result(future, 2.second)
      val (count, sum) = profiler.get_one_item("future")
      profiler.log_profile_summary(logger)
      count must beEqualTo(1)
      sum must beGreaterThan(4.0E+7)
      sum must beLessThan(4.9E+7)
    }

    "timedTest with logging works" in {
      val profiler = Profiler("timedTest With Logging")
      timedTest(100000000, "timedTest", profiler, Some(logger)) { (profiler) ⇒
        Thread.sleep(10)
        success
      }
    }
    "timedTest with printing works" in {
      val profiler = Profiler("timedTest With Printing")
      timedTest(100000000, "timedTest", profiler, print = true) { (profiler) ⇒
        Thread.sleep(10)
        success
      }
    }
  }
}
