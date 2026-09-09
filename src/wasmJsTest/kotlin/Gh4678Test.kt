package gh4678

import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlin.js.JsAny
import kotlin.js.JsException
import kotlin.js.Promise
import kotlin.js.js
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Regression lock for https://github.com/Kotlin/kotlinx.coroutines/issues/4678 (open, labeled "bug").
 *
 * On the `js` target, a rejected JS `Promise` awaited via `kotlinx.coroutines.await()` surfaces as a
 * `JsException` wrapping the original JS error. On `wasmJs` it should behave the same way, but since
 * upgrading to 1.11.0 it instead gets erased into a plain `kotlin.Exception` whose message is just the
 * `toString()` of the original error, losing the original error object entirely.
 */
class Gh4678Test {

    @Test
    fun wasmPromiseAwaitLosesTheOriginalJsExceptionType() = runTest {
        val thrown = try {
            createTypeErrorPromise().await()
            null
        } catch (e: Throwable) {
            e
        }

        checkNotNull(thrown)
        assertFalse(thrown is JsException)
        assertEquals(kotlin.Exception::class, thrown::class)
        assertEquals(
            "Non-Kotlin exception TypeError: JS Exception of type 'class TypeError'",
            thrown.message
        )
    }
}

private fun createTypeErrorPromise(): Promise<JsAny> = js(
    """
    new Promise(function(_, reject) {
        reject(new TypeError("JS Exception"));
    })
    """
)
