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

**Correction:** #3976 was originally listed below as "no longer reproduces." That was
wrong -- a testing mistake, not a real finding. See
`~/IdeaProjects/coroutines_gh_3976/findings.md` for the full writeup of what went wrong, how the
bug actually reproduces, why it reproduces (traced through the compiled JS output), and how it was
turned into the `Gh3976Test` above without needing to literally be a program's top-level `main()`.

**batch:** #2818, #3716, #4159, #4305, #4382, #4383, #4507, #4580, #4590, #4698 were found
by scanning all 42 currently-open `bug`-labeled issues in `Kotlin/kotlinx.coroutines` for ones with
no existing local reproducer, then researching, reproducing, and testing each in parallel (10
candidates attempted, 10/10 reproduced -- no misses in this batch). Every one was independently
re-verified (fresh `./gradlew test` run, output inspected, most run at least twice for determinism)
before being added here -- none were taken on the reporting agent's word alone. Of these 10, #4698
has since been incorporated into the real `kotlinx.coroutines` checkout under the `pinnedBugs`
convention (see below); the other 9 have not yet.

### Remaining open `bug`-labeled issues not attempted yet (22)

Out of the 42 open+`bug` issues, 20 are accounted for above (19 covered + #3874, explained below).
The other 22 have not been attempted. Split into two groups:

**Plausible candidates for a follow-up pass** (not attempted only for lack of time so far, no
known blocker). Being worked through one at a time, sequentially, per-issue verification and
report before moving to the next -- see `~/.claude/projects/-Users-vartiushkin/memory/` or ask for
current status rather than assuming this list is still accurate:

- [#3504](https://github.com/Kotlin/kotlinx.coroutines/issues/3504) -- lexical coroutine builders may discard resources on cancellation
- [#3679](https://github.com/Kotlin/kotlinx.coroutines/issues/3679) -- `collectLatest` job is not cancelled when blocked
- [#3875](https://github.com/Kotlin/kotlinx.coroutines/issues/3875) -- timeout not detected on `newSingleThreadContext()` when `withTimeout` wraps a blocking job
- [#4121](https://github.com/Kotlin/kotlinx.coroutines/issues/4121) -- `ThreadLocal.asContextElement` may not be cleaned up with `Dispatchers.Main.immediate` (needs a Main dispatcher set up, more setup than the others)
- [#4209](https://github.com/Kotlin/kotlinx.coroutines/issues/4209) -- dispatcher failures may leave coroutines uncompleted
- [#4215](https://github.com/Kotlin/kotlinx.coroutines/issues/4215) -- `runBlocking` executes other tasks instead of its own
- [#4518](https://github.com/Kotlin/kotlinx.coroutines/issues/4518) -- an exception in `ThreadContextElement.updateThreadContext` is impossible to handle properly
- [#4523](https://github.com/Kotlin/kotlinx.coroutines/issues/4523) -- potential bug involving `Undispatched` and `withContext`
- [#4681](https://github.com/Kotlin/kotlinx.coroutines/issues/4681) -- `java.lang.AssertionError` when trying to emit from a cancelled coroutine (already labeled `reproduced` by a maintainer)

**Skipped with a specific reason** (not just "ran out of time" -- each has a concrete blocker for a
single deterministic test in this repo):

- [#490](https://github.com/Kotlin/kotlinx.coroutines/issues/490) -- Samsung Android 5.0.x hardware-specific `AtomicReferenceFieldUpdater` bug; also labeled `wontfix`
- [#1061](https://github.com/Kotlin/kotlinx.coroutines/issues/1061) -- depends on GC timing (weak references), inherently non-deterministic
- [#2930](https://github.com/Kotlin/kotlinx.coroutines/issues/2930) -- also GC-timing dependent
- [#3480](https://github.com/Kotlin/kotlinx.coroutines/issues/3480) -- "unstable context preservation invariant in Flow": vague/broad, no concrete repro to anchor a test on
- [#3493](https://github.com/Kotlin/kotlinx.coroutines/issues/3493) -- design/feature discussion ("integrate TestDispatcher with the unconfined event loop"), not a discrete bug
- [#3626](https://github.com/Kotlin/kotlinx.coroutines/issues/3626) -- design discussion ("rethink Mutex's owner contract"), not a discrete bug
- [#3660](https://github.com/Kotlin/kotlinx.coroutines/issues/3660) -- "investigate potential starvation scenario": open-ended investigation, no concrete repro
- [#3803](https://github.com/Kotlin/kotlinx.coroutines/issues/3803) -- documentation-only (`Flow.cancellable()` docs describe the wrong guarantee)
- [#4128](https://github.com/Kotlin/kotlinx.coroutines/issues/4128) -- BlockHound false positive; needs the BlockHound agent/dependency wired in, more infra than the others
- [#4149](https://github.com/Kotlin/kotlinx.coroutines/issues/4149) -- non-linearizable behavior; inherently a Lincheck/stress-test-shaped bug, not a single deterministic assertion
- [#4243](https://github.com/Kotlin/kotlinx.coroutines/issues/4243) -- documentation-only (a linked sample app doesn't exist)
- [#4262](https://github.com/Kotlin/kotlinx.coroutines/issues/4262) -- "several issues with the DefaultExecutor": an umbrella/tracking issue bundling multiple problems, not one discrete bug
- [#4491](https://github.com/Kotlin/kotlinx.coroutines/issues/4491) -- "port a bugfix for the coroutine scheduler": a porting/tracking issue, not a discrete bug with its own repro

Counts: 9 + 13 = 22, plus the 19 covered above and #3874 = 42, matching the full open+`bug` count
as of 2026-09-01.

## Issues considered but not covered

Still open and labeled `bug`, but there isn't a deterministic, self-contained way to reproduce it
in this repo:

- **[#3874](https://github.com/Kotlin/kotlinx.coroutines/issues/3874)** (`WindowDispatcher` collision
  between two independently-bundled JS libraries) -- the crash depends on two independent production
  JS bundles minifying `WindowDispatcher`'s internal member names *differently*, which in turn depends
  on whatever bundler/minifier settings each consuming app happens to use. Rebuilding the existing
  `multiple-js-libs-with-coroutines` demo's real production bundles (Kotlin 2.3.20, coroutines 1.11.0)
  and loading both under a faked Node `window` did not reproduce a collision -- both libraries ran
  fine. Since the divergence is driven by external, arbitrary bundler configuration rather than
  anything this repo controls, there isn't a deterministic, self-contained way to force it here.
  There is a linked, still-unresolved compiler-team issue tracking the root cause:
  [KT-79907](https://youtrack.jetbrains.com/issue/KT-79907) (state: Backlog as of 2025-11-19).

## Incorporating these into the actual kotlinx.coroutines library sources

**Correction:** the original 4 incorporated tests (#2817, #4602, #4678, #4685) --
added as uncommitted edits directly on top of whatever branch was checked out at the time -- were
lost when that working tree was reset while switching branches. They were never committed or
pushed, so nothing to recover; they'd need to be redone from scratch under the convention below if
still wanted. Do not assume the table below still describes files present on disk without checking
`git status` in `~/IdeaProjects/kotlinx.coroutines` first.

**Current convention (established 2026-09-04):** pinned-bug tests are incorporated on the
`vsart-negative-tests` branch, one per file, under a dedicated `pinnedBugs` source folder per
platform source set (e.g. `kotlinx-coroutines-core/jvm/test/pinnedBugs/`), so they're
distinguishable from real tests by both path and name:

- Package: `kotlinx.coroutines.pinnedBugs`
- Class name: `Gh<issue>PinnedBugTest` (e.g. `Gh1578PinnedBugTest`)
- Test function names stay descriptive (`testXxx`), no issue number in the function name
- KDoc opens with `PINNED BUG. Related issue: <url>`, kept short
- A thread that's expected to hang forever is confirmed stuck via deterministic `Thread.State`
  polling (spin + `Thread.yield()` until `WAITING`/`TIMED_WAITING`, bounded by a generous fail-fast
  deadline as a safety net) instead of a fixed `join(timeoutMs)` -- avoids paying a fixed wall-clock
  cost on every run and matches the polling idiom already used in
  `BlockingCoroutineDispatcherMixedStealingStressTest.kt`. The corresponding fix was applied back
  here to `Gh1578Test.kt` too, so both copies use the same deterministic wait.
- Teardown of that thread depends on whether the hang is cancellation-responsive. For #1578,
  interrupting the thread makes `runBlocking`'s wait loop cancel itself (which cancels the bare
  `SupervisorJob` child too, since cancellation -- unlike failure -- always propagates downward
  regardless of `SupervisorJob`), so `thread.interrupt(); thread.join()` deterministically
  terminates it with no leak and no `ignoreLostThreads` needed -- confirmed empirically, not just
  assumed (see `kotlinx-coroutines-core/jvm/test/RunBlockingJvmTest.kt`'s
  `startInSeparateThreadAndInterrupt` for the library's own precedent of this exact pattern). For
  #4698, this does **not** work: the `ATOMIC`-started coroutine's `dispatch()` throws before the
  coroutine is fully linked in as a child, so cancelling the parent `runBlocking` job on interrupt
  never reaches it -- verified by actually letting it hang past a 60s bound before falling back to
  `ignoreLostThreads`. Each pinned-bug test that hangs a thread needs this checked per-issue, not
  assumed from the #1578 precedent.
- Where the hanging thread's body doesn't need a genuine bare `runBlocking` to reproduce the issue
  (i.e. the call itself never leaks past the repro), the pinned copy uses `TestBase`'s own `runTest`
  (a thin `runBlocking` wrapper with the same exception-handling machinery used throughout this
  module's tests, e.g. `DelayTest.kt`, `ScopedBuildersCancelledStartTest.kt`) instead of a bare
  `runBlocking`, to match repo convention -- done for #4698's `ATOMIC` hang test. This has no
  equivalent in this standalone project, since these classes don't extend `TestBase`; `Gh4698Test.kt`
  here keeps plain `runBlocking`, so the two copies intentionally diverge on this one point.

**No `build.gradle.kts` changes are needed** -- the root build already wires `test-utils` (which
provides `TestBase`, `kotlin.test`, etc.) into every module's `commonTest`/`jvmTest` automatically.

| Issue | Location in `kotlinx.coroutines` | Notes |
|---|---|---|
| #1578 | `kotlinx-coroutines-core/jvm/test/pinnedBugs/Gh1578PinnedBugTest.kt` | First test incorporated under the `pinnedBugs` convention above; verified passing via `./gradlew :kotlinx-coroutines-core:jvmTest --tests "kotlinx.coroutines.pinnedBugs.Gh1578PinnedBugTest"`. Hang is interrupt-responsive; teardown uses `thread.interrupt(); thread.join()`, no `ignoreLostThreads`. |
| #4698 | `kotlinx-coroutines-core/jvm/test/pinnedBugs/Gh4698PinnedBugTest.kt` | Highest-numbered issue in the covered table above, ported next per that ordering; verified passing via `./gradlew :kotlinx-coroutines-core:jvmTest --tests "kotlinx.coroutines.pinnedBugs.Gh4698PinnedBugTest"` (also re-verified alongside `Gh1578PinnedBugTest` in the same JVM run, since this repo has no `forkEvery`/JVM-per-class isolation -- no cross-test interference from the leaked thread). Hang is *not* interrupt-responsive; teardown uses `ignoreLostThreads`. |
| #4685 | `kotlinx-coroutines-test/jvm/test/pinnedBugs/Gh4685PinnedBugTest.kt` | Next-highest-numbered unported issue after #4698. Not `TestBase`-based (unlike the two above) -- it exercises `kotlinx.coroutines.test.runTest` itself, so extending `TestBase` would shadow that with `TestBase`'s own member `runTest`; kept as a plain class, matching this module's own existing test style (e.g. `UncaughtExceptionsTest.kt`). No hang, no thread teardown concern -- the background job is a real `Dispatchers.Default` daemon-pool thread. Pins both the primary symptom (the background job outliving `runTest`, deterministic) and the secondary `UncaughtExceptionsBeforeTest` crash (made deterministic here, unlike the flaky repro in the issue itself, by having the leaked job throw and deterministically polling `bgJob.isCompleted` before the next `runTest` call, then asserting the thrown exception's suppressed cause). The thrown type itself (`kotlinx.coroutines.test.TestScopeImpl.UncaughtExceptionsBeforeTest`) is `internal`, so this standalone project's mirror of the test (below) catches its public `IllegalStateException` supertype and matches on the message text instead. Verified passing via `./gradlew :kotlinx-coroutines-test:jvmTest --tests "kotlinx.coroutines.pinnedBugs.Gh4685PinnedBugTest"`, and the full `:kotlinx-coroutines-test:jvmTest` suite (85 tests) also passes with it in place. **Later hardened against Linux-CI-only flakiness**: this test failed twice in a row on Linux CI (`AssertionError: unreached` inside the *second* `runTest`, meaning the poll loop's own exit condition -- `bgJob.isCompleted` -- was already satisfied, so it wasn't a timeout race) while never reproducing locally on macOS. Traced the actual exception-delivery path (`JobSupport.finalizeFinishingState`'s `cancelParent`/`handleJobException` call, which runs strictly *before* `completeStateFinalization`/`notifyCompletion` publishes the job's terminal state, synchronously invoking `ReportingSupervisorJob.childCancelled` -> `TestScopeImpl.reportException` -> `ExceptionCollector`) and confirmed that swapping the `Thread.sleep` poll for `runBlocking { bgJob.join() }` would *not* close this, since both `isCompleted` and `join()`'s resumption are gated behind that same `completeStateFinalization` call -- so a race there would affect both equally. The likelier culprit: `bgJob` originally ran on `Dispatchers.Default`, whose parallelism is core-count-dependent and much lower/more contended on typical CI runners (2-4 vCPUs) than on a many-core dev machine, widening whatever asynchronous gap exists between the `withContext(NonCancellable)` child's completion and the outer job's own resumption. Fixed by giving `bgJob` its own dedicated `newSingleThreadContext("Gh4685-bg")` instead of `Dispatchers.Default`, removing platform-dependent thread-pool parallelism as a variable entirely (closed via `.use { }` around the whole test body). Re-verified passing (5 consecutive local runs, plus the full `:kotlinx-coroutines-test:jvmTest` suite, 14 test classes, 0 failures); mirrored the same change into `Gh4685Test.kt` below (verified passing individually and in the full standalone `jvmTest` suite, 17 test classes, 0 failures). Since this can't be reproduced locally, this fix narrows a plausible cause rather than being empirically confirmed against the actual CI failure. |
| #4580 | `kotlinx-coroutines-test/jvm/test/pinnedBugs/Gh4580PinnedBugTest.kt` | Next-highest unported issue after skipping #4602/#4678/#4590 (see below). Not `TestBase`-based, same reasoning as #4685 (exercises `kotlinx.coroutines.test.runTest`/`TestScope` directly). Fully deterministic, no real threads/sleeps -- `StandardTestDispatcher` + `GlobalScope.launch` on the bare dispatcher, then `dispatcher.scheduler.advanceUntilIdle()` proves the orphaned job was merely abandoned by `runTest`, not actually deadlocked. Verified passing via `./gradlew :kotlinx-coroutines-test:jvmTest --tests "kotlinx.coroutines.pinnedBugs.Gh4580PinnedBugTest"`, and the full `:kotlinx-coroutines-test:jvmTest` suite (86 tests) also passes with it in place. |
| #4383 | `kotlinx-coroutines-core/jvm/test/pinnedBugs/Gh4383PinnedBugTest.kt` | Next-highest unported issue after skipping #4507 (see below). Verified on GitHub the maintainer (`dkhalanskyjb`) confirmed this as a genuine, still-intended-to-be-fixed gap ("yes, this is expected [given the current impl], but I do believe we should change this behavior"), unlike #4507/#4602/#4678/#4590 -- so it's a legitimate pin, not a skip. `TestBase`-based, using `runTest` in place of the standalone copy's bare `runBlocking` (test body is otherwise byte-for-byte identical -- deterministic, single-threaded, no real threads/sleeps, so nothing else needed changing). Verified passing via `./gradlew :kotlinx-coroutines-core:jvmTest --tests "kotlinx.coroutines.pinnedBugs.Gh4383PinnedBugTest"`, and the full `:kotlinx-coroutines-core:jvmTest` suite (283 test classes, 0 failures) also passes with it in place. A second `@Test` (`testUndispatchedCollectLatestDoesNotSynchronouslySubscribeToUpstream`) was added to the same pinned file -- sourced from the still-open, still-red PR [#4488](https://github.com/Kotlin/kotlinx.coroutines/pull/4488), which generalizes the same #4383 root cause (`collectLatest`'s internal producer not starting synchronously under an `UNDISPATCHED` launch) from a `MutableSharedFlow`/`tryEmit` repro to a plain cold `flow {}`. PR #4488's own version of the test is deliberately written to *fail* on current `develop` (confirmed empirically -- `IllegalStateException: Expecting action index 4 but it is actually 3`; the PR author states "The breaking test is separated so that we can see in the CI that it is indeed broken"). Since pinned-bug tests must pass against current behavior, the `expect(3)`/`expect(4)` calls were swapped to match the real, currently-observed execution order before porting; re-verified passing after the swap. Mirrored into this standalone project's `Gh4383Test.kt` too, as `undispatchedCollectLatestDoesNotSynchronouslySubscribeToUpstream` -- rewritten to use a plain event-order list + `assertEquals` instead of the repo-internal `TestBase`/`expect`/`finish` helpers, which aren't available here since this project only depends on the published `kotlinx-coroutines-core`/`kotlinx-coroutines-test` artifacts. Verified passing individually and as part of the full standalone `jvmTest` suite (17 test classes, 0 failures). Separately simplified the first test's wait for the internal producer: it originally used `withTimeoutOrNull(300ms) { while (!valueReceived) { delay(10ms) } }` to give the producer a chance to run before concluding the emitted value was permanently missed. Verified empirically (via `flow.subscriptionCount` and iteration counters in scratch tests) that since everything runs on the same single-threaded `runBlocking`/`runTest` dispatcher, a single `yield()` is already enough for the `ATOMIC`-started producer to run and subscribe -- confirmed directly via `flow.subscriptionCount.value > 0` right after the `yield()`, rather than inferred from a timeout expiring. Replacing `delay()` with `yield()` inside the old loop was tried first and also "worked", but it degenerates into a ~450K-iteration CPU-burning busy-spin for the full timeout window since `valueReceived` never becomes true on its own; removing the loop and timeout entirely is the actually-correct simplification. Re-verified passing (now in ~0.03ms vs. the old ~300ms) individually and in the full suites on both sides. |
| #4382 | `kotlinx-coroutines-core/jvm/test/pinnedBugs/Gh4382PinnedBugTest.kt` | Next-highest unported issue after #4383. Uses the maintainer's own simplified reproducer from the issue (`coroutineScope { launch(perpetuallyBusyDispatcher) { error(...) }; cancel() }`, where the dispatcher's `dispatch()` silently never runs the submitted `Runnable`). Different root-cause shape than #4698's `ignoreLostThreads` case even though the teardown mechanism is the same: #4698's dispatcher *throws* from `dispatch()`, while here `dispatch()` just does nothing, so the child coroutine is never even started -- there's nothing running that could ever observe or act on the `cancel()`. First attempt at teardown tried `thread.interrupt(); thread.join()` (the #1578 pattern) and confirmed empirically that it does *not* work here: interrupting unparks `runBlocking`'s wait loop exactly once, but `Thread.interrupted()` is consumed by that single check and the unstarted child never completes, so the loop parks again -- `thread.join()` then hangs the test JVM itself (had to `TaskStop` the run and `./gradlew --stop` to recover). Switched to `ignoreLostThreads`, matching #4698. Verified passing via `./gradlew :kotlinx-coroutines-core:jvmTest --tests "kotlinx.coroutines.pinnedBugs.Gh4382PinnedBugTest"`, and the full `:kotlinx-coroutines-core:jvmTest` suite (284 test classes, 0 failures) also passes with it in place. The standalone `Gh4382Test.kt` below doesn't extend `TestBase` and has no lost-thread checker to satisfy, so its existing fixed `thread.join(3_000)` teardown needs no change. |
| #2818 | `kotlinx-coroutines-core/jvm/test/pinnedBugs/Gh2818PinnedBugTest.kt` | Next-highest unported issue after #4382. The issue's only comment is a maintainer's own self-reported repro confirmation on Kotlin 2.3.21 / library 1.11.0 (same code, same off-by-one), not a separate maintainer triage -- still treated as a legitimate pin since it's a first-party confirmation that the bug reproduces on a recent release. Re-verified empirically on this repo's current `develop` source via a scratch test before porting: 1st `emit` completes (bypasses the rendezvous buffer), 2nd `emit` times out. No hang, no thread teardown concern -- the second `emit`'s suspension is bounded from inside the coroutine itself. `TestBase`-based, using `runTest` (JVM's `TestBase.runTest` is a thin `runBlocking` wrapper with no virtual time, confirmed by reading `test-utils/jvm/src/TestBase.kt`). Initial port ported the original `withTimeout`/`withTimeoutOrNull` + `repeat(EMITS_THAT_CURRENTLY_BYPASS_RENDEZVOUS)` structure byte-for-byte -- flagged as needlessly obscure for a loop that only ever runs once, since the whole point is that there's exactly one bypassing emit. Rewritten as a linear `expect(i)`-ordered sequence instead: `expect`s bracket the first `emit` (which just returns, proving the bypass, no timeout needed), then a second `emit` is `launch`ed separately and confirmed to still be suspended (`job.isActive`) after a `yield()`, rather than racing either emit against a timeout. Re-verified passing via `./gradlew :kotlinx-coroutines-core:jvmTest --tests "kotlinx.coroutines.pinnedBugs.Gh2818PinnedBugTest"`, and the full `:kotlinx-coroutines-core:jvmTest` suite (285 test classes, 0 failures) also passes with it in place. Mirrored the same restructuring into `Gh2818Test.kt` below, using a plain event-order list + `assertEquals` in place of `expect`/`finish` (not available outside the repo, same reasoning as `Gh4383Test.kt`'s mirror) -- verified passing individually and in the full standalone `jvmTest` suite (17 test classes, 0 failures). |
| #2817 | `kotlinx-coroutines-core/jvm/test/pinnedBugs/Gh2817PinnedBugTest.kt` | Next-highest unported issue after #2818. Also assigned to maintainer `dkhalanskyjb`, and the same first-party (`vsartiushkin`) comment that confirmed #2818's repro on Kotlin 2.3.21/library 1.11.0 separately confirms **two distinct** fusion bugs here, so this file has two `@Test`s instead of one: (1) `SharedFlow`+`buffer(0)`+`produceIn` with no intermediate operator -- same fusion family as #2818, but without the `.onEach {}` workaround, so a fixed number of emits (not just 1) bypass rendezvous; (2) `channelFlow`+`buffer(0)`+`shareIn` with no intermediate operator before the `shareIn` -- the `buffer(0)` is honored only on the upstream `channelFlow`'s own channel, but `shareIn`'s internal sharing mechanism still lets the producer run ahead by its own default capacity, so the buffer is "only applied upstream." Initial port carried forward the original standalone project's `withTimeout`/`withTimeoutOrNull`-based single test as-is, verified passing (65 emits bypass, matching current source) both individually and against the full suite (286 test classes) -- then explicitly rejected: real-time racing achieves nothing here since everything runs on one single-threaded event loop, and the single test conflated two separate bug mechanisms into one. Redone as two tests, both `launch`+`yield()`-based with zero timeouts. Getting the right yield count needed real empirical work: an initial scratch attempt using a handful of `yield()` calls (5, then 10-20) showed both counters still visibly climbing, not plateaued -- initially looked like unbounded growth with no fixed magic number at all (unlike #2818's clean "exactly 1"). Logging every intermediate count to a file (to get around Gradle buffering all test stdout until the whole task finishes, which made a `while(true)`-loop scratch test that was still running look like a silent hang) showed both actually do plateau, deterministically, at a fixed count: **65** emits for the `SharedFlow`+`produceIn` case (reached by yield #131, stable through #150 -- matches the old timeout-based test's number exactly, as expected since it's the same underlying mechanism) and **66** sends for the `channelFlow`+`shareIn` case (reached by yield #70, stable through #200). Both tests assert the plateaued count via `assertEquals` after a fixed, generously-margined `repeat(N) { yield() }` (150 and 100 respectively) rather than any wall-clock timeout. The `shareIn` test isolates its measurement from the `SharedFlow`+`produceIn` bug being pinned in the *other* test: its own final downstream hop (`shared.onEach {}.buffer(Channel.RENDEZVOUS).produceIn(this)`) uses the `.onEach {}` workaround so that hop's own rendezvous is trustworthy and doesn't confound the upstream `channelFlow`+`shareIn` measurement. Re-verified passing via `./gradlew :kotlinx-coroutines-core:jvmTest --tests "kotlinx.coroutines.pinnedBugs.Gh2817PinnedBugTest"`, and the full `:kotlinx-coroutines-core:jvmTest` suite (286 test classes, 0 failures) also passes with it in place. Mirrored the same two-test, no-timeout restructuring into `Gh2817Test.kt` below (plain `runBlocking`, no `TestBase`/`expect`/`finish` needed since neither test relies on strict interleaving order, just magnitude) -- verified passing individually and in the full standalone `jvmTest` suite (17 test classes, 0 failures). **Further simplified afterward**, once it became clear the large margins were overkill: grepping the repo's own suite (`BufferTest.kt`, `BufferedChannelTest.kt`'s `checkBufferChannel`/`testTryOp`, `ShareInBufferTest.kt`) showed the established idiom is an inline loop up to the exact known capacity plus a single purposeful `yield()`, not a generously-multiplied `repeat(N){yield()}`. For the `SharedFlow`+`produceIn` test this applies directly and cleanly: an emit that hands off to an already-waiting collector doesn't suspend at all, so `repeat(65) { stream.emit(Unit) }` called inline (no wrapping `launch`) drains all 65 with **zero** yields (confirmed via scratch test), and a separate tiny `launch` probe for the 66th item only needs **1** `yield()` to prove it's genuinely stuck (`probe.isActive` stayed `true` through yield #1 and #2, no drift) -- replacing the old 150-yield margin entirely. The `channelFlow`+`shareIn` test can't use the same trick, since its producer lives inside `channelFlow{}`'s own builder block, which is inherently a separate coroutine no matter how it's called, so a real multi-hop settle (through `shareIn`'s internal collector) is unavoidable; but that settle point is still exactly deterministic on the single-threaded scheduler, so the margin was trimmed from a `*2`-multiplied 132 down to a small fixed 80 (comfortably above the confirmed-stable 70). Re-verified passing on both sides after the trim: repo (`:kotlinx-coroutines-core:jvmTest`, 286 test classes, 0 failures) and standalone (`jvmTest`, 17 test classes, 0 failures). **`shareIn` test's downstream sink simplified further still**: the original `shared.onEach {}.buffer(Channel.RENDEZVOUS).produceIn(this)` chain looked more complex than "just a subscriber to trigger `WhileSubscribed()`," which raised the question of whether a plain `launch { shared.collect {} }` would do. Verified empirically that it does *not* -- a `collect {}` with a trivial (non-suspending) body never gets stuck, so it keeps draining indefinitely and `sent` grows unboundedly (+1 every ~2 yields, confirmed to 500 yields with no plateau at all). The reason the original chain worked is that `downstream` is a RENDEZVOUS channel that's *never drained* (nothing ever calls `.receive()` on it) -- so it's a permanently-blocked sink after exactly one item, which is what saturates all upstream slack and reveals the true fixed bypass count; the `.onEach {}` was there only to dodge the bug-1-style fusion that would otherwise discard the `buffer(RENDEZVOUS)` applied directly after the `SharedFlow`. The same permanently-blocked-sink effect is achievable far more simply with `launch { shared.collect { awaitCancellation() } }`: it consumes exactly one item, then suspends forever inside the collector body, so it never loops back for a second one -- and since it's a direct `collect` on the `SharedFlow` rather than `buffer()` immediately followed by `produceIn()`, it was never exposed to the bug-1 fusion issue to begin with, so no workaround operator is needed at all. Verified this reproduces the identical plateau (66, stable by yield #69) via scratch test, then replaced the chain in both `Gh2817PinnedBugTest.kt` and `Gh2817Test.kt` below (dropping the now-unused `Channel` import from both). Re-verified passing on both sides: repo (`:kotlinx-coroutines-core:jvmTest`, 286 test classes, 0 failures) and standalone (`jvmTest`, 17 test classes, 0 failures). |

**#4580 vs. #4685 -- same symptom class, different root cause.** Both look like "async work
outlives `runTest`, cleanup never runs," but tracing the actual mechanism in
`kotlinx-coroutines-test/common/src/TestBuilders.kt`'s `runTest` (the `finally` block, roughly
lines 368-373) shows they're distinct bugs:
- **#4685**: the leaked job (`backgroundScope.launch { ... }`) *is* tracked as a child of the
  test's job hierarchy. In the `finally` block, `runTest` calls `backgroundScope.cancel()` but
  never follows it with a `.join()` -- only a single `testScheduler.advanceUntilIdleOr { false }`.
  A child that ignores cancellation (e.g. wrapped in `NonCancellable`) or runs on a real,
  non-scheduler-linked dispatcher keeps executing after `runTest` has already returned. Root
  cause: **cancel-without-join on a tracked child**.
- **#4580**: the leaked job (`GlobalScope.launch(dispatcher) { ... }`) is not a structural child of
  the test's scope at all -- `scope.children` never sees it, and `backgroundScope.cancel()` can't
  reach it either. It only stays alive while the test runs because it happens to be dispatched on
  `dispatcher`, which is scheduler-linked, so `runTest`'s internal `workRunner` loop pumps it. Once
  that loop stops and the single post-test `advanceUntilIdleOr { false }` drain finishes, nothing
  is left pumping that scheduler at all -- the job is never cancelled, never joined, never even
  known about. If it later becomes runnable (e.g. an external thread completes a
  `CompletableDeferred`, as in the issue's own reproducer), there's no driver left to resume it.
  Root cause: **no job-hierarchy tracking at all, plus the scheduler pump stopping** -- not a
  cancel/join gap.

**Skipped, not ported (still open + labeled "bug", but deliberately excluded):**
- **#4602** -- "Missing main dispatcher hangs if `start = CoroutineStart.ATOMIC`". Verified empirically
   that it still reproduces on current `kotlinx.coroutines` source (a scratch test showed
  the repro thread still hangs). It shares the exact same root cause as #4698 (`ATOMIC` swallows an
  exception thrown from a dispatcher's `dispatch()`) -- the real fix lives in maintainer
  [PR #4662](https://github.com/Kotlin/kotlinx.coroutines/pull/4662), which is not yet merged to
  `develop` and is what #4698 was filed to track. Since #4698's pinned test already locks in this
  exact mechanism, porting #4602 too would just be the same regression lock with a different trigger
  (missing `Dispatchers.Main` vs. a generic broken dispatcher) -- skipped as redundant.
- **#4678** -- "`Promise.await` is not propagating underlying JS error (only on WASM)". Verified
   that the buggy code path described in the issue no longer exists on current source:
  `kotlinx-coroutines-core/wasmJs/src/Promise.wasm.kt` now delegates to stdlib's
  `toThrowableOrNull()` instead of the old inline `Exception("Non-Kotlin exception $this...")`
  fallback that caused the bug. The GitHub issue itself is still open with no closing PR linked, but
  the fix has structurally landed -- skipped as already fixed.
- **#4590** -- "Missing `updateThreadContext` call when emit resumes" (`flowOn`'s
  `ThreadLocal.asContextElement` leaking past the collector and past `runBlocking` itself). Confirmed
  still open, no closing PR. Deliberately skipped for now (not evaluated further
  this session) -- revisit later.
- **#4507** -- "`Flow.zip` breaks if Flow1 impl catches & rethrows a `CancellationException`, but
  not if Flow2 does it". Read the full issue discussion: the maintainer (`qwwdfsad`)
  frames this as the known, already-documented-elsewhere antipattern of catching and rewrapping a
  `CancellationException` instead of rethrowing the exact instance -- cross-referencing #3658, #4073,
  #2860, and #4159 as the same class of "all bets are off once you wrap a CE" behavior. They even
  considered closing it "as designed"; the `docs` label (alongside `bug`) reflects that the real gap
  identified is that this behavior isn't clearly documented, not that `zip`'s logic is wrong. Skipped
  as a misunderstanding of CE-propagation semantics rather than a genuine regression to pin.

As of 2026-09-08 these exist only as untracked files on the `vsart-negative-tests` branch in the
`kotlinx.coroutines` working tree (nothing staged, committed, or pushed) -- check `git status` there
before assuming this description still holds.

**Environment gotcha:** the `kotlinx.coroutines` repo's Gradle 8.14.4 wrapper cannot run under the
ambient default JDK 26 on this machine (fails immediately at settings evaluation with a bare
`IllegalArgumentException: 26`). Export
`JAVA_HOME=/Users/vartiushkin/Library/Java/JavaVirtualMachines/jbr-17.0.14/Contents/Home` (or any
JDK <=~21) before running `./gradlew` there.
