package gh4685

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

// kotlinx.coroutines.test.TestScopeImpl.UncaughtExceptionsBeforeTest is `internal`, so it's not
// accessible from outside the library module -- catch its public supertype instead and match on
// the message it's constructed with, which is stable library-internal behavior we're pinning.

/**
 * Regression lock for https://github.com/Kotlin/kotlinx.coroutines/issues/4685 (open, labeled "bug").
 *
 * `runTest` cancels `backgroundScope`'s job when the test body finishes, but does not join it.
 * A child that is not immediately cancellable (e.g. wrapped in `NonCancellable`) is therefore
 * still running in the background after `runTest` has already returned, which is the root cause
 * behind the flaky `UncaughtExceptionsBeforeTest` reported in the issue.
 *
 * This locks in both the deterministic primary symptom -- the background job outliving `runTest`
 * -- and, by having that background job throw and deterministically waiting for it to finish, the
 * secondary crash too: the exception is reported after its own `runTest` has already unregistered
 * its exception collector, so it is queued as "unprocessed" and only surfaces on the *next*
 * `runTest` call, as `UncaughtExceptionsBeforeTest`.
 */
class Gh4685Test {

    @Test
    fun backgroundScopeJobIsNotJoinedBeforeRunTestReturns() = newSingleThreadContext("Gh4685-bg").use { bgDispatcher ->
        lateinit var bgJob: Job

        runTest {
            bgJob = backgroundScope.launch(bgDispatcher) {
                withContext(NonCancellable) {
                    delay(1_000)
                    error("UncaughtException")
                }
            }
            delay(50)
        }

        // Correct behavior: runTest should join backgroundScope's job before returning, so it
        // would already be completed by now. Today it only cancels it and returns immediately,
        // leaving the NonCancellable child still running on a real background thread.
        assertTrue(bgJob.isCancelled)
        assertFalse(bgJob.isCompleted)

        // Wait deterministically for the leaked job to actually finish and report its exception,
        // instead of sleeping a fixed duration.
        val deadline = System.currentTimeMillis() + 10_000
        while (!bgJob.isCompleted) {
            if (System.currentTimeMillis() > deadline) fail("bgJob did not complete within 10s")
            Thread.sleep(10)
        }

        try {
            runTest {
                fail("unreached")
            }
            fail("expected UncaughtExceptionsBeforeTest to be thrown")
        } catch (e: IllegalStateException) {
            assertEquals(
                "There were uncaught exceptions before the test started. Please avoid this, as " +
                    "such exceptions are also reported in a platform-dependent manner so that " +
                    "they are not lost.",
                e.message
            )
            val cause = assertIs<IllegalStateException>(e.suppressedExceptions.single())
            assertEquals("UncaughtException", cause.message)
        }
    }
}
