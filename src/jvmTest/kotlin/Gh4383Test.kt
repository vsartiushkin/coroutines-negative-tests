package gh4383

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Regression lock for https://github.com/Kotlin/kotlinx.coroutines/issues/4383 (open, labeled "bug").
 *
 * `collectLatest` is implemented as `mapLatest(action).buffer(0).collect()`, and the `buffer()`
 * operator's `ChannelFlow.produceImpl` starts its internal producer coroutine -- the one that
 * actually subscribes to the upstream flow -- with `CoroutineStart.ATOMIC`, not `UNDISPATCHED`.
 * So `launch(start = CoroutineStart.UNDISPATCHED) { flow.collectLatest { ... } }` does *not*
 * synchronously subscribe to `flow`: the outer `launch` itself runs inline up to its first real
 * suspension point, but the inner producer coroutine is merely scheduled, not run inline.
 *
 * The issue reports this as "flaky" because on a multi-threaded dispatcher (e.g. `Dispatchers.IO`,
 * as used in the issue's own repro) that scheduled producer coroutine may or may not get picked up
 * by another thread before the next line runs. On a single-threaded dispatcher (plain
 * `runBlocking`, used below, with no `Dispatchers.IO`) there is no other thread to pick it up, so
 * the miss stops being a race: it is guaranteed to happen on every run, because the producer
 * coroutine cannot possibly run before this thread becomes free. That is the deterministic angle
 * this test locks in, instead of the flaky one from the original report.
 *
 * The maintainer agreed collection should start synchronously here; once fixed, `valueReceived`
 * will already be `true` right after `tryEmit`, and this test will fail.
 *
 * The wait for the internal producer to run used to be a `withTimeoutOrNull` + polling `delay`
 * loop, checking for the value to (never) arrive. It's simpler than that: since everything here
 * runs on the same single-threaded dispatcher (plain `runBlocking`), a single `yield()` is enough
 * to give the ATOMIC-started producer its one and only turn to run -- confirmed directly via
 * `flow.subscriptionCount`, rather than inferred from a timeout expiring.
 */
class Gh4383Test {

    @Test
    fun undispatchedCollectLatestMissesValueEmittedBeforeItsInternalProducerRuns() = runBlocking {
        val flow = MutableSharedFlow<Int>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        var valueReceived = false

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            flow.collectLatest {
                valueReceived = true
            }
        }

        // tryEmit always succeeds (it's buffered by extraBufferCapacity), regardless of whether
        // anyone is subscribed yet.
        check(flow.tryEmit(1))

        // Immediately, synchronously, with no delay: on a single-threaded dispatcher the internal
        // producer coroutine that collectLatest schedules with CoroutineStart.ATOMIC cannot have
        // run yet, so it cannot have subscribed to `flow` yet either.
        assertFalse(valueReceived)

        // A single yield is enough for the internal producer (ATOMIC-started, same event loop) to
        // run and subscribe -- it does not need a real dispatch, just its turn on this thread.
        yield()

        // Emitted value is never collected: the producer subscribed only after the value was
        // already gone (replay = 0), so it will never see it.
        assertTrue(flow.subscriptionCount.value > 0)
        assertFalse(valueReceived)

        job.cancel()
    }

    // Generalizes the same root cause -- collectLatest's internal producer not starting
    // synchronously under an UNDISPATCHED launch -- from a MutableSharedFlow/tryEmit repro to a
    // plain cold flow {}. Sourced from https://github.com/Kotlin/kotlinx.coroutines/pull/4488,
    // whose own version of this test uses the repo-internal `TestBase`/`expect`/`finish` ordering
    // helpers (not available to this standalone project, which only depends on the published
    // artifacts) and is deliberately written to fail on current `develop` (the PR author: "The
    // breaking test is separated so that we can see in the CI that it is indeed broken"). Since
    // this is a *pinned* regression lock, it must pass against current behavior, so this version
    // records the actual event order into a list and asserts on it directly, in the order that
    // really occurs today (verified empirically) rather than the order the PR's test expects.
    @Test
    fun undispatchedCollectLatestDoesNotSynchronouslySubscribeToUpstream() = runBlocking {
        val events = mutableListOf<String>()
        events += "start"
        val myFlow = flow<Int> {
            events += "flowBodyStarted"
            yield()
            events += "flowBodyResumedAfterYield"
        }

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            events += "launchStarted"
            myFlow.collectLatest {
                fail("collectLatest action should never be invoked -- myFlow never emits")
            }
            events += "collectLatestCompleted"
        }

        events += "afterLaunchCall"

        job.join()

        // Correct behavior would have the UNDISPATCHED launch synchronously enter myFlow's body,
        // so "flowBodyStarted" would appear right after "launchStarted", before "afterLaunchCall".
        // Today the internal producer is merely scheduled, not run inline, so "afterLaunchCall"
        // is observed first.
        assertEquals(
            listOf(
                "start",
                "launchStarted",
                "afterLaunchCall",
                "flowBodyStarted",
                "flowBodyResumedAfterYield",
                "collectLatestCompleted"
            ),
            events
        )
    }
}
