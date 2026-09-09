# coroutines-negative-tests

Regression-lock ("negative") tests for kotlinx.coroutines GitHub issues that:

1. have an existing reproducer project under `~/IdeaProjects/coroutines_gh_*`,
2. are still **open** on GitHub, and
3. are labeled **bug**.

Each test asserts the *exact current (buggy) behavior* of `kotlinx-coroutines-core:1.11.0` (the
latest released version at the time these were written). The tests pass today and are expected to
start failing the moment the underlying bug is fixed or its behavior changes in any way -- that's
the point: they exist to notice a behavior change, not to demand a fix.

Run everything with `./gradlew jvmTest jsNodeTest wasmJsNodeTest`.

**JVM test isolation:** `jvmTest` is configured with `forkEvery = 1` / `maxParallelForks = 1` (see
`build.gradle.kts`). Several tests here deliberately mutate global JVM state
(`Thread.setDefaultUncaughtExceptionHandler`) or leave permanently-running daemon threads behind
(some busy-spinning, by design, to lock in a genuine hang) -- without forking a fresh JVM per test
class, those leak into later tests in the same run. Discovered when `Gh2504Test` passed reliably in
isolation but failed every time as part of the full suite (see that test's own doc comment for the
full story) -- always verify a new test passes both standalone *and* as part of the full
`jvmTest` run before considering it done.

## Covered issues

| Issue | Source project | Test |
|---|---|---|
| [#2817](https://github.com/Kotlin/kotlinx.coroutines/issues/2817) -- two distinct fusion bugs: `SharedFlow`+`buffer(0)`+`produceIn` (65 emits bypass rendezvous) and `channelFlow`+`buffer(0)`+`shareIn` (66 sends bypass it, buffer only honored upstream) | `coroutines_gh_2817` | `Gh2817Test` (jvmTest, 2 tests) |
| [#2818](https://github.com/Kotlin/kotlinx.coroutines/issues/2818) -- same fusion family as #2817, but with the `.onEach {}` workaround applied: a true rendezvous channel still lets exactly 1 emit bypass backpressure | `coroutines_gh_2818` | `Gh2818Test` (jvmTest) |
| [#3716](https://github.com/Kotlin/kotlinx.coroutines/issues/3716) -- `TimeoutCancellationException` thrown inside `flatMapLatest`'s inner flow is silently swallowed instead of reaching `.catch`/`onCompletion` | `coroutines_gh_3716` | `Gh3716Test` (jvmTest) |
| [#3976](https://github.com/Kotlin/kotlinx.coroutines/issues/3976) -- K/JS: an uncaught failure in an undispatched continuation leaks into an unrelated resumer coroutine instead of its own completion | `coroutines_gh_3976` | `Gh3976Test` (jsTest) |
| [#4159](https://github.com/Kotlin/kotlinx.coroutines/issues/4159) -- `.catch` placed after `.flowOn(...)` never sees an upstream exception that arrives after `first()` already got its element | `coroutines_gh_4159` | `Gh4159Test` (jvmTest) |
| [#4305](https://github.com/Kotlin/kotlinx.coroutines/issues/4305) -- `limitedParallelism` discards the caller-supplied `CoroutineContext` on `dispatch()`, passing the `LimitedDispatcher` itself instead | `coroutines_gh_4305` | `Gh4305Test` (jvmTest) |
| [#4382](https://github.com/Kotlin/kotlinx.coroutines/issues/4382) -- `cancel()` hangs forever when a child is `launch`ed onto a dispatcher whose `dispatch()` never runs the submitted block | `coroutines_gh_4382` | `Gh4382Test` (jvmTest) |
| [#4383](https://github.com/Kotlin/kotlinx.coroutines/issues/4383) -- `CoroutineStart.UNDISPATCHED` + `collectLatest` doesn't actually subscribe synchronously (`collectLatest`'s internal producer is started `ATOMIC`, not `UNDISPATCHED`), so a value emitted right after can be missed | `coroutines_gh_4383` | `Gh4383Test` (jvmTest) |
| [#4507](https://github.com/Kotlin/kotlinx.coroutines/issues/4507) -- `Flow.zip` is asymmetric: a flow that rewraps `CancellationException` in `.catch` breaks `zip` only as the *first* argument | `coroutines_gh_4507` | `Gh4507Test` (jvmTest) |
| [#4580](https://github.com/Kotlin/kotlinx.coroutines/issues/4580) -- a task `launch`ed on a bare `StandardTestDispatcher` (not a scope) is silently orphaned when `runTest` returns; its cleanup never runs | `coroutines_gh_4580` | `Gh4580Test` (jvmTest) |
| [#4590](https://github.com/Kotlin/kotlinx.coroutines/issues/4590) -- `flowOn`'s `ThreadLocal.asContextElement` leaks its value into the downstream collector block (and even past the whole `runBlocking` call) instead of being restored | `coroutines_gh_4590` | `Gh4590Test` (jvmTest) |
| [#4602](https://github.com/Kotlin/kotlinx.coroutines/issues/4602) -- missing Main dispatcher hangs instead of throwing when `start = ATOMIC` | `coroutines_gh_4602` | `Gh4602Test` (jvmTest) |
| [#4678](https://github.com/Kotlin/kotlinx.coroutines/issues/4678) -- `Promise.await()` loses the `JsException` type on `wasmJs` | `coroutines_gh_4678` | `Gh4678Test` (wasmJsTest) |
| [#4685](https://github.com/Kotlin/kotlinx.coroutines/issues/4685) -- `runTest` cancels but doesn't join `backgroundScope`, so a still-running leaked job's later exception surfaces as `UncaughtExceptionsBeforeTest` on the *next* `runTest` call | `coroutines_gh_4685` | `Gh4685Test` (jvmTest) |
| [#4698](https://github.com/Kotlin/kotlinx.coroutines/issues/4698) -- `CoroutineStart.ATOMIC` doesn't fail fast when the target dispatcher's `dispatch()` throws (unlike every other start mode) | `coroutines_gh_4698` | `Gh4698Test` (jvmTest) |
| [#1578](https://github.com/Kotlin/kotlinx.coroutines/issues/1578) -- `runBlocking` hangs forever if a manually-created `SupervisorJob(coroutineContext[Job])` child is never completed, even though nothing is actually in flight | `coroutines_gh_1578` | `Gh1578Test` (jvmTest) |
| [#1679](https://github.com/Kotlin/kotlinx.coroutines/issues/1679) -- a nested `runBlocking` that depends on progress from its *outer* `runBlocking`'s event loop deadlocks (busy `yield()` spin) by definition | `coroutines_gh_1679` | `Gh1679Test` (jvmTest) |
| [#2089](https://github.com/Kotlin/kotlinx.coroutines/issues/2089) -- `CancellableContinuation.invokeOnCancellation`'s `cause` is `null` or a real `CancellationException` depending purely on registration order relative to `cancel()`, breaking `CompletionHandler`'s documented contract | `coroutines_gh_2089` | `Gh2089Test` (jvmTest) |
| [#2504](https://github.com/Kotlin/kotlinx.coroutines/issues/2504) -- cancelling a very deep (1,000,000-level) coroutine hierarchy overflows the stack of `kotlinx.coroutines.DefaultExecutor`'s background thread, surfacing as a `NoClassDefFoundError` that poisons a JVM-internal class | `coroutines_gh_2504` | `Gh2504Test` (jvmTest) |
