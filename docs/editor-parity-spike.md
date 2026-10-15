# ADR-005 Addendum & Spike Report: Editor Parity with Monaco/VS Code

- **Author:** Antigravity (Task C Parallel Implementation)
- **Date:** 2026-10-14
- **Spike Location:** `spikes/editor-parity/`
- **Scope:** Isolated prototype, formal bridge contract, original 5k/20k-line benchmark fixtures, and empirical evaluation of JavaFX WebView (JavaFX 23.0.2 spike host) vs. Standalone Browser hosting (Microsoft Edge / Chromium).
- **Production Status:** Spike only (`spikes/editor-parity`). No production editor changes or shared POM modifications applied.

---

## 1. Executive Summary & Decision Gate (Go / No-Go)

### Recommendation: **CONDITIONAL GO for Bridge Contract; NO-GO for JavaFX WebView in Production**

| Candidate Architecture | Viability Verdict | Key Technical Reason |
|---|---|---|
| **JavaFX WebView + Monaco 0.52.2** | **NO-GO for Production** | Evaluated on JavaFX 23.0.2 (WebKit 619.1) because `javafx-web` is absent from the local JavaFX 25.0.2 cache. While the isolated prototype runs and executes model edits (`executeEdits` API p95 ~1–2 ms), production embedding carries critical liabilities: `netscape.javascript.JSObject` is deprecated for removal in modern JDKs (JDK 25); `javafx-web` requires ~35 MB of native binaries per OS; OpenJDK 25 triggers mandatory native access warnings (`twkInitWebCore`); disposal calls take an observed 1,213 ms; and native WebKit memory is unmanaged by JVM heap controls. |
| **RichTextFX 0.11.7 (Current Production)** | **RETAIN for Immediate Milestones (M1–M4)** | Zero browser runtime dependencies, zero native WebKit binary overhead, measured 11 ms p95 typing in ADR-005 on `loadmap.asm`, native JavaFX scenegraph integration, and already accepted in ADR-005. |
| **Monaco via Out-of-Process Shell (Chromium/CEF/Tauri)** | **EVALUATION TARGET for Future Migration Gate (M5–M6)** | Native web platform fidelity, low API model update times (p95 ~0.8 ms in Edge), clean IPC boundary avoiding JDK `JSObject` deprecation, and independent process memory isolation. |

### Decision Summary
1. **Preserve RichTextFX 0.11.7 as the active production editor** for the upcoming execution, debugger, and classroom parity tranches (M1–M4).
2. **Adopt the Bridge Contract defined in this spike** as the formal interface for `EditorComponent`. This decouples the presentation layer from RichTextFX-specific internals (`StyleSpans`, `VirtualizedScrollPane`, `TwoDimensional.Bias`).
3. **Do not introduce `javafx-web` into the shared Maven reactor**. Adding JavaFX WebView would bloat the distribution by 35+ MB per platform, introduce JDK 25 `JSObject` deprecation risks, and risk broken builds on platforms where native WebKit binaries are missing.

---

## 2. Inventory of Current EditorComponent & RichTextFX Couplings

Inspection of `idearm-app` revealed significant concrete coupling between the presentation layer and RichTextFX internals:

### Current Couplings
1. **Direct Styling Pipeline (`RichTextFxEditorComponent`):**
   - Direct reliance on `org.fxmisc.richtext.CodeArea`, `StyleSpans<Collection<String>>`, and `StyleSpansBuilder`.
   - RichTextFX paragraph-level style overwriting: every restyle completely replaces existing styles, requiring syntax highlighting and diagnostic underline spans to be computed simultaneously.
2. **Scrolling and Viewport Virtualization:**
   - Explicit wrapper `org.fxmisc.flowless.VirtualizedScrollPane<CodeArea>`.
   - Caret position calculations tightly bound to `org.fxmisc.richtext.model.TwoDimensional.Bias`.
3. **Custom Floating Popups:**
   - Autocompletion implemented as custom JavaFX `PopupControl` (`CompletionPopup`), manually computing screen bounds via `codeArea.getCharacterBoundsOnScreen()`.
   - Hover card implemented as `HoverCardPopup`, manually subscribed to `org.fxmisc.richtext.event.MouseOverTextEvent`.
4. **Editor Commands & Selection Manipulation (`EditorActions`):**
   - Directly manipulates `codeArea.replaceSelection()`, `codeArea.getAnchor()`, and `codeArea.getCaretPosition()`.
   - Search/replace dialog is a custom JavaFX `VBox` mounted on `BorderPane.top` instead of an integrated editor overlay.
