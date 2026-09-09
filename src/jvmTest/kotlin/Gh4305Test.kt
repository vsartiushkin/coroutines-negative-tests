package gh4305

import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Regression lock for https://github.com/Kotlin/kotlinx.coroutines/issues/4305 (open, labeled "bug").
 *
 * `limitedParallelism` should propagate the full `CoroutineContext` it receives in `dispatch()` to
 * the underlying dispatcher it wraps. Instead, `LimitedDispatcher.dispatch` completely discards the
 * `context` parameter it is called with and, when it actually starts a worker on the underlying
 * dispatcher, always passes itself (`this@LimitedDispatcher`) as the context -- see
 * `LimitedDispatcher.dispatch`/`dispatchInternal` in `internal/LimitedDispatcher.kt`. Any extra
 * `CoroutineContext.Element` (e.g. `CoroutineName`) supplied by the caller is silently lost.
 *
 * This locks in the exact current (buggy) behavior: the context observed by the underlying
 * dispatcher's `dispatch()` has no trace of the caller-supplied `CoroutineName` and is instead
 * reference-identical to the `LimitedDispatcher` itself.
 */
class Gh4305Test {

    @Test
    fun limitedParallelismDropsCallerSuppliedContextOnDispatch() {
        var receivedContext: CoroutineContext? = null
        val base = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                receivedContext = context
                block.run()
            }
        }

        val limited = base.limitedParallelism(2)
        limited.dispatch(CoroutineName("Hi!"), Runnable {})

        val context = receivedContext ?: error("dispatch() on the underlying dispatcher was never called")

        // Correct behavior would make the CoroutineName supplied by the caller observable here.
        // Today it is dropped entirely.
        assertNull(context[CoroutineName])

        // The context the underlying dispatcher actually sees is not "some context without the
        // name" -- it is literally the LimitedDispatcher instance itself, reused as the context.
        assertSame(limited, context)
    }
}
