# PLAN.md — IDEARM project brief and handoff

> **Read this file first.** It is the entry point for any person or AI agent continuing IDEARM: vision, rules,
> architecture, current status and next steps, in a few minutes of reading. The detailed design lives in
> [`docs/technical-plan.md`](docs/technical-plan.md), every decision in [`docs/adr/`](docs/adr/), and the Phase 0
> evidence in [`spikes/REPORT.md`](spikes/REPORT.md).
>
> **Last updated:** 2026-09-22 · **Current phase:** Phase 1 of [`docs/action-plan.md`](docs/action-plan.md) is
> implemented (Windows and Linux) → **release v1.0.0 once the user approves version, git init and tag** (§9)
>
> User documentation: [`docs/user-guide.md`](docs/user-guide.md) and [`docs/troubleshooting.md`](docs/troubleshooting.md).

---

## 1. What IDEARM is

IDEARM is a desktop IDE specialized in **x86 Assembly**, aimed first at university students who write 16-bit DOS
programs (8086) with TASM or MASM and run them in DOSBox.

- **Looks like VS Code:** explorer, tabs, bottom panels (Output, Problems, Debug, Terminal), status bar, command
  palette, themes, shortcuts. VS Code is only a UX reference; nothing is copied from its internals.
- **Works like NetBeans/IntelliJ:** the user creates a *project*, picks a *target profile* (e.g. 16-bit DOS EXE)
  and a *toolchain* (e.g. TASM + TLINK), and the IDE manages assemblers, linkers, DOSBox, debuggers, paths, batch
  files and environment variables.
- **Motto:** *"Create the project, write main, run it, it works, package it."* Assembly is simplified without
  hiding how it works: every command the IDE executes is shown in the Output panel.
- **Safety:** the user's program never runs directly on the host by default; it runs inside an isolated
  environment (DOSBox staging copy, emulator or VM).

It replaces the ad-hoc Python/`.bat` orchestrators found in the user's real projects (see §8).

---

## 2. Rules every contributor (human or AI) must follow

| # | Rule | Source |
|---|---|---|
| 1 | **English everywhere:** code, identifiers, comments, docs, ADRs, scripts and their messages, sample sources, file names, commit messages. **Only the IDE UI is bilingual:** English (default) and Spanish, switchable at runtime; every UI string goes into both resource bundles. | ADR-006 |
| 2 | **No proprietary tools** (TASM, TLINK, TD, MASM, LINK, CodeView) in the repo, CI or installer, and never download them (especially not from unofficial mirrors). The IDE detects them or lets users register their own copies. | ADR-004 |
| 3 | **N-layer architecture with dependency inversion:** Presentation → Application → Domain ← Infrastructure. No JavaFX below Presentation; no I/O in the Domain; only composition roots know concrete infrastructure classes. | ADR-007 |
| 4 | **No `if assembler == TASM`:** variation goes through providers and adapters (ports implemented in Infrastructure, discovered with `ServiceLoader`). | technical-plan §10 |
| 5 | **Isolation:** the user's program runs in an isolated environment by default; assemblers and linkers may run natively because they do not execute user code. | technical-plan §13 |
| 6 | **Verify before building:** technical hypotheses must be proven and recorded (ADR or spike report) before code depends on them. Never invent facts; mark unknowns explicitly. | technical-plan |
| 7 | **No overengineering:** create a module when its first class exists; external plugins only after ≥3 real implementations of a port. | technical-plan §9, §19 |
| 8 | **Respect the user's machine:** read a file before overwriting it; ask before downloading or installing anything; do not commit or push unless asked; spike output that may contain proprietary tool logs stays in the git-ignored `spikes/out/`; never copy the user's reference projects into the repo. | Session history |
| 9 | **Keep this file current:** update §7 (status) and §9 (next steps) at the end of every work session, and record new decisions as ADRs. | — |

---

## 3. Key decisions

| Topic | Decision | Record |
|---|---|---|
| Stack | Java 25 LTS + JavaFX 25, multi-module Maven + JPMS, AtlantaFX theme | ADR-001 |
| Architecture | N-layer (Presentation, Application, Domain, Infrastructure) with dependency inversion; tool families are vertical slices inside Infrastructure | ADR-007 |
| DOS environment | **Accepted:** one DOSBox provider with dialects. **Amended 2026-09-21:** automatic order DOSBox 0.74-3 → DOSBox-X → Staging for Build, Run and Debug; builds without a window (SDL dummy drivers for 0.74-3, `-silent` for DOSBox-X); `config -securemode` after the IDE's mounts; DOSBox-X for long file names | ADR-002 |
| Project file | `idearm.toml` (logical configuration only) + `.idearm/` local state + global `%APPDATA%\IDEARM\` (tools, settings) | ADR-003 |
| Licensing | Public GitHub distribution; proprietary tools never bundled or downloaded | ADR-004 |
| Editor | **Accepted:** RichTextFX 0.11.7+ behind `EditorComponent`, per-paragraph highlighting; JavaFX incubator `CodeArea` as fallback | ADR-005 |
| Language | English artifacts; UI English (default) / Spanish | ADR-006 |
| First toolchain | v0.1: TASM + TLINK inside DOSBox · v0.2: MASM 6.11 (ML native on the host + LINK inside DOSBox) | technical-plan §0 |
| First target profile | `dos-exe-16`: x86 · 8086 baseline · 16-bit code · DOS · MZ executable · OMF objects | technical-plan §7 |
| Debugging path | v0.4 external (TD/CodeView inside DOSBox) → v0.5 own 8086 emulator with integrated panels → GDB-RSP for 32/64-bit | technical-plan §14 |

---

## 4. Architecture at a glance

```
L1  PRESENTATION    idearm-app (JavaFX, MVVM, EN/ES localization) · idearm-cli
        │ calls use cases, observes application events
L2  APPLICATION     idearm-application: CreateProject, BuildProject, RunProject, DebugProject, CleanProject, StopTask...
        │ uses model, rules and ports
L3  DOMAIN          idearm-domain: Project, TargetProfile, ToolchainSelection, BuildPlan, Diagnostic, rules, PORTS
                    idearm-language: Assembly lexer, parser, symbol index, instruction knowledge base model
        ▲ implements the ports (dependency inversion)
L4  INFRASTRUCTURE  idearm-infrastructure (TOML, files, processes, settings) · idearm-toolchain-dos (DOSBox family,
                    TASM/TLINK/TD, MASM/LINK/CV, parsers) · idearm-emu8086 (8086 emulator + debug backend)
```

Key ports (Domain) and what they separate:

| Port | Answers the question |
|---|---|
| `ToolchainProvider`, `AssemblerAdapter`, `LinkerAdapter` | Which tools build this target, and with which command lines? |
| `ToolRunner` | **Where do the tools run?** On the host (ML 6.11) or inside DOSBox (TASM, LINK)? |
| `ExecutionEnvironmentProvider`, `ExecutionSession` | **Where does the user's program run**, with which isolation? |
| `DebugEnvironmentProvider`, `DebugSession`, `BreakpointStore` | How is it debugged (external, emulator, GDB-RSP), with which capabilities? |
| `DiagnosticParser` | How is raw tool output turned into `file:line` problems? |
| `ProjectRepository`, `ToolRegistry`, `DistPackager` | Persistence of projects and tools, and packaging of `dist/` |

Logical vs physical: the project stores *what* is built, *with what* and *where it runs*; concrete tool paths and
versions on a machine are never stored in the project (technical-plan §7).

---

## 5. Repository map (2026-09-17)

```
IDEARM/
├── PLAN.md                      ← this file
├── README.md                    project overview for GitHub visitors
├── LICENSE                      MIT
├── .gitignore                   user's global template + IDEARM section
├── .github/workflows/ci.yml     tests on Windows and Linux (also called by release.yml)
├── .github/workflows/release.yml  tag-triggered: tests, then the Windows and Linux packages of a GitHub release
├── pom.xml                      parent pom (io.github.dinamo541:idearm-parent:1.0.0)
├── docs/
│   ├── user-guide.md            student guide: DOS and 32/64-bit workflows, debugger, shortcuts, CLI
│   ├── troubleshooting.md       every build/link/run/debug problem with its cause and fix
│   ├── workbench-shortcuts.md   editor and workbench key bindings
│   ├── action-plan.md           prioritized work list from the 2026-09-21 review (phases, done criteria)
│   ├── technical-plan.md        full design: analysis, requirements, domain model, architecture, roadmap, risks
│   └── adr/                     ADR-001 … ADR-008
├── idearm-domain/               L3 · Project model, profiles, planner, breakpoints, register state, memory view, ports
├── idearm-language/             L3 · Assembly lexer, parser, AST, multi-file symbol index, instruction catalog, linter
├── idearm-application/          L2 · BuildProject, RunProject, DebugProject, LintProject, ManageBreakpoints, PackageProject, CleanProject, editor query use cases
├── idearm-infrastructure/       L4 · TOML repository, FileBreakpointStore, ProcessService (Job Objects), ToolDetector, ToolRegistry, host runner and GDB/MI session
├── idearm-toolchain-dos/        L4 · Borland TASM/TLINK & Microsoft MASM/LINK, DOSBox run/debug environments, DOS dist packager, parsers
├── idearm-toolchain-nasm/       L4 · NASM assembler, GNU ld linker, their diagnostic parsers
├── idearm-emu8086/              L4 · In-process 8086 CPU emulator, ModR/M decoder, COM/MZ loader, DOS/BIOS handler, LST source map, Emu8086DebugSession
├── idearm-cli/                  L1 · Headless CLI entry point (build, run, dist, clean, import, doctor, tools add, --json), ArchUnit and module-descriptor tests
├── idearm-app/                  L1 · JavaFX workbench (editor with breakpoints gutter, debugger panel, registers, hex dump, EN/ES localization)
├── examples/                    Sample projects: hello (TASM), hello64-nasm (NASM, 64-bit Windows)
├── scripts/                     idearm.ps1 (CLI on the module path), package-native.ps1 (jpackage)
├── fixtures/                    Real TASM/TLINK/ML/LINK output (53 cases + index.csv), listings and maps for parser tests
└── spikes/                      Phase 0 throwaway experiments, outside the product build
    ├── REPORT.md                Phase 0 results and consequences for F1
    ├── asm/                     sample sources written from scratch (valid, broken, warnings, LFN test)
    ├── scripts/                 PowerShell test runners & spike helpers
    ├── java/                    single-file Java spikes: DosBoxBuildSpike (S2 prototype), ProcessTreeSpike (S6)
    ├── editor/                  S5 RichTextFX benchmark (own pom)
    ├── editor-incubator/        S5 JavaFX incubator CodeArea benchmark (own pom)
    └── out/                     generated results (git-ignored)