5. **Gutter Graphics Factory:**
   - Breakpoint and line number gutter relies on `IntFunction<Node>` factory (`createGutterFactory()`), requiring custom CSS styling and manual graphic cache invalidation on breakpoint toggle.

### Decoupling Strategy
The bridge abstraction decouples these features by having the host communicate purely via high-level events (documents, monotonic versions, diagnostics, breakpoint line sets, and execution line pointers), while the editor implementation internally handles view rendering, popups, and scrolling.

---

## 3. Bridge Contract Specification

The bridge contract governs communication between the IDE presentation layer (Java) and the editor host (Monaco / WebView).

### 3.1 Document & Version Management
- **Monotonic Version Counter:** Every edit increments the document's version number (`version: integer`).
- **Stale and Future-Version Diagnostic Guards:**
  $$\text{if } \text{responseVersion} < \text{currentDocumentVersion} \implies \text{DROP (Stale)}$$
  $$\text{if } \text{responseVersion} > \text{currentDocumentVersion} \implies \text{REJECT (Future Desync)}$$
  When an asynchronous background linter finishes analyzing an earlier document snapshot, the bridge checks the version tag and automatically discards stale diagnostics, preventing diagnostic flicker and out-of-order squiggle rendering. If a version from the future is received due to an out-of-band race condition, it is rejected to prevent applying ungrounded markers.
- **Asynchronous Autocompletion & Stale/Future Response Drop Guards:**
  The host initiates an autocompletion request with a unique `requestId` and snapshot version. If the user edits the document while candidates are in transit ($V_{\text{response}} < V_{\text{current}}$), or if an invalid future version arrives ($V_{\text{response}} > V_{\text{current}}$), the bridge drops the response, preventing stale completions from popping up over modified text.
- **Pending Completion Cancellation on Disposal:**
  Disposing a document (`disposeDocument`) or the entire editor (`disposeEditor`) actively cancels all in-flight autocompletion requests for that document, resolving their promises with empty results and emitting `onCompletionCancelled`.
- **Debounced Edits with Automatic Switch Flushing:**
  Edits are debounced per document (50 ms). If the user switches tabs or opens another document while a debounce timer is running, the bridge immediately flushes pending edits for the previous document before the new document takes focus, eliminating cross-tab edit pollution and lost changes.
- **Avoidance of Leaked Initial Anonymous Model:**
  The editor initializes with `model: null`, preventing Monaco from instantiating an unmanaged initial anonymous model (`inmemory://model/1`).
- **Explicit Language Provider Lifecycle Tracking:**
  Language providers (`registerCompletionItemProvider`, `registerHoverProvider`) return `IDisposable` handles that are tracked in `providerDisposables` and explicitly disposed on `disposeEditor()` to prevent retained listener handles across test or tab sessions.

### 3.2 Host-to-Editor (Inbound API)
- `openDocument({ uri, text, version, languageId })`: Loads document, creates model, sets version, resets undo stack, and flushes previous document's pending debounced edits.
- `setText(text)`: Synchronizes text from disk without dirtying the buffer.
- `setDiagnostics({ uri, version, diagnostics })`: Applies error/warning markers to the model, strictly guarded by stale ($v < v_{\text{cur}}$) and future ($v > v_{\text{cur}}$) checks.
- `requestCompletions({ uri, line, column, prefix })`: Initiates asynchronous candidate lookup with unique `requestId` and document version.
- `provideCompletionsResponse({ requestId, version, items })`: Resolves completion lookup; discards response if version is stale or desynced.
- `setBreakpoints({ uri, activeLines, disabledLines })`: Updates gutter decorations using `deltaDecorations` (`glyph-breakpoint-active` vs `glyph-breakpoint-disabled`).
- `setExecutionLine({ uri, line, reveal })`: Decorates instruction pointer line with yellow glyph arrow and background highlight; scrolls line into center if `reveal=true`. Clears decoration when `line=null`.
- `goToLine({ line, column })`: Moves cursor and centers line in view.
- `replaceWordAtCaret(replacement)`: Atomic replacement of prefix/word at current caret.
- `disposeDocument(uri)`: Destroys model, cancels pending debounce timers and in-flight completions, and frees model entry.
- `disposeEditor()`: Completely unmounts Monaco, destroys all models, cancels all pending in-flight requests, and unregisters all language providers.

