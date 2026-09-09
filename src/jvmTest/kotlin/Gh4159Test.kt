package gh4159

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class UpstreamFailure : RuntimeException("upstream failure")

/**
 * Regression lock for https://github.com/Kotlin/kotlinx.coroutines/issues/4159 (open, labeled "bug").
 *
 * When `.catch { }` is placed *downstream* of a `.flowOn(...)` operator (so the throwing producer
 * runs in a different coroutine than the collector), and the collector stops early via `first()`,
 * an exception thrown by the upstream producer AFTER the collector has already gotten its one
 * element can cross the `flowOn` coroutine boundary uncaught: `.catch` is never invoked, and the
 * raw upstream exception (with an `AbortFlowException` suppressed onto it, from `first()`'s
 * cancellation of the flow) propagates out of `first()` instead.
 *
 * Placing `.catch` *before* `.flowOn` (same coroutine as the producer) does not have this problem --
 * see [firstDoesNotThrowWhenCatchIsBeforeFlowOn] below for the control case.
 *
 * This locks in the exact current (buggy) behavior: it will fail the moment `.catch` starts being
 * invoked in this scenario, or the exception stops crossing the `flowOn` boundary.
 */
class Gh4159Test {

    @Test
    fun catchAfterFlowOnDoesNotSeeUpstreamExceptionOnFirst() = runBlocking {
        var caughtByCatchOperator = false

        val flow = flow {
                emit("yo")
                throw UpstreamFailure()
            }
            .flowOn(Dispatchers.Default)
            .catch { caughtByCatchOperator = true }

        val thrown = withTimeout(5_000) {
            try {
                flow.first()
                null
            } catch (e: Throwable) {
                e
            }
        }

        assertFalse(caughtByCatchOperator)

        val upstream = assertIs<UpstreamFailure>(thrown)
        assertTrue(upstream.suppressed.any { it::class.simpleName == "AbortFlowException" })
    }

    @Test
    fun firstDoesNotThrowWhenCatchIsBeforeFlowOn() = runBlocking {
        var caughtByCatchOperator = false

        val flow = flow {
                emit("yo")
                throw UpstreamFailure()
            }
            .catch { caughtByCatchOperator = true }
            .flowOn(Dispatchers.Default)

        val result = withTimeout(5_000) { flow.first() }

        assertTrue(caughtByCatchOperator)
        assertTrue(result == "yo")
    }
}