```

The repository implements the 4-layer N-tier architecture defined in ADR-007. Every module strictly isolates its dependencies and passes ArchUnit architecture tests.

---

## 6. Development machine facts (Windows 11 Pro)

| Item | Location / version |
|---|---|
| JDK / build | Temurin 25.0.3 · Maven 3.9.16 · jpackage 25.0.3 · WiX Toolset **not installed** |
| DOSBox-X 2026.08.31 portable (SHA-256 verified) | `%LOCALAPPDATA%\IDEARM\tools\dosbox-x-2026.08.31\bin\x64\Release\dosbox-x.exe` |
| DOSBox 0.74-3 | `C:\Program Files (x86)\DOSBox-0.74-3\dosbox.exe` |
| DOSBox Staging 0.82.2 | `C:\Codigo\Asembly\Turtoria\tools\dosbox\dosbox-staging-v0.82.2\dosbox.exe` |
| TASM 4.1 + TLINK 7.1 + TD (DPMI) | `C:\Codigo\Asembly\Turtoria\tools\tasm\bin` |
| TASM 3.2 + TLINK 3.01 + TD | `C:\Codigo\Asembly\Architecture_Project_2-main\ASSAM` |
| MASM 6.11 (ML, LINK 5.31, CodeView) | `C:\masm\MASM611\BIN` |
| MSYS2 UCRT64: NASM 3.01, GNU ld 2.46, GDB 17.2, gcc 16.1.0 | `C:\msys64\ucrt64\bin` (NASM installed 2026-09-16 with the user's approval) |
| Registered tools (`%APPDATA%\IDEARM\tools.toml`) | TASM 4.1 / TLINK 7.1 / TD from Turtoria, registered with `idearm tools add` (2026-09-17). The detector no longer contains any path of this machine. |
| User's reference projects (local tests only) | `C:\Codigo\Asembly\Mastermind` (MASM 6.11), `C:\Codigo\Asembly\Turtoria` (TASM 4.1), `C:\Codigo\Asembly\Architecture_Project_2-main` (TASM 3.2, runtime resources, `loadmap.asm` 104 KB) |

---

## 7. Roadmap and status

### GitHub documentation refresh — 2026-09-22 (Codex)

- Rebuilt the README around installation, first use, real dark/light workbench screenshots, capabilities,
  target-specific requirements, CLI examples, documentation, development and support. Kept English artifacts
  and the distinction between DOSBox isolation and native execution permissions.
- Added `CONTRIBUTING.md`, bug/feature issue forms and a pull request template. Added copies of the existing
  sample-project screenshots in `docs/images/`; original captures remain in ignored scratch space.
- GitHub's releases API returned no published releases during this session. Source installation remains the
  primary path; release download instructions are conditional. Packaging instructions follow the current script.
- Validation: 38 local links/anchors passed; both issue forms and their chooser configuration parsed as YAML;
  screenshots matched their original captures; the rendered README loaded all eight images. Documentation-only
  changes did not require running the Java test suite.

### Phase 1 of the action plan implemented — 2026-09-22 (Claude)

- Done: P1-01 … P1-07 of [`docs/action-plan.md`](docs/action-plan.md), plus two items the user added: **P1-09**
  DOSBox 0.74-3 first for Build, Run and Debug (then DOSBox-X, then Staging) with builds that never show a window,
  and **P1-10** Windows and Linux support. D3 decided: Windows-1252 for sources that are not UTF-8.
- Highlights: automatic ASCII staging folder (user names with spaces or accents), `TextDecoding` + atomic save +
  encoding badge, keyboard input and a program console for the built-in emulator (`examples/hello-input`),
  emulator faults that pause on the line (`emu.*`), `config -securemode` in every DOSBox session, validated
  `[run] cycles/memsize/args`, MI c-string quoting, Restart Debug, project-name validation, linter and hover
  fixes, `scripts/idearm.sh`, `.github/workflows/ci.yml` (Windows + Linux).
- Evidence: spike S7 (`spikes/REPORT.md`, ADR-002 Amendment 1). On Windows the suite is green and the real-tool
  tests pass (`requires-dosbox` with DOSBox 0.74-3); the Linux CI job reproduced in Docker (Ubuntu 24.04) is green
  with the real NASM, ld, GDB and DOSBox 0.74-3, and found three bugs, fixed with tests (case-only duplicate
  names in the explorer, two Windows-only test assumptions, a GDB program reading the keyboard hung the session).
- The end-to-end check with a user folder named `José Pérez` found one more: the 8.3 staging folder was taken
  for a link (`path.link.disallowed`) when the build was published. Link checks now compare
  `toRealPath(NOFOLLOW_LINKS)` with `toRealPath()` (`SafePaths`, `TomlProjectRepository`; `SafePathsTest`). With it,
  TASM builds in DOSBox 0.74-3 without a window and the program runs, exit code 0.
- Desktop smoke: with that user folder the IDE opens a Windows-1252 file with accents (`windows-1252 · CRLF`,
  bytes unchanged) and the editor, recent-history and window-control checks pass; the tooltip check needs the
  window to have focus, so it fails while someone uses another window (it passes under Xvfb). Run the smoke with
  `IDEARM_USER_DATA_DIR` pointing to a scratch folder, or it adds entries to the user's real recent history.
- Not done (needs the user's approval): version `1.0.0`, git init/publish and the `v1.0.0` tag. Still open: window
  controls on a real Linux desktop, a packaged Linux build, macOS.

### Full review and action plan — 2026-09-21 (Claude)

- Reviewed every module; `mvn test` green (410 tests, 0 failures, 1 skipped). No code was changed.
- Release blockers found (details, files and done criteria in [`docs/action-plan.md`](docs/action-plan.md)):
  the IDE does not start when `%LOCALAPPDATA%` has a space or an accent; sources that are not UTF-8 cannot be
  opened; the built-in emulator never receives keyboard input (the user guide says it does), runs away on a
  division by zero and mishandles `CBW`/`AAM`; a DOS program can escape DOSBox through `MOUNT`, and `[run]
  cycles/args` are not validated.
- Also planned: INCLUDE dependencies, debugger choice in the UI, Pause and debugger capabilities, encoding
  choices, more emulator services, lint while typing, and architecture work (provider-driven debugger selection,
  application-layer file access, shared helpers, dead code).

### DOSBox dialect selection, drag-to-top maximize, debug dialect fix — 2026-09-18 (Claude)

- **Choose the DOSBox dialect per project.** Project → Properties now shows a dropdown (Auto / DOSBox-X /
  DOSBox Staging / DOSBox 0.74) listing the dialects installed on this machine, saved to `[run] environment` in
  `idearm.toml`. `"dosbox"` still means auto-pick, so every existing project is unchanged. Run, Build and Debug
  try the chosen dialect first and fall back to auto when it is not installed. A value that is not a DOSBox id is
  ignored (guarded), so a hand-edited `environment` cannot resolve an unrelated tool as the emulator.
- **Detection fix (was blocking the above):** classic 0.74 and Staging both ship as `dosbox.exe`, so
  `ToolDetector` never gave 0.74 its own registry key and could mislabel Staging. Each dialect now keeps a
  distinct id (`dosbox-0.74`, `dosbox-staging`, `dosbox-x`); a bare `dosbox.exe` is told apart by its install
  folder name (a folder containing "staging" is Staging, otherwise 0.74). Tool Doctor shows the specific dialect.
- **Window chrome:** dragging the title bar to the top edge of the screen maximizes the window, as native windows
  do (`WindowChrome`); dragging a maximized window back down still restores it.
- **Debug** now launches in the dialect the project selected — the DOS debug path previously ignored
  `[run] environment` and used its own hard-coded DOSBox order.
- Verified: full offline `mvn test` green. New tests in `ToolDetectorTest` (classic self-key, Staging-by-folder),
  `RunProjectTest` (honours/falls back/guards a corrupt value), `Microsoft16ToolchainProviderTest` and a new
  `BorlandToolchainProviderTest` (honours/fallback/still validates the executable). Drag-to-top maximize added to
  the `IDEARM_DRAG_SMOKE` Robot smoke. The interactive combo and the drag were not run in a live GUI this session.

### Header drag correction — 2026-09-17 (Codex)

- Capture title gestures before toolbar skins consume mouse events. Holding the primary button on empty
  header space moves the window; actual buttons and menus retain their own interactions.
- Track the gesture from its initial press and stop on release. The toolbar spacer is explicitly pickable.
- Verified compilation and native-pointer hold/move/release through `IDEARM_DRAG_SMOKE=1`; the window
  follows the pointer and remains stationary after release.

### Recent history, window chrome and branding — 2026-09-17 (Codex)

- Added persistent Open Recent via File, Ctrl+R, toolbar and palette: searchable project/file filters,
  twenty entries per category, reopen without duplicate tabs, remove/clear and safe missing-path handling.
- Added vector buttons and delayed bilingual hover descriptions, accessible names and focus states.
- Integrated minimize/maximize/restore/close controls, title dragging, edge resizing, Alt+Space,
  and unsaved-file protection for all close entry points.
- Designed an original Assembly monogram and integrated SVG/PNG/ICO assets into the header, JavaFX window
  icons and native packaging configuration. No dependencies were downloaded or installed.
- Verified offline `mvn clean test`: **394 tests, 0 failures/errors, 1 skipped**. Real JavaFX smoke passed
  editor shortcuts, recent navigation, window controls, delayed hover and close cancellation. Dark/light EN/ES
  captures are in `scratch/workbench-review/`.
- Handoff: [workbench guide](docs/workbench-shortcuts.md), [branding](docs/branding.md),
  [ADR-009](docs/adr/ADR-009-workbench-history-and-window-chrome.md).

### Visual and keyboard update — 2026-09-16 (Codex)

- Implemented a VS Code-inspired dark/light workbench: compact header, activity bar, flat editor tabs,
  breadcrumbs, resizable sidebar and bottom panel, blue status bar, and coordinated Assembly colors.
- Added focus-scoped editor shortcuts, including cut/copy line with no selection, line move/duplicate,
  comment, indent, find/replace, go to line, and word wrap. Menus and palette share command metadata.
- Added sidebar/panel toggles, tab navigation, safe dirty-tab closing, Open File, Save As, and Save All chords.
- Verified all modules with offline Maven tests: 302 tests, 0 failures/errors, 1 skipped. Real JavaFX
  key-event smoke checks and dark/light EN/ES snapshots passed.
- Details, supported bindings, and handoff notes: [Workbench shortcuts](docs/workbench-shortcuts.md).

| Phase | Goal | Release | Status |
|---|---|---|---|
| F0 | Foundations and risk spikes | — | **Complete** (2026-09-14) |
| F1 | Headless core by layers: project model, TASM toolchain, diagnostics, CLI, ArchUnit layer tests | — | **Complete** (2026-09-15) |
| F2 | Run (isolated staging), Clean and dist/ in the core | — | **Complete** (2026-09-15) |
| F3 | JavaFX workbench (MVVM), editor highlighting, EN/ES infrastructure | — | **Complete** (2026-09-15) |
| F4 | UI integration, New Project wizard, properties, installer | **v0.1 (MVP)** | **Complete** (2026-09-15) |
| F5 | MASM 6.11, project import, multi-module, incremental builds | v0.2 | **Complete** (2026-09-15) |
| F5b | Hardening pass: architecture review, run isolation, reproducible builds, localized presentation | v0.2 | **Complete** (2026-09-15) |
| F6 | Intelligent Assembly editor | v0.3 | **Complete** (2026-09-15) |
| F7 | Integrated Debugger & Real-Mode Execution (TD/CodeView) + Breakpoints & Visual Panels | v0.4 | **Complete** (2026-09-15) |
| F8 | 8086 emulation engine & in-process debugger core (`idearm-emu8086`) | v0.5 | **Complete** (2026-09-15) |
| F9 | Integrated in-process debugger polish & cycle analytics | v0.5 | **Complete** (2026-09-16) |
| F10 | Educational assistant, terminal, polish | v0.6 | **Complete** (2026-09-16) |
| F11 | First 32/64-bit target: NASM + GNU ld, PE32/PE64 (ELF64 on Linux) | v0.7 | **Complete** (2026-09-16) |
| F12 | GDB/MI visual debugger backend, native packaging | v0.8 | **Complete** (2026-09-17) |
| F13 | Source-level debugging, call stack, watches, memory, release workflow | v0.9 | **Complete** (2026-09-17) |
| RC | User documentation, localized problems, full quality review | v1.0 RC | **Complete** (2026-09-17) |
| AP1 | Action plan Phase 1: release blockers, DOSBox 0.74-3 by default, Windows + Linux | v1.0 | **Complete** (2026-09-22) except the release steps |
| Next | Action plan Phases 2–4, external plugins, managed downloads, LSP/DAP, macOS | Future | — |

### Phase 0 checklist
- [x] **S1** Multi-module build on JDK 25 + JavaFX 25, smoke run with EN→ES switch, jlink image, AtlantaFX.
- [x] **S2** TASM/TLINK headless in DOSBox-X, 0.74-3 and Staging; Java `DosBoxBuildSpike` prototype.
- [x] **S2b** Response files for TASM, TLINK and LINK 5.31.
- [x] **S3** Real diagnostics, listings and maps exported to `fixtures/`.
- [x] **S4** Paths with spaces/non-ASCII characters and long file names.
- [x] **S5** Editor benchmark: RichTextFX 0.11.7 and JavaFX incubator `CodeArea`.
- [x] **S6** Process stop and Job Object against orphan processes.
- [x] **R11** Large real source (`loadmap.asm`) with TASM 3.2/4.1 and ML 6.11.
- [x] `spikes/REPORT.md`, ADR-002 and ADR-005 accepted, `[verify F0]` markers resolved in the technical plan.

### Phase 1 checklist
- [x] Multi-module Maven setup with JPMS: `idearm-domain` (L3), `idearm-application` (L2), `idearm-infrastructure` (L4), `idearm-toolchain-dos` (L4), `idearm-cli` (L1), and `idearm-app` (L1).
- [x] Enforcer JavaFX ban enforced and verified on all non-Presentation modules (`mvn enforcer:enforce`).
- [x] ArchUnit layer tests (`ArchitectureTest`) guaranteeing ADR-007 N-layer rules and dependency inversion.
- [x] Domain layer (`idearm-domain`): records (`Project`, `TargetProfile`, `ToolchainSelection`, `BuildPlan`), `FileNameRules` (8.3 strict/flexible), `CompatibilityResolver`, `BuildPlanner`, `TargetProfileCatalog`, and ports (`ProjectRepository`, `ToolRegistry`, `ToolchainProvider`, `AssemblerAdapter`, `LinkerAdapter`, `ToolRunner`, `DiagnosticParser`). 15/15 unit tests passing.
- [x] Toolchain DOS (`idearm-toolchain-dos`): Borland provider (TASM 4.1/3.2, TLINK 7.1/3.01), parsers (`TasmDiagnosticParser`, `TlinkDiagnosticParser`) verified against real fixtures in `fixtures/diagnostics/`, `PathMapper`, response file generator. 11/11 tests passing.
- [x] Infrastructure (`idearm-infrastructure`): `ProcessService` with Win32 Job Object (FFM), `DosBoxToolRunner` with ADR-002 rules (headless, `-fastlaunch`, `-exit`, ASCII mounts), `BinaryScanner` (MZ/PE/NE header inspector), `ToolDetector` (companion validation: RTM.EXE, DPMI16BI.OVL), `DefaultToolRegistry`, `TomlProjectRepository`, `FileBuildWorkspace`. 10/10 tests passing.
- [x] Application layer (`idearm-application`): `BuildProject` and `CleanProject` use cases with progress event stream, cancellation support, and fake-port unit tests (4/4 tests passing).
- [x] Presentation CLI (`idearm-cli`): `idearm build|clean|doctor <dir> [--json]`, service discovery via `ServiceLoader`, integration test on TASM/TLINK/DOSBox-X (8/8 tests passing).
- [x] 100% full reactor test suite passing (`mvn clean test` across all 7 modules).

### Phase 2 checklist
- [x] Domain layer (`idearm-domain`): `ExecutionEnvironmentProvider`, `ExecutionSession`, `LaunchSpec`, `ExitInfo`, `IsolationLevel`, `EnvCapability`, `SessionState` (`io.github.dinamo541.idearm.domain.execution`), `DistResult`, and `DistPackager` (`io.github.dinamo541.idearm.domain.dist`).
- [x] Application layer (`idearm-application`): `RunProject` (build coordination, staging, session monitoring, errorlevel retrieval, cancellation) and `PackageProject` (release target build, resource gathering, standalone bundle generation). `RunProjectTest` and `PackageProjectTest` passing (8/8 tests).
- [x] Infrastructure DOS Toolchain (`idearm-toolchain-dos`): `DosBoxExecutionEnvironmentProvider` (windowed run session, sandbox configuration, exit sentinel batch ladder), `DosBoxExecutionSession` (process tree kill, sentinel parsing), and `DosDistPackager` (portable `RUN.BAT`, `DOSBOX.CONF`, `README.TXT`). `DosDistPackagerTest` passing (14/14 tests).
- [x] Presentation CLI (`idearm-cli`): `idearm run <dir> [--keep-open]` and `idearm dist <dir> [--zip]`. `FilesystemIsolationTest` verifying host source immutability, `DistIntegrationTest` verifying standalone bundle portability.
- [x] Full reactor build passing across all 7 modules (59 tests passing, 0 failures, 0 errors).

### Phase 3 checklist
- [x] AtlantaFX theme integration: PrimerDark default, PrimerLight runtime toggle.
- [x] AssemblySyntaxHighlighter with regex token classification for instructions, registers, directives, labels, comments, numbers, strings.
- [x] RichTextFX 0.11.7 integration behind `EditorComponent` interface (ADR-005) with per-paragraph styling computation and line numbering.
- [x] MVVM ViewModels: `WorkbenchViewModel`, `ProjectExplorerViewModel`, `EditorAreaViewModel`, `EditorDocumentViewModel`, `BottomPanelViewModel`, `StatusBarViewModel`.
- [x] Workbench visual views: `WorkbenchView`, `WorkbenchMenuBar`, `WorkbenchToolBar`, `ProjectExplorerView`, `EditorAreaView`, `BottomPanelView`, `StatusBarView`.
- [x] Dynamic English and Spanish localization (i18n) with 100% key parity across `messages_en.properties` and `messages_es.properties`.
- [x] Problems panel integration with double-click navigation to file and line in editor.
- [x] Unit tests for highlighters and ViewModels (15/15 tests passing in `idearm-app`).

### Phase 4 checklist (v0.1 MVP Release)
- [x] Application layer (`idearm-application`): `CreateProject` use case with directory scaffolding, starter `MAIN.ASM`, standard `.gitignore`, and `idearm.toml` persistence (11/11 tests passing).
- [x] New Project Wizard (`NewProjectDialog`): graphical modal wizard for project name, directory selection, target profile (`dos-exe-16`), CPU (`8086`), and toolchain selection.
- [x] Clean & Build action: chained clean and build execution via menu, toolbar, and palette.
- [x] Tool Doctor (`DoctorDialog`): visual inspect table of detected host tools (DOSBox, TASM, TLINK, MASM, LINK) with versions, paths, and host kind.
- [x] Project Properties (`ProjectPropertiesDialog`): visual dialog displaying active project metadata, target profile, toolchain, run settings.
- [x] Command Palette (`CommandPaletteDialog`): VS Code-style `Ctrl+Shift+P` / `F1` fuzzy launcher for all workbench actions.
- [x] Bilingual parity for all new dialogs and actions in English and Spanish.
- [x] Full reactor clean test suite passing across all 7 modules (64 tests passing, 0 failures, 0 errors).
- [x] JavaFX automated smoke test (`$env:IDEARM_SMOKE="1"; mvn -f idearm-app/pom.xml javafx:run`) verified.
- Carried over to v0.2 / Packaging:
  - RichTextFX 0.11.7 automodule packaging: requires `--module-path` or jar patching rather than bare `jlink` image.
  - jpackage installer with WiX on Windows.
  - module name `dinamo541` terminal digit warning.

### Phase 5 checklist (v0.2 — MASM 6.11 + Multi-Module + Project Import)
- [x] Infrastructure DOS Toolchain (`idearm-toolchain-dos`):
  - `MasmAssemblerAdapter` (`ML.EXE` 6.11 native Win32 console, `/c /nologo /W2`, `/Fo<obj>`, `/Fl<lst>`, case-insensitive by default).
  - `MasmLinkerAdapter` (`LINK.EXE` 5.31 16-bit DOS in DOSBox, `/NOLOGO /ONERROR:NOEXE /BATCH`, response file with mandatory `;`).
  - `Microsoft16ToolchainProvider` (id `"microsoft-masm"`), registered in `module-info.java` and SPI services.
  - `MasmDiagnosticParser` and `MasmLinkerDiagnosticParser` (parses single and multi-module linker errors e.g. L2025 duplicate symbol).
  - `PathMapper` enhanced with `driveS` reverse mapping back to absolute project files.
- [x] Domain multi-module builds (`idearm-domain`):
  - `BuildPlanner` iterates over `sources.entry()` and `sources.modules()`, emits an `AssembleRequest` per file, and combines all `.OBJ` files into a single `LinkRequest`.
- [x] Workspace & Staging (`idearm-infrastructure` & `idearm-toolchain-dos`):
  - `FileBuildWorkspace` validates all entry and module paths.
  - `BuildWorkspace.GENERATED_MARKER` constants defined on port.
  - `HybridToolRunner` orchestrates Win32 host execution (`ML.EXE`) with DOSBox execution (`LINK.EXE`), staging intermediate `.OBJ` files safely in ASCII directories.
- [x] Application layer (`idearm-application`):
  - `ImportProject` use case discovers entry point and modules, detects TASM vs MASM directives, places `.idearm-generated` marker in existing `build`/`dist` directories, generates `idearm.toml` and `.gitignore` (7/7 tests passing).
- [x] Presentation layer (`idearm-cli` & `idearm-app`):
  - CLI: `idearm import <dir> [--toolchain <id>]`.
  - GUI: "Import Project..." menu item, command palette action, and `"microsoft-masm"` toolchain option in `NewProjectDialog`.
  - Bilingual UI parity (EN/ES) verified with `MessageBundlesTest`.
- [x] Real-world verification:
  - `Mastermind` imported and built with `microsoft-masm` 6.11; hybrid compilation verified; duplicate symbol diagnostic L2025 verified; single-module standalone build succeeded; `idearm dist` packaged into standalone bundle.
  - `Turtoria` imported with `borland-tasm` 4.1; built in DOSBox-X; packaged with `idearm dist` into standalone bundle.
- [x] Full reactor build passing across all 7 modules (114 tests passing, 0 failures, 0 errors, 0 ArchUnit violations).

### Phase 5b checklist (hardening pass over the code written so far)

Review of everything built in F1-F5, fixing what was wrong and finishing what was half-built. 147 tests passing.

**Correctness**
- [x] **Project lock is re-entrant for its owning thread** (`FileBuildWorkspace`). Run and Package hold the project
  and then delegate to a build, whose second `tryLock` reported the caller's own lock as `project.busy`: **Run and
  `idearm dist` failed whenever the executable had to be built first.** Another thread is still refused.
- [x] **The run session no longer overflows DOSBox's autoexec.** The 255-step errorlevel ladder lived in the
  `[autoexec]` section, which DOSBox copies into a 4 KiB AUTOEXEC.BAT: it aborted with
  `E_Exit: SYSTEM:Autoexec.bat file overflow` and every run reported exit code 1. The ladder now lives in
  `C:\RUN.BAT`, which autoexec calls (`call`, or the batch never returns and the emulator stays open).
- [x] **Run reports the program's exact DOS status** (verified: a program ending with `AH=4Ch, AL=7` reports 7).
- [x] **Run is owned by a Windows Job Object** through the new `ProcessLauncher` port, so the emulator dies with the
  IDE instead of being left behind (ADR-002). Verified: no `dosbox-x.exe` remains after a run.
- [x] **Every source pattern is expanded** (`SourceSet`): `modules = ["src/*.ASM"]` and `exclude` are part of the
  documented project format and were silently rejected as unsafe 8.3 names.
- [x] **Tool environments are reproducible**: processes inherit an explicit allowlist instead of the whole machine
  environment, so `INCLUDE`, `LIB`, `TASM` or `MASM` variables cannot change a build.
- [x] **Service registration completed**: `ExecutionEnvironmentProvider`, `DistPackager` and `ProcessLauncher` were
  only declared in `module-info`, so nothing was discovered on the class path (how the CLI and its tests run).
- [x] **A broken `idearm.toml` is reported** instead of silently replaced by a default project that would build
  something the user never described.
- [x] "Run (pause on exit)" overrides `keep-open` for that run; the menu entry did nothing before.
- [x] The packaged `DOSBOX.CONF` enables long file names, which programs loading resources such as
  `inv_bottom.spr` need.

**Architecture (ADR-007)**
- [x] Composition root extracted to `app.bootstrap.WorkbenchBootstrap`; `WorkbenchViewModel` receives ports through
  `WorkbenchServices` and no longer builds adapters. `PresentationArchitectureTest` enforces it, along with
  "view models never depend on views" and "view models never format user text".
- [x] `DoctorDialog` reads the same `ToolRegistry` the build uses (new `ToolRegistry.all()`) instead of detecting
  tools a second time, and reports whether each tool and its companions are still on disk.
- [x] `ProjectRepository.exists` lets the UI tell "not a project yet" from "broken project" without knowing the file name.

**Presentation**
- [x] Status text is published as a `Message` (key plus arguments) and resolved by the view, so switching language
  also retranslates what is on screen. Tooltips, severities, dialogs and errors are bilingual.
- [x] `MessageBundlesTest` now fails on a key used in code but missing from a bundle, and on text no screen shows.
- [x] Single-task guard is an atomic flag, not the `busy` property: Clean & Build could drop the build because the
  property is cleared on the JavaFX thread. Clean and build now run as one task.
- [x] Build timeout is configurable (`IDEARM_BUILD_TIMEOUT_SECONDS`), staging root stays `IDEARM_STAGING_DIR`.

**Coverage added**
- [x] `ProcessServiceTest` and `JobProcessLauncherTest` (the process layer had no tests): environment allowlist,
  timeout, cancellation, exit codes, stop and kill-on-close.
- [x] `DosBoxRunSessionTest`: autoexec under 4 KiB, `call`, the full ladder, the sentinel, staging cleanup.
- [x] `SourceSetTest`, `ServiceDiscoveryTest`, nested and cross-thread locking, presentation architecture rules.

**Verified end to end on this machine** (TASM 4.1 + DOSBox-X 2026.08.31)
- [x] `idearm build examples/hello`: 3.0 s cold, four artifacts published.
- [x] Two-module project with `include/`: both modules assembled despite the first failing, both errors mapped to
  `file:line` in the host paths, link skipped, then a clean build linking `MAIN.OBJ` and `UTILS.OBJ`.
- [x] `idearm run`: exit code 7 reported in 2.1 s, staging removed, no orphan emulator.
- [x] `idearm dist` from a clean tree: `MAIN.EXE`, `DOSBOX.CONF`, `RUN.BAT`, `README.TXT`.

### Phase 6 checklist (v0.3 — Intelligent Assembly Editor & Educational Assistant)
- [x] **Language domain layer (`idearm-language`)**:
  - Pure domain module in Layer 3 with zero JavaFX, zero I/O, zero process spawning.
  - `AssemblyLexer` & `Token`: high-speed tokenizer for MASM/TASM syntax.
  - `AssemblyParser` & AST: line-oriented parser extracting procedures, labels, data, constants, segments, includes, and instructions.
  - `ProjectSymbolIndex` & `FileSymbols`: thread-safe multi-file symbol index supporting incremental updates on edit/save.
  - `InstructionCatalog`: knowledge base of ~100 core 8086 instructions with affected flags, syntax variants, CPU requirements, and bilingual descriptions (English & Spanish).
  - `AssemblyLinter`: domain linting rules (`MissingTerminationRule`, `CpuBaselineRule`, `UndefinedSymbolRule`).
  - Unit tests: 18/18 tests passing.
- [x] **Application query use cases (`idearm-application`)**:
  - `QueryDefinition`: resolves definition location across project symbol index.
  - `QueryReferences`: gathers all references across project files.
  - `QueryCompletion`: generates contextual completions (instructions, registers, directives, symbols) filtered by CPU baseline.
  - `QueryHover`: formats bilingual instruction cards, numeric base conversions (Hex, Dec, Bin, ASCII), and symbol declarations.
  - `QueryOutline`: builds hierarchical tree of segments, procedures, and internal labels.
  - `LintSource`: executes educational linter rules.
  - Unit tests: 28/28 tests passing.
- [x] **Architecture enforcement (`idearm-cli`)**:
  - `ArchitectureTest` updated to enforce `..language..` in Layer 3 (Domain) with zero dependencies on Application, Infrastructure, Presentation, JavaFX, `Files`, or `ProcessBuilder`.
- [x] **Presentation layer (`idearm-app`)**:
  - `EditorComponent` & `RichTextFxEditorComponent`: implemented `getWordAtCaret`, `getPrefixAtCaret`, `replaceWordAtCaret`, `F12` (Definition), `Shift+F12` (References), `Ctrl+Space` (Completion), mouse hover (`MouseOverTextEvent`).
  - `HoverCardPopup`: card popup displaying instruction documentation, flag table, and numeric base conversions.
  - `CompletionPopup`: autocompletion popup dropdown with kind badges and CPU requirements.
  - `DocumentOutlineView`: sidebar tree showing document outline with click-to-jump navigation.
  - `BottomPanelView`: added References tab with table view for cross-file symbol usages.
  - Bilingual UI parity (EN/ES) verified with `MessageBundlesTest`.
  - Unit & Integration tests: 27/27 tests passing.
- [x] **Real-world project verification (`RealProjectLanguageVerificationTest`)**:
  - `Mastermind` (MASM 6.11): Go to Definition on `desigual` (line 30), Hover on `INT` (Spanish flag table), Hover on `21h` (base conversion), Document Outline (procedure `main` with nested labels), Linter warning on missing DOS exit (`4Ch / INT 21h`).
  - `Turtoria` (TASM 4.1): Symbol indexing of variable `mensaje`, Document Outline with `MAIN PROC`, clean termination verified (0 missing-exit warnings).
- [x] **Full reactor verification**: all 8 modules pass cleanly with **153 tests passing**, 0 failures, 0 errors, 0 ArchUnit violations.

### Phase 7 checklist (v0.4 — Integrated Debugger & Real-Mode Execution)
- [x] **Domain layer (`idearm-domain`)**:
  - `Breakpoint` record (file path, line number, enabled flag, immutability helpers).
  - `RegisterState` record (16-bit registers: AX, BX, CX, DX, SI, DI, BP, SP, CS, DS, ES, SS, IP, FLAGS; helper accessors for low/high 8-bit registers and individual condition code flags CF, ZF, SF, OF, PF, AF, IF, DF).
  - `MemoryView` record (base segment:offset, byte buffer, uppercase hex dump formatter, ASCII text preview).
  - `StackFrame` record (word value, SP offset, optional BP frame link).
  - `DebugLaunchSpec` record (environment, executable, debugger path, arguments, working dir, source directories, active breakpoints).
  - `DebugEvent` sealed interface (`Started`, `Paused`, `Resumed`, `Output`, `Stopped`, `Exited`).
  - Domain ports: `BreakpointStore`, `DebugSession`, `DebugEnvironmentProvider`.
  - Unit tests: `BreakpointTest`, `RegisterStateTest`, `MemoryViewTest` (27/27 tests passing).
- [x] **Infrastructure & Toolchain (`idearm-infrastructure` & `idearm-toolchain-dos`)**:
  - `ToolDetector`: Added detection for Borland Turbo Debugger (`TD.EXE` + `TDHELP.TDH`, `TDMEM.EXE`) and Microsoft CodeView (`CV.EXE` + `CVPACK.EXE`). Registered aliases `"td"`, `"turbo-debugger"`, `"cv"`, `"codeview"`.
  - `FileBreakpointStore`: JSON persistence of project breakpoints in `.idearm/breakpoints.json`.
  - `DosBoxDebugSession`: Monitors debug process exit, sentinel extraction, and staging directory cleanup.
  - `DosBoxDebugEnvironmentProvider`: Stages executable to Drive `C:`, source tree to Drive `S:` for source-level debugging, debugger tools to Drive `T:`, generates `DEBUG.BAT` with source directory switches (`TD.EXE -sdS:\;S:\SRC C:\MAIN.EXE` or `CV.EXE /S:S:\ C:\MAIN.EXE`), executes inside DOSBox under Win32 Job Object isolation.
  - Unit tests: `FileBreakpointStoreTest`, `ToolDetectorTest`, `DosBoxDebugEnvironmentProviderTest` (71 tests passing).
- [x] **Application layer (`idearm-application`)**:
  - `DebugResult` outcome record (`state`, `exitInfo`, `diagnostics`, `output`).
  - `ManageBreakpoints` use case: CRUD operations on breakpoints, file filtering, toggle logic.
  - `DebugProject` use case: coordinates debug build (`/zi` / `/Zi` and `/v` / `/CO`), tool resolution, breakpoint loading, and session monitoring.
  - Unit tests: `ManageBreakpointsTest`, `DebugProjectTest` (31/31 tests passing).
- [x] **Presentation layer (`idearm-app`)**:
  - `EditorComponent` & `RichTextFxEditorComponent`: Compound gutter with line numbers and clickable breakpoint column (`●` red active, ghost hover indicator), execution line indicator arrow (`▶`), `F9` toggle shortcut.
  - CSS styling in `editor.css`: `.idearm-compound-gutter`, `.idearm-gutter-breakpoint`, `.idearm-breakpoint-active`, `.idearm-breakpoint-ghost`, `.idearm-execution-arrow`.
  - ViewModels: `BreakpointItemViewModel`, `RegisterItemViewModel`, `DebugViewModel`.
  - Views: `DebuggerPanelView` (16-bit registers table with hex/dec formatting, condition flags badges CF/ZF/SF/OF/PF/AF/IF/DF, memory hex dump with segment:offset inputs, project breakpoints table with enabled checkboxes and double-click navigation).
  - `BottomPanelView`: added `DEBUG` tab.
  - `WorkbenchViewModel`: integrated `DebugProject`, `ManageBreakpoints`, single-task execution guard, breakpoint syncing with open editors, caret breakpoint toggling, clear all breakpoints, execution line highlighting, and status bar updates.
  - Menu, toolbar, command palette: "Start Debugging" (`F5`), "Toggle Breakpoint" (`F9`), "Clear All Breakpoints", toolbar debug button (warning outlined style).
  - Bilingual UI parity: 100% key parity in `messages_en.properties` and `messages_es.properties`, verified by `MessageBundlesTest`.
  - Unit & Integration tests: 30/30 tests passing.
- [x] **Full reactor verification**: all 8 modules pass cleanly with **180+ tests passing**, 0 failures, 0 errors, 0 ArchUnit violations.

### Phase 8 checklist (v0.5 — 8086 Emulation Engine & In-Process Debugger Core)
- [x] **Layer 4 Infrastructure Module (`idearm-emu8086`)**:
  - Registered in root `pom.xml`, module descriptor `module-info.java`, and SPI `META-INF/services/io.github.dinamo541.idearm.domain.port.DebugEnvironmentProvider`.
  - ArchUnit architecture compliance verified: zero JavaFX dependencies, properly fits Layer 4 (Infrastructure) implementing Domain ports.
- [x] **CPU Registers & 1 MB Real-Mode Memory**:
  - `CpuRegisters`: 16-bit general/pointer/index/segment registers (`AX, BX, CX, DX, SI, DI, BP, SP, CS, DS, ES, SS, IP`), low/high byte accessors (`AL/AH, BL/BH, CL/CH, DL/DH`), condition and control flags (`CF, PF, AF, ZF, SF, TF, IF, DF, OF`), domain `RegisterState` mapping.
  - `RealModeMemory`: 1,048,576 bytes physical address space, little-endian word read/write, physical address calculation `(segment << 4) + offset`, stack `push` and `pop`.
- [x] **ModR/M Decoder & 8086 Instruction Interpreter**:
  - `ModRmDecoder`: Decodes ModR/M byte with all 8 addressing modes (`[BX+SI]`, `[BX+DI]`, `[BP+SI]`, `[BP+DI]`, `[SI]`, `[DI]`, `[BP]`, `[BX]`, plus 8-bit/16-bit displacements and direct addressing), segment override prefixes (`CS, DS, ES, SS`).
  - `Cpu8086`: Fetch-decode-execute loop covering:
    - Data transfer: `MOV` (reg/mem, immediate, segment registers), `XCHG`, `LEA`, `LDS`, `LES`, `PUSH`, `POP`, `PUSHF`, `POPF`, `SAHF`, `LAHF`, `CBW`, `CWD`.
    - Arithmetic & Logic: `ADD`, `ADC`, `SUB`, `SBB`, `CMP`, `NEG`, `INC`, `DEC`, `MUL`, `IMUL`, `DIV`, `IDIV`, `AND`, `OR`, `XOR`, `NOT`, `TEST`, shifts and rotates (`SHL/SAL, SHR, SAR, ROL, ROR, RCL, RCR`).
    - BCD Adjustments: `DAA`, `DAS`, `AAA`, `AAS`, `AAM`, `AAD`.
    - Control Transfer: `JMP` (short/near/indirect), `CALL` (near/indirect), `RET` / `RETF` with optional pop bytes, conditional jumps (`JZ/JE, JNZ/JNE, JS, JNS, JC/JB, JNC/JNB, JO, JNO, JP, JNP, JL, JGE, JLE, JG, JCXZ`), `LOOP`, `LOOPZ/LOOPE`, `LOOPNZ/LOOPNE`.
    - String operations with `REP`, `REPE/REPZ`, `REPNE/REPNZ` prefixes: `MOVSB`, `MOVSW`, `STOSB`, `STOSW`, `LODSB`, `LODSW`, `CMPSB`, `CMPSW`.
    - Processor control: `CLC, STC, CMC, CLD, STD, CLI, STI, NOP, HLT, WAIT`.
    - Interrupts: `INT imm8`, `INTO`, `IRET`.
- [x] **Program Loaders & DOS/BIOS Interrupt Services**:
  - `ComLoader`: Loads flat `.COM` binaries at `0x0100` with simulated 256-byte PSP (Program Segment Prefix).
  - `MzLoader`: Parses DOS MZ EXE headers (magic `MZ`/`ZM`, header paragraphs, image size, initial `CS:IP`, `SS:SP`), applies 16-bit segment relocation table, and initializes PSP.
  - `DosInterruptHandler`:
    - `INT 21h`: AH=01h (read char), AH=02h (write char), AH=06h (direct console I/O), AH=07h/08h (unfiltered char input), AH=09h (print $-terminated string), AH=0Ah (buffered input), AH=0Bh (check input status), AH=25h (set interrupt vector), AH=30h (get DOS version 5.0), AH=35h (get interrupt vector), AH=4Ch (terminate with return code).
    - `INT 10h`: AH=0Eh (teletype output), AH=00h (set video mode), AH=0Fh (get video mode).
    - `INT 16h`: AH=00h (blocking keyboard read), AH=01h (keyboard status).
    - `INT 20h`: Program termination.
- [x] **Source Mapping & In-Process DebugSession**:
  - `SourceLocation` & `SourceMap`: Parses TASM/MASM `.LST` listing files to map between `(sourceFile, lineNumber)` and physical/relative instruction offsets.
  - `Emu8086DebugSession`: Implements `DebugSession` with initial pause at program entry point (`CS:IP`), `stepInto()`, `stepOver()` (advancing through calls and interrupts without stopping inside subroutines), `resume()` (continuous execution until breakpoint or termination), `readMemory()`, and `stack()` frames.
  - `Emu8086DebugEnvironmentProvider`: Discovered by `ServiceLoader` with backend ID `"emu8086"`.
- [x] **Application & Domain Integration**:
  - `DebugEvent.SessionAttached(session)`: Enables dynamic binding between domain sessions and presentation view models.
  - `DebugProject`: Automatically selects `emu8086` backend when configured or when external DOSBox is unavailable.
  - `MemoryView`: Added `toHexDump()` formatting helper.
- [x] **Presentation Layer Controls & Bilingual Parity (`idearm-app`)**:
  - `DebugViewModel`: Session attachment/detachment, actions for Step Over (`F10`), Step Into (`F11`), Resume/Continue (`F5`), Stop (`Shift+F5`), automatic memory refresh on pause.
  - `DebuggerPanelView`: Action toolbar with Continue, Step Over, Step Into, and Stop buttons bound to session pause/active state.
  - `WorkbenchMenuBar`: Run menu entries for Continue (`F5`), Step Over (`F10`), Step Into (`F11`).
  - Resource bundles updated with 100% EN/ES key parity, strictly validated by `MessageBundlesTest`.
- [x] **Full Reactor Verification**:
  - 9 modules, over 200 tests passing with 0 failures, 0 errors, 0 ArchUnit violations.

### Phase 8b checklist (quality pass, explorer and naming, 2026-09-16)

290 tests pass by default, plus the TASM integration tests under `-Plocal-tools`.

**Naming rule (user requirement): extensions are never upper case, and names keep the spelling the user typed.**
- [x] `FileNames` (domain) is the single convention. New projects create `src/main.asm` and declare
  `modules = ["src/*.asm"]`, so any `.asm` added to `src/` joins the build.
- [x] Build outputs are `obj/<name>.obj`, `lst/<name>.lst`, `bin/<name>.exe`, `map/<name>.map`: the source's own
  spelling with lower-case folders and extensions (`src/Game.ASM` gives `bin/Game.exe`). DOS still upper-cases
  names inside the emulator, so runners copy artifacts out under the planned names (`DosStaging.exportArtifacts`)
  instead of relying on a case-insensitive file system.
- [x] `dist/` holds `<name>.exe`, `dosbox.conf`, `run.bat`, `readme.txt`; a previous package is cleared first, and a
  `dist/` folder without the IDEARM marker is refused instead of overwritten.
- [x] Files the IDE writes for DOSBox are lower case too (`build.bat`, `run.bat`, `debug.bat`, `s001.rsp`); only
  what DOS itself creates inside the emulator (logs, `EXITCODE.TXT`) keeps DOS's upper case. That staging lives
  outside the project and is deleted after each session.
- [x] `examples/hello` uses `src/main.asm`.

**VS Code style explorer**
- [x] Header with New File, New Folder, Refresh and Collapse Folders; empty state with an Open Folder button.
- [x] Like VS Code, the tree shows every entry on disk: hidden files, dotfiles, `.git`, `.idearm`, `build/`, `dist/`
  and linked folders included (only `.git/objects` is left out of the file watcher).
- [x] Inline creation and rename in the tree. While typing, the field explains the result: an error blocks
  (existing name, reserved Windows/DOS names, invalid characters, leading/trailing spaces, `..`), a warning allows
  (name DOS tools cannot see, not 8.3), and an info line shows the stored name (`Utils.ASM` is saved as
  `Utils.asm`). `lib/io.asm` creates the folders; a trailing slash creates a folder. Enter confirms, Escape
  cancels, leaving the field keeps a valid name. Rename selects the name without its extension.
- [x] Context menu: New File, New Folder, Open, Copy Path, Copy Relative Path, Rename (F2), Delete (Delete key),
  Refresh. Delete goes to the Windows Recycle Bin (`SHFileOperationW` through FFM, never destroying silently) and
  asks before any permanent deletion.
- [x] File > New File (Ctrl+N) / New Folder and command palette entries; new files open in the editor.
- [x] The tree follows the disk: one recursive watch per project on Windows (per-folder watches keep deleted
  folders "pending delete" and broke the build's delete-and-recreate of `build/debug`), refresh keeps expanded
  folders and the selection, and the explorer reveals the active editor's file.
- [x] Ports and layers: `ProjectFiles` port (domain) · `ManageProjectFiles` use case (application) ·
  `LocalProjectFiles`, `WindowsRecycleBin`, `ProjectTreeWatcher` (infrastructure) · `ProjectExplorerViewModel`
  and `ProjectExplorerView` (presentation). Name rules live in `EntryNames` (domain).

**Connected workbench**
- [x] Renaming a file or folder moves its open tabs and its breakpoints, and updates `idearm.toml` when it is the
  entry, an explicit module or an include folder. Deleting closes unmodified tabs (a modified tab stays and saving
  recreates the file) and drops its breakpoints.
- [x] Opening a project clears the previous project's symbol index and opens the entry `idearm.toml` names; the
  index skips `build/`, `dist/`, `.git/` and `.idearm/`.
- [x] Menu, toolbar and palette agree on shortcuts (F5 debug or continue, Ctrl+F5 run, F7 build, Shift+F7 clean and
  build, F9 breakpoint, F10/F11 step). Remaining hard-coded English in dialogs and the palette is translated.
- [x] Listener leaks fixed (caret position and outline listeners were added again on every tab switch).
- [x] The editor paints plain text with the theme colour (it was black on the dark theme) and highlights with
  theme colours, so both themes stay readable.

**Correctness**
- [x] **Run, Debug and Package no longer use a stale program.** They reused any executable that existed, so after
  the first build every Run showed the old code. `FreshBuild` rebuilds whenever an artifact is missing or older
  than `idearm.toml`, a source or an include file, and forwards that build's log to the run output. Verified with
  TASM: run (exit 7), edit, run again (rebuilt, exit 9).
- [x] **The 8086 debugger maps real listings.** It looked for the listing in the wrong folder (always an empty
  source map), and TASM numbers *listing* lines, not source lines: included lines and wrapped `DB` strings shift
  every later number. `SourceMap` now matches each listing line to the source text (TASM and MASM), maps only
  code-segment instructions, and reports project-relative paths the editor can open; breakpoints stop only on
  their own instruction. Verified on a real TASM build: entry at `src/MAIN.ASM:9`, breakpoint at line 14, exit 9.
- [x] An include folder may be the source folder (`include = ["src"]`); staging copied those files twice and failed.
  Verified with a MASM 6.11 hybrid build.
- [x] Breakpoints follow renamed files and folders (`ManageBreakpoints.movePath/removePath`).

**Verification tooling**
- [x] `IDEARM_SMOKE_SNAPSHOT=<folder>` with `IDEARM_SMOKE_PROJECT=<scratch copy>` runs a scripted explorer walk
  (New File, type `Utils.ASM`, Enter) and saves screenshots; it creates a file, so point it at a copy.
- [x] Real-listing tests (`RealListingSourceMapTest`) run against `fixtures/listings` for TASM 3.2, 4.1 and ML 6.11.

---

### Phase 10 checklist (educational assistant, terminal, and polish, 2026-09-16)

**Educational Assistant & Register/Flag Diffing**
- [x] `RegisterState` (`idearm-domain`): Added `isChanged(name, previous)` and `changedRegisters(previous)` helper methods.
- [x] `DebugViewModel` (`idearm-app`): Tracks previous register and flag snapshots; exposes dynamic change indicators (`cfChangedProperty`, etc.).
- [x] `DebuggerPanelView` (`idearm-app`): Custom cell factories highlight changed registers with `-color-danger-emphasis` and bold typography; condition code badges highlight changed flags with active borders.

**Complete CPU-Baseline Linting**
- [x] `InstructionCatalog` (`idearm-language`): Cataloged 80186, 80286, and 80386 instructions (`INS`, `OUTS`, `BOUND`, `ARPL`, `LAR`, `LSL`, `MOVSX`, `MOVZX`, `BSF`, `BSR`, `BT`, `BTC`, `BTR`, `BTS`, `CWDE`, `CDQ`).
- [x] `CpuBaselineRule` (`idearm-language`): Extended lint rule to detect and warn on 32-bit registers (`EAX`, `EBX`, `ECX`, etc.) and 32-bit segment registers (`FS`, `GS`) when target CPU baseline is < 80386.

**Interactive Host Terminal**
- [x] Domain ports: `TerminalSession` and `TerminalRunner` defined in `idearm-domain.port`.
- [x] Infrastructure: `HostTerminalRunner` and `HostTerminalSession` in `idearm-infrastructure.process` spawn interactive shells (`PowerShell`/`CMD` on Windows, `bash` on Unix) owned by Windows Job Objects to guarantee no orphan processes.
- [x] Presentation: `TerminalViewModel` and `TerminalPanelView` with monospace console area, input prompt (`❯`), command history navigation (Up/Down arrow), restart, and clear controls.
- [x] Bottom panel integration: Added "Terminal" tab alongside Problems, Build, Output, References, and Debug. Accessible via Menu "View > Terminal" and shortcut `Ctrl+``, plus Command Palette.

