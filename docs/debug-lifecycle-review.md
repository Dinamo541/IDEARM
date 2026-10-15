# Task D Review: Application Run/Debug Lifecycle Integration

## 1. Overview & Root Cause Analysis

### The Bug (Desktop QA Reproduction)
During Codex desktop QA testing with `hello-input` (and interactive DOS programs using INT 21h AH=0Ah keyboard input), the output terminal logged duplicate debug exit lines:
```
[DEBUG] exit code 0
[DEBUG] exit code 0
```

### Initial Root Causes
Investigation into the event pipeline identified a race and dual-emission defect between `DebugProject` and session backends:
1. **Unconditional Dual Emission**:
   - `DebugProject.java` passed the client `events` consumer (`Consumer<DebugEvent>`) directly to `envProvider.launchDebug(spec, events)`.
   - In `Emu8086DebugSession.checkTermination()`, upon INT 21h AH=4Ch termination, the emulator completes `exitFuture` and then emits `events.accept(new DebugEvent.Exited(exitInfo))`.
   - In `DebugProject.java`, a loop polled `while (!session.exit().isDone()) Thread.sleep(30)`. As soon as `exitFuture` completed, `DebugProject` exited the loop and unconditionally emitted `events.accept(new DebugEvent.Exited(info))`.
   - As a result, both the backend and `DebugProject` delivered identical `DebugEvent.Exited` events to the caller (`WorkbenchViewModel.reportDebugEvent`), causing duplicate `[DEBUG] exit code 0` entries in the output panel.
2. **Session Teardown & Stop Side Effects**:
   - In `Emu8086DebugSession.close()`, `stop()` was invoked. If the session had not previously been stopped, `stop()` emitted `DebugEvent.Stopped()`. Thus, a session could deliver `Exited(0)` followed by `Stopped()`, or `DebugProject` could deliver `Exited` after a user had stopped the session.
   - In `GdbProcessDebugSession`, GDB/MI output (`*stopped,reason="exited-normally"`) emitted `DebugEvent.Exited` followed immediately by `DebugEvent.Stopped()`.
3. **Silent External Backends**:
   - In contrast, external backends like `DosBoxDebugSession` (wrapping Turbo Debugger or CodeView) do not take an event sink and emit no events at all. They rely on `DebugProject` to synthesize the terminal event upon process termination. Simply removing the emission from `DebugProject` would leave silent backends without any terminal event.
4. **Arbitrary Sleep Timing**:
   - The loop in `DebugProject` relied on `Thread.sleep(30)`, which introduced non-deterministic latency and prevented future-driven synchronization.

### Integration Verification Feedback & Exceptional Flow Defect
During Codex integration verification, two additional critical findings were reported:
1. **Fixture Compilation Error in `DebugProjectTest.java`**:
   - Line 715 constructed `ToolRunResult` with `List<Diagnostic>` where the domain contract `ToolRunResult(BuildStatus, List<ToolResult>, Path, String)` requires `List<ToolResult>`.
2. **Exceptional Exit Future Swallowing**:
   - In previous code, exceptional exit completions (e.g. backend crash or unhandled runtime failure) were queried via `session.exit().getNow(ExitInfo.stopped(...))`.
   - When the future completed exceptionally, `getNow()` threw `CompletionException`.
   - The outer `catch (Exception ex)` caught this and assigned `info = ExitInfo.stopped(...)`, setting `wasStopped = true`.
   - This erroneously reported backend failure as a user cancellation (`SessionState.STOPPED` with `DebugEvent.Stopped`), discarding the actual exception cause and dropping actionable diagnostics.
3. **Event/Result Divergence**:
   - For edge cases including launch failure, cancellation after completion, and close throwing, the terminal event emitted to downstream consumers could diverge from the final `DebugResult` state.

---

## 2. Solution: Single Terminal-Event & Agreement Contract

`DebugProject` implements `DebugLifecycleCoordinator` and explicit state evaluation to enforce a strict lifecycle contract across all debug backends:

### Event Ownership Hierarchy
- **`DebugEvent.Started`**: Owned by `DebugProject`. Emitted before launching the session.
- **`DebugEvent.SessionAttached`**: Owned by `DebugProject`. Emitted immediately upon successful session launch.
- **Intermediate Events (`Output`, `Paused`, `Resumed`, `WaitingForInput`)**: Owned by the backend (`DebugSession`). Forwarded downstream as long as the session has not reached a terminal state. Late intermediate events arriving after session termination are dropped.
- **Actionable Diagnostics (`DebugEvent.Problem`)**: Always delivered downstream, even after a terminal event has been reached (e.g. for teardown failures in `session.close()`).
- **Terminal Events (`Exited`, `Stopped`)**:
  - Exactly **ONE** terminal event is ever emitted to the downstream consumer.
  - If an emitting backend delivers `Exited` or `Stopped`, that event is forwarded and marks the terminal transition.
  - If a backend attempts to emit a second terminal event (such as `Exited` followed by `Stopped` in GDB, or duplicate `Exited`), the subsequent terminal event is suppressed.
  - If the backend is silent (e.g. `DosBoxDebugSession`) and completes without emitting a terminal event, `coordinator.ensureTerminal(wasStopped, exitInfo)` synthesizes and emits the appropriate terminal event (`Stopped` if cancelled/stopped, `Exited` otherwise).
  - All event dispatches through `DebugLifecycleCoordinator` are synchronized under an internal monitor lock, guaranteeing thread safety and serialized delivery to downstream consumers.

