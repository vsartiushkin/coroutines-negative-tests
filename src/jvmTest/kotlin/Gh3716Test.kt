package gh3716

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression lock for https://github.com/Kotlin/kotlinx.coroutines/issues/3716 (open, labeled "bug").
 *
 * `withTimeout(0L)` throws a `TimeoutCancellationException`, which is a `CancellationException`.
 * When that exception is thrown from inside the *inner* flow of `flatMapLatest`, it is silently
 * swallowed instead of propagating downstream to `.catch`/`.onCompletion`: `onCompletion` observes
 * a `null` cause (as if the flow completed successfully) and `.catch` never fires. The exact same
 * `withTimeout(0L)`, thrown from a flow that is *not* wrapped by `flatMapLatest`, behaves correctly
 * and is delivered to `.catch` as expected. This test locks in that inconsistency.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Gh3716Test {

    // Faithful port of the issue's own `runTest` helper (renamed to avoid colliding with
    // kotlinx.coroutines.test.runTest, which is not used here).
    private suspend fun flatMapLatestWrapped(
        onCompletionCause: (Throwable?) -> Unit,
        onCaught: (Throwable) -> Unit,
        block: suspend FlowCollector<Boolean>.() -> Unit,
    ) = flow {
        emit(true)
    }.flatMapLatest {
        flow { block() }
    }.onCompletion {
        onCompletionCause(it)
    }.catch {
        onCaught(it)
    }.collect()

    @Test
    fun timeoutCancellationExceptionIsSwallowedInsideFlatMapLatestInnerFlow() = runBlocking {
        var completionCauseCaptured = false
        var completionCause: Throwable? = null
        var caught: Throwable? = null

        withTimeout(5_000) {
            flatMapLatestWrapped(
                onCompletionCause = { completionCauseCaptured = true; completionCause = it },
                onCaught = { caught = it },
            ) {
                withTimeout(0L) {
                    // Do nothing, this will not execute -- the deadline has already passed.
                }
            }
        }

        // Correct behavior: onCompletion should observe the TimeoutCancellationException as the
        // cause, and .catch should receive it. Today, onCompletion observes `null` (as if nothing
        // went wrong) and .catch is never invoked -- the exception is swallowed by flatMapLatest.
        assertTrue(completionCauseCaptured)
        assertNull(completionCause)
        assertNull(caught)
    }

    @Test
    fun plainExceptionStillPropagatesThroughFlatMapLatestInnerFlow() = runBlocking {
        var completionCause: Throwable? = null
        var caught: Throwable? = null

        withTimeout(5_000) {
            flatMapLatestWrapped(
                onCompletionCause = { completionCause = it },
                onCaught = { caught = it },
            ) {
                throw RuntimeException("boom")
            }
        }

        // Unlike TimeoutCancellationException, a plain exception is not a CancellationException,
        // so it propagates normally: both onCompletion and .catch observe it. This confirms the
        // swallowing above is specific to CancellationException-shaped failures, not a broken
        // flatMapLatestWrapped helper.
        assertTrue(completionCause is RuntimeException)
        assertTrue(caught is RuntimeException)
    }

    @Test
    fun sameTimeoutCancellationExceptionPropagatesWithoutFlatMapLatestWrapping() = runBlocking {
        var caught: Throwable? = null

        withTimeout(5_000) {
            flow {
                emit(true)
                withTimeout(0L) {
                    // Do nothing, this will not execute
                }
            }.catch {
                caught = it
            }.collect()
        }

        // Without flatMapLatest in the way, the exact same withTimeout(0L) is correctly delivered
        // to .catch -- proving the swallowing above is specific to the flatMapLatest wrapping.
        assertTrue(caught is TimeoutCancellationException)
    }
}
