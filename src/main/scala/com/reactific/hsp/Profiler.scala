/*
 * Copyright © 2015-2017 Reactific Software LLC. All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.reactific.hsp

import java.io.PrintStream

import org.slf4j.Logger
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

object Profiler extends Profiler("Profiler", true)

/** Profiler Module For Manual Code Instrumentation
  * This module provides thread aware profiling at microsecond level
  * granularity with manually inserted code Instrumentation. You can
   * instrument a
  * block of code by wrapping it in Profiling.profile("any name you like").
  * For example, like this:
  * {{{
  * Profiling.profile("Example Code") { "example code" }
  * }}}
  * The result of the Profiling.profile call will be whatever the block returns
  * ("example code" in this case).
  * You can print the results out with print_profile_data or log it with
  * log_profile_data. Once printed or logged, the data is reset and a new
  * capture starts. The printout is grouped by threads and nested
  * Profiling.profile calls are accounted for. Note that when profiling_enabled
  * is false, there is almost zero overhead. In particular, expressions
  * involving computing the argument to profile method will not be computed
  * because it is a functional by-name argument that is not evaluated if
  * profiling is disabled.
 */
case class Profiler(name: String = "Default", enabled: Boolean = true) {

  private case class ProfileItem(
    id: Int,
    t0: Long,
    t1: Long,
    what: String,
    depth: Int)
  private class ThreadInfo {

    var profile_data: mutable.Queue[ProfileItem] = { // scalastyle:ignore
      mutable.Queue.empty[ProfileItem]
    }
    var depth_tracker: Int = 0 // scalastyle:ignore

    def record(id: Int, t0: Long, t1: Long, what: String, depth: Int): Unit = {
      require(depth_tracker > 0)
      depth_tracker -= 1
      profile_data.enqueue(ProfileItem(id, t0, t1, what, depth))
    }
  }

  private val thread_infos = mutable.Map.empty[Thread, ThreadInfo]

  private final val tinfo = new ThreadLocal[ThreadInfo] {
    override def initialValue(): ThreadInfo = {
      val result = new ThreadInfo()
      thread_infos.put(Thread.currentThread(), result)
      result
    }
    override def remove(): Unit = {
      super.remove()
      val _ = thread_infos.remove(Thread.currentThread())
    }
  }

  final def in_profile_context: Boolean = {
    val ti = tinfo.get()
    ti.depth_tracker > 0
  }

  private final def require_non_profile_context(where: String): Unit = {
    if (in_profile_context) {
      throw new IllegalStateException(
        s"$where cannot be called from profile context"
      )
    }
  }

  private var id_counter: Int = 0 // scalastyle:ignore

  @inline private def nextThreadInfo(): (ThreadInfo, Int, Int) = {
    val ti = tinfo.get()
    Profiler.synchronized { id_counter += 1 }
    val depth = ti.depth_tracker
    ti.depth_tracker += 1
    (ti, id_counter, depth)
  }

  def profileF[R](
    what: => String
  )(block: => Future[R]
  )(implicit ec: ExecutionContext
  ): Future[R] = {
    if (enabled) {
      val (ti, id, depth) = nextThreadInfo()
      val t0 = System.nanoTime()
      block.map { r =>
        ti.record(id, t0, System.nanoTime(), what, depth)
        r
      }.recover {
        case x: Throwable =>
          ti.record(id, t0, System.nanoTime(), what, depth)
          throw x
      }
    } else {
      block
    }
  }

  def profile[R](what: ⇒ String)(block: ⇒ R): R = {
    if (enabled) {
      val (ti, id, depth) = nextThreadInfo()
      var t0 = 0L // scalastyle:ignore
      var t1 = 0L // scalastyle:ignore
      try {
        t0 = System.nanoTime()
        val r: R = block // call-by-name
        t1 = System.nanoTime()
        r
      } finally {
        ti.record(id, t0, t1, what, depth)
      }
    } else {
      block
    }
  }

  /*
  trait FilterMonadic[+A, +Repr] extends Any {
    def map[B, That](f: A => B)(implicit bf: CanBuildFrom[Repr, B, That]): That
  def map[A,Repr,B,That](what : ⇒ String, coll: FilterMonadic[A,Repr])
    ( block : A ⇒ B)
    (implicit bf: CanBuildFrom[Repr, B, That]) : That =
  {
    if (profiling_enabled) {
      val (ti, id, depth) = nextThreadInfo()

    }
    else {
      coll.map(block)
    }
  }
   */

  def futureMap[S, B](
    what: ⇒ String,
    future: ⇒ Future[S]
  )(block: S ⇒ B
  )(implicit ec: ExecutionContext
  ): Future[B] = {
    if (enabled) {
      val t0 = System.nanoTime()
      future.map { x ⇒
        val t1 = System.nanoTime()
        val r = block(x)
        val t2 = System.nanoTime()
        val (ti1, id, depth) = nextThreadInfo()
        ti1.record(id, t0, t1, what + ".wait", depth)
        val (ti2, id2, depth2) = nextThreadInfo()
        ti2.record(id2, t1, t2, what, depth2)
        r
      }
    } else {
      future.map(block)
    }
  }

  def asyncStart: Long = {
    System.nanoTime()
  }

  def asyncEnd(what: ⇒ String, t0: Long): Unit = {
    if (enabled) {
      val t1 = System.nanoTime()
      val (ti, id, depth) = nextThreadInfo()
      ti.record(id, t0, t1, what, depth)
    }
  }

  def get_one_item(itemName: String): (Int, Double) = {
    require_non_profile_context("get_one_item")
    if (enabled) {
      var count: Int = 0 // scalastyle:ignore
      var sum: Double = 0.0 // scalastyle:ignore
      for {
        (_, ti) ← thread_infos
      } {
        for {
          info ← ti.profile_data if info.what == itemName
        } {
          count += 1
          sum += (info.t1 - info.t0)
        }
      }
      (count, sum)
    } else {
      (0, 0.0)
    }
  }

  def format_one_item(itemName: String): String = {
    require_non_profile_context("format_one_item")
    if (enabled) {
      val (count, sum) = get_one_item(itemName)
      val normalized_sum = sum / 1000000.0D
      val sumF = normalized_sum.formatted("%1$ 10.3f")
      val avgF = (normalized_sum / count).formatted("%1$ 10.3f")
      s"count=$count, sum=$sumF, avg=$avgF  (" + itemName + ")"
    } else {
      ""
    }
  }

  type SummaryMap = Seq[(String, Int, Int, Double, Double, Double)]

  def summarize_profile_data: SummaryMap = {
    require_non_profile_context("summarize_profile_data")
    val mb =
      new mutable.HashMap[(Int, String), (Int, Int, Double, Double, Double)]()
    for {
      (_, ti) ← thread_infos
    } {
      for {
        info ← ti.profile_data.sortBy(_.id)
      } {
        val time_len: Double = (info.t1 - info.t0).toDouble
        mb.get(info.depth → info.what) match {
          case Some((_id, count, sum, min, max)) ⇒
            mb.put(
              info.depth → info.what,
              (
                _id,
                count + 1,
                sum + time_len,
                Math.min(min, time_len),
                Math.max(max, time_len)
              )
            )
          case None ⇒
            mb.put(
              info.depth → info.what,
              (info.id, 1, time_len, time_len, time_len)
            )
        }
      }
    }
    mb.view.toSeq.sortBy {
      case ((_, _), (id, _, _, _, _)) ⇒ id
    }.map {
      case ((depth, msg), (_, count, sum, min, max)) ⇒
        (msg, depth, count, sum, min, max)
    }
  }

  def format_profile_summary: String = {
    require_non_profile_context("format_profile_summary")
    val initialSize: Int = 4096
    val sb = new StringBuilder(initialSize)
    for {
      (msg, depth, count, sum, min, max) ← summarize_profile_data
    } {
      sb.append((sum / 1000000.0D).formatted("%1$ 12.3f"))
        .append(" ms / ")
        .append(count.formatted("%1$ 7d"))
        .append(" = ")
        .append((sum / 1000000.0D / count).formatted("%1$ 10.3f"))
        .append(", min=")
        .append((min / 1000000.0D).formatted("%1$ 10.3f"))
        .append(", max=")
        .append((max / 1000000.0D).formatted("%1$ 10.3f"))
        .append(" - ")
      for (_ ← 1 to depth) sb.append(".")
      sb.append(msg).append("\n")
    }
    sb.toString()
  }

  def print_profile_summary(out: PrintStream): Unit = {
    require_non_profile_context("print_profile_summary")
    out.print(s"Profiling Summary of $name:\n$format_profile_summary")
  }

  def log_profile_summary(log: Logger): Unit = {
    require_non_profile_context("log_profile_summary")
    log.debug(s"Profiling Summary of $name:\n$format_profile_summary")
  }

  def log_profile_summary(log: Logger, summary_name: String): Unit = {
    require_non_profile_context("log_profile_summary")
    log.debug(
      s"Profiling Summary of $summary_name($name):\n$format_profile_summary"
    )
  }

  def format_profile_data: StringBuilder = {
    require_non_profile_context("format_profile_data")
    val initialSize: Int = 4096
    val str = new StringBuilder(initialSize)
    if (enabled) {
      for {
        (thread, ti) ← thread_infos
      } {
        str
          .append("\nTHREAD(")
          .append(thread.getId)
          .append("): ")
          .append(thread.getName)
        str.append("[").append(thread.getThreadGroup).append("]\n")
        for (info ← ti.profile_data.sortBy(_.id)) {
          val time_len: Double = (info.t1 - info.t0) / 1000000.0D
          str
            .append((info.t0 / 1000000000.0D).formatted("%1$ 12.3f"))
            .append(" - ")
            .append(time_len.formatted("%1$ 10.3f"))
            .append(" ")
          for (_ ← 1 to info.depth) str.append(".")
          str.append(info.what).append("\n")
        }
      }
    }
    reset_profile_data()
    str
  }

  def reset_profile_data(): Unit = {
    thread_infos.clear()
  }

  def log_profile_data(logger: Logger): Unit = {
    require_non_profile_context("log_profile_data")
    if (enabled) {
      logger.debug(s"Profiling Data For $name:\n$format_profile_data")
    }
  }

  def print_profile_data(out: PrintStream): Unit = {
    require_non_profile_context("print_profile_data")
    if (enabled) {
      out.print(s"Profiling Data For $name:\n$format_profile_data")
    }
  }
}