### 3.3 Editor-to-Host (Outbound Events)
- `onDocumentChanged({ uri, version, isDirty, changes, fullText })`: Debounced (50 ms) notification of text edits.
- `onCaretChanged({ uri, line, column, offset, word, prefix })`: Caret coordinates, word under caret, and completion prefix.
- `onBreakpointToggled({ uri, line })`: Fired when user clicks the gutter glyph margin or presses F9.
- `onCompletionRequested({ requestId, uri, version, line, column, prefix })`: Asynchronous completion trigger.
- `onCompletionCancelled({ requestId, uri, reason })`: In-flight completion cancellation telemetry.
- `onStaleDiagnosticsDropped({ uri, receivedVersion, currentVersion, droppedCount })`: Diagnostic drop telemetry.
- `onFutureDiagnosticsDropped({ uri, receivedVersion, currentVersion, droppedCount })`: Diagnostic desync telemetry.
- `onStaleCompletionDropped({ requestId, uri, receivedVersion, currentVersion, itemCount })`: Stale completion drop telemetry.
- `onFutureCompletionDropped({ requestId, uri, receivedVersion, currentVersion, itemCount })`: Future completion drop telemetry.
- `onDefinitionRequested({ uri, line, column, word })`: Fired on F12 or Ctrl+Click.
- `onReferencesRequested({ uri, line, column, word })`: Fired on Shift+F12.
- `onAcademicCenterRequested({ uri, line, column, word })`: Fired on Shift+F1.
- `onBenchmarkMetric({ name, durationMs, details })`: Performance telemetry.

---

## 4. Empirical Benchmark Methodology & Results

### 4.1 Test Environment & Fixtures
- **Machine:** Windows 11 amd64, 64-bit.
- **Java Runtime:** OpenJDK 25.0.3 (Temurin / Adoptium LTS).
- **JavaFX Runtime Evaluated in Spike:** OpenJFX 23.0.2 (`javafx-web-23.0.2-win.jar`, WebKit 619.1 / Safari 619.1) from local Maven cache.
  *(Note: Production IDEARM reactor uses JavaFX 25.0.2, which does not have a cached `javafx-web` artifact locally).*
- **Browser Runtime Evaluated:** Microsoft Edge 133.0 (Chromium V8 Engine).
- **Monaco Version:** `monaco-editor@0.52.2` (AMD local distribution, pinned).
- **Original Synthetic Fixtures:**
  - `load5k.asm`: 5,000 lines, 131.6 KB (127 procedures, macros, lookup tables, realistic 8086 code).
  - `load20k.asm`: 20,000 lines, 526.1 KB (510 procedures, structured 8086 code).
- **Measurement Disclaimers & Provenance:**
  - `executeEdits` timings measure **synchronous JavaScript model mutation API calls**, not end-to-end OS keystroke event dispatch, layout, font glyph rasterization, and GPU compositor presentation.
  - ADR-005 baseline was measured using a distinct harness on `loadmap.asm` (104 KB, 4,839 lines). Because fixtures, harnesses, and measurement methodologies differ, these figures cannot be construed as a proof that Monaco outperforms RichTextFX end-to-end.
  - JVM heap measurements report only `Runtime.totalMemory() - freeMemory()`. They do **not** reflect native WebKit C++ memory allocations, JavaScriptCore heap, or GPU texture buffers.
  - Tab disposal durations represent the elapsed wall-clock time of the cleanup routine; they are not profiled breakdowns of WebKit internal garbage collection or thread synchronization.

### 4.2 Raw Measurement Data

| Metric / Scenario | RichTextFX 0.11.7 (ADR-005 Baseline on `loadmap.asm` 4.8k lines, JavaFX 25) | Monaco 0.52.2 in JavaFX 23.0.2 WebView (WebKit 619.1 on `load5k.asm` / `load20k.asm`) | Monaco 0.52.2 in Standalone Edge 133 (V8 on `load5k.asm` / `load20k.asm`) | Gate Target |
|---|---|---|---|---|
| **5k Lines Load Time** | 90 ms | **77.0 ms** (bridge openDocument: 67.0 ms) | **38.4 ms** (bridge openDocument: 29.0 ms) | < 1,000 ms |
| **5k API Edit Time (p50)** | 6 ms (keystroke latency) | **1.0 ms** (executeEdits) | **0.2 ms** (executeEdits) | < 25 ms |
| **5k API Edit Time (p95)** | 11 ms (keystroke latency) | **2.0 ms** (executeEdits) | **0.8 ms** (executeEdits) | < 50 ms |
| **5k API Edit Time (max)** | 34 ms (keystroke latency) | **14.0 ms** (executeEdits) | **9.2 ms** (executeEdits) | < 100 ms |
| **5k Diagnostics Update (50 markers)** | 59 ms (whole-file restyle) | **2.0 ms** | **0.4 ms** | < 50 ms |
| **20k Lines Load Time** | Not measured | **216.0 ms** (bridge openDocument: 133.0 ms) | **49.8 ms** (bridge openDocument: 31.9 ms) | < 2,000 ms |
| **20k API Edit Time (p50)** | Not measured | **< 1.0 ms** (executeEdits) | **0.4 ms** (executeEdits) | < 25 ms |
| **20k API Edit Time (p95)** | Not measured | **1.0 ms** (executeEdits) | **0.9 ms** (executeEdits) | < 50 ms |
| **20k API Edit Time (max)** | Not measured | **2.0 ms** (executeEdits) | **1.6 ms** (executeEdits) | < 100 ms |
| **20k Diagnostics Update (100 markers)** | Not measured | **1.0 ms** | **0.1 ms** | < 50 ms |
| **Observed Tab Disposal Elapsed** | Instant (Java object release) | **1,213.0 ms** (elapsed cleanup call) | **32.8 ms** (elapsed cleanup call) | Responsive cleanup |
| **Host JVM Heap** | ~45 MB base | **69 MB** *(excludes native WebKit memory)* | N/A (separate browser process) | Stable JVM heap |

