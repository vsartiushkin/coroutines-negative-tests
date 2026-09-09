package gh1679

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression lock for https://github.com/Kotlin/kotlinx.coroutines/issues/1679 (open, labeled
 * "bug"): "Deadlock in case of multiple nested runBlocking".
 *
 * This is the maintainer's own minimal reproducer
 * (https://github.com/Kotlin/kotlinx.coroutines/issues/1679#issuecomment-563249536), which the
 * original reporter confirmed reproduces "the same effect" as their original, larger repro
 * (https://github.com/Kotlin/kotlinx.coroutines/issues/1679#issuecomment-563253909).
 *
 * A `launch`ed child (`job`) itself runs a nested `runBlocking { }`. After a single `yield()`,
 * the outer `runBlocking` starts *another* nested `runBlocking` that busy-loops on
 * `while (job.isActive) yield()`. Per the maintainer's diagnosis
 * (https://github.com/Kotlin/kotlinx.coroutines/issues/1679#issuecomment-564967353), a nested
 * `runBlocking` that depends on progress being made by its *outer* `runBlocking`'s event loop is
 * a deadlock by definition: the outermost loop can't process the work that would mark `job`
 * complete while the innermost `runBlocking` is itself spinning on that same thread waiting for
 * exactly that to happen.
 *
 * This is a "live" deadlock -- a busy `yield()` spin, not a blocked thread -- so this test runs
 * the repro on its own daemon thread and asserts it is still spinning (alive) after a bounded
 * wait, without risking hanging the test run itself.
 */
class Gh1679Test {

    @Test
    fun nestedRunBlockingWaitingOnOuterEventLoopDeadlocks() {
        val completed = AtomicBoolean(false)
        val thread = Thread {
            runBlocking {
                val job = launch {
                    runBlocking {
                    }
                }

                yield() // Comment yield and test starts to pass
                runBlocking {
                    while (job.isActive) yield()
                }
            }
            completed.set(true)
        }
        thread.isDaemon = true
        thread.start()
        thread.join(5_000)

        // Correct behavior would have the innermost runBlocking's loop observe job.isActive ==
        // false shortly after the child completes, so the whole thing returns promptly. Today it
        // just spins forever.
        assertTrue(thread.isAlive)
        assertFalse(completed.get())
    }
}
