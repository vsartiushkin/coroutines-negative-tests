package gh4602

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression lock for https://github.com/Kotlin/kotlinx.coroutines/issues/4602 (open, labeled "bug").
 *
 * With no Main dispatcher module on the classpath, resolving `Dispatchers.Main` should throw
 * `IllegalStateException("Module with the Main dispatcher is missing")`. Normally it does, but
 * when the coroutine is started with `CoroutineStart.ATOMIC`, the dispatch never happens and the
 * coroutine (and anyone joining it) hangs forever instead of throwing.
 *
 * The hang is a genuine thread block, not just a suspension: `withTimeout`/cancellation cannot
 * unstick it (this is why the original report needed a JUnit `CoroutinesTimeout` rule with a
 * thread interrupt). So this test runs the repro on its own daemon thread and asserts it is still
 * stuck after a bounded wait, without risking hanging the test run itself.
 */
class Gh4602Test {

    @Test
    fun atomicStartOnMissingMainDispatcherHangsInsteadOfThrowing() {
        val completed = AtomicBoolean(false)
        val thread = Thread {
            runBlocking {
                val job = launch(Dispatchers.Main, start = CoroutineStart.ATOMIC) {}
                job.join()
            }
            completed.set(true)
        }
        thread.isDaemon = true
        thread.start()
        thread.join(2_000)

        // Correct behavior would throw IllegalStateException synchronously from `launch`,
        // so `completed` would become true almost instantly. Today it just hangs.
        assertTrue(thread.isAlive)
        assertFalse(completed.get())
    }
}