### 4.3 Technical Observations
1. **Model Buffer Mutation Overhead:** In both WebKit and Chromium, Monaco's synchronous `executeEdits` API operates on piece-tree buffer structures, resulting in low API call times (p95 $\le$ 2 ms). However, full typing responsiveness in an embedded application also depends on browser event loop scheduling, IME handling, and JavaFX-to-WebKit rendering blits.
2. **Diagnostic Marker Independence:** Monaco manages diagnostic markers via `setModelMarkers` independently of its Monarch tokenization pipeline, avoiding full-buffer restyles on lint updates.
3. **Disposal Latency in WebView:** Disposing 5k and 20k models in JavaFX WebView took an observed 1,213 ms. While model entries were unmapped from the bridge, this duration indicates substantial synchronous work on the JavaFX application thread when tearing down WebKit document representations.

---

## 5. Technical Evaluation of JavaFX WebView Feasibility

While Monaco's JavaScript bundle initializes and executes within JavaFX 23 WebView, embedding it in the production application poses multiple severe technical and architectural obstacles:

### 5.1 Critical Blockers & Architectural Liabilities
1. **`netscape.javascript.JSObject` Deprecation in JDK 25:**
   ```
   warning: [removal] JSObject in netscape.javascript has been deprecated and marked for removal
   ```
   `JSObject` is the standard bridge mechanism provided by JavaFX `WebEngine` to map Java objects into the JavaScript execution context (`window.setMember`). Its impending removal in modern OpenJDK releases means any solution depending on `JSObject` will require a custom native JNI or WebSocket IPC replacement in future Java updates.
2. **Native Module Dependency and Packaging Bloat:**
   - `javafx-web` requires platform-specific native binaries (e.g., `javafx-web-23.0.2-win.jar` is **32.6 MB**).
   - In the local Maven repository, `javafx-web` is only cached for version `23.0.2`, while the IDEARM reactor targets `javafx.version=25.0.2`. Shipping `javafx-web` across Windows, Linux, and macOS would add ~100 MB of native shared libraries to distributions.
3. **Java 25 Native Access Restrictions:**
   Running `StandaloneWebViewHost` on OpenJDK 25 emits mandatory restricted-access warnings:
   ```
   WARNING: A native method in com.sun.webkit.WebPage has been bound
   WARNING: Use --enable-native-access=javafx.web to avoid a warning for native methods declared in this module
   WARNING: Restricted methods will be blocked in a future release unless native access is enabled
   WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release
   ```
   Deploying this requires adding `--enable-native-access=javafx.web,javafx.graphics` to startup scripts, which restricts launcher flexibility and flags future compatibility issues.
4. **Memory Transparency:**
   Because WebKit allocates DOM nodes, rendering contexts, and JavaScriptCore heaps in native C++ memory, `Runtime.getRuntime()` cannot observe or constrain WebKit memory usage.
5. **Origin Constraints:**
   Monaco's web workers fail under the `file://` protocol due to WebKit's local origin isolation policies, necessitating an internal loopback HTTP server (`http://127.0.0.1:8089`).

---

## 6. Reproducibility & Test Execution Guide

All spike artifacts are self-contained in `spikes/editor-parity/`.