### Failure Discrimination & Event/Result Agreement
`DebugProject.execute()` strictly guarantees that the terminal event and `DebugResult.state()` agree for all proven execution paths:

| Execution Case | Terminal Event | `DebugResult.state()` | Agreement & Diagnostics Handling |
| :--- | :--- | :--- | :--- |
| **Normal Completion** | `DebugEvent.Exited(0)` | `SessionState.EXITED` | Agreement: Both indicate normal completion. Exit code 0, `wasStopped = false`. |
| **Exceptional Completion** | `DebugEvent.Exited(1)` | `SessionState.FAILED` | Agreement: Both indicate failure. `DebugEvent.Problem` emitted with code `debug.session.failed`. Zero `Stopped` events emitted. Exception cause preserved in `result.diagnostics()`. |
| **Launch Failure** | `DebugEvent.Exited(1)` (or `Stopped` if cancelled) | `SessionState.FAILED` (or `STOPPED` if cancelled) | Agreement: When launch fails, `DebugEvent.Problem` is emitted with domain or launch diagnostic, and terminal event is `Exited(1)` (not `Stopped`). If pre-cancelled, both event and result agree on `STOPPED`. |
| **Cancellation After Completion** | `DebugEvent.Exited(0)` | `SessionState.EXITED` | Agreement: If session exit future completes before cancellation is observed, `cancelledWhileRunning` remains `false`. The completed run is preserved as `EXITED` without flipping to `STOPPED`. |
| **Close Throwing** | `DebugEvent.Exited` | `SessionState.FAILED` | Agreement: Teardown exceptions from `session.close()` emit `DebugEvent.Problem` with code `debug.close.failed`, ensure terminal event is `Exited`, and return `SessionState.FAILED` with diagnostics. |
| **User Stop / Cancellation During Run** | `DebugEvent.Stopped()` | `SessionState.STOPPED` | Agreement: Cancellation during execution sets `cancelledWhileRunning = true`, calls `session.stop()`, emits exactly one `DebugEvent.Stopped()`, and returns `SessionState.STOPPED`. |

### Synchronization & Future-Driven Wait
- Replaced `Thread.sleep(30)` in `DebugProject` with `session.exit().get(20, TimeUnit.MILLISECONDS)`.
- When the exit future completes, `get(...)` unblocks immediately with zero sleep latency, while the 20ms slice allows responsive polling of `cancellation.cancelled()`.
- Thread interruption is properly caught: `session.stop()` is triggered, `Thread.currentThread().interrupt()` status is preserved, and a terminal `Stopped` event is emitted.

---

## 3. Files Modified

