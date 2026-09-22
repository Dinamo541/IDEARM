# IDEARM action plan

> **Created:** 2026-09-21 from a full review of every module (build green: 410 tests, 0 failures, 1 skipped).
> **Scope:** everything that is broken, missing or architecturally inconsistent, ordered so that each phase leaves
> the product releasable. Items marked **(reproduced)** were confirmed with a throwaway probe program during the
> review; the rest come from reading the code.
>
> Read [`PLAN.md`](../PLAN.md) first for the project rules. This file is the work list; PLAN.md §7 records what
> gets done.
>
> **Status 2026-09-22:** Phase 1 is implemented: P1-01 … P1-07, plus P1-09 (DOSBox 0.74-3 by default) and P1-10
> (Linux), which the user added while deciding D3. Only the release steps of P1-08 remain; they need the user's
> approval. Each item below ends with its status.

---

## How to use this plan

- Work phase by phase. Phase 1 is the exit condition for tagging **v1.0.0**; later phases can ship as v1.0.x/v1.1.
- Each item has an ID (`P1-03`), the problem, where it lives, what to do and a **Done when** list.
- Sizes are rough estimates: **S** ≤ half a day · **M** 1–2 days · **L** 3 days or more.
- Items that say **Verify first** depend on a fact that must be proven (spike or real tool) before code relies on it
  (PLAN.md rule 6). Record the evidence in `spikes/REPORT.md` or an ADR.

### Definition of done (applies to every item)

1. A regression test that fails before the change and passes after it (unit test, or a tagged integration test
   `requires-tasm` / `requires-masm` / `requires-nasm` when a real tool is needed).
2. `mvn test` green; `mvn install -Plocal-tools` green when a toolchain, runner or environment changed; the desktop
   visual smoke (`IDEARM_VISUAL_SMOKE`) when the UI changed. The CLI and tests run on the class path, so only the
   desktop app proves the module path.
3. Every new UI text is in `messages_en.properties` **and** `messages_es.properties`; every new diagnostic code has
   `diagnostic.<code>` in both bundles (`MessageBundlesTest` enforces it) and a row in `docs/troubleshooting.md`.
4. User-visible behavior changes are reflected in `docs/user-guide.md`; decisions in an ADR; progress in PLAN.md §7.

---

## 0. Decisions needed from the user

