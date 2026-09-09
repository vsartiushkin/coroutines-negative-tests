package gh4590

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/**
 * Regression lock for https://github.com/Kotlin/kotlinx.coroutines/issues/4590 (open, labeled "bug").
 *
 * `flowOn` installs a `CopyableThreadContextElement` (here, `ThreadLocal.asContextElement`) around
 * the upstream flow. When the *downstream* collector has no such element of its own and the whole
 * pipeline runs on a single thread (no dispatch actually needed anywhere), the thread-local value
 * installed by `flowOn` is not restored back to its prior value at every point the docs promise:
 * it leaks into the collector's `collect { ... }` block (which has no such context element at all),
 * survives a `yield()` inside that block, and -- worst of all -- is still not restored to `null`
 * on the calling thread even after the enclosing `runBlocking` call has fully returned and every
 * coroutine involved has completed.
 *
 * This locks in the exact current (buggy) sequence of observed thread-local values. It will fail
 * the moment `updateThreadContext`/`restoreThreadContext` calls around `flowOn`'s fast path are
 * fixed to be fully matched, which is exactly what should happen once the underlying bug is fixed.
 */
class Gh4590Test {

    @Test
    fun flowOnThreadLocalLeaksPastCollectorAndPastRunBlocking() {
        val threadLocal = ThreadLocal<String?>()
        val observed = mutableListOf<Pair<Int, String?>>()

        runBlocking {
            withTimeout(5_000) {
                launch {
                    flow {
                        observed += 1 to threadLocal.get()
                        emit(0)
                        observed += 2 to threadLocal.get()
                        delay(100.milliseconds)
                        observed += 3 to threadLocal.get()
                    }
                        .flowOn(threadLocal.asContextElement("flow"))
                        .collect {
                            observed += 4 to threadLocal.get()
                            yield()
                            observed += 5 to threadLocal.get()
                        }
                    observed += 6 to threadLocal.get()
                }
                observed += 7 to threadLocal.get()
            }
        }

        // Correct behavior would have 4, 5 and 8 (below) all be `null`, since neither the
        // collector's `launch` nor the calling thread itself ever installs "flow" on the
        // thread local -- only the upstream `flowOn` side does, and only transiently.
        assertEquals(
            listOf(
                7 to null,
                1 to "flow",
                4 to "flow", // BUG: should be null -- leaks into the collector block
                5 to "flow", // BUG: should be null -- survives a yield() too
                2 to "flow",
                3 to "flow",
                6 to null,
            ),
            observed
        )

        // The worst manifestation: even after every coroutine above has completed and control is
        // back in ordinary, non-suspending code on the original calling thread, the thread local
        // set by flowOn's context element is still not restored to null.
        assertEquals("flow", threadLocal.get()) // BUG: should be null
    }
}