**Advanced Command Palette**
- [x] `CommandPaletteDialog`: Categorized commands with pill badges (`[File]`, `[Build]`, `[Run]`, `[View]`, `[Help]`) and multi-term fuzzy/token search.

**Localization & Quality**
- [x] 100% EN/ES localization parity verified by `MessageBundlesTest`.
- [x] Clean 4-layer architecture verified by `PresentationArchitectureTest` and ArchUnit.

---

### Phase 11 checklist (v0.7 — First 32/64-bit target: NASM, GNU ld/gcc, PE32/PE64 & ELF64, 2026-09-16)

**Architecture & Decisions (ADR-008)**
- [x] Adopted Option C: Unified NASM toolchain with Windows PE32/PE64 and Linux ELF64 target profiles, native host tool runner, and supervised execution under Win32 Job Objects. Documented in `docs/adr/ADR-008-first-32-64-bit-target.md`.

**Domain Extensions (`idearm-domain`)**
- [x] Target profiles: `WIN_PE64_CONSOLE` (`win-pe64-console`), `WIN_PE32_CONSOLE` (`win-pe32-console`), and `LINUX_ELF64` (`linux-elf64`) cataloged in `TargetProfileCatalog`.
- [x] Extended registers: Added `Map<String, Long> extended` to `RegisterState`, with `getExtended(...)`, `isChanged(...)`, and `changedRegisters(...)` to track 32/64-bit registers.
- [x] Nullable toolchain environment in `ResolvedToolchain` for host-native toolchains.