| File | Module | Changes |
| :--- | :--- | :--- |
| [`DebugProject.java`](file:///c:/Codigo/Proyectos/IDEARM/idearm-application/src/main/java/io/github/dinamo541/idearm/application/DebugProject.java) | `idearm-application` | Implemented `DebugLifecycleCoordinator`, single terminal-event contract, future-driven exit wait loop, exception cause discrimination, preservation of actionable diagnostics (`DebugEvent.Problem`), guaranteed event/result agreement, and structured teardown error handling. |
| [`DebugProjectTest.java`](file:///c:/Codigo/Proyectos/IDEARM/idearm-application/src/test/java/io/github/dinamo541/idearm/application/DebugProjectTest.java) | `idearm-application` | Fixed `ToolRunResult` compilation fixture. Added 14 comprehensive, deterministic regression tests covering hello-input reproduction, silent backends, late backend events, stops, GDB-style dual emission, cancellation, interruptions, build failure, concurrent coordinator stress, exceptional exit futures, cancellation after completion, close throwing, and launch failure. |
| [`docs/debug-lifecycle-review.md`](file:///c:/Codigo/Proyectos/IDEARM/docs/debug-lifecycle-review.md) | Docs | Comprehensive lifecycle review, root cause breakdown, event ownership contract, failure discrimination matrix, and ready-for-test status. |

---

## 4. Test Suite Details

All test additions in [`DebugProjectTest.java`](file:///c:/Codigo/Proyectos/IDEARM/idearm-application/src/test/java/io/github/dinamo541/idearm/application/DebugProjectTest.java) are deterministic and use synchronization barriers (`CountDownLatch`, `CompletableFuture`, `ExecutorService`) rather than `Thread.sleep`:

1. `reproducesAndResolvesCodexHelloInputDuplicateExitBug`:
   - Simulates the exact hello-input flow: startup -> `Paused` -> `Resumed` -> `Output` prompt -> `WaitingForInput` -> `sendInput` -> `Output` echo -> `exitFuture.complete()` + `events.accept(Exited)`.
   - Asserts downstream receives exactly 1 `DebugEvent.Exited`, 0 `Stopped`, and correct event sequencing.
2. `silentBackendEmitsTerminalExitedEventOnce`:
   - Tests silent backends (`DosBoxDebugSession`) that never call the event consumer.
   - Asserts `DebugProject` synthesizes exactly 1 `DebugEvent.Exited`.
3. `lateBackendEventsAfterFutureCompletionAreSafelyHandledWithoutSleeps`:
   - Simulates concurrent race where `exitFuture` completes, `DebugProject.execute` finishes and returns, and delayed backend thread emits late `Output`, `Exited`, and `Stopped`.
   - Uses `CountDownLatch` barriers to coordinate threads without sleeps. Asserts duplicate exit and late events are safely dropped.
4. `emittingBackendStopEmitsSingleStoppedEventAndNoExited`:
   - Simulates user cancellation/stop with emitting backend.
   - Asserts exactly 1 `DebugEvent.Stopped` and 0 `DebugEvent.Exited`.
5. `silentBackendCancellationEmitsSingleStoppedEvent`:
   - Simulates cancellation of silent backend.
   - Asserts exactly 1 synthesized `DebugEvent.Stopped` and 0 `DebugEvent.Exited`.
6. `backendEmittingExitedFollowedByStoppedDeliversSingleTerminalEvent`:
   - Simulates GDB-style backend that emits `Exited` followed immediately by `Stopped`.
   - Asserts `Exited` is delivered and redundant `Stopped` is suppressed.
7. `interruptedWaitStopsSessionRestoresInterruptAndEmitsStoppedEvent`:
   - Tests thread interruption while waiting on session exit.
   - Asserts `session.stop()` is invoked, thread interrupt flag is restored, and `Stopped` is emitted.
8. `preCancelledTokenReturnsStoppedResultWithoutLaunchingSession`:
   - Tests cancellation token already cancelled prior to launch.
   - Asserts session is never launched and result state is `STOPPED`.
9. `buildFailureReturnsFailedResultWithoutLaunchingSession`:
   - Tests build failure path with real `ToolRunResult` domain fixture.
   - Asserts session is never launched, diagnostics are returned, and no terminal events are emitted.
10. `exceptionalExitFutureReportsFailureRatherThanStop`:
    - Simulates backend crash via `exitFuture.completeExceptionally(...)`.
    - Asserts result state is `FAILED` (not `STOPPED`), `exitCode == 1`, `wasStopped == false`, `debug.session.failed` diagnostic is preserved, `DebugEvent.Problem` is emitted, terminal event is `Exited(1)`, and 0 `Stopped` events are emitted.
11. `cancellationAfterCompletionPreservesExitedStatus`:
    - Simulates cancellation arriving strictly after the session exit future has already completed.
    - Asserts result state remains `EXITED` (not converted to `STOPPED`), terminal event is `Exited(0)`, and 0 `Stopped` events are emitted.
12. `sessionCloseThrowingReportsFailureAndAgreesWithTerminalEvent`:
    - Simulates normal completion followed by `session.close()` throwing during teardown.
    - Asserts result state is `FAILED`, `debug.close.failed` diagnostic is preserved, `DebugEvent.Problem` is emitted, terminal event is `Exited`, and terminal event and result agree.
13. `launchFailureReportsFailureAndEmitsMatchingTerminalEvent`:
    - Simulates `launchDebug` throwing `DomainException("debug.tool.missing", ...)`.
    - Asserts result state is `FAILED`, diagnostic is preserved, `DebugEvent.Problem` is emitted, terminal event is `Exited(1)`, and terminal event and result agree.
14. `lifecycleCoordinatorDirectUnitContract`:
    - Multi-threaded barrage submitting concurrent `Exited`, `Stopped`, and `Output` events across 8 threads simultaneously.
    - Asserts exactly 1 terminal event is delivered downstream.

---

## 5. Ready-for-Test Status

- **Shared Maven Execution**: Per the coordination contract, Maven was **not run** concurrently in this shared checkout to avoid conflicting with other running tasks.
- **Ownership Adherence**: Only files owned under Task D were edited:
  - `idearm-application/src/main/java/io/github/dinamo541/idearm/application/DebugProject.java`
  - `idearm-application/src/test/java/io/github/dinamo541/idearm/application/DebugProjectTest.java`
  - `docs/debug-lifecycle-review.md`
  No emulator, presentation, language, or root files were modified. All existing local edits were preserved intact.
- **Verification Commands for Codex**:
  ```powershell
  # Run DebugProject unit test suite
  ./mvnw test -pl idearm-application -Dtest=DebugProjectTest

  # Run emulator integration debug test
  ./mvnw test -pl idearm-cli -Dtest=EmulatorDebugIntegrationTest
  ```
- **Status**: Ready for Codex review and verification.
