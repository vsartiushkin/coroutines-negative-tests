package gh2818

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression lock for https://github.com/Kotlin/kotlinx.coroutines/issues/2818 (open, labeled
 * "bug", "flow-sharing", "flow-fusing", "reproduced").
 *
 * Distinct from [gh2817.Gh2817Test]: `.onEach {}` is the workaround for #2817's much larger fusion
 * bypass (~65 emits), which forces `buffer(Channel.RENDEZVOUS).produceIn(this)` onto an actual
 * rendezvous channel. Even with that workaround, a true rendezvous channel with nobody ever
 * calling `receive()` should suspend on the very first `emit`. It doesn't: exactly one emit slips
 * through first, and only the second one actually suspends.
 *
 * Written as a linear sequence of events (mirroring the repo's own `expect`/`finish`-based version,
 * which isn't available here) rather than timeouts: the first `emit` returning at all, synchronously,
 * with no subscriber ever calling `receive()`, already demonstrates the bypass -- no need to race a
 * timeout against it. The second `emit` is launched separately and confirmed to still be suspended
 * (never completing, never observed past its own start) after a `yield()`, instead of being raced
 * against a timeout too.
 */
class Gh2818Test {

    @Test
    fun sharedFlowOnEachBufferRendezvousProduceInLetsOneEmitBypassBackpressure() = runBlocking {
        val events = mutableListOf<String>()
        events += "start"
        val stream = MutableSharedFlow<Unit>()
        val channel = stream.onEach { }.buffer(Channel.RENDEZVOUS).produceIn(this)
        yield()
        events += "afterInitialYield"

        // Expected (per the emit() docs) to suspend until a subscriber receives it -- nobody ever
        // does -- but it returns immediately instead: this one emit bypasses backpressure.
        stream.emit(Unit)
        events += "firstEmitCompleted"

        val job = launch {
            events += "secondEmitLaunchStarted"
            stream.emit(Unit) // a second emit does suspend, as the first one should have
            events += "secondEmitCompleted" // never reached
        }
        events += "afterLaunchCall"
        yield()
        events += "afterSecondYield"

        assertTrue(job.isActive)
        assertEquals(
            listOf(
                "start",
                "afterInitialYield",
                "firstEmitCompleted",
                "afterLaunchCall",
                "secondEmitLaunchStarted",
                "afterSecondYield"
            ),
            events
        )

        job.cancel()
        channel.cancel()
        coroutineContext.cancelChildren()
    }
}
