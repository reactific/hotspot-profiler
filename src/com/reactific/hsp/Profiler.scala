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

package com.reactific.hsp

import java.io.PrintStream

import scala.collection.generic.{CanBuildFrom, FilterMonadic}
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

/** Profiler Module For Manual Code Instrumentation
  * This module provides thread aware profiling at microsecond level granularity with manually inserted code Instrumentation. You can instrument a
  * block of code by wrapping it in Profiling.profile("any name you like"). For example, like this:
  * {{{
  * Profiling.profile("Example Code") { "example code" }
  * }}}
  * The result of the Profiling.profile call will be whatever the block returns ("example code" in this case).
  * You can print the results out with print_profile_data or log it with log_profile_data. Once printed or logged, the data is reset
  * and a new capture starts. The printout is grouped by threads and nested Profiling.profile calls are accounted for.
  * Note that when profiling_enabled is false, there is almost zero overhead. In particular, expressions involving computing the argument to
  * profile method will not be computed because it is a functional by-name argument that is not evaluated if profiling is disabled.
  */
object Profiler {

  var profiling_enabled = true

  class ThreadInfo {
    var profile_data = mutable.Queue.empty[(Int, Long, Long, String, Int)]
    var depth_tracker : Int = 0
  }

  val thread_infos = mutable.Map.empty[Thread, ThreadInfo]

  private final val tinfo = new ThreadLocal[ThreadInfo] {
    override def initialValue() : ThreadInfo = {
      val result = new ThreadInfo()
      thread_infos.put(Thread.currentThread(), result)
      result
    }
    override def remove() : Unit = {
      super.remove()
      thread_infos.remove(Thread.currentThread())
    }
  }

  private final def in_profile_context : Boolean = {
    val ti = tinfo.get()
    ti.depth_tracker > 0
  }

  private final def require_non_profile_context(where : String) : Unit = {
    if (in_profile_context) {
      throw new IllegalStateException(s"$where cannot be called from profile context")
    }
  }

  private var id_counter : Int = 0

  @inline private def nextThreadInfo() : (ThreadInfo, Int, Int) = {
    val ti = tinfo.get()
    Profiler.synchronized {id_counter += 1 }
    val depth = ti.depth_tracker
    ti.depth_tracker += 1
    (ti, id_counter, depth)
  }

  @inline private def record(id: Int, depth: Int, what: String, t0: Long, t1: Long) = {
    val ti = tinfo.get()
    ti.depth_tracker -= 1
    ti.profile_data.enqueue((id, t0, t1, what, depth))
  }

  def profile[R](what : ⇒ String)(block : ⇒ R) : R = {
    if (profiling_enabled) {
      val (ti, id, depth) = nextThreadInfo()
      var t0 = 0L
      var t1 = 0L
      try {
        t0 = System.nanoTime()
        val r : R = block // call-by-name
        t1 = System.nanoTime()
        r
      } finally {
        record(id, depth, what, t0, t1)
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

  def futureMap[S,B](what : ⇒ String, future: ⇒ Future[S])(block: S ⇒ B)(implicit ec: ExecutionContext) : Future[B] = {
    if (profiling_enabled) {
      val t0 = System.nanoTime()
      future.map { x ⇒
        val t1 = System.nanoTime()
        val r = block(x)
        val t2 = System.nanoTime()
        val (ti, id, depth) = nextThreadInfo()
        record(id, depth, what + ".wait", t0, t1)
        val (ti2, id2, depth2) = nextThreadInfo()
        record(id2, depth2, what, t1, t2)
        r
      }
    } else {
      future.map(block)
    }
  }

  def get_one_item(itemName : String) : (Int, Double) = {
    require_non_profile_context("get_one_item")
    if (profiling_enabled) {
      var count : Int = 0
      var sum : Double = 0.0
      for ((thread, ti) ← thread_infos) {
        for ((id, t0, t1, msg, depth) ← ti.profile_data if msg == itemName) {
          count += 1
          sum += (t1 - t0)
        }
      }
      (count, sum)
    } else {
      (0, 0.0)
    }
  }

  def format_one_item(itemName : String) : String = {
    require_non_profile_context("format_one_item")
    if (profiling_enabled) {
      val (count, sum) = get_one_item(itemName)
      "count=" + count + ", sum=" + (sum / 1000000.0D).formatted("%1$ 10.3f") + ", avg=" + (sum / 1000000.0D / count).formatted("%1$ 10.3f") + "  (" + itemName + ")"
    } else {
      ""
    }
  }

  type SummaryMap = Seq[(String, Int, Int, Double)]
  def summarize_profile_data : SummaryMap = {
    require_non_profile_context("summarize_profile_data")
    val mb = new mutable.HashMap[(Int, String), (Int, Int, Double)]()
    for ((thread, ti) ← thread_infos) {
      for ((id, t0, t1, msg, depth) ← ti.profile_data.sortBy { x ⇒ x._1 }) {
        val time_len : Double = t1 - t0
        mb.get(depth → msg) match {
          case Some((_id, count, sum)) ⇒ mb.put(depth → msg, (_id, count + 1, sum + time_len))
          case None ⇒ mb.put(depth → msg, (id, 1, time_len))
        }
      }
    }
    mb.view.toSeq.sortBy { case ((depth, msg), (id, count, sum)) ⇒ id } map {
      case ((depth, msg), (id, count, sum)) ⇒ (msg, depth, count, sum)
    }
  }

  def format_profile_summary : String = {
    require_non_profile_context("format_profile_summary")
    val sb = new StringBuilder(4096)
    for ((msg, depth, count, sum) ← summarize_profile_data) {
      sb.append((sum / 1000000.0D).formatted("%1$ 12.3f")).append(" / ").append(count.formatted("%1$ 7d")).append(" = ").
        append((sum / 1000000.0D / count).formatted("%1$ 10.3f")).append(" - ")
      for (i ← 1 to depth) sb.append(".")
      sb.append(msg).append("\n")
    }
    sb.toString()
  }

  def print_profile_summary(out : PrintStream) : Unit = {
    require_non_profile_context("print_profile_summary")
    out.print(format_profile_summary)
  }

  def format_profile_data : StringBuilder = {
    require_non_profile_context("format_profile_data")
    val str = new StringBuilder(4096)
    if (profiling_enabled) {
      for ((thread, ti) ← thread_infos) {
        str.append("\nTHREAD(").append(thread.getId).append("): ").append(thread.getName)
        str.append("[").append(thread.getThreadGroup).append("]\n")
        for ((id, t0, t1, msg, depth) ← ti.profile_data.sortBy { x ⇒ x._1 }) {
          val time_len : Double = (t1 - t0) / 1000000.0D
          str.append((t0 / 1000000000.0D).formatted("%1$ 12.3f")).append(" - ").append(time_len.formatted("%1$ 10.3f")).append(" ")
          for (i ← 1 to depth) str.append(".")
          str.append(msg).append("\n")
        }
      }
    }
    reset_profile_data()
    str
  }

  def reset_profile_data() : Unit = {
    require_non_profile_context("reset_profile_data")
    thread_infos.clear()
  }

  def print_profile_data(out : PrintStream) = {
    require_non_profile_context("print_profile_data")
    if (profiling_enabled) {
      out.print(format_profile_data.toString())
    }
  }

}