**Language Intelligence (`idearm-language`)**
- [x] `CpuLevel.CPU_X86_64` (level 6) added to CPU hierarchy.
- [x] Added 64-bit instructions to `InstructionCatalog` (`SYSCALL`, `SYSRET`, `CQO`, `CDQE`, `MOVABS`) with bilingual documentation.
- [x] `AssemblyLexer` recognizes 64-bit registers (`RAX`–`R15`, `R8D`–`R15B`, `SIL`, `DIL`, `BPL`, `SPL`, `RIP`), 64-bit data sizes (`DQ`, `RESQ`), and NASM directives (`default rel`, `global`, `extern`, `section`, `bits`, `use64`).

**New Infrastructure Module (`idearm-toolchain-nasm`)**
- [x] Registered JPMS submodule `idearm-toolchain-nasm` in root `pom.xml`.
- [x] `NasmAssemblerAdapter`: format options `-f win64`, `-f win32`, `-f elf64`, debug formats `-gcv8` and `-g -F dwarf`.
- [x] `GnuLinkerAdapter`: links via GNU `ld` or `gcc` with subsystem and entry switches.
- [x] `NasmDiagnosticParser` and `GnuLinkerDiagnosticParser`: parses `file:line: error/warning` messages.
- [x] `NasmToolchainProvider`: discovered by `ServiceLoader` under ID `"nasm"`.

