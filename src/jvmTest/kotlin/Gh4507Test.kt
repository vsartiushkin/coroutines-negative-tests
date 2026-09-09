package gh4507

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Regression lock for https://github.com/Kotlin/kotlinx.coroutines/issues/4507 (open, labeled "bug").
 *
 * `Flow.zip` cancels the *other* upstream flow with an internal `AbortFlowException` once one side
 * has produced enough elements, and assumes that cancellation exception will surface unchanged from
 * user code. If a flow's own `catch` block on `CancellationException` rethrows a *different*
 * `CancellationException` subclass (a common pattern when instrumenting/annotating exceptions),
 * `zip` fails outright -- but only when that flow is the *first* argument. The exact same flow used
 * as the *second* argument works fine, so the observable behavior is asymmetric depending on
 * argument order, which is the core complaint in the issue.
 *
 * This locks in the exact current (buggy) asymmetry: `f2.zip(f1)` completes normally, while
 * `f1.zip(f2)` throws the flow's own rethrown exception with the library's internal
 * `AbortFlowException` as its cause. It should fail the moment `zip` is fixed to stop leaking this
 * implementation detail into user code.
 */
class Gh4507Test {

    class InstrumentedCancellationException(override val cause: Throwable) : CancellationException()

    private fun flow1() = flow {
        try {
            emit(1)
            emit(2)
            emit(3)
        } catch (e: CancellationException) {
            throw InstrumentedCancellationException(e)
        }
    }

    private fun flow2() = flow {
        emit("one")
        emit("two")
    }

    @Test
    fun zipIsAsymmetricWhenFirstFlowRewrapsCancellationException() = runBlocking {
        withTimeout(5_000) {
            // The flow that rewraps CancellationException in its catch block works fine as the
            // *second* argument to zip.
            val ok = flow2().zip(flow1()) { v1, v2 -> v1 to v2 }.toList()
            assertEquals(listOf("one" to 1, "two" to 2), ok)

            // The exact same flow breaks zip when it's the *first* argument: zip's own internal
            // cancellation leaks out wrapped in the flow's rethrown exception instead of the
            // collection completing normally.
            val failure = assertFailsWith<InstrumentedCancellationException> {
                flow1().zip(flow2()) { v1, v2 -> v1 to v2 }.toList()
            }

            val cause = failure.cause
            assertEquals("kotlinx.coroutines.flow.internal.AbortFlowException", cause.let { it::class.qualifiedName })
            assertEquals("Flow was aborted, no more elements needed", cause.message)
        }
    }
}
