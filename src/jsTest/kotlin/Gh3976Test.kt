package gh3976

import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Regression lock for https://github.com/Kotlin/kotlinx.coroutines/issues/3976 (open, labeled "bug").
 *
 * Full mechanism written up in `coroutines_gh_3976/findings.md`. Short version: resuming a
 * continuation that has no dispatcher in its context (e.g. `EmptyCoroutineContext`) happens
 * synchronously, in-line, on the resumer's own call stack. If the resumed coroutine doesn't catch
 * its own failure, the exception is forwarded to *its* completion continuation -- and if that
 * completion continuation itself throws on failure (as `suspend fun main()`'s compiler-generated
 * `EmptyContinuation` does), the throw unwinds back up the *shared* synchronous call stack and
 * lands in whichever unrelated `catch` happens to be nearest on it -- here, the resumer's own.
 *
 * `startCoroutine` with a completion that mimics `EmptyContinuation` (throws via `getOrThrow()`)
 * reproduces this without needing to literally be the program's top-level `main()`: wrapping the
 * body in `launch {}` or using a completion that doesn't throw both "fix" it, because both break
 * one of the two preconditions above (a real dispatcher gets added, or nothing throws to unwind).
 */
class Gh3976Test {

    @Test
    fun testUncaughtFailureInUndispatchedContinuationLeaksIntoUnrelatedResumer(): TestResult = runTest {
        val mainContinuation = CompletableDeferred<Continuation<Unit>>()
        val scope = CoroutineScope(EmptyCoroutineContext)
        var caughtInResumer: Throwable? = null
        var mainCompletedNormally = false

        scope.launch {
            val continuation = mainContinuation.await()
            val exception = Exception("Test exception")
            try {
                continuation.resumeWithException(exception)
            } catch (unexpected: Throwable) {
                caughtInResumer = unexpected
            }
        }

        val mainBody: suspend () -> Unit = {
            suspendCancellableCoroutine<Unit> {
                mainContinuation.complete(it)
            }
            mainCompletedNormally = true
        }
        // Completion continuation that throws on failure, just like the compiler-generated
        // `EmptyContinuation` used to bootstrap a real top-level `suspend fun main()`.
        mainBody.startCoroutine(Continuation(EmptyCoroutineContext) { result ->
            result.getOrThrow()
        })

        delay(200)
        scope.cancel()

        // Correct behavior: the exception should surface from `mainBody`'s own completion, not
        // from the resumer's unrelated try/catch, and `mainBody` should never complete normally.
        assertNotNull(caughtInResumer)
        assertEquals("Test exception", caughtInResumer?.message)
        assertFalse(mainCompletedNormally)
    }
}