**Infrastructure & Host Execution (`idearm-infrastructure`)**
- [x] `HostProcessToolRunner`: invokes host-native tools directly without DOSBox wrappers.
- [x] `CompositeToolRunner`: cleanly dispatches tool execution between host-native tools and DOSBox/hybrid tools.
- [x] `HostExecutionEnvironmentProvider` & `HostExecutionSession`: native process execution managed by Win32 Job Objects to ensure complete process tree termination.
- [x] `NativeDistPackager`: packages native executables and resources into standalone `dist/`.
- [x] `ToolDetector`: detects `nasm`, `gcc`, `ld`, and `gdb` from system paths and MSYS2/MinGW environments.

**Presentation & Workflows (`idearm-app`, `idearm-cli`)**
- [x] `CreateProject`: Scaffolds 32-bit and 64-bit starter templates for `win-pe64-console`, `win-pe32-console`, and `linux-elf64`.
- [x] `NewProjectDialog`: target profiles, CPU `x86-64`, and `"nasm"` toolchain selections with auto-linking bindings.
- [x] `RegisterItemViewModel`: formats 16/32/64-bit values (`%016X`, `%08X`, `%04X`).
- [x] `DebugViewModel`: dynamically populates extended 32/64-bit registers from `RegisterState.extended()`.
- [x] `WorkbenchBootstrap` and `Main`: wires `CompositeToolRunner` with `HostProcessToolRunner`.
- [x] 100% EN/ES bilingual parity verified by `MessageBundlesTest`.
- [x] Sample project `examples/hello64-nasm` created.
- [x] Full reactor build passing: **325 tests passing** across 10 modules, 0 failures, 0 errors, 0 ArchUnit violations.

