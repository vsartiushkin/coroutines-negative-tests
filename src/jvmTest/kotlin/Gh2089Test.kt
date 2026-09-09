package gh2089

import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Regression lock for https://github.com/Kotlin/kotlinx.coroutines/issues/2089 (open, labeled
 * "bug", "breaking change"): "CancellableContinuation.invokeOnCancellation cause is inconsistent
 * and breaks its own contract".
 *
 * This is the issue's own minimal repro, unchanged in the two follow-up comments
 * (https://github.com/Kotlin/kotlinx.coroutines/issues/2089#issuecomment-644613495,
 * https://github.com/Kotlin/kotlinx.coroutines/issues/2089#issuecomment-1303117912) that both
 * re-confirm this exact inconsistency is still present.
 *
 * `CompletionHandler`'s documented contract says `cause` is `null` only when the job completed
 * *normally*, and is a `CancellationException` instance when it was cancelled *normally* -- in
 * both scenarios below the coroutine is cancelled, never completes normally, yet the `cause` seen
 * by the `invokeOnCancellation` handler depends purely on registration order relative to the
 * `cancel()` call:
 *
 * - If `invokeOnCancellation` is registered *before* `cancel()` is called, the handler observes
 *   `cause == null` (as if the job completed normally, which it did not).
 * - If `cancel()` is called *before* `invokeOnCancellation` is registered, the handler observes
 *   the actual `CancellationException` instance.
 *
 * This locks in the exact current (buggy) behavior: it will fail the moment either branch starts
 * observing a different `cause` value than what's asserted here.
 */
class Gh2089Test {

    @Test
    fun handlerRegisteredBeforeCancelObservesNullCause() {
        var observedCause: Throwable? = null
        runBlocking {
            try {
                suspendCancellableCoroutine<Unit> { c ->
                    c.invokeOnCancellation { observedCause = it }
                    c.cancel()
                }
            } catch (e: CancellationException) {
                // expected: the coroutine itself is cancelled too
            }
        }

        // Contract-breaking: the job was cancelled, not completed normally, yet cause is null.
        assertNull(observedCause)
    }

    @Test
    fun cancelCalledBeforeHandlerRegisteredObservesCancellationException() {
        var observedCause: Throwable? = null
        runBlocking {
            try {
                suspendCancellableCoroutine<Unit> { c ->
                    c.cancel()
                    c.invokeOnCancellation { observedCause = it }
                }
            } catch (e: CancellationException) {
                // expected: the coroutine itself is cancelled too
            }
        }

        // Same cancellation, opposite registration order -- now cause is the actual exception.
        assertIs<CancellationException>(observedCause)
    }
}
