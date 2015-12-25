[![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)
[![Build Status](https://travis-ci.org/reactific/hotspot-profiler.svg?branch=master)](https://travis-ci.org/reactific/hotspot-profiler)
[![Coverage Status](https://coveralls.io/repos/reactific/hotspot-profiler/badge.svg?branch=master)](https://coveralls.io/r/reactific/hotspot-profiler?branch=master)
[![Release](https://img.shields.io/github/release/reactific/hotspot-profiler.svg?style=flat)](https://github.com/reactific/hotspot-profiler/releases)
[![Downloads](https://img.shields.io/github/downloads/reactific/hotspot-profiler/latest/total.svg)](https://github.com/reactific/hotspot-profiler/releases)
[![Join the chat at https://gitter.im/reactific/hotspot-profiler](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/reactific/hotspot-profiler?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Reactific HotSpot Profiler
==========================

This is a very simplistic tool aimed at focusing in on a particular hot spot where sampled profiling just isn't
good enough. It is quite simple to use, just like this:

```scala
import com.reactific.hsp.Profiler

def myHotSpot = Profiler.profile("myHotSpot") {
  ...
}
```

The execution time of myHotSpot will be recorded, separately in each thread. After the test is over, you can write
code to extract the results. To get a simple summary of the values, you can:

```scala
val (count, sum) = Profiler.get_one_item("myHotSpot")
```
to extract the number of invocations (`count`) and the sum of the execution times (`sum`) for the `myHotSpot` function.
If you need fancier reporting you can also do something like:

```scala
log.debug(s"Profiling Results:\n${Profiler.format_profile_data.toString()}")
```
to print out all the results in a nested format for each thread.

Overhead is about 50 microseconds for each Profiler.profile call if profiling is enabled. When it is disabled,
overhead is exactly one boolean dereference in an if statement and a function call (minimal).
