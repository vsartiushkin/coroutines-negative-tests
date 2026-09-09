package gh4580

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression lock for https://github.com/Kotlin/kotlinx.coroutines/issues/4580 (open, labeled
 * "bug", "test").
 *
 * When the system under test is only handed a bare `CoroutineDispatcher` (not a `CoroutineScope`)
 * to launch on -- the documented anti-pattern the issue is about -- `runTest` has no way to know
 * that task still exists. If that task is suspended on something that resumes from *outside* the
 * test scheduler's own virtual-time queue (e.g. a real background thread completing a
 * `CompletableDeferred`, as in the issue's reproducer), `runTest` returns without cancelling,
 * joining, or otherwise reporting on it: it is silently orphaned. Its `finally`/cleanup code never
 * runs, because nothing is left driving that dispatcher's scheduler once `runTest` has returned.
 *
 * This locks in the exact, fully deterministic shape of the bug (no real threads or sleeps
 * needed to observe it):
 *  1. The instant `runTest` returns, the orphaned job is still active -- not completed, and,
 *     tellingly, not even cancelled. `runTest` neither waits for it nor tears it down.
 *  2. Completing the resource it was waiting on does *not* make it finish: with `runTest` gone,
 *     nothing pumps the dispatcher's queue, so the resumed continuation just sits there forever.
 *  3. The job is not actually stuck -- it's merely abandoned. Manually draining the same
 *     scheduler after the fact (something only possible because the test happened to keep a
 *     reference to it) completes the job and runs its cleanup, proving the loss was entirely a
 *     `runTest`-teardown problem, not a deadlock in the task itself.
 */
class Gh4580Test {

    @Test
    fun taskLaunchedOnBareDispatcherIsOrphanedByRunTest() {
        val dispatcher = StandardTestDispatcher()
        val resource = CompletableDeferred<Unit>()
        var cleanedUp = false
        lateinit var orphanedJob: Job

        TestScope(dispatcher).runTest {
            // Simulates the system under test launching on the bare dispatcher it was handed,
            // not on a scope the test can join.
            orphanedJob = GlobalScope.launch(dispatcher) {
                try {
                    resource.await()
                } finally {
                    cleanedUp = true
                }
            }
        }

        // Correct behavior would have runTest either wait for this job or forcibly cancel it.
        // Today it does neither: the job is left exactly as it was mid-test.
        assertTrue(orphanedJob.isActive)
        assertFalse(orphanedJob.isCancelled)
        assertFalse(orphanedJob.isCompleted)
        assertFalse(cleanedUp)

        // The asynchronous operation the task was waiting on now finishes, exactly as it does in
        // the issue's reproducer once the background thread finishes its work. Since nothing is
        // left to service `dispatcher`'s queue, this alone does not let the job proceed.
        resource.complete(Unit)
        assertFalse(orphanedJob.isCompleted)
        assertFalse(cleanedUp)

        // The job was never actually deadlocked -- only abandoned. Manually driving the very same
        // scheduler runTest used lets it run to completion, confirming runTest is what dropped it.
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(orphanedJob.isCompleted)
        assertTrue(cleanedUp)
    }
}