| ID | Question | Recommendation | Blocks |
|---|---|---|---|
| D1 | Default debugger for new DOS projects: built-in emulator (`emu8086`) or Turbo Debugger/CodeView in DOSBox (`external`, today's default, needs TD.EXE/CV.EXE)? | `emu8086`: it needs no proprietary tool and every IDE panel works with it. | P2-02 |
| D2 | May the user build while a program or debug session is still open (for example DOSBox waiting for a key)? | Yes for Build/Clean from the IDE after P3-04; keep one Run/Debug session at a time. | P3-04 |
| D3 | Charset used for a source that is not valid UTF-8. | **Decided 2026-09-21: Windows-1252** (what Notepad/Notepad++ "ANSI" write on Spanish Windows); switchable per file later (P2-04). | P1-02 |
| D4 | Charset of *new* files in DOS projects. | Keep UTF-8, and warn when a DOS string literal contains non-ASCII characters (P2-04). | P2-04 |
| D5 | Should the Terminal become a real console (ConPTY through a library such as pty4j)? | Not now; revisit after student feedback. | — |

The open questions already listed in PLAN.md §11 (git init and publishing, product name, extra native libraries,
WiX) still apply.

---

## Phase 1 — Release blockers (before tagging v1.0.0)

### P1-01 · The IDE fails to start when the Windows user name has a space or an accent — M **(reproduced)**

**Problem.** The staging root defaults to `%LOCALAPPDATA%\IDEARM\staging`. `WorkbenchBootstrap.system()` builds a
`HybridToolRunner`, whose constructor calls `DosStaging.requireMountable` and throws for `C:\Users\Juan Perez\...`
or `C:\Users\José\...`. `App.start()` does not catch it, so the window never opens. The CLI resolves the same path
and fails for every command, native projects included.

**Where.** `idearm-app/.../bootstrap/WorkbenchBootstrap.java` (`stagingRoot()`, line 43–47),
`idearm-cli/.../Main.java` (`resolveStagingDirectory()`), `idearm-toolchain-dos/.../HybridToolRunner.java:49`,
`DosBoxToolRunner.java:39`, `DosStaging.java:75`, `App.java:45`.

**Do.**
1. Add one pure predicate for "a folder DOSBox can mount" (ASCII, no spaces, none of `& | < > % "`) in the domain,
   for example `domain.execution.StagingPaths.isMountable(String)`. It replaces the three different copies
   (`DosStaging`, `DosBoxExecutionEnvironmentProvider`, `DosBoxDebugEnvironmentProvider`).
2. Add one resolver used by both composition roots, for example `infrastructure.workspace.StagingLocation`, with an
   injectable environment map for tests. Candidate order, first mountable and creatable wins:
   `IDEARM_STAGING_DIR` → `%LOCALAPPDATA%\IDEARM\staging` → the 8.3 short name of that folder (create it, then
   `GetShortPathNameW` through FFM as `WindowsJob` does) → `%ProgramData%\IDEARM\staging` →
   `%SystemDrive%\IDEARM\staging`.
3. Runner constructors must never throw for an unmountable root: move the check into `run()`, so NASM projects keep
   working and a DOS build reports the localized `build.non-ascii-staging` diagnostic.
4. `App.start()`: catch bootstrap failures and show a localized error dialog with the reason.

**Done when.**
- Resolver tests with `C:\Users\Juan Perez` and `C:\Users\José` pick an ASCII candidate.
- Constructing both runners with such a root does not throw; a DOS build with it yields the diagnostic.
- Manual: with `$env:LOCALAPPDATA = 'C:\Temp\Juan Perez'` the IDE opens and `examples/hello` builds and runs.

**Status: done.** `StagingPaths`, `StagingLocation` (+ `WindowsShortPaths`; Linux candidates
`~/.cache/idearm/staging` and a private `/tmp/idearm-<user>/staging`), runner checks moved into `run()`, startup
error dialog `app.startup.failed`. The manual check with a user folder named `José Pérez` found that the 8.3
staging folder was refused as a link when the build was published; the link checks were fixed (`SafePaths`,
`TomlProjectRepository`) and TASM then built and ran in DOSBox 0.74-3. Tests: `StagingLocationTest`,
`HybridToolRunnerTest`, `SafePathsTest`.

### P1-02 · Sources that are not UTF-8 cannot be opened; saves are not atomic — M

**Problem.** Every reader uses `Files.readString(path, UTF_8)`, which throws `MalformedInputException` on ANSI
(Windows-1252) or DOS (CP437/CP850) files with accents in comments, typical of files written in EDIT.COM, emu8086
or Notepad++ "ANSI". The editor cannot open them at all; the symbol index and lint silently skip them. Saving
rewrites the file in place (a crash mid-write truncates the student's source), always as UTF-8, and may change line
endings. The status bar has an encoding badge, but `StatusBarViewModel.setEncoding` is never called.

**Where.** `EditorAreaViewModel.java:79`, `EditorDocumentViewModel.java:80` and `:89`,
`WorkbenchViewModel.java:707` (`indexProjectFiles`) and `:797` (`extractLineSnippet`), `LintProject.java:75`,
`ImportProject.java` (reads ISO-8859-1), `StatusBarViewModel.setEncoding`.

**Do.**
1. A pure decoder in the domain (no I/O), for example `domain.files.TextDecoding`:
   bytes → `DecodedText(text, charset, bom, lineSeparator)` and back. Order: BOM (UTF-8, UTF-16LE/BE) → strict UTF-8
   (`CodingErrorAction.REPORT`) → the D3 fallback.
2. Use it in every place listed above, so the editor, index, lint and import read files the same way.
3. `EditorDocumentViewModel` remembers charset, BOM and line separator; `save()` writes them back through a temporary
   file and `ATOMIC_MOVE` (as `FileRecentItemsStore` does). If a character cannot be encoded in the file's
   single-byte charset, ask whether to save as UTF-8 instead of writing `?`.
4. **Verify first:** whether RichTextFX keeps `\r` in `replaceText`. Normalize to `\n` inside the editor and restore
   the original separator on save.
5. Show the active document's charset (and CRLF/LF) in the status bar.

**Done when.**
- A Windows-1252 file containing `ñ` opens, displays correctly and round-trips byte-identical when unchanged.
- UTF-8 with BOM and CRLF files round-trip unchanged; malformed UTF-8 never throws.
- The status bar shows the encoding of the active tab.

**Status: done.** `TextDecoding` (BOM → strict UTF-8 → Windows-1252) in the editor, index, snippets, lint and the
emulator's source reader; atomic save that keeps charset, BOM and line separator; badge such as
`windows-1252 · CRLF`. Instead of a question, a character Windows-1252 cannot store saves the file as UTF-8 and
the status bar says so (`status.editor.savedAsUtf8`). Tests: `TextDecodingTest`, `EditorAreaViewModelTest`.

### P1-03 · The built-in emulator never receives keyboard input — M

**Problem.** `DosInterruptHandler.provideInput()` is never called, and `nextChar()` returns a carriage return when
its queue is empty. Every keyboard read (`INT 21h` 01h/07h/08h/0Ah, `INT 16h` 00h) returns Enter at once, so
menus, "press any key" and "type a number" programs do not work. `docs/user-guide.md` §6.1 says they do.

**Where.** `idearm-emu8086/.../dos/DosInterruptHandler.java:231`, `Emu8086DebugSession`, domain
`port/DebugSession.java`, `debug/DebugEvent.java`, `DebugViewModel`, `BottomPanelView`/`DebuggerPanelView`.

**Do.**
1. Blocking reads for 01h/07h/08h/0Ah and `INT 16h`/00h on the emulator worker thread (a `BlockingQueue`); keep the
   status checks non-blocking (06h with DL=FFh, 0Bh, `INT 16h`/01h).
2. Port changes: `DebugSession.sendInput(String)` (default no-op) and a new `DebugEvent.WaitingForInput`.
   `stop()`/`close()` must release a blocked read, otherwise Stop hangs.
3. UI: a program console in the Debug tab: the program's output plus key capture while an emulator session is
   attached. Each key is sent immediately; Enter sends `\r`, Backspace `0x08`. On `WaitingForInput` focus the
   console and show `status.debug.waitingInput` (EN/ES).
4. Add an example project that reads a name and greets it, and correct user guide §6.1.

**Done when.**
- Tests: a program blocked in 0Ah receives a line sent from another thread; 01h returns the typed character; Stop
  while blocked ends the session within one second.
- Visual smoke: the new example runs interactively in the Debug tab.

**Status: done.** Keys are typed in a field under **Terminal Output** (not the Debug tab), which also shows the
program's text exactly as written (no longer one character per line). Example `examples/hello-input`. Tests:
`Emu8086FaultsAndInputTest`, `BottomPanelViewModelTest`.

### P1-04 · Emulator faults and instruction bugs — M **(reproduced)**

**Problem.**
- Division by zero (or a quotient overflow) jumps through an empty interrupt table to `0000:0000` and executes
  garbage forever (still RUNNING after 2 000 000 steps).
- `CBW` stores `-1` instead of `0xFFFF` in AX (`Cpu8086.java:306`), so a later `ADD AX,1` clears CF.
- `AAM`/`AAD` compute ZF/SF/PF from AX instead of AL; `AAM 0` uses base 10 instead of raising a divide error.
- An unsupported opcode silently halts and is reported as "exit code 0" (`Cpu8086.java:555`).
- `INT 21h`/09h loops forever when the string has no `$` in its segment (`DosInterruptHandler.java:114`).
- Unsupported `INT 21h` functions (files, 40h, date/time) return "success" silently (`DosInterruptHandler.java:176`).

**Do.**
1. Fix `CBW` (mask to 16 bits) and audit every direct register field write (P3-05 makes this impossible later).
2. `AAM`/`AAD`: flags from AL; `AAM 0` raises the divide error.
3. Divide error and unhandled interrupts: when a vector still holds its default value, the session **pauses at the
   faulting instruction** with an explanation ("Division by zero at src/main.asm:NN"); resuming terminates the
   program as DOS does. `INT 3` pauses like a breakpoint. Other interrupts without a service report
   "INT xxh is not supported by the built-in emulator" and pause.
4. `Cpu8086` records why it stopped (HLT vs invalid opcode with byte and CS:IP); the session reports it in Output and
   in the exit message instead of "exit code 0".
5. `INT 21h`/09h stops after 65 536 characters and reports the missing `$` terminator.
6. Unsupported DOS functions are reported once per AH value, suggesting the external debugger for file programs.

**Done when.** Tests in `Cpu8086InstructionsTest`, `DosInterruptHandlerTest` and `Emu8086DebugSessionTest`:
`MOV AL,0FFh / CBW / ADD AX,1` gives AX=0, CF=1; `AAM` on AL=0Ah sets ZF; `DIV BL` with BL=0 pauses with the message
instead of running away; an invalid opcode is reported; 09h without `$` terminates.

**Status: done.** The session pauses on the faulting line, reports it in Terminal Output, Problems and the status
bar (`emu.divide-error`, `emu.invalid-opcode`, `emu.interrupt.unsupported`, `emu.string.unterminated`), and the
next resume ends the program with exit code 255; `emu.dos.unsupported` is a warning once per function. Tests:
`Emu8086FaultsAndInputTest`.

### P1-05 · DOSBox isolation can be broken — M

**Problem.** Isolation is a core promise (PLAN.md §1, rule 5), but:
- a DOS program can run `Z:\MOUNT.COM` (or `IMGMOUNT`) and mount the real disk; DOSBox 0.74 also ignores `-ro`;
- `[run] cycles` and `args` from `idearm.toml` are written unvalidated into the generated `.conf` and batch
  (`DosBoxExecutionEnvironmentProvider.java:152`, `:177`). A downloaded project with a line break in those values
  injects its own commands;
- GDB receives breakpoint locations and program arguments unquoted (`GdbProcessDebugSession.java:96`, `:120`), so a
  line break injects MI commands and a path with spaces breaks the breakpoint.

**Do.**
1. **Verify first (spike):** `config -securemode` as the last `[autoexec]` line after the mounts, in DOSBox-X
   2026.08.31, Staging 0.82.2 and 0.74-3. A test program that runs `MOUNT D C:\` must fail, while the build batch,
   the run batch and TD/CV still work. Record the results in `spikes/REPORT.md` and amend ADR-002. Also verify the
   `memsize` limits of each dialect.
2. Apply it to the three generated configurations: build (`DosStaging.writeConfiguration`), run and debug.
3. Validate `[run]` in one domain place used by Run and Debug: `cycles` ∈ `auto`, `max`, `max N%`, `fixed N`, `N`;
   `memsize` within the verified range; `args` printable ASCII without control characters or `| < > & % "`, command
   tail ≤ 126 characters for DOS. Codes `run.cycles.invalid`, `run.memsize.invalid` and `run.argument.unsafe`
   (it already exists for native runs) with EN/ES texts.
4. GDB/MI: pass locations and arguments as quoted MI c-strings and refuse line breaks.

**Done when.** Spike evidence is recorded; tests show that values containing `\r\n` produce a diagnostic and no
configuration is written; the three configuration generators contain the secure-mode line; a GDB breakpoint in
`src/my file.asm` is inserted.

**Status: done.** Spike S7 and ADR-002 Amendment 1; `RunSettings` (`run.cycles.invalid`, `run.memsize.invalid`
1–63, `run.argument.unsafe`, `run.arguments.tooLong`); secure mode in build, run and debug sessions; MI c-string
quoting. Tests: `DosBoxRulesTest`, `RunProjectTest`, `DosStagingTest`, `DosBoxRunSessionTest`,
`GdbProcessDebugSessionTest`.

### P1-06 · Small functional fixes — M (five S items)

| # | Problem | Where | Fix | Test |
|---|---|---|---|---|
| a | **Restart Debug does nothing**: `stop()` then `debug()` runs while the old task still holds the task slot. | `WorkbenchViewModel.java:533` | Keep the running task's future; restart when it completes. | `WorkbenchViewModelTest` |
| b | **`idearm dist` reports "Packaging failed" for native projects** although it packaged them: the launcher is `null` there. | `idearm-cli/.../Main.java:260` | Print the launcher line only when there is one. | `MainCliTest` with a fake packager |
| c | **A project named `..` is created one folder up (reproduced)**; `.`, `name.` and `CON` are also accepted. | `CreateProject.java:32`, `ImportProject` | Validate the name with `EntryNames.check` rules (no dot-only names, trailing dot or space, device names). | `CreateProjectTest` |
| d | **Linter false positives (reproduced)**: `.EXIT` is not seen as a program exit; `JMP SHORT fin` reports `short fin` as undefined. | `MissingTerminationRule.java:54`, `UndefinedSymbolRule.java:42`, `AssemblyParser` | Treat `.EXIT` as an exit; strip `SHORT`, `NEAR`, `FAR`, `... PTR` before looking up the target; ignore `$`-relative and segment-prefixed operands; complete the register list. | `AssemblyLinterTest` |
| e | **Hover shows `AH`, `BH` and labels such as `each` as numbers (reproduced)**; the Spanish text lacks a line break, says "Binario" in both languages and is hard-coded in the application layer (ADR-006). | `QueryHover.java:90–128` | Numbers must start with a digit (reuse the lexer's number rules). `QueryHover` returns data; the presentation formats it with new `hover.*` bundle keys. | `QueryEditorUseCasesTest`, `MessageBundlesTest` |

**Status: done** (a–e), with the tests listed in the table (`HoverTextTest` for the localized hover).

### P1-07 · Continuous integration — S

**Problem.** `release.yml` runs only on tags or by hand, and lacks `permissions: contents: write`, which new
repositories need for `softprops/action-gh-release` to publish.

**Do.** Add `.github/workflows/ci.yml` (push and pull request, `windows-latest`, Temurin 25, `mvn -B verify`) and
add the permission to `release.yml`. It takes effect once the repository exists (PLAN.md §11, user approval).

**Done when.** The first push shows a green CI run.

**Status: written; runs once the repository exists.** `ci.yml` has a Windows job (`mvn -B verify`) and a Linux job
(apt `nasm gdb dosbox xvfb`, `xvfb-run -a mvn -B verify -DexcludedGroups=requires-tasm,requires-masm`); the Linux
job was reproduced locally in Docker and is green (P1-10). `release.yml` has `permissions: contents: write`.

### P1-09 · DOSBox 0.74-3 by default for Build, Run and Debug, with invisible builds — M (added 2026-09-21)

**Why.** The user's decision while answering D3: students already have DOSBox 0.74-3, so it is tried first, then
DOSBox-X, then Staging, and builds must not show a window.

**Done.** `DosBoxDialects` (automatic order, one `isDosBox` rule instead of four copies), `DefaultToolRegistry` and
`ToolDetector` tell 0.74-3 from Staging by the version text inside `dosbox`/`dosbox.exe`, `DosBoxResolution` picks
the build dialect (project choice → first invisible → first installed), 0.74-3 builds with the SDL dummy drivers
(`-noconsole` only on Windows), and Run adds `run.resource.lfn` when a long resource name needs DOSBox-X. Evidence:
spike S7; ADR-002 Amendment 1. Tests: `DefaultToolRegistryTest`, `DosBoxResolutionTest`, `RunProjectTest`,
`DosBoxRunIntegrationTest` (`requires-dosbox`: a `.COM` exits with code 7 through the automatic dialect).

### P1-10 · Windows and Linux — M (added 2026-09-21)

**Why.** The user asked for IDEARM to work on any computer; this batch covers Windows and Linux (macOS should work
but is not tested).

**Done.** Linux tool folders and display variables for DOSBox; native Linux programs print in Terminal Output
and read the line typed there (the CLI uses the terminal; `scripts/idearm.sh`); GDB's non-MI lines are program
output, and the program's input is `/dev/null` so it cannot swallow MI commands; MASM runs ML inside DOSBox
where Win32 programs cannot run (`DOSXNT.EXE`). Verified on Ubuntu 24.04 in Docker: the whole suite (506 tests),
NASM → ld → run → GDB breakpoint, apt's DOSBox 0.74-3, the CLI with keyboard input, and the desktop IDE starting
under Xvfb. Bugs found there and fixed: the explorer allowed `MAIN.asm` next to `main.asm`; a GDB program reading
the keyboard hung the session. **Open:** window controls (maximize/restore, minimize) on a real Linux desktop;
a packaged Linux build.

### P1-08 · Release checklist (exit criteria of Phase 1) — S

- [x] P1-01 … P1-07 done; `mvn test` and `mvn install -Plocal-tools` green; visual smoke in EN and ES
      (2026-09-22: 509 tests on Windows with the real tools, 506 on Linux; the smoke's tooltip step needs the
      window to have focus).
- [ ] Manual end-to-end in the desktop app: TASM, MASM and NASM samples build, run and debug; the interactive
      emulator example works. Already checked from the command line and tests (2026-09-22): TASM and MASM 6.11
      (ML on the host, and ML inside DOSBox as on Linux) build and run in DOSBox 0.74-3 from a `José Pérez` user
      folder; NASM builds, runs and stops under GDB on Windows and Linux; the emulator reads a typed name. Clicking
      through Debug and typing in the IDE's own input field still need a person.
- [x] Docs: user guide §6.1 (emulator input and limits), troubleshooting rows for every new diagnostic, PLAN.md §7.
- [ ] Version `1.0.0` in every pom; git init, publish and tag `v1.0.0` (user approval).
- [ ] The portable zip tested on a clean Windows account whose user name has a space and an accent (covers P1-01
      and P1-02).

---

## Phase 2 — Missing features (v1.0.x → v1.1)

### P2-01 · INCLUDE dependencies — L

**Problem.** New and imported projects have `include = []`, and staging copies only the `.asm` sources and declared
include folders. `INCLUDE macros.inc` next to `main.asm` therefore fails with TASM, which searches the current
directory (`C:\` in the build batch). `FreshBuild` ignores such files, so after editing `macros.inc` Run can execute
the old program. `ImportProject` adds `.asm` files that other files INCLUDE as separate modules. Declaring
`include = ["src"]` is not a safe default either: `copyIncludes` rejects any non-8.3 file in that folder.

**Where.** `CreateProject.java:139`, `ImportProject.java`, `DosStaging.java:146–191`, `HybridToolRunner.java`,
`TasmAssemblerAdapter.java`, `FreshBuild.java:104`, domain `build/BuildPlan.java`.

**Do.**
1. `IncludeResolver` in the application layer: parse each planned source (the parser already produces
   `IncludeNode`), resolve every include relative to the including file's folder, then the declared include folders,
   recursively and cycle-safe. It returns project-relative dependency files and problems: `build.include.missing` at
   the INCLUDE line (before any tool runs), and `path.dos.invalid` for non-8.3 names in DOS targets.
2. Carry them in `BuildPlan.dependencies`; DOS and hybrid runners stage them at their project path on drive S (the
   native runner already works from the project folder).
3. `TasmAssemblerAdapter` adds `/i` for the source's own folder. **Verify first** with TASM 3.2 and 4.1.
4. `FreshBuild` uses sources + dependencies + `idearm.toml` to decide whether a rebuild is needed.
5. `ImportProject` leaves included `.asm` files out of `modules` and prefers the label named by `END <label>` as
   the entry.

**Done when.** A project whose `main.asm` includes `macros.inc` builds with TASM and MASM (tagged tests); editing
`macros.inc` triggers a rebuild; a missing include is reported at its line.

### P2-02 · Choose the debugger in the UI — S (after D1)

**Problem.** The built-in emulator is reachable only by editing `[debug] backend = "emu8086"` by hand; the default
`external` backend needs TD.EXE or CV.EXE, which many students do not have.

**Do.** A "Debugger" choice in New Project and Project Properties (DOS: built-in 8086 emulator, or Turbo
Debugger/CodeView in DOSBox; native: GDB), saved to `[debug] backend`. Defaults for CreateProject and ImportProject
follow D1. When `external` is chosen and TD/CV is missing, the diagnostic suggests the emulator and Properties
offers to switch. Update the examples and user guide §6.1.

**Done when.** A view model test covers saving the choice; `DebugProjectTest` covers the suggestion; visual smoke.

### P2-03 · Debugger capabilities and Pause — M

**Problem.** `DebugSession` stepping methods are default no-ops: with Turbo Debugger in DOSBox the IDE shows buttons
that do nothing. There is no Pause, so a program stuck in an infinite loop (a classic student bug) can only be
stopped, not inspected.

**Do.**
1. Domain `DebugCapability` (`STEP`, `PAUSE`, `BREAKPOINTS`, `REGISTERS`, `MEMORY`, `WATCHES`, `CALL_STACK`,
   `PROGRAM_INPUT`); `DebugSession.capabilities()` (default empty) and `pause()` (default no-op).
2. Emulator: the run loop honors a pause request and reports the paused location. GDB: `-exec-interrupt`
   (**verify first** on Windows with `new-console on`). DOSBox/TD: no capabilities; the Debug panel says debugging
   happens in the DOSBox window and hides stepping and register panels.
3. Pause command in toolbar, menu and palette (F6, as in VS Code); every debug button is bound to a capability.
4. Remove `EnvCapability` if it stays unused (see P3-07).

**Done when.** An emulator test pauses an infinite loop within 100 ms with registers reported; a tagged GDB test
pauses; visual smoke with each backend.

### P2-04 · Encoding choices and DOS accents — M (after P1-02, D3, D4)

**Do.** The encoding badge opens "Reopen with encoding" and "Save with encoding" (UTF-8, Windows-1252, CP437,
CP850). A lint rule `lint.dos-non-ascii-string` warns when a DOS string literal contains non-ASCII characters in a
UTF-8 file (DOS shows them as garbage) and suggests CP437.

**Done when.** Tests for re-encoding and the rule.

### P2-05 · More DOS and BIOS services in the emulator — M

**Do.** `INT 21h`: 40h (write to handles 1 and 2), 3Fh (read from handle 0), 2Ah/2Ch (date and time).
`INT 10h`: 02h/03h (cursor position, tracked), 09h/0Ah (write a character CX times), 13h (write string).
`INT 1Ah`/00h (ticks). `INT 16h` scan codes for Enter, Esc and arrow keys. Files and graphics stay out of scope and
are reported by P1-04. Update the user guide.

**Done when.** One `DosInterruptHandlerTest` case per service.

### P2-06 · Lint while typing — L

**Problem.** Educational warnings appear only after a build; `LintSource` exists but nothing uses it, and
`EditorComponent` cannot mark problems in the text.

**Do.** Add `EditorComponent.setDiagnostics(...)` (underline, gutter marker, hover text). Run lint on the active
document 400 ms after typing stops, on a background thread, for DOS targets. Publish results as a separate "live"
group in Problems, replaced on every run; build diagnostics stay separate.

**Done when.** A debounce test with `FakeEditorComponent`; with `loadmap.asm` (104 KB) lint takes under 100 ms off
the UI thread and typing latency stays within ADR-005 (p95 11 ms).

### P2-07 · Tool detection off the UI thread — S **(reproduced)**

**Problem.** The first `ToolRegistry.all()` runs `ToolDetector.detectAll()` on the JavaFX thread
(`ProjectPropertiesDialog.java:126`, `DoctorDialog.java:154`): it scans every PATH folder, runs `nasm/gcc/ld/gdb`
and hashes each binary (1.2 s on the development machine, more on a student laptop). Results are cached for the
whole session, and a registered tool whose file was moved keeps being returned.

**Do.** Load asynchronously with a placeholder; warm detection up in the background at startup; a "Rescan" button
in Tool Doctor (`ToolRegistry.refresh()`); fall back to detection, with a warning, when a registered executable no
longer exists; ignore PE `LINK.EXE`/`ML.EXE` (for example Visual Studio's) for the MASM 16-bit roles.

**Done when.** Tests for refresh and for a missing registered tool; both dialogs open instantly.

### P2-08 · CLI completeness — S

**Do.** Add `idearm new <folder> --name --target --cpu --toolchain` using CreateProject. CreateProject derives the
default toolchain from the target (NASM for native targets instead of always `borland-tasm`). Produce JSON with
Jackson (escapes tabs and other control characters). Decide whether `build` defaults to `debug` as in the IDE.

**Done when.** New `MainCliTest` cases.

### P2-09 · Feedback when a task is already running — S

**Do.** Build, Run or Debug requested while a task runs shows `status.task.busy` instead of silently doing nothing.

**Done when.** A view model test.

---

## Phase 3 — Architecture (v1.1)

### P3-01 · Provider-driven debugger and environment selection — M

**Problem.** `DebugProject` chooses environments by comparing ids (`"gdb"`, `"emu8086"`, `"dosbox"`) and the
debugger with `toolchainId.contains("borland")` (`DebugProject.java:99`, `:161`), which breaks rule 4 ("no
`if assembler == TASM`"). Run and Debug duplicate the DOSBox preference logic (`isDosBoxFamily`).

**Do.** `ToolchainProvider.externalDebugger()` returns the tool roles and kind (TD for Borland, CV for Microsoft).
`DebugEnvironmentProvider` declares the backend id it serves. One application `EnvironmentSelector` shared by Run and
Debug: preferred id → installed dialect → automatic choice. Doing this before or with P2-02 keeps the UI simple.

**Done when.** `DebugProjectTest` and `RunProjectTest` pass with no id or toolchain string comparisons left in the
application layer.

### P3-02 · File-system access from the application layer (ADR-010) — L

**Problem.** `BuildProject` documents "no filesystem knowledge", yet the application layer reads and writes files
directly: `FreshBuild`, `SourceSet`, `CreateProject`, `ImportProject` (it even writes the generated-folder markers
that belong to `BuildWorkspace`), `LintProject` and the resource checks in Run, Debug and Package.

**Do.** Record the decision in ADR-010. Recommended: a read port `SourceTree` (exists, list/walk with skipped
folders, read bytes, last-modified) implemented in infrastructure and used by `SourceSet`, `FreshBuild`,
`IncludeResolver` (P2-01), `LintProject` and the resource checks. Move the writes behind ports
(`ProjectFiles.writeTextIfAbsent`, `BuildWorkspace.adoptGeneratedFolders`). Then add an ArchUnit rule forbidding
`java.nio.file.Files` in the application layer.

**Done when.** Application tests use an in-memory `SourceTree`; the ArchUnit rule passes.

### P3-03 · Shared runtime and staging helpers — M

**Problem.** Duplicated code has already diverged: three different `requireMountable` checks; resource validation
copied three times; resource copying five times (only the build path rejects links); session deletion,
`ESSENTIAL_VARIABLES` and process-tree termination copied across `ProcessService`, `JobProcessLauncher` and
`HostTerminalRunner`. The DOSBox debug provider copies every `.asm`/`.inc` of the whole project without link checks
(`DosBoxDebugEnvironmentProvider.java:105`), passes TD only `S:\` and `S:\SRC` as source folders and ignores
`[run] cycles/memsize`. The dist packagers accept a marker by existence only, unlike `FileBuildWorkspace`.

**Do.** An application `RuntimeResources` for Run, Debug and Package; the domain predicate from P1-01; one staging
helper per module (`toolchain-dos` and `infrastructure`) for copying with link checks and deleting marked sessions;
a shared `ProcessTrees` in infrastructure. The DOSBox debug session stages only planned sources and dependencies.
The dist packagers use the same generated-folder check as `FileBuildWorkspace`.

**Done when.** Existing tests stay green and the only behavior changes are the ones listed here.

### P3-04 · Workspace lock scope during Run and Debug — S (after D2)

**Problem.** Run and Debug hold the project lock for the whole program session (`RunProject.java:89`,
`DebugProject.java:79`). While DOSBox waits for a key, the CLI and other operations get "project busy".

**Do.** Hold the lock for build and launch (the staging copies), then release it before waiting on the session.
Apply D2 in the workbench task rules.

**Done when.** A test builds from a second thread while a fake session is running.

### P3-05 · Encapsulate the emulator registers — M

**Do.** Make `CpuRegisters` fields private with setters that mask to 16 bits (the root cause of the `CBW` bug), and
update `Cpu8086`, the loaders and the session.

**Done when.** Emulator tests are green, plus a randomized test showing that register values never leave 16 bits.

### P3-06 · One version-constraint rule — S

**Do.** A pure domain `VersionConstraint` replaces the copied `checkVersion` in `BorlandToolchainProvider` and
`Microsoft16ToolchainProvider`; its messages name the actual tool.

### P3-07 · Remove or wire dead code — S

| Unused today | Action |
|---|---|
| `TaskManager` (application) and its test | Remove; the workbench and the CLI have their own task rules. |
| `LintSource` | Wired by P2-06, otherwise remove. |
| `EnvCapability` / `ExecutionEnvironmentProvider.capabilities()` | Replace with P2-03 or remove. |
| `DistConfiguration.launcher` | Honor it in `DosDistPackager` (skip `run.bat` when false). |
| `borland/DosArguments` (only delegates) | Remove. |
| `SymbolKind.MACRO` | Index `name MACRO` definitions, so macro calls get definitions and are not flagged. |

### P3-08 · CLI composition root — S

**Do.** Build the services once in a `CliServices` factory instead of repeating the wiring in `build`, `run` and
`dist`.

---

## Phase 4 — Polish (any time)

- **Build and Output panels:** they append by re-setting the whole text (quadratic on long logs) and never trim.
  Append to the text area and cap the size (for example 2 MB).
- **Breakpoints:** `FileBreakpointStore` ignores write errors and replaces a corrupt file with an empty list. Write
  atomically, report failures, and keep a backup of a corrupt file.
- **Projects reached through a junction or link:** they are rejected with `path.link.disallowed`. Check links only
  from the project root down, and explain the problem when one is found.
- **Lexer and parser:** doubled quotes inside strings (`'It''s'`), NASM local labels (`.loop:`), and more
  instructions (386 string forms such as `MOVSD` and `STOSD`, the real `SETcc` family instead of `SETCC`, `PUSHAD`,
  `POPAD`).
- **Linter:** `SAL`, `RCL` and `RCR` in the 8086 shift rule; `PUSH OFFSET x` counts as an immediate.
- **Completion:** registers filtered by the target CPU; detail text in the UI language.
- **Instruction catalog:** examples use Spanish identifiers (`bucle`, `fin_bucle`, `tabla`); switch to English
  (rule 1).
- **Emulator details:** shift counts are masked to 5 bits (80186 behavior) under an 8086 target; 80186 opcodes
  (`PUSH imm`, `SHL r,imm`) run without a warning; `IDIV` quotient -128.
- **Domain nits:** `RegisterState.isChanged` Javadoc contradicts its code; `MemoryView.bytes()` exposes its internal
  array; `EntryNames` and `FileNameRules` disagree on `COM0`/`LPT0`.
- **Terminal:** decodes PowerShell output with `Charset.defaultCharset()` (UTF-8), while the console uses the OEM
  code page, so accents break on Spanish Windows. A real console is D5.
- **Staleness:** switching the registered tool version does not trigger a rebuild. Include the resolved toolchain in
  the up-to-date check.

---

## Order and dependencies

```
P1-01 ─► P3-03 (shared mountable predicate)
P1-02 ─► P2-04, P2-06 (one decoder for editor, index and lint)
P1-03 ─► P2-03 (PROGRAM_INPUT capability)
P3-01 ─► P2-02 (recommended: selection by providers before adding the UI)
P3-02 ─► P2-01 (optional: IncludeResolver can start on java.nio and move to SourceTree later)
D1 ─► P2-02 · D2 ─► P3-04 · D3 ─► P1-02 · D4 ─► P2-04
```

## Summary

| ID | Title | Size | Depends on |
|---|---|---|---|
| P1-01 | IDE start with spaces/accents in the user folder | M | — |
| P1-02 | Source encodings and atomic save | M | D3 |
| P1-03 | Keyboard input in the built-in emulator | M | — |
| P1-04 | Emulator faults and instruction bugs | M | — |
| P1-05 | DOSBox isolation and input validation | M | spike |
| P1-06 | Five small functional fixes | M | — |
| P1-07 | Continuous integration | S | git init |
| P1-09 | DOSBox 0.74-3 by default, invisible builds | M | spike S7 |
| P1-10 | Windows and Linux | M | P1-01, P1-09 |
| P1-08 | Release checklist | S | P1-01…07, P1-09, P1-10 |
| P2-01 | INCLUDE dependencies | L | (P3-02) |
| P2-02 | Debugger choice in the UI | S | D1, (P3-01) |
| P2-03 | Debugger capabilities and Pause | M | P1-03 |
| P2-04 | Encoding choices and DOS accents | M | P1-02, D4 |
| P2-05 | More DOS/BIOS services | M | P1-04 |
| P2-06 | Lint while typing | L | P1-02 |
| P2-07 | Tool detection off the UI thread | S | — |
| P2-08 | CLI completeness | S | — |
| P2-09 | Busy feedback | S | — |
| P3-01 | Provider-driven selection | M | — |
| P3-02 | Application file-system access (ADR-010) | L | — |
| P3-03 | Shared runtime and staging helpers | M | P1-01 |
| P3-04 | Lock scope during Run/Debug | S | D2 |
| P3-05 | Encapsulated emulator registers | M | P1-04 |
| P3-06 | One version-constraint rule | S | — |
| P3-07 | Dead code | S | P2-03, P2-06 |
| P3-08 | CLI composition root | S | — |