---

### Phase 12 checklist (v0.8 — GDB-MI Visual Debugger Backend & Native Packaging, 2026-09-17)

**Architecture & Decisions**
- [x] Adopted GDB Machine Interface (GDB/MI over stdio `gdb.exe --interpreter=mi3`) supervised by Win32 Job Objects. Avoids firewall popups, eliminates port collisions, and provides robust token-sequenced command/response processing.

**Domain Layer (`idearm-domain`)**
- [x] Extended `RegisterState`: added `fromExtended(Map<String, Long> regs)` factory method mapping 64-bit registers (`RAX`..`R15`, `RIP`) and low 16 bits to `AX`..`DX` and flags.
- [x] Tested factory creation and register mapping in `RegisterStateTest` (58/58 tests passing).

**Infrastructure Layer (`idearm-infrastructure`)**
- [x] `GdbMiRecord`: Immutable data model representing GDB/MI output (result records, exec/status/notify async records, console/target streams, prompt tokens).
- [x] `GdbMiParser`: High-performance recursive-descent parser for GDB/MI lines with guaranteed forward-progress safety to prevent infinite loops.
- [x] `GdbProcessDebugSession`: Bidirectional stdio GDB/MI debug session managing `gdb.exe --interpreter=mi3` under `WindowsJob` supervision. Atomic token sequencing, register queries (`-data-list-register-names`, `-data-list-register-values`), memory reading (`-data-read-memory-bytes`), stepping (`-exec-step-instruction`), continue (`-exec-continue`), and breakpoints (`-break-insert`).
- [x] `GdbDebugEnvironmentProvider`: SPI provider (`id="gdb"`) supporting 32-bit and 64-bit targets (`win-pe64-console`, `win-pe32-console`, `linux-elf64`). Registered in `module-info.java` and `META-INF/services/io.github.dinamo541.idearm.domain.port.DebugEnvironmentProvider`.
- [x] Unit tests: `GdbMiParserTest` (7 tests), `GdbProcessDebugSessionTest` (4 tests), `GdbDebugEnvironmentProviderTest` (1 test). (53/53 tests passing).

**Application Layer (`idearm-application`)**
- [x] `DebugProject`: Execution environment resolution automatically selects `GdbDebugEnvironmentProvider` for 32/64-bit target profiles or `"gdb"` backend.
- [x] Unit tests: `DebugProjectTest` verifying GDB session resolution for 64-bit target profiles (46/46 tests passing).

**Presentation Layer & CLI (`idearm-app`, `idearm-cli`)**
- [x] `DebuggerPanelView`: Widened register table columns (hex and dec columns set to 130px) and adjusted horizontal splitter ratios (0.36, 0.68) for full 64-bit value visibility without truncation.
- [x] `idearm-cli`: Module declaration consumes `DebugEnvironmentProvider` SPI.
- [x] `ServiceDiscoveryTest`: Verified discovery of `gdb`, `emu8086`, and `dosbox` debug environment providers via `ServiceLoader`.
- [x] Preserved 100% bilingual UI parity (`MessageBundlesTest`) and architectural compliance (`PresentationArchitectureTest`, `ArchitectureTest`).

**Native Distribution Packaging (`scripts/`)**
- [x] `scripts/package-native.ps1`: Automated PowerShell script detecting `jpackage`, checking WiX Toolset for `.msi` output or generating a standalone native portable application directory (`--type app-image`).

**Build & Verification**
- [x] Full Maven reactor passes: **362 tests passing** across all 10 modules, 0 failures, 0 errors.

---

### Phase 13 checklist (v0.9 — Source-level Debugging, Memory Visualizer & Release Packaging, 2026-09-17)

**Domain Layer (`idearm-domain`)**
- [x] `CallFrame`: Immutable record representing stack frames in call traces with `#level addr in func at file:line` display formatting.
- [x] `DebugSession`: Added `callStack(int depth)` and `evaluateExpression(String expr)` default methods to the debugging session SPI.
- [x] Unit tests: `CallFrameTest` (3 tests). (61/61 domain tests passing).

**Infrastructure Layer (`idearm-infrastructure`)**
- [x] `GdbProcessDebugSession`:
  - Prioritizes `fullname` over `file` in `*stopped` GDB/MI async event frame records for exact DWARF source file and line mapping.
  - Implements `callStack(int depth)` using GDB/MI `-stack-list-frames`.
  - Implements `evaluateExpression(String expr)` using GDB/MI `-data-evaluate-expression`.
  - Supports 64-bit flat linear memory addressing in `readMemory(int segment, int offset, int length)`.
- [x] Unit tests: Added tests in `GdbProcessDebugSessionTest` for call stack parsing, expression evaluation, and `fullname` prioritization (56/56 tests passing).

**Toolchain Alignment (`idearm-toolchain-nasm`)**
- [x] Verified `NasmAssemblerAdapter` generates `-g -F cv8` for Windows targets and `-g -F dwarf` for ELF targets. (11/11 tests passing).

**Presentation Layer & UI (`idearm-app`)**
- [x] `WorkbenchViewModel`: Enhanced `resolvePath` to normalize path separators, resolve absolute DWARF paths, and locate sources in `src/` or configured entry paths.
- [x] `WatchItemViewModel`: View model for user-defined watch expressions with reactive value properties.
- [x] `DebugViewModel`:
  - Added watch expressions management (`getWatches()`, `addWatch(...)`, `removeWatch(...)`, `refreshWatches()`).
  - Added 64-bit unsigned address parsing for memory dumps.
  - Formats call stack frames from `callStack(16)` with automatic fallback to 16-bit stack words `stack(8)`.
  - Evaluates active watch expressions on every debugger pause.
- [x] `DebuggerPanelView`:
  - Split Center Panel into a `TabPane`: Tab 1 "Memory" (address fields, hex dump area, and interactive Read button); Tab 2 "Call Stack" (monospace ListView of call stack frames).
  - Split Right Panel into a `TabPane`: Tab 1 "Breakpoints" table; Tab 2 "Watches" table (Expression and Value columns, input field with placeholder, Add button, and reactive Remove button).