### 6.1 Running Real Bridge Contract Tests
To run the automated test suite exercising the actual exported `MonacoEditorBridge` class across 8 behavioral scenarios:
```powershell
cd c:\Codigo\Proyectos\IDEARM\spikes\editor-parity
node test-bridge.js
```
*Validates: 5k/20k fixtures, Monarch grammar, prevention of initial anonymous model leak, stale/future diagnostic drops, async autocompletion, in-flight completion cancellation on disposal, tab-switching debounce flush, and debug decorations.*

### 6.2 Running Server Security & Containment Tests
To test `server.js` against malformed URI encodings (`%E0%A4%A`), directory traversal (`../../pom.xml`), and sibling prefix traversal via `path.relative` containment:
```powershell
cd c:\Codigo\Proyectos\IDEARM\spikes\editor-parity
node test-server.js
```

### 6.3 Combined Test Script
To run both test suites via npm:
```powershell
cd c:\Codigo\Proyectos\IDEARM\spikes\editor-parity
npm test
```

### 6.4 Running the Automated Benchmark Suite
To run the fully reproducible benchmark suite (which automatically manages the local server lifecycle, checks tests, and displays measured telemetry):
```powershell
cd c:\Codigo\Proyectos\IDEARM\spikes\editor-parity
npm run bench
```
To force a fresh execution of both browser and JavaFX WebView benchmarks:
```powershell
node benchmark-runner.js --rerun
```

### 6.5 Running Standalone JavaFX WebView Host Interactively
```powershell
cd c:\Codigo\Proyectos\IDEARM\spikes\editor-parity
# Compile:
$m2 = "$env:USERPROFILE\.m2\repository\org\openjfx"
$mp = "$m2\javafx-base\23.0.2\javafx-base-23.0.2-win.jar;$m2\javafx-graphics\23.0.2\javafx-graphics-23.0.2-win.jar;$m2\javafx-controls\23.0.2\javafx-controls-23.0.2-win.jar;$m2\javafx-media\23.0.2\javafx-media-23.0.2-win.jar;$m2\javafx-web\23.0.2\javafx-web-23.0.2-win.jar"
javac --module-path $mp --add-modules javafx.controls,javafx.web -d bin src\StandaloneWebViewHost.java

# Run in benchmark mode:
powershell -ExecutionPolicy Bypass -File run-webview-host.ps1 -Benchmark
```

### 6.6 Running Standalone Browser Benchmark
```powershell
cd c:\Codigo\Proyectos\IDEARM\spikes\editor-parity
powershell -ExecutionPolicy Bypass -File run-browser-benchmark.ps1
```

---

## 7. Proposed Production Integration Patches (Not Applied)

When the project decides to integrate an alternative editor behind `EditorComponent` (e.g. in milestone M5/M6), the following decoupled contracts are proposed:

### Patch 1: Implement `MonacoEditorComponent` behind `EditorComponent`
```java
// Target: idearm-app/src/main/java/io/github/dinamo541/idearm/app/editor/MonacoEditorComponent.java
package io.github.dinamo541.idearm.app.editor;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.scene.Node;
import java.util.List;
import java.util.Set;

public final class MonacoEditorComponent implements EditorComponent {
    // Implements EditorComponent completely without leaking browser/WebView types
    // Manages monotonic versions, diagnostic updates, breakpoints, and disposal
}
```

### Patch 2: Factory Binding in `EditorAreaViewModel`
```java
// Target: idearm-app/src/main/java/io/github/dinamo541/idearm/app/viewmodel/EditorAreaViewModel.java
// Support swappable editor component factory:
public EditorAreaViewModel(Supplier<EditorComponent> editorFactory) {
    this.editorFactory = Objects.requireNonNull(editorFactory, "editorFactory");
}
```

---

## 8. Conclusion

Task C has delivered an executable isolated prototype, an automated verification harness, and a formal bridge contract:
1. **Bridge contract fully verified:** Monotonic versioning, stale and future-version diagnostic rejection, asynchronous completion tracking with stale/future drop guards, in-flight request cancellation on disposal, tab-switching debounce flush, active/disabled breakpoint gutter decorations, and execution line pointers are implemented and verified by 8 automated unit tests.
2. **Empirical data recorded with clear provenance:** Raw API timings and load times were measured on original 5k/20k assembly fixtures on the user's machine, with exact distinctions between synchronous model mutation (`executeEdits`) and end-to-end rendering latency.
3. **Objective decision gate:** JavaFX WebView is **not** recommended for production integration due to JDK 25 `JSObject` deprecation, native binary distribution bloat (+35 MB per OS), and JVM restricted-access warnings. Retaining RichTextFX in production remains the sound architectural decision for M1–M4.
