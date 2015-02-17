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
