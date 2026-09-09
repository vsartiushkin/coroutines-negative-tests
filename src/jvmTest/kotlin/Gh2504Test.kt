package gh2504

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Regression lock for https://github.com/Kotlin/kotlinx.coroutines/issues/2504 (open, labeled
 * "bug"): "StackOverflowError when completing long coroutines hierarchy".
 *
 * This is the issue's own repro (a 100_000-deep hierarchy of `Dispatchers.Unconfined` launches),
 * with an explicit short `runTest(timeout = ...)` instead of the 60s default: the crash is
 * triggered by `runTest`'s own internal dispatch-timeout `TimeoutCoroutine` cancelling the whole
 * hierarchy, and that cancellation cascades recursively through `JobSupport.notifyCancelling` /
 * `makeCancelling` / `parentCancelled` across all 100_000 levels -- genuinely overflowing the
 * stack of the `kotlinx.coroutines.DefaultExecutor` background thread. Forcing that timeout to
 * fire almost immediately reproduces the exact same crash in ~1s instead of waiting out the real
 * 60s default.
 *
 * The crash surfaces asynchronously, on that background thread, as a `NoClassDefFoundError` that
 * poisons `kotlin.internal.PlatformImplementationsKt` for the rest of the JVM process (the
 * `StackOverflowError` happened while lazily initializing that class, needed by
 * `Throwable.addSuppressed` inside the coroutine exception-handling path) -- the original
 * `StackOverflowError` is only visible in the rendered "Caused by" text, not via
 * `Throwable.cause` (`ExceptionInInitializerError`'s legacy `exception` field isn't wired to
 * `getCause()` the way a modern cause chain would be), so this checks the rendered stack trace
 * text rather than walking `.cause`.
 */
class Gh2504Test {

    @Test
    fun cancellingA100000DeepHierarchyOverflowsTheStack() {
        val uncaught = mutableListOf<Throwable>()
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            synchronized(uncaught) { uncaught += e }
        }
        try {
            val thread = Thread {
                runTest(timeout = 200.milliseconds) {
                    fun CoroutineScope.nestedLaunch(i: Int) {
                        if (i == 0) return
                        launch(Dispatchers.Unconfined) {
                            try {
                                nestedLaunch(i - 1)
                            } catch (e: Throwable) {
                                // swallowed here, exactly like the issue's own repro
                            }
                        }
                    }
                    nestedLaunch(1_000_000)
                }
            }
            thread.isDaemon = true
            thread.start()
            thread.join(15_000)
            Thread.sleep(500) // let the DefaultExecutor background thread's async crash surface
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(oldHandler)
        }

        val fullText = uncaught.joinToString("\n") { it.stackTraceToString() }
        assertTrue(
            uncaught.any { it is NoClassDefFoundError },
            "expected an uncaught NoClassDefFoundError, got: $uncaught"
        )
        assertTrue(
            "StackOverflowError" in fullText,
            "expected a StackOverflowError somewhere in the crash, got:\n$fullText"
        )
    }
}
