package gh4382

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression lock for https://github.com/Kotlin/kotlinx.coroutines/issues/4382 (open, labeled "bug").
 *
 * `cancel()`ing a scope should promptly finish it and all its children, even a child that was
 * `launch`ed onto a dispatcher whose `dispatch()` never actually runs the submitted block (this is
 * the same root cause the original report's `withTimeout` + `withContext(dispatcher)` hits when
 * the target dispatcher cannot execute the body -- e.g. because its single thread is already
 * blocked). Since the child coroutine is never dispatched, cancelling the enclosing
 * `coroutineScope` never gets a chance to observe/finish it, and the whole `runBlocking` hangs
 * forever instead of completing.
 *
 * This is the simplified reproducer a maintainer posted directly on the issue
 * (https://github.com/Kotlin/kotlinx.coroutines/issues/4382#issuecomment-2862396584).
 *
 * The hang is a genuine thread block, not just a suspension: this test runs the repro on its own
 * daemon thread and asserts it is still stuck after a bounded wait, without risking hanging the
 * test run itself.
 */
class Gh4382Test {

    @Test
    fun cancelHangsWhenChildIsLaunchedOnDispatcherThatNeverRunsIt() {
        val completed = AtomicBoolean(false)
        val thread = Thread {
            runBlocking {
                val perpetuallyBusyDispatcher = object : CoroutineDispatcher() {
                    override fun dispatch(context: CoroutineContext, block: Runnable) {
                        // never actually runs `block` -- the dispatcher "cannot execute the body"
                    }
                }
                coroutineScope {
                    launch(perpetuallyBusyDispatcher) {
                        error("Unreachable code")
                    }
                    cancel()
                }
            }
            completed.set(true)
        }
        thread.isDaemon = true
        thread.start()
        thread.join(3_000)

        // Correct behavior would have `cancel()` finish the scope (and its unstarted child)
        // promptly, so `completed` would become true almost instantly. Today it just hangs.
        assertTrue(thread.isAlive)
        assertFalse(completed.get())
    }
}
