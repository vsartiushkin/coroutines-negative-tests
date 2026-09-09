package gh2817

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression lock for https://github.com/Kotlin/kotlinx.coroutines/issues/2817 (open, labeled "bug").
 *
 * There are two distinct fusion bugs described in the issue, so there are two tests here.
 *
 * This locks in the exact current (buggy) bypass counts: each test will fail the moment its number
 * changes, which is exactly what should happen once the underlying fusion bug is fixed.
 */
class Gh2817Test {

    @Test
    fun sharedFlowBufferZeroProduceInDoesNotApplyRendezvousBackpressure() = runBlocking {
        val stream = MutableSharedFlow<Unit>()
        val channel = stream.buffer(0).produceIn(this)
        yield() // let produceIn's collector coroutine subscribe

        // buffer(0) right before produceIn is discarded; produceIn's own default capacity (64)
        // is used instead, plus one in-flight slot. A successful emit() to an already-waiting
        // collector doesn't suspend, so this drains synchronously with no yields at all.
        var emitted = 0
        repeat(EMITS_THAT_CURRENTLY_BYPASS_BACKPRESSURE) {
            stream.emit(Unit)
            emitted++
        }
        assertEquals(EMITS_THAT_CURRENTLY_BYPASS_BACKPRESSURE, emitted)

        // The next emit is the one that should genuinely block.
        val probe = launch {
            stream.emit(Unit)
            throw AssertionError("66th emit should not complete")
        }
        yield()
        assertTrue(probe.isActive)

        probe.cancel()
        channel.cancel()
        coroutineContext.cancelChildren()
    }

    @Test
    fun channelFlowBufferZeroShareInAppliesBufferOnlyUpstream() = runBlocking {
        var sent = 0
        val shared = channelFlow<Unit> {
            while (true) {
                send(Unit)
                sent++
            }
        }
            .buffer(capacity = 0)
            .shareIn(this, SharingStarted.WhileSubscribed(), replay = 0)

        // Subscribes to trigger WhileSubscribed(), then gets stuck forever on the very first item
        // it receives - a permanently-blocked sink, so the count below reflects only the upstream
        // channelFlow+buffer(0)+shareIn fusion's own slack, not how fast a real consumer could drain it.
        val downstream = launch { shared.collect { awaitCancellation() } }
        // Unlike the test above, the producer here lives inside channelFlow's own coroutine, so
        // it always needs real dispatcher round-trips through shareIn's internal collector. This
        // settle point is deterministic on a single-threaded dispatcher (confirmed stable from
        // yield #70 through #200), so a small fixed margin over that is enough.
        repeat(SEND_SETTLE_YIELDS) { yield() }

        // buffer(0) is honored only between the channelFlow and shareIn's own internal collector;
        // shareIn's sharing mechanism still lets the producer run ahead by its own default capacity.
        assertEquals(SENDS_THAT_CURRENTLY_BYPASS_BACKPRESSURE, sent)

        downstream.cancel()
        coroutineContext.cancelChildren()
    }

    companion object {
        // Observed on kotlinx-coroutines-core 1.11.0. The correct value would be 1.
        private const val EMITS_THAT_CURRENTLY_BYPASS_BACKPRESSURE = 65

        // Observed on kotlinx-coroutines-core 1.11.0. The correct value would be 1.
        private const val SENDS_THAT_CURRENTLY_BYPASS_BACKPRESSURE = 66
        private const val SEND_SETTLE_YIELDS = 80
    }
}