- [x] 100% bilingual UI parity verified across `messages_en.properties` and `messages_es.properties`.
- [x] Unit tests: Added `DebugViewModelTest` (48/48 tests passing in idearm-app).
- [x] Preserved ADR-007 architectural constraints verified by `ArchitectureTest` and `PresentationArchitectureTest`.

**Automated Release CI Pipeline (`.github/workflows/`)**
- [x] `.github/workflows/release.yml`: GitHub Actions pipeline for tag-triggered (`v*`) and manual builds, executing `mvn clean verify`, running `package-native.ps1 -Type app-image`, archiving ZIP artifacts, and publishing GitHub Releases.

**Build & Verification**
- [x] Full Maven reactor passes: **348 tests passing** (0 failures, 0 errors, 1 skipped) across all 10 modules.

---

### v1.0 RC checklist (documentation, localization and quality review, 2026-09-17)

This pass reviewed every layer end to end with the real tools and corrected several earlier claims: the F11–F13
notes above describe what was *planned*; the items below describe what the code does now.

**Documentation**
- [x] `docs/user-guide.md`: requirements, tool registration, DOS workflow (TASM/MASM, DOSBox-X), 32/64-bit workflow
  (NASM, ld, GDB, calling conventions), visual debugger (registers, call stack, watches, memory), shortcuts, CLI,
  local state and environment variables. Written against the running application.
- [x] `docs/troubleshooting.md`: tool, pre-build, assembler, linker, run, debug and packaging problems, quoting
  output captured from TASM 4.1, TLINK 7.1, MASM 6.11, NASM 3.01 and ld 2.46.
- [x] `README.md` written (it was empty).

**Localized problems (ADR-006 open question closed)**
- [x] `DomainException` carries `arguments()` (the values its message mentions); `Diagnostic` carries them too.
  Every construction site passes its values (≈ 130 sites).
- [x] Bundles define `diagnostic.<code>` for every code in EN and ES (≈ 130 keys). The app's `Problem` value and
  `Localization.describe(...)` translate them when shown (Problems panel, status bar, explorer, Tool Doctor), falling
  back to the English sentence when a translation needs a value the problem lacks. Tool output stays in the tool's words.
- [x] Lint warnings (`lint.*`) carry arguments and are translated too.
- [x] `MessageBundlesTest` fails when a code has no translation, a translation has no code, or a text has a lone quote.
- [x] UI texts that said "in DOSBox" for every target were corrected; build failures count errors, not warnings.

**Tools**
- [x] `ToolDetector` no longer contains paths of the development machine. It searches `IDEARM_TOOL_PATH`,
  `%LOCALAPPDATA%\IDEARM\tools` (four levels), common install folders and `PATH`.
- [x] Native tools report their real version (`nasm -v`, `--version`); DOSBox-X's version is read from its Windows
  version resource; nothing invents a version any more.
- [x] Registering a folder: Tool Doctor **Add Tools Folder...**, `idearm tools add <folder>`, `ToolRegistry.registerFolder`.
  `tools.toml` keeps only registered tools (never detected ones), with their role, and is written atomically.
- [x] `DebugProject` no longer invents a GDB installation; a missing GDB is a clear `debug.gdb.missing` problem.

**Native builds and debugging (verified end to end)**
- [x] `HostProcessToolRunner` runs NASM/ld from the project folder with outputs redirected into a staging session and
  published only on success (tests rewritten for this behavior).
- [x] Debug builds for Windows assemble ELF objects with DWARF (`-f elf64|elf32 -g -F dwarf`); GDB cannot read the
  CodeView that NASM attaches to COFF. ld links them into the same PE program.
- [x] ld: `-m i386pep|i386pe`, `-e main|_main`, `--subsystem console`, `--enable-stdcall-fixup` for PE32, system DLLs
  linked directly (`-L%SystemRoot%\System32|SysWOW64 -lkernel32`).
- [x] Native programs run in their own console window (`cmd /c start "" /wait cmd /c run.cmd`), with the exit code
  returned through a sentinel file and an optional "press any key" pause.
- [x] `GnuLinkerDiagnosticParser` rewritten for real Windows output (ld prefixes lines with its full path; DWARF joins
  NASM's compilation folder and the source name). A missing entry label (ld only warns) now fails the build.
- [x] GDB session: events are delivered on their own thread (listeners asking for memory, stack or watches used to
  block the only thread that reads GDB's answers, so those panels stayed empty); only general-purpose registers are
  listed, in reading order (GDB reports 92 including FPU and pseudo registers); memory is read at flat 64-bit
  addresses; watches written as in the source (`RAX`, `[RSP+8]`, `byte [msg]`, labels) are translated to GDB syntax;
  exit codes are parsed as octal; `new-console on` gives the program its own window.
- [x] `DebugViewModel`: switches between 8086 and native register sets, applies changes on the JavaFX thread,
  queries the session off the UI thread, addresses native memory by hex or register name (default RSP/ESP), and
  DOS memory from the program's DS.
- [x] Built-in 8086 emulator: watches implemented (registers, `[BX+2]`, `byte [bp+2]`, `[DS:DX]`, numbers); panels
  fill for the first stop at the entry point.
- [x] `NativeToolchainIntegrationTest` (`requires-nasm`): a new 32-bit and 64-bit project builds, runs with exit code 0
  and stops on a source line under GDB. Real-UI checks of both native widths and of the DOS emulator also passed.

**Other corrections**
- [x] The desktop app did not start: `idearm-infrastructure` loaded `ProcessLauncher` without `uses` in its module
  descriptor (the CLI and tests run on the class path and never noticed). `ModuleDescriptorTest` now checks every module.
- [x] Educational warnings were never shown (the use case was not wired). `LintProject` adds them to Problems after
  each build of a DOS project; native projects get none (the rules describe MASM/TASM DOS programs).
- [x] `dist/`: resources keep their project layout (as in Run) and folders are copied; the zip is written inside
  `dist/` so Clean removes it.
- [x] Problems panel shows project-relative paths for every tool; the message column follows the UI language with
  the English original as a tooltip.
- [x] `scripts/idearm.ps1` named a class that does not exist; fixed and verified on the module path.
  `examples/hello64-nasm` now prints its greeting.

**Build & Verification**
- [x] `mvn install -Plocal-tools`: **388 tests, 0 failures, 0 errors, 1 skipped** (real TASM, DOSBox-X, NASM, ld, GDB).
- [x] `mvn test` (no tools, as in CI): **384 tests, 0 failures, 0 errors, 1 skipped**.
- [x] Visual smoke (`IDEARM_VISUAL_SMOKE`) and scripted UI checks of Problems (ES), Tool Doctor and the debugger panel.

---

## 8. Verified facts (do not re-investigate)

Details and numbers: [`spikes/REPORT.md`](spikes/REPORT.md).

**Reference projects**
- Mastermind uses **MASM 6.11**, not TASM. Its `build.py` builds a single file inside DOSBox, mounts the project
  root with write access, and its `--package` bundles MASM with the sources. `debug.py` calls a non-existent
  `build._cleanup`. `src/main.asm` never terminates the program; `Extras.asm` defines a second `main`.
- Turtoria downloads TASM from an unofficial mirror and uses DOSBox Staging; Architecture_Project_2 ships TASM
  binaries next to its sources and opens non-8.3 resource files (`inv_bottom.spr`) at run time.

**Tools**
- `ML.EXE` 6.11 is a Win32 console binary and runs natively (≈ 20–60 ms per file). `LINK.EXE` 5.31 is DOS-only and
  **prompts for input unless its command line ends with `;`**. Objects are OMF; executables are DOS MZ, which do
  not run on 64-bit Windows.
- Exit codes: TASM 0 = clean, 1 = errors, 2 = fatal · TLINK 1 on error · LINK 5.31 2 on error · ML 1 on error.
- **TLINK writes the .EXE even when linking fails.** Decide success by exit code, never by output existence.
- Diagnostic formats (all captured in `fixtures/diagnostics/`):
  - TASM: `**Error** S:\ERRSYM.ASM(12) Undefined symbol: PRINTNUMBER` · `**Fatal** S:\NOEND.ASM(19) Unexpected end of file encountered` · `**Fatal** Command line: Can't locate file: S:\NOFILE.ASM` · `*Warning* S:\WARN.ASM(7) Reserved word used as symbol: LENGTH` (warnings need `/w2`)
  - TLINK: `Error: Undefined symbol PRINTNUMBER in module S:\EXTERN.ASM` · `Error: HELPER defined in module S:\DUPA.ASM is duplicated in module S:\DUPB.ASM` · `Warning: No stack`
  - ML 6.11: `C:\...\ERRSYM.ASM(12): error A2006: undefined symbol : printNumber` · `C:\...\WARN.ASM(3): warning A4011: multiple .MODEL directives found : .MODEL ignored`
  - LINK 5.31: `C:\OBJ\EXTERN.OBJ(C:\...\EXTERN.ASM) : error L2029: 'printNumber' : unresolved external` · `LINK : warning L4021: no stack segment` · with response files it echoes `Object Modules [.obj]: ...` prompt lines
- **TASM does not reject 80186 instructions under `.8086`:** it silently expands them into 8086 sequences. ML 6.11
  reports A2001/A2070. The CPU-baseline lint must live in the IDE.
- MASM 6 makes every `PROC` public, so two modules with `main PROC` fail in LINK (L2025); TASM only reports symbols
  declared `PUBLIC`.
- **TASM's MASM mode and MASM 6.11 are not interchangeable:** `loadmap.asm` builds cleanly with TASM 3.2/4.1 (3.5 s
  session) but ML 6.11 reports 15 errors.
- Listings (`.LST`) from both assemblers give line number, offset and bytes per source line; MAP files give the
  segment layout and entry point.
- TASM, TLINK and ML accept LF-only line endings and UTF-8 bytes in comments and strings.
- Response files work: `TASM @FILE`, `TLINK @FILE` (one long line or `+` continuation), `LINK @FILE` (`+` continuation).

**DOSBox family**
- DOSBox-X `-silent -exit -fastlaunch` runs headless: one program assembled and linked from Java in ≈ 1.5 s.
  DOSBox 0.74-3 has no silent mode, but **`SDL_VIDEODRIVER=dummy` + `SDL_AUDIODRIVER=dummy` hide it** (TASM HELLO
  in 1.8 s, correct artifacts and exit codes). **Staging crashes with the dummy driver** (`0xC0000409`), so it cannot
  build hidden. `-noconsole` is Windows-only (S7).
- **`config -securemode` as the last `[autoexec]` line after the mounts** blocks `MOUNT`/`IMGMOUNT`/`BOOT` in 0.74-3,
  DOSBox-X 2026.08.31 and Staging 0.82.2, and later batch lines keep running (S7).
- `memsize` 1–63 works in every dialect; 0.74-3 clamps 64 to 63 (S7).
- ML 6.11 runs inside DOSBox 0.74-3 when `DOSXNT.EXE` (MASM `BIN`) is on the DOS `PATH` (S7).
- 0.74-3 and Staging both ship as `dosbox`/`dosbox.exe` without a Windows version resource; their version text
  (`DOSBox 0.74-3`, `dosbox-staging 0.82.2`) is inside the binary beyond the first 512 KB (S7).
- **Linux (Ubuntu 24.04, 2026-09-22):** apt's `dosbox` is 0.74-3; with no display at all it does not fail but runs
  without a window (SDL falls back, exit 0). A program run by GDB inherits GDB's stdin, which carries MI commands:
  give it `< /dev/null` through `-exec-arguments` (GDB starts it through a shell). JavaFX tests and the IDE run under
  `xvfb-run`; maximize/restore/minimize checks fail there even with Openbox (real desktop still to check).
- `mount X "path" -ro` is honored by DOSBox-X and Staging and **ignored by DOSBox 0.74-3**.
- Host paths with spaces and non-ASCII characters **cannot be mounted reliably** (only DOSBox 0.74-3 with an ANSI conf
  worked) → always mount an ASCII-only staging folder.
- A DOS program can open a long file name only in DOSBox-X with `[dos] lfn=true` (both the classic and the LFN API
  then work).
- **A redirection inside `IF` creates its file even when the condition is false** → use environment variables as
  flags in generated batches.
- Staging localizes shell messages to the host language → never parse DOS shell output.
- `>` redirection captures tool output and DOS program output.
- **The `[autoexec]` section is copied into a 4 KiB AUTOEXEC.BAT.** A longer one aborts the emulator with
  `E_Exit: SYSTEM:Autoexec.bat file overflow`, so anything long (the 1..255 errorlevel ladder) belongs in a batch
  file on the mounted drive. Verified 2026-09-15 with DOSBox-X 2026.08.31.
- **A batch started from autoexec without `call` never returns**, so a trailing `exit` never runs and the emulator
  stays at the DOS prompt after the program ends. Use `call NAME.BAT`, and end the batch with `exit` as well.
- The exit-code ladder reproduces a program's exact DOS status: a program ending with `AH=4Ch, AL=7` is reported
  as 7 through the `EXITCODE.TXT` sentinel.
- DOS matches file names case-insensitively, so batches and response files written in lower case (`run.bat`,
  `s001.rsp`) work in DOSBox-X, while every file DOS creates comes out upper case (`MAIN.OBJ`).

**Native toolchain (MSYS2 UCRT64, verified 2026-09-16/17)**
- MSYS2's ld 2.46 emulates only `i386pep` and `i386pe` (no ELF output) but reads `elf64-x86-64` and `elf32-i386`
  objects. It links directly against DLLs: `-LC:/Windows/System32 -lkernel32` (64-bit), `-LC:/Windows/SysWOW64`
  (32-bit). PE32 imports need `--enable-stdcall-fixup` to silence the `_ExitProcess@4` warning.
- NASM's `win32`/`win64` formats only support `cv8` debug information, which GDB 17.2 does not read
  ("No symbol table is loaded"). ELF objects with `-g -F dwarf`, linked into a PE, give GDB source lines.
- With DWARF, ld reports `<compilation folder>\/<source as given to NASM>:<line>:(.text+0x1): undefined reference`;
  NASM's compilation folder is the source's own folder, so only the part after the `/` join is a usable path.
- ld prints its full path as the message prefix (`C:\msys64\ucrt64\bin\ld.exe: ...`), and a missing entry symbol is
  only a warning; the output program then starts at the beginning of `.text`.
- `cmd /c start "" /wait cmd /c run.cmd` opens a console window but does not return the program's exit code; the
  batch writes it to a file. An access violation arrives as `-1073741819` (`0xC0000005`).
- GDB/MI reports `exit-code` in octal. `-gdb-set new-console on` gives the debuggee its own window on Windows.
  `-data-list-register-values x` lists 92 registers on amd64, including FPU, vector and pseudo registers.
- GDB/MI answers are read by one thread: a listener running on that thread must not wait for another answer.
- DOSBox-X 2026.08.31 declares its version only in its Windows version resource (`ProductVersion`), near the end of
  its 23 MB executable.
- On the module path, `ServiceLoader.load(X.class)` throws unless the calling module declares `uses X`; the class
  path does not check this.

**Assembler listings**
- **TASM's first listing column counts listing lines, not source lines.** Lines from an `INCLUDE` (marked with a
  nesting digit in column 1) and continuation lines of a long `DB` both take numbers, so later numbers drift from
  the source (real example: `MOV AX, @DATA` on source line 9 is listed as line 15). Match listing text to the
  source instead.
