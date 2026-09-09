package gh1578

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail


/**
 * Regression lock for https://github.com/Kotlin/kotlinx.coroutines/issues/1578 (open, labeled
 * "bug", "design", "structured concurrency").
 *
 * Creating a `SupervisorJob(coroutineContext[Job])` as a child of `runBlocking`'s job attaches it
 * to the structured-concurrency hierarchy, but a manually-created `SupervisorJob` is never
 * completed automatically -- only an explicit `.complete()` (or `.cancel()`) finishes it. Since
 * `runBlocking` waits for *all* of its children to finish before returning, simply creating such a
 * job -- without ever running anything on it or completing it -- leaves `runBlocking` waiting
 * forever, even though nothing is actually in flight.
 *
 * This is the simplified reproducer a maintainer confirmed directly on the issue
 * (https://github.com/Kotlin/kotlinx.coroutines/issues/1578#issuecomment-535957288, explained in
 * https://github.com/Kotlin/kotlinx.coroutines/issues/1578#issuecomment-535980159): "This test
 * hangs because you create a child of the `runBlocking` ... that never completes." The maintainer
 * called it "a serious API problem," referencing a fix attempt that never fully landed -- still
 * open and discussed as recently as 2026-07-16.
 *
 * The hang is a genuine thread block, not just a suspension. So this test runs the repro on its
 * own daemon thread and asserts it is still stuck after a bounded wait, without risking hanging
 * the test run itself.
 */
class Gh1578Test {

    @Test
    fun runBlockingHangsOnNeverCompletedManualSupervisorJobChild() {
        val completed = AtomicBoolean(false)
        val thread = Thread {
            try {
                runBlocking {
                    val supervisorJob = SupervisorJob(coroutineContext[Job])
                    assertTrue(supervisorJob.isActive)
                    // supervisorJob is never completed/cancelled -- runBlocking must wait for it anyway.
                }
                completed.set(true)
            } catch (_: InterruptedException) {
                // expected: runBlocking's wait loop throws this once interrupted below
            }
        }
        thread.isDaemon = true
        thread.start()

        // Wait for the thread to actually park inside runBlocking's wait loop instead of sleeping a
        // fixed duration -- deterministic either way: it returns the moment the thread parks, and
        // fails fast (rather than hanging the suite) if it never does.
        val deadline = System.currentTimeMillis() + 10_000
        while (thread.isAlive && thread.state != Thread.State.WAITING && thread.state != Thread.State.TIMED_WAITING) {
            if (System.currentTimeMillis() > deadline) {
                fail("thread neither parked nor completed within 10s; state=${thread.state}")
            }
            Thread.yield()
        }

        // Correct behavior would have `runBlocking` return promptly, since nothing was ever
        // launched on `supervisorJob` and there is nothing left to wait for. Today it just hangs.
        assertTrue(thread.isAlive)
        assertFalse(completed.get())

        // Unblock deterministically: interrupting makes runBlocking's wait loop cancel itself
        // (which cancels supervisorJob too), so join() is guaranteed to return promptly.
        thread.interrupt()
        thread.join()
    }
}
