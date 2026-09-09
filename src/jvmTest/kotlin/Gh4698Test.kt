package gh4698

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Regression lock for https://github.com/Kotlin/kotlinx.coroutines/issues/4698 (open, labeled "bug").
 *
 * When a [CoroutineDispatcher.dispatch] implementation throws, `launch` with the default
 * `CoroutineStart.DEFAULT` fails fast: the exception propagates synchronously out of the
 * `launch`/`runBlocking` call (this is the special handling added in #880). But
 * `CoroutineStart.ATOMIC` bypasses that fail-fast path entirely -- the coroutine is dispatched via
 * the broken dispatcher, dispatch throws, and the throw is swallowed somewhere in the atomic-start
 * machinery: nothing is ever run, no exception is reported, and anyone joining the launched job
 * hangs forever.
 *
 * The hang is a genuine thread block (dispatch never invokes the continuation, and there is no
 * cancellation-checkpoint to interrupt), so this test runs the repro on its own daemon thread and
 * asserts it is still stuck after a bounded wait, without risking hanging the test run itself --
 * same pattern as [gh4602.Gh4602Test].
 */
class Gh4698Test {

    // Same shape as the issue's own repro: a dispatcher whose dispatch() throws.
    private val brokenDispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) = TODO("dispatcher is broken")
    }

    @Test
    fun defaultStartOnBrokenDispatcherFailsFast() {
        val exception = runCatching {
            runBlocking {
                launch(brokenDispatcher) {}
            }
        }.exceptionOrNull()

        // Baseline: CoroutineStart.DEFAULT already fails fast (the #880 mechanism this issue
        // wants extended to ATOMIC). If this stops throwing, the baseline assumption is broken.
        assertIs<NotImplementedError>(exception)
    }

    @Test
    fun atomicStartOnBrokenDispatcherHangsInsteadOfFailingFast() {
        val completed = AtomicBoolean(false)
        val thread = Thread {
            runBlocking {
                val job = launch(brokenDispatcher, start = CoroutineStart.ATOMIC) {}
                job.join()
            }
            completed.set(true)
        }
        thread.isDaemon = true
        thread.start()

        // Wait for the thread to actually park instead of sleeping a fixed duration --
        // deterministic either way: it returns the moment the thread parks, and fails fast
        // (rather than hanging the suite) if it never does.
        val deadline = System.currentTimeMillis() + 10_000
        while (thread.isAlive && thread.state != Thread.State.WAITING && thread.state != Thread.State.TIMED_WAITING) {
            if (System.currentTimeMillis() > deadline) {
                fail("thread neither parked nor completed within 10s; state=${thread.state}")
            }
            Thread.yield()
        }

        // Correct (fixed) behavior would fail fast, the same way CoroutineStart.DEFAULT does
        // above, so `completed` would become true almost instantly. Today it just hangs.
        assertTrue(thread.isAlive)
        assertFalse(completed.get())
    }
}