- TASM rewrites runs of spaces as tabs in the echoed source text, even inside string literals; compare with
  whitespace collapsed. Relocatable bytes carry markers (`0000s`, `0000r`, `0000e`).
- ML 6.11 listings print no line numbers: ` 0000  B8 4C00` for code and tab-indented text for lines without code.
- Data and code segments both start at offset 0000; only code-segment lines may be mapped to execution addresses.

**Processes**
- `Process.destroy()` stops DOSBox-X in ≈ 120 ms. Without a Job Object, DOSBox stays running when its owner dies; a
  Job Object created through Java's FFM API (`JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE`) kills it.
- Child output must always be redirected (read or discarded); inherited handles keep the parent's pipes open.
- **A file lock is per JVM, not per call:** a use case that holds the project and delegates to another one locks
  the same file twice and `tryLock` reports the caller's own lock as busy. The workspace lock is therefore
  re-entrant for the thread that owns it, and still refuses any other thread.
- A child process inherits the whole machine environment unless it is cleared: builds pass an explicit allowlist so
  `INCLUDE`, `LIB`, `TASM` or `MASM` cannot make the same project build differently on another machine.
- **On Windows, watching a folder keeps a handle on it**: a watched folder that is deleted stays "pending delete"
  and a new folder with the same name cannot be created until the watch key is cancelled. Watch the project root
  recursively (`ExtendedWatchEventModifier.FILE_TREE`) instead of each subfolder.
- `Files.move` does nothing when source and target differ only in letter case on Windows (they are the same file);
  a case-only rename needs a temporary name in between.
- **`Path.toRealPath()` expands Windows 8.3 short names**, so comparing it with `toAbsolutePath().normalize()` takes
  a short path (`C:\...\JOSPRE~1\...`) for a link. `toRealPath(LinkOption.NOFOLLOW_LINKS)` also expands them but does
  not resolve links or junctions: the two differ only when a link is on the way (verified 2026-09-22, JDK 25).
- `SHFileOperationW` with `FOF_ALLOWUNDO | FOF_WANTNUKEWARNING` sends entries to the Recycle Bin and asks before
  destroying anything it cannot recycle (x64 `SHFILEOPSTRUCTW`: 56 bytes; `wFunc` at 8, `pFrom` at 16, `fFlags` at 32).

**Java platform and editor**
- JavaFX 25 requires JDK 23+; on JDK 25 it needs `--enable-native-access=javafx.graphics` to avoid a warning.
- ResourceBundle lookups use explicit per-language bundles (no base bundle): `ResourceBundle.Control` cannot be used
  from a named module, and the JVM default locale on this machine is Spanish.
- RichTextFX 0.11.5 does not start on JavaFX 25; 0.11.7 does. With per-paragraph highlighting, typing in the 104 KB
  `loadmap.asm` takes p95 11 ms (incubator `CodeArea`: 33 ms). Re-highlighting the whole file per keystroke is too slow.
- javafx-maven-plugin 0.0.8 does not put `jfx-incubator-*` artifacts on the module path, and `javafx:run` does not
  compile (use `compile javafx:run`).

---

## 9. Next steps: v1.0.0 release and student feedback

**Release packaging (2026-09-22):** `scripts/package-native.ps1` (PowerShell 7, Windows and Linux) builds the
release files from the poms' version: MSI and portable zip, or DEB and tar.gz, into `dist/release` with a
`.sha256` beside each one. `release.yml` runs the tests, builds both systems (the DEB on Ubuntu 22.04) and
publishes the GitHub release. Keep README, `docs/user-guide.md` §1 and `docs/release-notes/` in step with it.

**Title dragging:** keep the header mouse event filters in `WindowChrome`; bubbling handlers miss events
consumed by JavaFX toolbar skins. Only arm dragging on a noninteractive title-bar press.

**Workbench handoff (2026-09-17, Codex):** recent history, hover help, integrated window controls and original
branding are implemented. Future refinements can add session restoration and configurable keybindings.
Keep persistence behind `RecentItemsStore`, preserve corrupt history until explicit reset, and route every exit
through the close-request event. Keep SVG and `BrandLogo` geometry synchronized; use `scripts/export-branding.py`
to regenerate packaged icons. See ADR-009 for current limits, including native Snap Layout hover behavior.

**RC status:** complete (2026-09-17): documentation, localized problems and the quality review above.

**Action plan (2026-09-21):** a full review found release blockers. Phase 1 of
[`docs/action-plan.md`](docs/action-plan.md) is implemented (2026-09-22, see §7); what remains of P1-08 is step 1
below. Phases 2–4 follow the release. Each item lists its files, the fix and the tests that prove it.

1. **Release:** the version is `1.0.0` and the repository is published at github.com/Dinamo541/IDEARM. Push
   `main`, make the repository public, tag `v1.0.0-rc.1` to check the published files, then tag `v1.0.0`.
   Afterwards move `main` to `1.1.0-SNAPSHOT`.
2. **Student feedback:** have a class build, run and debug the examples following `docs/user-guide.md`; turn every
   confusing step into a guide fix or an issue.
3. **Known limits to consider next:** the built-in emulator has no DOS file services or graphics output; native
   programs link only `kernel32`; on Linux the window controls are unverified on a real desktop and there is no
   packaged build; under GDB on Linux the program's input is empty; macOS is untested; the explorer shows every
   file, including `.idearm/`.

### Notes for future AI agents and contributors:
- Always preserve the N-layer architecture (ADR-007):
  - L1 (Presentation: CLI, GUI) -> L2 (Application) -> L3 (Domain) <- L4 (Infrastructure).
  - Never import JavaFX in Domain, Application, or Infrastructure.
  - Never put concrete file I/O or process spawning in the Domain.
  - In the desktop app, only `io.github.dinamo541.idearm.app.bootstrap` may name an adapter; views and view models
    receive ports (`WorkbenchServices`). `PresentationArchitectureTest` fails the build otherwise.
  - View models publish `Message` keys, never finished sentences, so both languages switch at runtime.
  - Keep all code, comments, identifiers, tests, and documentation in English.
  - UI remains 100% bilingual (EN/ES) with key parity in `messages_en.properties` and `messages_es.properties`.
  - Run `mvn clean test` across the root to verify all submodules pass before finishing any phase.

---

## 10. How to build and run

```powershell
mvn clean install                                              # product build (requires JDK 25+)
$env:IDEARM_SMOKE = '1'; mvn -f idearm-app/pom.xml javafx:run  # smoke run: shows the window, switches EN→ES, exits
Remove-Item Env:IDEARM_SMOKE; mvn -f idearm-app/pom.xml javafx:run
mvn -f idearm-app/pom.xml javafx:jlink                         # runtime image in idearm-app/target/idearm
mvn install -Plocal-tools                                      # plus the requires-tasm/-nasm/-dosbox tests with real tools
```

Visual smoke without touching your own recent history: set `IDEARM_USER_DATA_DIR` to a scratch folder together
with `IDEARM_VISUAL_SMOKE=<folder for screenshots>`.

Linux: the same Maven commands; `sh scripts/idearm.sh` is the CLI launcher. The CI Linux job
(`.github/workflows/ci.yml`) runs `xvfb-run -a mvn -B verify -DexcludedGroups=requires-tasm,requires-masm` with
apt's `nasm binutils gdb dosbox xvfb xauth`; `spikes/REPORT.md` has the Docker recipe used to reproduce it.

Spikes: every command with its parameters is listed in [`spikes/REPORT.md`](spikes/REPORT.md#how-to-reproduce).

---

## 11. Open questions for the user

- Whether to initialize git, publish the repository and tag v1.0.0 now (and bump the version from 0.1.0-SNAPSHOT).
- Whether native programs should link more libraries than `kernel32` (for example `user32` or `msvcrt`).
- The final product name ("IDEARM" may suggest the ARM architecture) and whether module names keep the GitHub
  handle (`dinamo541` triggers a javac warning).
- Whether to install WiX Toolset so jpackage can produce MSI installers (the release workflow ships an app image).
- The decisions D1–D5 in [`docs/action-plan.md`](docs/action-plan.md) §0: default DOS debugger, building while a
  program runs, charset for non-UTF-8 sources and for new DOS files, and a real console for the Terminal.
