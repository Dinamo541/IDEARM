# IDEARM — Technical Plan for an IDE Specialized in x86 Assembly

> Planning document (not an implementation). Date: 2026-09-14.
> Project convention: every artifact (code, comments, docs, scripts, commits) is written in English. The IDE user
> interface is bilingual: **English by default, switchable to Spanish** at runtime.
> Items marked **[verified]** were checked by inspecting files or binaries during the analysis.
> Items marked **[verify F0]** were technical hypotheses validated in Phase 0; they have been replaced by the findings,
> which are recorded in [`spikes/REPORT.md`](../spikes/REPORT.md).
> The short handoff brief for people and AI agents is [`PLAN.md`](../PLAN.md) at the repository root.

---

## 0. Context and confirmed decisions

**Why this plan exists.** Every Assembly project from the course carries its own improvised orchestrator
(Python + `.bat` + DOSBox `.conf` + VS Code tasks) to assemble, link, run and debug. The three local projects
reimplement the same six responsibilities, each with different defects. The goal is an IDE that turns that flow
into *create project → write code → Run → Debug → Build (dist/)* without hiding what Assembly does underneath.

**Decisions made with the user:**
1. **First toolchain (v0.1): TASM + TLINK inside DOSBox.** MASM 6.11 arrives in v0.2 and validates the
   abstraction (adding MASM must not require changes to the upper layers).
2. **Distribution: public on GitHub.** The IDE **neither bundles nor downloads** TASM/MASM (proprietary
   licenses). It detects them or lets the user register them. Only free/open-source tools may be bundled or
   downloaded.
3. **Language:** all project artifacts are in English; the IDE UI is available in English (default) and Spanish,
   switchable at runtime (ADR-006).
4. **Architecture style:** an **N-layer architecture** with four layers — Presentation, Application, Domain and
   Infrastructure — and dependency inversion between Domain and Infrastructure (§9, ADR-007).

**Material analyzed:**
- `C:\Codigo\Asembly\Mastermind.zip` (and its extracted copy): the main reference project.
- `C:\Codigo\Asembly\Turtoria` and `C:\Codigo\Asembly\Architecture_Project_2-main`: found in the same folder.
  They use TASM and change the conclusions, so they were analyzed as well.
- `C:\Codigo\Proyectos\IDEARM`: the existing Maven/JavaFX skeleton (NetBeans template).
- Attached PDF books: **could not be read** (no `pdftoppm` or PDF libraries on the machine). No conclusion is
  based on them. The digital-logic books (Mano, logic gates) do not affect the architecture.

---

## 1. Executive summary

- **Feasibility: yes.** It is feasible if three concerns that are currently mixed are separated: *where the tools
  run* (host or emulated DOS), *where the user's program runs* (isolated environment) and *how it is debugged*
  (debug backend). DOSBox covers the first two roles well for 16-bit DOS. The third one (register panels inside
  the IDE) is **not possible with the official DOSBox/DOSBox-X builds**: they expose no remote debugging protocol.
  Integrated DOS debugging therefore needs **an 8086 emulator of our own** (later phase); until then, debugging
  uses TD/CodeView inside DOSBox.
- **MVP v0.1:** Windows 11 host, *16-bit DOS EXE (8086)* profile, TASM + TLINK, DOSBox-X (also compatible with
  DOSBox 0.74-3 and Staging), New Project, explorer, editor with highlighting, Build/Run/Clean/Clean&Build/Stop,
  output console, **Problems panel with click-to-line**, minimal project properties, tool detection, basic
  `dist/` and an **English/Spanish UI switch**.
- **Recommended technology:** **Java 25 LTS + JavaFX 25** (the skeleton exists, JDK 25 and Maven are installed,
  and the user works with NetBeans), RichTextFX editor, multi-module Maven.
- **Architecture:** a **four-layer N-layer architecture** — Presentation → Application → Domain ← Infrastructure —
  where the Domain owns the ports and Infrastructure implements them. Tool families (Borland, Microsoft, DOSBox,
  emulator) are vertical slices inside the Infrastructure layer, discovered with `ServiceLoader`. The domain model
  separates logical configuration (what / with what / where) from physical installations on a machine.
- **Main risks to clear before building:** reliable headless DOS builds, the real TASM/TLINK error formats, 8.3
  names and paths with spaces or `ñ` (`G:\Mi unidad\...`), the 127-character DOS command-line limit, licensing,
  and JavaFX editor performance.

---

## 2. IDE vision

**Philosophy:** VS Code-inspired UX; NetBeans/IntelliJ-like behavior. The project is in charge: the user picks a
*target profile* and a *toolchain*, and the IDE resolves paths, mounts, flags, DOS batches and emulators.
Everything the IDE runs is **shown** in the Output tab (exact commands), so users learn without configuring
anything.

```
┌───────────────────────────────────────────────────────────────────────────────────────┐
│ File Edit View Project Run Help        [Run][Debug][Build][Clean][Clean&Build][Stop]  │
├──┬───────────────┬───────────────────────────────────────────────────┬─────────────────┤
│A │ EXPLORER      │ MAIN.ASM x   UTILS.ASM x                          │ OUTLINE         │
│c │ v HELLO       │  1  .MODEL small                                  │  main PROC      │
│t │   v src       │  2  .STACK 100h                                   │   start:        │
│i │     MAIN.ASM  │  3  .DATA                                         │ (while debugging│
│v │     UTILS.ASM │  ...                                              │  REGISTERS /    │
│. │   include     │                                                   │  FLAGS / STACK) │
│  │   resources   │                                                   │                 │
├──┴───────────────┴───────────────────────────────────────────────────┴─────────────────┤
│ PROBLEMS (1) │ OUTPUT │ BUILD │ DEBUG │ TERMINAL                                        │
│ [x] MAIN.ASM:12  Undefined symbol: PRINTNUM                   (double-click -> line 12)  │
├───────────────────────────────────────────────────────────────────────────────────────┤
│ Ready | DOS-EXE-16 · 8086 | TASM 4.1 | DOSBox-X | Ln 12, Col 5 | UTF-8 | EN             │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Analysis of the reference projects

### 3.1 Mastermind (main reference project)

**Structure [verified]:**
```
Mastermind/
├── src/main.asm, src/Extras.asm          sources (.MODEL small, INT 21h)
├── build.py                              orchestrator (23 KB): build/run/debug/doctor/package
├── .vscode/tasks.json (11 tasks), launch.json (7), debug.py, settings.json, snippets
├── bin/  MAIN.OBJ, MAIN.EXE + Build.bat, build.cmd, dosbox.conf (legacy, absolute paths)
├── herramientas/masm/  ML.EXE, LINK.EXE, DOSXNT.EXE/.386, INCLUDE/, LIB/  (copy of MASM 6.11)
├── build/, dist/  PyInstaller artifacts: Mastermind.exe (13 MB), Mastermind.sh (591 KB)
├── stdout.txt, stderr.txt                logs DOSBox 0.74-3 leaves in the working directory
└── doc/, sprites/ (empty), LICENSE, README.md (1 line), generic .gitignore
```

**Actual toolchain (it is not TASM) [verified]:**

| Piece | Evidence | Consequence |
|---|---|---|
| **ML.EXE** (MASM 6.11) | i386 PE header, console subsystem. `ML /?` ran **natively** on Windows 11: "Macro Assembler Version 6.11", exit 0 | It can assemble on the host: fast, with capturable stdout. Inside DOS it works thanks to DOSXNT |
| **LINK.EXE** | Pure MZ (`e_lfanew=0`): 16-bit DOS binary | Does **not** run on Windows x64. Needs DOSBox or an alternative linker |
| MAIN.OBJ | First record `0x80` (THEADR) | **OMF** object format |
| MAIN.EXE | MZ, 597 bytes | **DOS MZ** executable format |
| DOSBox | `C:\Program Files (x86)\DOSBox-0.74-3\dosbox.exe` | Released in 2019, unmaintained |
| Installed MASM | `C:\masm\MASM611\BIN` (includes **CV.EXE**, CodeView; no DEBUG.EXE) | CodeView only available in development mode |

**Current flows reconstructed from `build.py`, `debug.py` and `tasks.json`:**

| Action | What it really does |
|---|---|
| `python build.py` ("debug" mode) | Detects DOSBox and MASM (fixed paths or `DOSBOX`/`MASM` variables), writes `bin/_DOS.BAT` (`ml /c /Fo MAIN.OBJ ..\src\main.asm` → `link MAIN.OBJ,MAIN.EXE;` → `MAIN.EXE`) and `bin/_DOSRUN.CONF` (mounts **C: = project root** and E: = MASM; sets PATH/INCLUDE/LIB). Opens an interactive DOSBox; the user closes the window by hand and the temporary files are deleted |
| `--build` | Same, with `-exit -noconsole`: output is redirected to `BUILDLG.TXT` and a sentinel `DONE.TXT` (OK/FAIL) reports the result, then the log is printed. VS Code parses it with a `problemMatcher` (MASM regex, paths relative to `bin/`) |
| `--run` | Conf that runs the already built `MAIN.EXE` |
| `debug.py --mode cv/dosdebug/shell` | `ml /Zi` + `link /CO` + `cv`; or `debug`; or a DOS console with the tools mounted |
| `--doctor` | Prints the detected paths |
| `--package` | PyInstaller bundles **MASM + sources + build.py** into a 13 MB .exe that **re-assembles when it runs**, plus a self-extracting `.sh` for Linux |
| Legacy `bin/build.cmd` | `start dosbox -conf bin\dosbox.conf` with absolute paths `C:\Codigo\Asembly\Mastermind` and `C:\masm\MASM611`. Calls `c:\build.bat`, which does not exist at the root, and `Build.bat` expects `\Build` and `..\Main.asm`: **obsolete** |

**Defects and friction found [verified]** (each one becomes an IDE requirement):
1. `debug.py` calls `build._cleanup(...)`, which **does not exist** in `build.py` (only `_rm` does). It fails in
   the `finally` block.
2. The `dosdebug` mode needs DEBUG, which is neither in `herramientas/masm` nor in `MASM611\BIN`.
3. **One file per build.** There is no multi-module support; the "current file" tasks work around it by hand.
4. `Extras.asm` defines its own `main PROC` and `END MAIN`, so it cannot be linked together with `main.asm`
   (duplicate symbol and entry point). It also uses AX uninitialized before `DIV`.
5. `src/main.asm` **never terminates the program**: after `fin:` there is no `.EXIT` or `INT 21h/4Ch`, so the CPU
   keeps executing garbage bytes. An ideal candidate for an educational *lint*.
6. **Weak isolation:** the project root is mounted as `C:` with write access. The running program can delete or
   modify the sources.
7. Temporary files (`_DOS.BAT`, `.CONF`) are written into `bin/`: two simultaneous runs collide and a crash
   leaves garbage behind.
8. Absolute paths and obsolete scripts in `bin/`. `dist/Mastermind/src/main.asm` differs from `src/main.asm`
   (stale package).
9. Packaging **redistributes MASM 6.11** (proprietary) and needs to re-assemble on the target machine for a
   550-byte program.
10. DOSBox and MASM detection relies on fixed path lists, without version checks.
11. DOSBox 0.74-3 leaves `stdout.txt`/`stderr.txt` in the working directory.
12. `_dos_path` uses the long host path. A file such as `functions.asm` (more than 8 characters) would break inside
    DOSBox, which sees it as `FUNCTI~1.ASM`.
13. **No separation of concerns:** a single script mixes tool detection, batch generation, console prompts,
    cleanup and packaging, and `debug.py` reaches into its internals (motivation for §9).

### 3.2 Turtoria [verified]
- **TASM 4.1** (`TASM.EXE`, DOS real mode), `TASMX.EXE` (NE/DPMI), **TLINK** (NE/DPMI, needs `RTM.EXE` and
  `DPMI16BI.OVL`), **TD** "Turbo Debugger for DPMI" and TDREMOTE. They come from a TASM 5 package that `debug.py`
  **downloads from an unofficial GitHub mirror** (`qb40/tasm`), which is legally questionable.
- **DOSBox Staging 0.82.2**, downloaded automatically. Includes `dosbox_with_debugger.exe` and **DEBUG.COM** (Paul
  Vojta/Japheth, MIT-style license + public domain, **redistributable**).
- Flow: copies the source to `tools\work\main.asm` (forced name, a single file) and runs `tasm /zi` → `tlink /v` →
  `td main.exe` in DOSBox. It detects errors **by the absence of the .OBJ/.EXE**, not by errorlevel. **It rejects
  paths with spaces.**

### 3.3 Architecture_Project_2 [verified]
- **TASM 3.2 + TLINK 3.01 + TD + MAKE** copied **next to the sources**, with binaries committed to the repo.
- `loadmap.asm` (104 KB, a single file, INT 10h graphics mode) **opens resources at run time** by relative name:
  `map.map` and `*.spr`. Several names **are not 8.3** (`inv_bottom.spr`, `player_right.spr`) and
  `player_right.spr` **does not exist** in the folder. A real case to validate `resources/`, `dist/` and 8.3
  warnings.
- `tasm_assistant.py` copies the whole TASM folder into a temporary directory on every run, with a fixed DOSBox path.

### 3.4 Existing IDEARM skeleton [verified]
- Maven with `javafx-controls/fxml 25.0.2`, but `maven.compiler.release=11`. **JavaFX 25 is compiled with
  `--release 23` and requires JDK 23+**, so `release` must be raised to 25. The host has **Temurin 25.0.3** and
  **Maven 3.9.16**.
- It is the NetBeans template (`App`, `Primary/SecondaryController`, FXML, `nbactions.xml`). Nothing reusable
  beyond the startup code.

### 3.5 Conclusion
Three projects, three orchestrators solving the same things:
**(1)** detect tools, **(2)** mount DOS drives, **(3)** generate batches, **(4)** launch DOSBox, **(5)** capture
output and detect errors, **(6)** clean up and package.
That is exactly the core of the IDE, and it validates the `ToolRegistry`, `ToolRunner`, `ExecutionEnvironment` and
`DiagnosticParser` abstractions — and the need to keep them in separate layers.

---

## 4. Current flow vs. IDE flow

| # | Current manual step | Evidence | What the IDE automates | Abstraction | Version |
|---|---|---|---|---|---|
| 1 | Install or locate DOSBox; fixed paths or `DOSBOX` variable | `find_dosbox` | Detection + global registry + "Tools" page | `EnvironmentProvider.detect`, `ToolRegistry` | v0.1 |
| 2 | Locate TASM/MASM; copy tools into the project | `find_masm`, `herramientas/`, `ASSAM/` | Detection, manual registration, version read from the binary, companion files (RTM, DPMI16BI) | `ToolRegistry`, `ToolRequirement` | v0.1 TASM / v0.2 MASM |
| 3 | Mount C:/E:, PATH/INCLUDE/LIB | `_mounts`, `DEBUG.BAT` | Conf generated per session; tools mounted read-only | `DosBoxToolRunner`, `PathMapper` | v0.1 |
| 4 | Write the .BAT (assemble, link, errorlevel) | `write_build_batch` | Build plan → generated batch | `AssemblerAdapter`/`LinkerAdapter` → `ToolInvocation` | v0.1 |
| 5 | Capture the log and the DONE.TXT sentinel | `do_build` | Log and status per step | `ToolResult` | v0.1 |
| 6 | Error regex in `tasks.json` | `problemMatcher` | Per-tool parser → Problems panel → click-to-line | `DiagnosticParser`, `DiagnosticsService` | v0.1 |
| 7 | Pick "current file"; copy to `work/main.asm` | `tasks.json`, Turtoria | Project with `entry` and modules | `Sources` model | v0.1 entry / v0.2 multi-module |
| 8 | Run in DOSBox and close the window by hand | `do_run` | Run / Stop / "keep window open" / exit status | `ExecutionSession` | v0.1 |
| 9 | Debug with CV/TD/DEBUG (`/Zi`, `/CO`, `/v`) | `debug.py` ×2 | Debug configuration + external backend | `DebugBackendProvider` | v0.4 |
| 10 | DOS console with the tools | `--mode shell` | "Open project DOS console" command | `EnvironmentProvider.openShell` | v0.2 |
| 11 | `--doctor` | `build.py` | Validation when opening the project + Tools page | `ToolRegistry.health()` | v0.1 |
| 12 | Delete temporary files in `finally` | `_rm` | Own session folder with guaranteed cleanup | `SessionWorkspace` | v0.1 |
| 13 | PyInstaller with MASM + sources | `do_package` | Build → `dist/` with EXE + resources + launcher, **without the toolchain** | `DistPackager` | v0.1 |
| 14 | Snippets and associations in `.vscode` | snippets, settings | Project templates, snippets and per-dialect defaults | `TemplateCatalog`, `EditorSettings` | v0.1 / v0.3 |
| 15 | Maintain 11 tasks + 7 launch configurations | `.vscode` | Gone: fixed actions + project configuration | `CommandRegistry` + use cases | v0.1 |

**Equivalent Mastermind flow inside the IDE (v0.2):**
Open folder → *Import as IDEARM project* → the IDE detects `src/*.asm`, proposes `entry=src/main.asm` and the MASM
6.11 toolchain (found at `C:\masm\MASM611`), and marks `Extras.asm` as excluded because it defines another `main`
→ generates `idearm.toml` → **Run** → Problems warns "program without termination" (lint, v0.3).

---

## 5. Functional requirements

Priorities: **MVP** · **Next** · **Desirable** · **Advanced** · **Future**.

| ID | Requirement | Priority |
|---|---|---|
| **Projects** | | |
| FR-01 | New Project wizard: name, location, **target profile**, toolchain (filtered by compatibility), template | MVP |
| FR-02 | Standard structure + `idearm.toml` + generated `.gitignore` | MVP |
| FR-03 | Open project; recent projects; validate tools on open | MVP |
| FR-04 | Properties: General, Target, Toolchain, Sources, Run, Dist | MVP (subset) |
| FR-05 | Import an existing folder (Mastermind, Turtoria) | Next |
| FR-06 | Multi-module, include dirs, exclude a file from the build | Next |
| FR-07 | Validate 8.3 names and problematic paths for DOS targets | MVP |
| **Editor** | | |
| FR-10 | Tabs, save, dirty state, find/replace, line numbers | MVP |
| FR-11 | MASM/TASM syntax highlighting (instructions, registers, directives, numbers, strings, comments) | MVP |
| FR-12 | Outline, go to label/PROC, symbol search, references | Next |
| FR-13 | Completion (mnemonics filtered by CPU, registers by mode, directives, symbols) and snippets | Next |
| FR-14 | Hover: instruction/register documentation, EQU value, hex/dec/bin conversion | Next |
| FR-15 | Folding (PROC/ENDP, MACRO/ENDM, segments, IF/ENDIF) | Next |
| FR-16 | Live lint (basic syntax, program without termination, instruction unavailable on the CPU baseline) | Desirable |
| **Build / Run / Dist** | | |
| FR-20 | Build, Clean, Clean&Build, Run, Stop without blocking the UI | MVP |
| FR-21 | Output console showing the exact commands executed | MVP |
| FR-22 | Problems panel, click to file:line and squiggles in the editor | MVP |
| FR-23 | Run in an isolated environment (staging), "keep window open" option and exit status | MVP |
| FR-24 | `dist/` with executable + resources + launcher | MVP (basic) |
| FR-25 | Debug/Release configurations with logical flags | Next |
| FR-26 | Incremental build (fingerprints of inputs and flags) | Next |
| FR-27 | Capture the program's console output in the IDE (non-interactive programs) | Desirable |
| **Debug** | | |
| FR-30 | External debugging: TD/CodeView inside DOSBox with symbols | Next (v0.4) |
| FR-31 | Integrated debugging: breakpoints, step into/over/out, continue, stop, current line | Advanced (v0.5) |
| FR-32 | Panels: registers, flags, stack, hex memory, symbols/watch, disassembly; hex/dec/bin bases | Advanced (v0.5) |
| **Tools** | | |
| FR-40 | Global tool registry: detection, manual registration, version, SHA-256, companion files | MVP |
| FR-41 | Per-project local override (`.idearm/local.toml`, not versioned) | Next |
| FR-42 | Managed downloads **only for free tools** (DOSBox-X, DEBUG, NASM, UASM, WLINK) | Desirable |
| **Educational** | | |
| FR-50 | "Machine code" panel per line (bytes/hex taken from the assembler listing) | Next (v0.4) |
| FR-51 | "What does this instruction do?": description, affected flags, examples, minimum CPU — in English and Spanish | Desirable |
| FR-52 | Flag and register diff before/after each debug step | Advanced |
| **UI** | | |
| FR-60 | VS Code-like layout, status bar, shortcuts, light/dark themes | MVP |
| FR-61 | Command palette (built on the `CommandRegistry`) | MVP (basic) |
| FR-62 | Integrated host terminal (PTY) | Desirable |
| FR-63 | **UI language switch: English (default) / Spanish**, applied live and remembered between sessions | MVP |

---

## 6. Non-functional requirements

| ID | Requirement | Measurable criterion |
|---|---|---|
| NFR-01 Responsiveness | Never block the JavaFX thread | Build and Run run as background tasks; output batched to ≤ 20 UI updates/s |
| NFR-02 Performance | "Hello" build with TASM in headless DOSBox-X | ≤ 3 s cold (F0: 1.5 s to assemble and link in one DOSBox-X session started from Java); Run after an up-to-date build ≤ 1.5 s until the window appears |
| NFR-03 Editor | Smooth typing in large files | `loadmap.asm` (104 KB): keystroke latency < 50 ms (F0: p95 11 ms with RichTextFX 0.11.7 and per-paragraph highlighting) |
| NFR-04 Reliability | No orphan processes; Clean only deletes the project's `build/` and `dist/` | Stop terminates the whole process tree; a test proves Clean never leaves the root |
| NFR-05 Integrity | Atomic writes of `idearm.toml` (temp file + rename) | Simulated-crash test |
| NFR-06 Security | The user's program runs isolated by default; tools mounted read-only | Test: a program that tries to delete `src/` fails |
| NFR-07 Portability | OS-independent Domain and Application layers; MVP supported on Windows 11 x64 | No hard-coded `\` above Infrastructure; paths with spaces and `ñ` work |
| NFR-08 Extensibility | Adding a toolchain = a new Infrastructure module + `ServiceLoader` registration | v0.2: MASM is added **without modifying** the Presentation, Application or Domain modules |
| NFR-09 Maintainability | Layer rules respected: Domain and Application have no JavaFX and no I/O; parsers, resolvers and use cases have unit tests | The Maven module graph, JPMS exports and ArchUnit tests fail the build on a layer violation; the enforcer bans `org.openjfx` outside Presentation |
| NFR-10 Licensing | Nothing proprietary in the repo or the installer; GPL/MIT attributions if anything free is bundled | License review per release |
| NFR-11 Localization | All UI text in resource bundles; English is the default, Spanish is complete; switching updates the UI without a restart; lower layers return message codes, not text; all non-UI artifacts in English | No hard-coded UI text; a test checks both bundles define the same keys |
| NFR-12 Observability | IDE log file and "Copy diagnostic report" (versions, tools, last build) | A single file to report a bug |
| NFR-13 Keyboard | Every main action has a shortcut | F5 Run, Ctrl+F5 Debug, F11 Build, Shift+F11 Clean&Build, Shift+F5 Stop |

---

## 7. Domain model: separating logical from physical

### 7.1 Why not a single `architecture` variable
The evidence shows **two different kinds of "bitness"** that must not be mixed:
- `ML.EXE` is a **32-bit Win32 tool** that generates **16-bit code**.
- `TASM.EXE` is a **16-bit DOS tool** that can generate 32-bit code.

DOSBox also appears in **two roles**: it runs tools (TASM) and it runs the user's program. Each role has
different policies.

### 7.2 Model (Domain layer)
```
Project
├── ProjectInfo { name, version, schema }
├── TargetProfile ─────────────────────── WHAT is built (logical)
│   ├── architecture : x86                                (ISA family)
│   ├── cpuBaseline  : 8086|80186|80286|80386|80486|Pentium|x86-64   (instruction validation)
│   ├── codeMode     : 16|32|64                           (bitness of the GENERATED code)
│   ├── processorMode: real|protected|long                (derived)
│   ├── platform     : DOS|Windows|Linux|BareMetal        (target OS/ABI)
│   ├── executableFormat : COM|MZ|PE32|PE32+|ELF32|ELF64|RAW
│   └── memoryModel  : tiny|small|medium|compact|large|huge|flat   (template only; the source decides)
├── ToolchainSelection ────────────────── WITH WHAT it is built (logical)
│   ├── toolchainId  : "borland-tasm"  + versionConstraint ">=3.2"
│   ├── objectFormat : OMF|COFF|ELF                        (derived from toolchain + target)
│   └── buildConfigs : debug|release { debugInfo, listing, map, defines, extraArgs (advanced) }
├── Sources { entry, modules (globs), includeDirs, exclude }
├── Resources { globs copied next to the executable }
├── RunConfiguration ──────────────────── WHERE it runs (logical)
│   ├── environment : "dosbox"   (family; the concrete flavor is a local preference)
│   ├── isolation   : required|preferred|nativeAllowed
│   └── options     { keepOpen, cycles, memsize, args }
├── DebugConfiguration { backend : external|emu8086|gdb-rsp }
└── DistConfiguration  { launcher, zip }

Local machine ───────────────────────────── PHYSICAL (never inside idearm.toml)
├── ToolInstallation { toolId, version, path, sha256, hostKind, companions, source }
│      hostKind : WIN32_CONSOLE|WIN64|DOS_REAL|DOS_DPMI|LINUX_ELF   (property of the TOOL's binary)
├── EnvironmentInstallation { id: dosbox-x|dosbox-staging|dosbox-0.74, path, version, capabilities }
└── ResolvedToolchain = ToolchainSelection × ToolRegistry      (in memory only)
```

### 7.3 Compatibility rules (data, not `if`s)
- `cpuBaseline → codeModes`: 8086/80186/80286 → {16}; 80386–Pentium → {16, 32}; x86-64 → {16, 32, 64}.
  (The 286 has a 16-bit protected mode; it is modeled as an advanced profile, not in the MVP.)
- Each `ToolchainProvider` **declares** the `(codeMode, objectFormat, executableFormat, platform)` tuples it
  supports. Each `EnvironmentProvider` declares `(platform, codeMode)`. A `CompatibilityResolver` (a domain service)
  intersects them and the wizard only offers valid combinations.

### 7.4 Profile catalog (data file)
| Profile | Details | Priority |
|---|---|---|
| `dos-exe-16` | x86 · 8086 · 16 · DOS · MZ · small · OMF | **MVP** |
| `dos-com-16` | tiny · COM (`tlink /t`) | Next |
| `bootsector-16` | RAW 512 B · NASM `-f bin` · QEMU/Bochs | Desirable |
| `dos-32-dpmi` | 386 · 32 · DOS with extender | Advanced |
| `win-pe32-console` / `win-pe64-console` | Windows | Advanced/Future |
| `linux-elf64` | Linux | Advanced/Future |

---

## 8. Compatibility matrix

### 8.1 Assemblers
| Assembler | 16-bit | 32-bit | 64-bit | Output | Tool host | DOS | Win | Linux | License | Plan |
|---|---|---|---|---|---|---|---|---|---|---|
| **TASM 3.2/4.1** | Yes | Yes (32-bit OMF; PE with TASM 5's TASM32/TLINK32) | **No** | OMF | DOS (4.1 also TASMX DPMI) | Yes | Win16/32 | No | Proprietary | **v0.1** |
| **MASM 6.11 (ML)** | Yes | Yes (OMF/COFF) | **No** | OMF, COFF | Native Win32 **[verified]** + DOS via DOSXNT | Yes (with 16-bit LINK) | Win32 (with a 32-bit linker) | No | Proprietary | **v0.2** |
| Modern MASM (Visual Studio ml/ml64) | Not practical | ml | ml64 | COFF | Windows | No | Yes | No | Proprietary (VS/Build Tools) | Future |
| JWasm / UASM | Yes | Yes | Yes | OMF, COFF, ELF, BIN, **MZ (`-mz`, no externals)** | Win, Linux (JWasm also DOS) | Yes | Yes | Yes | OWPL (free) | Next (free MASM; useful for CI) |
| NASM | Yes | Yes | Yes | bin, obj (OMF), win32/64, elf32/64, macho | Win/Linux/macOS/DOS | Yes (with a linker, or `.COM` with `bin`) | Yes | Yes | BSD-2 | 32/64 phase |
| FASM | Yes | Yes | Yes | bin, MZ, PE, COFF, ELF (no linker) | DOS/Win/Linux | Yes | Yes | Yes | Own permissive license | Desirable |
| GNU as | Limited (`.code16`) | Yes | Yes | ELF, COFF/PE | Linux/MinGW | Not practical | Yes | Yes | GPL | Future |

> Syntax: TASM (MASM mode) ≈ MASM ≈ JWasm/UASM share most of their syntax, so the editor lexer can be shared, but they
> are **not** interchangeable: in F0, Architecture_Project_2's `loadmap.asm` built cleanly with TASM 3.2 and 4.1 while
> ML 6.11 reported 15 errors. A project stays bound to its toolchain, and lints and diagnostics are
> toolchain-specific. NASM and FASM have their own dialects, and the `.MODEL small` template does **not** apply to them.

### 8.2 Linkers
| Linker | Input | Output | Host | Note |
|---|---|---|---|---|
| TLINK 3.x | OMF | COM, MZ | DOS real mode | Architecture_Project_2 |
| TLINK 7.x | OMF | COM, MZ | DOS DPMI (needs `RTM.EXE` and `DPMI16BI.OVL`) | Turtoria |
| 16-bit LINK (MASM 6.11) | OMF | COM, MZ, NE | DOS | **Prompts interactively when the trailing `;` is missing**: invocations must end with `;` |
| Open Watcom WLINK | OMF (and others) | DOS COM/MZ, PE, ELF, … | Win/Linux/DOS | Free (OWPL): native alternative |
| MS link / lld-link / GNU ld | COFF/ELF | PE / ELF | Win / Linux | 32/64-bit targets |

### 8.3 Execution environments
| Environment | What it runs | Isolation | Integrable debugging | Status |
|---|---|---|---|---|
| DOSBox 0.74-3 | DOS 16 and 32 (extenders) | Emulation; the mounted FS is the risk surface | No | GPL; no releases since 2019; ignores `-ro` mounts (F0) |
| DOSBox Staging | Same | Same | Build with an interactive (not remote) debugger | GPL; active; no LFN observed in 0.82.2 (F0); shell messages follow the host language |
| **DOSBox-X** | Same + more machines and CPUs | Same | Interactive debugger; **GDB only in unofficial forks** | GPL; active; **LFN** (`lfn=true`), `-silent`, `-exit`, `-fastlaunch`, `-set`; honors `-ro` mounts (all verified in F0) |
| **IDEARM 8086 emulator** | DOS 16-bit subset | **Maximum**: in-process interpreter with a virtual FS | **Full** | Own (v0.5) |
| Bochs | Full PC 16/32/64 + FreeDOS/OS image | Full emulation | CLI debugger over stdin + gdbstub (build option) | LGPL |
| QEMU system | Full PC + image | Emulation/VM | **gdbstub (RSP)**; in real mode GDB needs `set architecture i8086` and manual `CS:IP` math | GPL |
| WSL2 / container | Linux ELF | Lightweight VM; **Windows interop and automount enabled by default** | gdbserver (RSP) | 32/64 phase |
| Windows Sandbox | Windows PE | Disposable VM (Win 11 Pro) | Hard | 32/64 phase |
| Native | Native PE | **None** | DbgEng/x64dbg | Only with explicit opt-in |

---

## 9. Architecture

### 9.1 Evaluation of the structure proposed by the user
| Problem | Why it matters | Correction |
|---|---|---|
| No explicit layers: views, tool orchestration, business rules and I/O can end up in the same class | That is exactly what happened in the reference scripts (Mastermind defect 13) | Explicit N-layer architecture (§9.2) |
| `Infrastructure/{Assemblers, Linkers, Emulators, Debuggers}` groups by **type** | TASM, TLINK and TD share the DOS runner, 8.3 path mapping, error format and companion files. A change to "Borland" would touch 4 folders | Group by **family/provider** (vertical slices inside the Infrastructure layer) |
| `Toolchain` contains the `Debugger` | The debugger depends on the **environment** and the target: the integrated emulator serves both TASM and MASM; GDB-RSP depends on QEMU | The debug backend is resolved from `(Target, Environment, DebugInfo)` |
| `Build System` below `Toolchain` | Inverted: the build **orchestrates** toolchain adapters | `BuildProject` use case (Application) uses `ResolvedToolchain` |
| `Execution Environment` below `Build` | Run consumes artifacts but is independent | `RunProject` is a sibling use case of `BuildProject` |
| `Editor` next to Core and UI | Mixes language intelligence (headless) with the JavaFX widget | `idearm-language` (Domain) + editor view in Presentation |
| `Plugins` from day 1 | Overengineering without 3 real implementations | Internal ports + `ServiceLoader`; external loading later |
| Missing pieces | — | `ToolRunner` (where tools run), `ProcessService`, diagnostics, `CompatibilityResolver`, `ToolRegistry`, events, settings, localization, test CLI |

### 9.2 N-layer architecture

IDEARM uses a **four-layer (N-layer) architecture with dependency inversion**. At runtime, calls flow top-down as in
any N-layer system. At compile time, Infrastructure depends on the Domain (it implements the Domain's ports)
instead of the Domain depending on Infrastructure.

```
        ┌───────────────────────────────────────────────────────────────────────┐
   L1   │ PRESENTATION    JavaFX workbench (MVVM) · CLI · localization EN/ES     │
        └───────────────────────────────────┬───────────────────────────────────┘
                                            │ calls use cases · observes application events
        ┌───────────────────────────────────▼───────────────────────────────────┐
   L2   │ APPLICATION     use cases · TaskManager · cancellation · events         │
        └───────────────────────────────────┬───────────────────────────────────┘
                                            │ uses the model, the rules and the ports
        ┌───────────────────────────────────▼───────────────────────────────────┐
   L3   │ DOMAIN          Project · TargetProfile · BuildPlan · Diagnostic ·      │
        │                 rules · Assembly language model · PORTS (interfaces)    │
        └───────────────────────────────────▲───────────────────────────────────┘
                                            │ implements the ports (dependency inversion)
        ┌───────────────────────────────────┴───────────────────────────────────┐
   L4   │ INFRASTRUCTURE  TOML · files · processes · DOSBox · TASM · MASM ·       │
        │                 diagnostic parsers · 8086 emulator                      │
        └───────────────────────────────────────────────────────────────────────┘
   The composition root of each front end (L1) wires the L4 implementations into the L2 use cases.
```

| Layer | Responsibility | Contains | May depend on | Must not |
|---|---|---|---|---|
| **L1 Presentation** | Show state, capture user intent, localize text | JavaFX views and view models (MVVM), CLI commands, resource bundles EN/ES, themes, composition roots | L2 (and read-only L3 records returned by use cases) | Call ports or infrastructure directly; contain build or project rules |
| **L2 Application** | Orchestrate use cases: sequencing, async execution, cancellation, progress, application events | `CreateProject`, `OpenProject`, `ImportProject`, `BuildProject`, `RunProject`, `DebugProject`, `CleanProject`, `StopTask`, `RegisterTool`, `UpdateSettings`, editor queries (`CompleteAt`, `HoverAt`), `TaskManager`, event bus | L3 | Use JavaFX, files, processes or DOSBox |
| **L3 Domain** | Model and rules of Assembly projects, independent of any technology | Entities and value objects (`Project`, `TargetProfile`, `ToolchainSelection`, `BuildPlan`, `Diagnostic`, session states), domain services (`CompatibilityResolver`, `BuildPlanner`, 8.3 `FileNameRules`), ports (`ProjectRepository`, `ToolRegistry`, `ToolchainProvider`, `AssemblerAdapter`, `LinkerAdapter`, `ToolRunner`, `ExecutionEnvironmentProvider`, `DebugBackendProvider`, `DistPackager`), Assembly language model (lexer, parser, symbols, instruction knowledge base model) | JDK only | Use frameworks, I/O, UI text |
| **L4 Infrastructure** | Implement the ports against the real world | `TomlProjectRepository`, tool registry storage, file system access, `ProcessService` (ProcessBuilder, Job Objects via FFM), `HostProcessToolRunner`, `DosBoxToolRunner`, DOSBox family, Borland and Microsoft16 providers with their parsers, knowledge base loading, 8086 emulator, GDB-RSP client | L3 (and ports declared by L2) | Reference Presentation; call use cases |

**Rules:**
1. A layer only calls the layer directly below it. The only relaxation: Presentation may *read* immutable domain
   records returned by use cases, to avoid duplicating them as DTOs; it never calls domain services or ports.
2. The Domain depends on nothing but the JDK: no JavaFX, no `java.nio.file.Files`, no `ProcessBuilder`, no network,
   no TOML, no DOSBox. File locations are plain value objects.
3. The Application layer depends only on the Domain. It orchestrates, but does not know how a port is implemented.
4. Infrastructure implements ports and never references Presentation or use cases.
5. Only the **composition root** of each front end (the bootstrap package of `idearm-app`, the entry point of
   `idearm-cli`) knows concrete infrastructure classes. Tool families are discovered with `ServiceLoader`
   (JPMS `uses` in the consumer, `provides` in the family module).
6. Lower layers never produce UI text: they return codes and arguments (e.g. `project.entry.missing` + path) that
   Presentation localizes into English or Spanish. Raw output from external tools passes through untouched.
7. Tool families (Borland, Microsoft16, DOSBox, emu8086) are **vertical slices inside Infrastructure**: everything
   about one family lives in one module, which keeps the "add MASM without touching the rest" goal (NFR-08).
8. A module is created when its first class exists.

**Why dependency inversion instead of a classic strict N-layer:** in the classic form the business layer calls the
data-access layer directly, so project rules would depend on DOSBox, TOML and `ProcessBuilder`; they could not be
tested without the real tools, and adding an assembler would change the business layer. Inverting that single
dependency keeps the familiar N-layer structure and call flow while making Domain and Application testable with
fakes and tool families pluggable.

### 9.3 Modules per layer (multi-module Maven + JPMS)
```
idearm/ (parent pom, groupId io.github.dinamo541)
├── L1 Presentation
│   ├── idearm-app              JavaFX workbench (MVVM), localization EN/ES, themes, composition root
│   └── idearm-cli              command-line front end (development and E2E harness), composition root
├── L2 Application
│   └── idearm-application      use cases, TaskManager, application events
├── L3 Domain
│   ├── idearm-domain           model, rules, ports (SPI)
│   └── idearm-language         Assembly language model: lexer, parser, symbol index, instruction KB model
└── L4 Infrastructure
    ├── idearm-infrastructure   TOML persistence, file system, ProcessService, settings and tool registry storage
    ├── idearm-toolchain-dos    DOSBox family, DosBoxToolRunner, 8.3 PathMapper, Borland (v0.1), Microsoft16 (v0.2)
    └── idearm-emu8086          8086 emulator + DebugBackend (v0.5)
```

| Module | Created in |
|---|---|
| `idearm-domain`, `idearm-app` | F0 (exist) |
| `idearm-application`, `idearm-infrastructure`, `idearm-toolchain-dos`, `idearm-cli` | F1 |
| `idearm-language` | F3 (lexer for highlighting), grows in F6 |
| `idearm-emu8086` | F8 |

**Compile-time dependencies:**
```
idearm-app ──┐                                   ┌── idearm-infrastructure
             ├──► idearm-application ──► idearm-domain ◄──┼── idearm-toolchain-dos
idearm-cli ──┘            │                  ▲           └── idearm-emu8086
                          └──► idearm-language ┘
Composition roots (bootstrap packages of idearm-app and idearm-cli) also depend on the Infrastructure
modules; tool families are resolved at runtime through ServiceLoader.
```

### 9.4 Conceptual model by layer
```
L1 PRESENTATION ┌──────────────────────────────────────────────────────────────────────────────────────────────┐
                │ idearm-app (JavaFX, MVVM): Workbench · Editor · Explorer · Problems/Output/Debug · Palette    │
                │ idearm-cli · Localization EN/ES                                                              │
                └──────────────┬─────────────────────── use cases ─────────────────────────▲── events ─────────┘
L2 APPLICATION  ┌──────────────▼──────────────────────────────────────────────────────────────┴─────────────────┐
                │ CreateProject · OpenProject · BuildProject · RunProject · DebugProject · CleanProject          │
                │ StopTask · RegisterTool · UpdateSettings · editor queries · TaskManager · application events   │
                └──────────────┬───────────────────────────────────────────────────────────────────────────────┘
L3 DOMAIN       ┌──────────────▼───────────────────────────────────────────────────────────────────────────────┐
                │ Project · TargetProfile · ToolchainSelection · BuildPlan · Diagnostic · session states        │
                │ CompatibilityResolver · BuildPlanner · FileNameRules (8.3) · Assembly language model           │
                │ PORTS: ProjectRepository · ToolRegistry · ToolchainProvider · AssemblerAdapter · LinkerAdapter │
                │        ToolRunner · ExecutionEnvironmentProvider · DebugBackendProvider · DistPackager         │
                └──────────────▲───────────────────────────────────────────────────────────────────────────────┘
                               │ implements the ports
L4 INFRA-       ┌──────────────┴───────────────────────────────────────────────────────────────────────────────┐
   STRUCTURE    │ TomlProjectRepository · tool registry storage · ProcessService (Job Objects via FFM)           │
                │ HostProcessToolRunner · DosBoxToolRunner · 8.3 PathMapper · DOSBox family (X / Staging / 0.74) │
                │ Borland: TASM/TLINK/TD + parsers (v0.1) · Microsoft16: ML/LINK/CV + parsers (v0.2)             │
                │ Emu8086 debug backend (v0.5) · GDB-RSP debug backend (32/64)                                   │
                └──────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 9.5 How a use case crosses the layers: Build
1. **Presentation:** the user presses F11. `WorkbenchViewModel.build()` calls the `BuildProject` use case and
   binds its task to the progress indicator.
2. **Application:** `BuildProject` loads the project through the `ProjectRepository` port, asks the
   `CompatibilityResolver` and the matching `ToolchainProvider` for a `ResolvedToolchain`, asks the `BuildPlanner`
   for a `BuildPlan`, runs its steps through the `ToolRunner` port on a background task, honors cancellation and
   publishes `BuildOutputLine`, `DiagnosticsPublished` and `BuildFinished` events.
3. **Domain:** pure decisions — is the profile valid, are the names 8.3-safe, which steps and flags make up the
   plan, what a `Diagnostic` is.
4. **Infrastructure:** `TomlProjectRepository` reads `idearm.toml`; `DosBoxToolRunner` mirrors the sources,
   generates the batch and the conf, and launches DOSBox-X through `ProcessService`; the TASM and TLINK parsers turn
   log lines into `Diagnostic` records with host paths.
5. **Presentation:** view models receive the events, marshal them to the JavaFX thread (throttled) and render
   Output and Problems in the selected UI language.

### 9.6 Enforcing the layers
- **Maven module graph:** a module cannot import classes from a module it does not depend on.
- **JPMS:** each module exports only its API packages; infrastructure internals stay unexported.
- **Maven Enforcer:** bans `org.openjfx` in every module outside Presentation.
- **ArchUnit tests** (from F1): the Domain has no dependency on Application, Infrastructure, Presentation,
  `java.nio.file.Files`, `ProcessBuilder` or JavaFX; Application has no dependency on Infrastructure or
  Presentation; only bootstrap packages depend on Infrastructure.

---

## 10. Key abstractions (sketch, not final code)

Each abstraction is labeled with its layer.

```java
// ── L2 Application: one entry point per user action, shared by the JavaFX UI and the CLI ──
interface BuildProject {
  TaskHandle<BuildResult> execute(ProjectLocation project, String configuration);  // async, cancellable, emits events
}

// ── L3 Domain ports (implemented in L4) ──
interface ProjectRepository {
  Project load(ProjectLocation location);
  void save(ProjectLocation location, Project project);
}

// Which family can build what? A family produces a COHERENT set of tools.
interface ToolchainProvider {
  String id();                                   // "borland-tasm"
  Set<TargetSupport> supports();                 // (codeMode, objFmt, exeFmt, platform) tuples
  List<ToolRequirement> requirements();          // TASM.EXE, TLINK.EXE (+RTM.EXE, DPMI16BI.OVL), optional TD.EXE
  List<ToolDetector> detectors();                // known paths, PATH, tools/ folders inside projects
  ResolvedToolchain resolve(Project project, ToolRegistry registry) throws ToolchainResolutionException;
}

interface AssemblerAdapter {                     // tool CLI ↔ logical request
  ToolInvocation assemble(AssembleRequest r);    // src, obj, lst, includes, defines, cpu, debugInfo
  DiagnosticParser diagnostics();
  HostKind host();                               // DOS_REAL → requires a DOS ToolRunner
}
interface LinkerAdapter {
  ToolInvocation link(LinkRequest r);            // objs, exe, map, libs, format (COM/MZ)
  DiagnosticParser diagnostics();
  HostKind host();
}

record ToolInvocation(String toolId, List<Arg> args, LogicalPath cwd,
                      Set<LogicalPath> inputs, Set<LogicalPath> outputs, Map<String,String> env) {}

interface ToolRunner {                           // WHERE the tool runs (not the program)
  boolean canRun(HostKind k);
  CompletableFuture<List<ToolResult>> run(List<ToolInvocation> batch, RunContext ctx, CancellationToken ct);
}

interface ExecutionEnvironmentProvider {         // WHERE the user's PROGRAM runs
  String id();                                   // "dosbox" (family)
  IsolationLevel isolation();                    // EMULATED | VIRTUALIZED | CONTAINER | NATIVE
  Set<EnvCapability> capabilities();             // WINDOW, CONSOLE_CAPTURE, EXTERNAL_DEBUGGER, GDB_RSP, SHELL
  boolean supports(TargetProfile t);
  ExecutionSession launch(LaunchSpec s);         // staging, args, keepOpen
}
interface ExecutionSession { State state(); CompletableFuture<ExitInfo> exit(); void stop(); }

interface DebugBackendProvider {
  boolean supports(TargetProfile t, String environmentId);
  Set<DebugCapability> capabilities();           // LAUNCH_ONLY | BREAKPOINTS | REGISTERS | MEMORY | DISASSEMBLY ...
  DebugSession start(DebugLaunchSpec s);
}
interface DebugSession {                         // Modeled on DAP concepts, in-process
  void setBreakpoints(SourceFile f, List<Integer> lines);
  void resume(); void pause(); void stepInstruction(); void stepOver(); void stepOut(); void terminate();
  RegisterSnapshot registers();                  // described by a RegisterSetDescriptor (X86_16/32/64)
  byte[] readMemory(Address a, int len);
  List<DisasmLine> disassemble(Address a, int count);
  void addListener(DebugEventListener l);        // stopped(reason, location), output, exited
}

interface DiagnosticParser { List<Diagnostic> parse(ToolOutput out, PathMapper paths); }

// ── L3 Domain value object: carries a code for IDE-generated messages; tool messages stay raw ──
record Diagnostic(Severity severity, String code, String message, Location location, String tool, String rawLine) {}
```

**How these abstractions answer the requirements:**
- **Toolchain:** one `ToolchainProvider` per family (implemented in L4). The project stores `toolchainId` + version;
  the `ResolvedToolchain` is assembled in memory from the real installations.
- **Assembler/Linker:** adapters translate a **logical request** (`debugInfo=true`) into concrete flags (`/zi` +
  `/v` for Borland; `/Zi` + `/CO` for MASM) and declare their `HostKind`. They never run anything themselves.
- **ToolRunner** (a new, key piece): the **same** TASM invocation runs in `DosBoxToolRunner`, while ML 6.11 runs in
  `HostProcessToolRunner`. This solves the hybrid MASM case (ML on the host, LINK in DOS) without any `if`.
- **Environment:** a `dosbox` family with **dialects** (X / Staging / 0.74) that declare their capabilities (LFN,
  `-silent`, read-only mounts). This improves on the `DOSBoxEnvironment/QemuEnvironment/VirtualMachineEnvironment/
  NativeEnvironment` list: "VirtualMachine" was too vague and becomes concrete products (WindowsSandbox, WSL,
  QemuSystem). `NativeEnvironment` exists, but only behind an explicit policy.
- **Debugger:** declared capabilities, so the UI only enables supported panels. Register panels are generated from
  a `RegisterSetDescriptor` (names, widths, FLAGS bits), with no `if` per architecture.

---

## 11. Build / Run / Debug / Clean / Stop semantics

| Action | Preconditions | Steps | Result | Cancellation |
|---|---|---|---|---|
| **Build** (F11) | Valid project and resolved toolchain | 1) Save editors (configurable) 2) Validate: entry exists, 8.3 names, paths 3) Release `BuildPlan`: assemble each module → link 4) Parse diagnostics 5) On success: copy resources and generate `dist/` | `build/release/*` + `dist/` + Problems | Stop kills the headless session; the build is marked "cancelled" and `dist/` is not written |
| **Clean** | — | Delete **only** `build/` and `dist/` under the root (marked with `.idearm-generated`) | Empty folders | Instant |
| **Clean & Build** | — | Clean → Build | Same as Build | Same as Build |
| **Run** (F5) | — | 1) **Incremental** Debug build in `build/debug/` 2) On errors: stop and show Problems 3) Staging: copy EXE + resources to `%LOCALAPPDATA%\IDEARM\run\<session>` (ASCII path) 4) Generate conf: mount **only** the staging folder as C:, no tools 5) Launch DOSBox with a window 6) On exit: status (sentinel) → Output | DOSBox window + "Program finished" | Stop closes DOSBox (process tree) |
| **Debug** (Ctrl+F5) | Backend available | Debug build with symbols/listing/map → `DebugBackend` (v0.4 TD/CV inside DOSBox with read-only tools; v0.5 integrated emulator) | Debug session | Stop terminates the session |
| **Stop** (Shift+F5) | An active task exists | Build: cancel the runner · Run: `session.stop()` · Debug: `terminate()` | Idle state | — |

Each action is an Application use case (`BuildProject`, `CleanProject`, `RunProject`, `DebugProject`, `StopTask`)
invoked identically by the JavaFX UI and the CLI.

**`DosBoxToolRunner` details (derived from the evidence):**
- **One DOSBox session per build** (one session per tool would be slow): `dosbox-x -silent -exit -fastlaunch -conf
  build.conf`, verified in F0 (≈ 1.5 s to assemble and link one program). DOSBox 0.74-3 and Staging have no silent
  mode, so their window flashes.
- Mounts: `T:` tools (read-only when the dialect supports it), `S:` mirror of the sources, `C:` output and logs.
  Sources are **always copied** to an ASCII-only staging folder: F0 showed that host paths with spaces and non-ASCII
  characters cannot be mounted reliably. DOSBox-X confs set `[dos] lfn=true` so programs can open long file names.
- Generated batch, per step: `> C:\LOG\Snn.LOG`, then an errorlevel ladder (`if errorlevel N set RC=N`) written to
  `C:\LOG\Snn.RC`, and finally `DONE`. Errorlevels are reliable (TASM 0/1/2, TLINK 1, LINK 5.31 2). Two F0 findings
  shape the batch: a redirection inside `IF` creates its file even when the condition is false (flags are
  environment variables instead), and **TLINK writes the .EXE even when linking fails** (success is decided by the
  errorlevel, and stale outputs are deleted before building).
- **127-character DOS command-line limit:** response files (`@FILE.RSP`), verified in F0 for TASM, TLINK (one long
  line or `+` continuation lines) and LINK 5.31 (which echoes its prompts into the log).
- 60 s timeout: if a tool waits for input (e.g. LINK without `;`), the session is killed and a "the tool was
  waiting for input" diagnostic is reported.
- Build and Run use **two separate sessions** (diagnostic structure + isolation). A combined session is only
  considered if F0 shows unacceptable latency.

---

## 12. Generating `dist/`

**Real constraint:** a **DOS MZ executable does not run natively on 64-bit Windows** (there is no NTVDM on x64). A
"ready to run" `dist/` on modern machines needs a launcher and an emulator.

```
dist/
├── MAIN.EXE              ← the program, built in Release (no debug info)
├── MAP.MAP, *.SPR ...    ← resources copied next to the EXE (the program opens them by relative path)
├── DOSBOX.CONF           ← RELATIVE conf: mounts "." as C: and runs MAIN.EXE
├── RUN.BAT / run.sh      ← finds an installed DOSBox-X/DOSBox and launches it with the conf
└── README.TXT            ← how to run it on real DOS, FreeDOS or DOSBox
   (optional, Desirable) dosbox-x/  ← bundled portable runtime, with the GPL LICENSE and a source link
```
- A `DistPackager` port with one implementation per `(platform, executableFormat)`: `DosDistPackager` (v0.1);
  `WindowsPePackager` and `LinuxElfPackager` later.
- The toolchain and the sources are **never** included (the opposite of Mastermind's `--package`).
- Validations: 8.3 resource names (warning for `inv_bottom.spr`), and strings in the sources that reference
  missing files (`player_right.spr`, Desirable lint). Option `zip = true` → `dist/<name>-<version>.zip`.

---

## 13. Security and isolation

1. **Principle:** isolate whatever runs **the user's code**. Assemblers and linkers do not execute the user's code:
   they may run on the host (ML 6.11) or in emulated DOS (TASM). They are still third-party binaries, so their
   SHA-256 is recorded and the IDE warns if a known binary changes.
2. **Per-project policy** (`run.isolation`): `required` (default) · `preferred` · `nativeAllowed`. Native mode
   requires explicit confirmation with a warning, remembered per project.
3. **DOSBox as a sandbox:** the emulated CPU does not execute instructions on the host; the real surface is the
   **mounted file system** and the devices. Therefore: mount **only** a staging copy, never the project root
   (Mastermind defect 6) or a whole drive; tools read-only; networking (NE2000/IPX/slirp), serial ports and printer
   disabled in the generated conf.
4. **32/64-bit:** Linux ELF in WSL2 or a container **with interop and automount disabled**, or QEMU; Windows PE in
   Windows Sandbox (it runs; debugging is hard) or native with opt-in. To be honest: full isolation with debugging
   for Windows targets is expensive and gets its own ADR.
5. **Downloads:** only free tools, from fixed official URLs, with version and SHA-256. Never mirrors of
   proprietary software (like the `qb40/tasm` mirror Turtoria uses).

---

## 14. Debugging strategy per target

| Level | Target | Mechanism | Panels in the IDE | Version |
|---|---|---|---|---|
| **L0 external** | DOS 16 | TD (Borland), CV (MASM) or the free DEBUG.COM inside DOSBox, with `/zi /v` or `/Zi /CO` | No (the debugger UI lives in the DOSBox window) | v0.4 |
| **L1 integrated** | DOS 16 | **Own 8086 emulator** in Java: CPU, MZ/PSP loader, INT 21h (console and files), INT 10h (text and mode 13h), INT 16h. `SourceMap` built from the real assembler's **listing (.LST) + map (.MAP)** | **All**: breakpoints, stepping, registers, flags, stack, memory, disassembly, symbols | v0.5 |
| L2 full fidelity | DOS 16/32 | GDB-RSP against QEMU+FreeDOS or a DOSBox-X fork with gdbserver (unofficial) | All (same `DebugSession`) | Advanced |
| L2 | Boot sector / protected mode | GDB-RSP against QEMU (`-s -S`) or the Bochs gdbstub; in real mode, addresses are `CS*16+IP` | All | Advanced |
| L2 | Linux x86-64 | gdbserver in WSL2/container → our own RSP client (no GDB install needed) | All | Future |
| — | Windows PE | Native with DbgEng or a debugger inside a sandbox | Decided in an ADR | Future |

(These debugging levels L0–L2 are unrelated to the architecture layers L1–L4.)

**Why an own emulator for integrated debugging** (and not DOSBox): the official DOSBox builds have no remote
debugging API. TD and CodeView symbol formats are proprietary or poorly documented, whereas LST and MAP are text
files both assemblers generate. An 8086 interpreter is bounded, testable with per-instruction test-vector suites
(e.g. *SingleStepTests* 8088/8086, **check license and coverage in F8**), instant, cross-platform and the safest
option. **Honest limit:** programs that depend on exact hardware behavior (timers, sound, advanced VGA) will still
run in DOSBox. There is a go/no-go decision at the end of F8.

---

## 15. Specialized editor and educational assistant

- **Language model in `idearm-language`** (Domain layer, no UI): per-dialect lexer (MASM/TASM first) →
  line-oriented parser (label, directive, mnemonic, operands, comment) → project symbol index (labels, PROC, MACRO,
  EQU, typed variables, segments, STRUC, INCLUDE resolution). Editor queries (completion, hover, references) are
  Application use cases; the editor widget lives in Presentation.
- **Instruction knowledge base (data, original content):** per mnemonic, the syntax forms, valid operands,
  affected flags (O D I T S Z A P C: modified/undefined/set/clear), minimum CPU, a description **in English and
  Spanish**, and examples. One asset feeds completion, hover, signature help, the CPU-baseline lint (`PUSH imm` and
  `SHL reg, imm>1` require an 80186) and the "What does this instruction do?" panel. The text is written from
  scratch (not copied from the Intel SDM). The model lives in the Domain; loading the data files is Infrastructure.
- **"Machine code" panel (v0.4, cheap and exact):** built from the real assembler's **listing file** (`tasm /l`,
  `ml /Fl`), which already contains the offset and bytes of each line: `source → bytes → hex → binary`. A live
  encoder of our own is Future.
- Educational lints derived from real defects: "program without termination" (Mastermind), "DS not initialized
  before INT 21h/09h", "two `END label` entry points in modules linked together" (Extras.asm), "register used
  before initialization" (Advanced, needs flow analysis).
- Encoding: files are saved as UTF-8 **without BOM**, with a warning when `DB` strings contain non-ASCII characters
  in DOS targets (they would render incorrectly in CP437). TASM 3.2/4.1 and ML 6.11 accept UTF-8 bytes in
  comments and strings, and LF-only line endings (F0).
- Project templates (e.g. the generated `MAIN.ASM`) use comments in the UI language selected when the project is
  created.

---

## 16. Persistent configuration

**Format: TOML (`idearm.toml`)**: readable, supports comments, familiar from Cargo or pyproject. It is read and
written by `TomlProjectRepository` (Infrastructure, Jackson `jackson-dataformat-toml`) behind the
`ProjectRepository` port, with a `schema` field and migrations. Saving from the properties dialog rewrites the file
in canonical form (accepted limitation: hand-written comments are not preserved).

```toml
schema = 1

[project]
name    = "HELLO"
version = "0.1.0"

[target]
profile = "dos-exe-16"          # x86 · DOS · MZ · 16-bit code
cpu     = "8086"                # instruction baseline: lint + template directive

[toolchain]
id      = "borland-tasm"
version = ">=3.2"

[sources]
entry   = "src/MAIN.ASM"
modules = ["src/*.asm"]         # each module is assembled into its own .OBJ
include = ["include"]
exclude = []

[resources]
files   = ["resources/**"]      # copied next to the .EXE for Run and dist

[build.debug]
debug-info = true
listing    = true
map        = true

[build.release]
debug-info = false

[run]
environment = "dosbox"          # family; the concrete flavor (X/Staging/0.74) is a local preference
isolation   = "required"
keep-open   = true

[debug]
backend = "external"            # v0.5: "emu8086"

[dist]
launcher = true
zip      = false
```

| **Stored** in the project | **Derived** (not stored) | **Local** to the machine (not versioned) |
|---|---|---|
| profile, CPU baseline, toolchain id and version, entry/modules/include/exclude, resources, logical flags per configuration, environment and options, debug backend, dist options, `extra-args` (advanced) | bitness, object/executable format, .OBJ/.EXE names, command lines, DOSBox confs, mounts, 8.3 mapping, tool paths | `.idearm/local.toml` (installation override, DOSBox flavor), `.idearm/workspace.json` (tabs, breakpoints); global `%APPDATA%\IDEARM\tools.toml` and `%APPDATA%\IDEARM\settings.toml` (UI language, theme) |

**Project Properties categories** (trimmed): *General* · *Target* (profile, CPU) · *Toolchain* (family, resolved
installation, link to Tools; the **linker is not a category of its own**, it is determined by the toolchain and
can only be changed under Advanced when the formats are compatible) · *Sources and directories* · *Build*
(Debug/Release, Next) · *Execution* (environment, isolation, keep open) · *Debug* (v0.4) · *Dist* · *Advanced*
(`extra-args`, timeouts, tool environment variables).
"Environment" and "Execution" are merged into *Execution*.

---

## 17. External tool management

**Strategy E (a combination), phased:**
| Mechanism | Details | Version |
|---|---|---|
| Automatic detection | Known paths (`C:\masm\MASM611`, `C:\TASM`, Program Files `DOSBox*`), PATH and `tools/` folders inside projects. **The version is identified by reading strings from the binary, without running it** (proven: "Turbo Assembler Version 4.1", "Turbo Link Version 3.01") | v0.1 |
| Manual registration | Folder picker; validates companion files and computes SHA-256 | v0.1 |
| Global registry | `%APPDATA%\IDEARM\tools.toml`: several installations per tool | v0.1 |
| Selection | The project asks for `id` + version range; the highest compatible or locally preferred installation is used | v0.1 |
| Per-project override | `.idearm/local.toml` (e.g. use `Turtoria/tools/tasm`) without versioning paths | Next |
| Managed downloads | Free tools only: DOSBox-X, DEBUG.COM, NASM, UASM/JWasm, WLINK. Pinned version, SHA-256, visible license | Desirable |
| Bundled with the installer | Nothing in the MVP. Later, a portable DOSBox-X complying with the GPL | Desirable |
| TASM/MASM | **Never** bundled or downloaded; the IDE explains how to register an own copy | Always |
| Updates | No automatic tool updates; a warning when the version does not satisfy the range | — |

Layer mapping: detection rules and version ranges are Domain; `RegisterTool` is an Application use case; detectors
that scan the disk and the registry file are Infrastructure.

---

## 18. Technology

### 18.1 Comparison for THIS project (1 = poor, 5 = excellent)
| Criterion | Java 25 + JavaFX | C#/.NET + Avalonia | C++ + Qt 6 | Electron + TS | Tauri 2 + TS/Rust |
|---|---|---|---|---|---|
| Code editor | 3 (RichTextFX; Monaco in a WebView possible) | 4 (AvaloniaEdit) | 3 (QScintilla) | **5** (Monaco) | **5** (Monaco) |
| VS Code-like modern UI | 3 (CSS; AtlantaFX helps) | 4 | 4 | **5** | **5** |
| Terminal | 3 (pty4j + JediTerm/xterm.js) | 3 | 3 | **5** (xterm.js + node-pty) | 4 |
| External processes | **5** (ProcessHandle, virtual threads) | **5** | 4 | 4 | **5** |
| 8086 emulator / parsers (CPU) | 4 | **5** | **5** | 3 | **5** |
| Cross-platform | **5** | 4 | **5** | **5** | 4 (WebView differences) |
| Distribution / size | 3 (jpackage ~60–100 MB; WiX for MSI) | 4 | 3 | 2 (≥150 MB, RAM) | **5** |
| Plugins | 4 (ServiceLoader, ModuleLayer) | 4 | 3 | **5** | 3 |
| Localization | 4 (ResourceBundle, live bindings) | 4 | 4 | 4 | 4 |
| Layered architecture support | 5 (Maven modules + JPMS + ArchUnit) | 5 (projects + NetArchTest) | 3 | 3 | 3 |
| **Current team** (Java/NetBeans, existing skeleton) | **5** | 2 | 1 | 2 | 1 |
| Long-term maintainability | 4 | 4 | 2 | 3 | 3 |

*Eclipse Theia / Code-OSS* would reach visual parity fastest, but it means adopting VS Code's internal architecture,
which the user explicitly rejects, at a considerable weight.

### 18.2 Recommendation: **Java 25 LTS + JavaFX 25**
**Why:** most of the value lives below the UI (processes, DOSBox, parsers, emulator, project model), where Java is
excellent and fully testable. Maven modules and JPMS make the N-layer boundaries physical. The team already works
with Java/NetBeans, the skeleton and JDK 25 exist, jpackage covers distribution and `ServiceLoader` covers plugins.
**Acknowledged weakness:** the editor and terminal are inferior to Monaco/xterm.js. **Mitigation:** an
`EditorComponent` interface (RichTextFX 0.11.7 in the MVP; the JavaFX incubator `CodeArea` as
fallback), validated by spike S5 with the real `loadmap.asm` (ADR-005). If visual parity with VS Code were the dominant criterion and the team knew
TypeScript, Electron/Tauri would win: that is not the case for this project.

**Concrete stack:** multi-module Maven · JPMS · JavaFX 25 with MVVM in Presentation · RichTextFX 0.11.7+ (editor; 0.11.5 does not start on JavaFX 25) ·
AtlantaFX (theme, evaluated in S1) · `ResourceBundle` properties (UTF-8) for the UI languages · Jackson +
`jackson-dataformat-toml` · JUnit 5 + AssertJ · ArchUnit (layer rules) · TestFX (optional UI smoke tests) ·
`System.Logger` · Java 25: `record`/`sealed` for the domain and events, virtual threads for process I/O, the
**FFM API** for Windows Job Objects (kill the process tree even if the IDE crashes) · jpackage for the installer.
No DI framework: a manual composition root per front end. No docking framework: fixed `SplitPane`s.

---

## 19. Patterns and the concrete problem each one solves

| Pattern | Where | Problem it solves | Where **not** to use it |
|---|---|---|---|
| **N-layer architecture + Dependency Inversion** (ports and adapters) | Whole system (§9) | Keep UI, orchestration, rules and I/O apart; test Domain and Application without DOSBox or JavaFX; plug tool families in | Do not create a layer module before it has content |
| **MVVM** | Presentation (JavaFX) | View state as bindable JavaFX properties, testable without rendering; views stay thin; language switching through bindings | Trivial dialogs can bind directly |
| **Use case / application service** | Application | One explicit entry point per user action (Build, Run…), shared by the JavaFX UI and the CLI | Simple reads that just return a value can be plain queries |
| Strategy | `AssemblerAdapter`, `LinkerAdapter`, `ToolRunner`, `DiagnosticParser`, `ExecutionEnvironment` | Choose the algorithm from configuration without `if assembler == TASM` | Internal utilities without variants |
| Abstract Factory / Provider | `ToolchainProvider` | Create a **coherent set** (TASM+TLINK+TD+parsers) and avoid invalid combinations | — |
| Registry + ServiceLoader | Provider discovery in composition roots | Add MASM **without editing** upper layers (NFR-08) | Not as a *service locator* all over the code |
| Adapter | Every CLI and output format; DOSBox conf dialects | Unify heterogeneous tools under `ToolInvocation` and `Diagnostic` | — |
| Command + registry | Presentation actions (Run/Build/Debug/Clean/Stop, editor actions) | Menu, toolbar, shortcuts and **command palette** read the same source, then call use cases | — |
| Pipeline (step plan) | `BuildPlan` (Domain) executed by `BuildProject` (Application) | Clean&Build, incremental builds and cancellation between steps | — |
| State machine | `ExecutionSession`, `DebugSession` | Consistent button enablement (Idle → Building → Running → Stopping…) | — |
| Observer / event bus (typed, in-process) | Application events → view models; locale changes → UI text | Decouple use cases from JavaFX; several panels react to the same event | Inside the editor: direct listeners |
| Plugin architecture | **Future** (external jars) | Third parties | MVP: internal ports only |

---

## 20. Scope classification

| Level | Content |
|---|---|
| **Needed for MVP (v0.1)** | Windows 11 · `dos-exe-16` profile · TASM + TLINK · DOSBox family (tested with DOSBox-X and 0.74-3) · New Project · explorer · tabs · editor with highlighting · Build/Clean/Clean&Build/Run/Stop · Output · **Problems with click-to-line** · minimal properties · tool registry and detection + "Doctor" · isolated staging · 8.3 validation · basic `dist/` · light/dark themes · basic palette · **English/Spanish UI** · development CLI · layer tests |
| **Next (v0.2–v0.4)** | Hybrid MASM 6.11 · project import · multi-module and include dirs · incremental build · Debug/Release · project DOS console · outline, go to definition, references, completion, hover, folding, snippets · L0 external debugging · machine code panel from LST · `dos-com-16` profile |
| **Desirable** | Educational lint · "What does this instruction do?" · host PTY terminal · program output capture · managed free downloads · bundled DOSBox-X · JWasm/UASM for CI · bootsector profile with QEMU |
| **Advanced** | 8086 emulator + integrated debugger (v0.5) · flags before/after · GDB-RSP (QEMU/Bochs) · DOS 32 DPMI profile · first 32/64-bit target |
| **Future** | External plugins · NASM/FASM/GAS · full Windows PE and Linux ELF · exposing LSP/DAP · IDE on Linux/macOS · live encoder · AI assistant · additional UI languages |

---

## 21. MVP v0.1: analysis of the user's proposal

The proposed selection is **essentially right**, with adjustments:
- **Add** (low cost, high value or essential): Problems panel with click-to-line (the parser is needed anyway),
  Stop/cancellation, tool detection and "Doctor" (without them the first run fails), **isolated staging** (a
  security requirement), **8.3 and path validation** (avoids mysterious failures), basic `dist/` (staging already
  does almost everything) and the **English/Spanish UI switch** (cheap if every string goes through bundles from
  the first screen).
- **Defer:** integrated terminal, completion and hover, debugging, multi-module, editable Debug/Release
  configurations, custom themes.
- **Nuance:** a complete "Project Configuration" is heavy; the MVP ships General, Target, Toolchain, Sources and
  Execution.
- **DOSBox vs DOSBox-X:** **DOSBox-X is recommended as the reference environment** (active, LFN, `-silent` for
  headless builds, more CPUs and machines for future 386/DPMI profiles, internal debugger as a manual resource),
  **without excluding** DOSBox 0.74-3 or Staging, which the user already has. All three are dialects of the same
  provider.

---

## 22. Roadmap by phase

> Every phase ends in something **runnable and verifiable**. Relative size: S/M/L.

### F0 — Foundations and risk spikes · M
- **Goal:** remove the risks that would invalidate the architecture before building on top of it.
- **Components and spikes:** S1 multi-module pom (`release 25`, JavaFX 25, empty window, jpackage/WiX smoke test,
  AtlantaFX, EN/ES bundles) · S2 `DosBoxToolRunner` prototype (DOSBox-X `-silent`, 0.74-3, Staging; TASM 3.2 and
  4.1; errorlevel; redirection; timings; response files) · S3 **fixtures** of real errors (undefined symbol,
  syntax, missing END, unresolved external, duplicate entry point like Extras.asm, warnings) from TASM 3.2/4.1,
  TLINK 3/7, ML 6.11 and 16-bit LINK, plus .LST and .MAP samples and UTF-8 text · S4 paths with spaces and non-ASCII
  characters (`G:\Mi unidad\...`), long names (the `inv_bottom.spr` case) with LFN on/off · S5 RichTextFX with
  `loadmap.asm` (latency, scrolling, completion popup) · S6 kill the DOSBox process tree from Java and a Job Object
  through FFM.
- **Dependencies:** none.
- **Not implemented:** real UI, final project model, layers below Presentation beyond `idearm-domain`.
- **Result:** spike report + ADR-001 stack, ADR-002 DOSBox flavor, ADR-003 project format, ADR-004 licensing and
  tools, ADR-005 editor component, ADR-006 UI localization, ADR-007 N-layer architecture + `fixtures/` folder.
- **Done when:** every **[verify F0]** hypothesis in this document is confirmed or replaced, and the headless TASM
  "Hello" build works from Java with its log captured.
- **Status:** completed on 2026-09-14 (`spikes/REPORT.md`). The jpackage installer check, which needs WiX Toolset,
  moves to F4.

### F1 — Headless core by layers: project + TASM + diagnostics · L
- **Goal:** build a TASM project through a use case, without a graphical UI.
- **Components by layer:**
  - *Domain:* project model, profile catalog, `CompatibilityResolver`, `BuildPlanner`, 8.3 `FileNameRules`,
    `Diagnostic`, ports.
  - *Application:* `BuildProject` and `CleanProject` use cases, `TaskManager`, build events.
  - *Infrastructure:* `TomlProjectRepository`, tool registry storage + detectors, `ProcessService`,
    `DosBoxToolRunner`, 8.3 `PathMapper`, `borland-tasm` provider (TASM/TLINK) with its parsers.
  - *Presentation:* `idearm-cli build|clean` with its composition root.
- **Dependencies:** F0.
- **Not implemented:** Run, dist, graphical UI, MASM, incremental builds.
- **Result:** `idearm build <dir> --json` produces `build/MAIN.EXE` or diagnostics with file and line.
- **Done when:** parser unit tests against the F0 fixtures, TOML round trip, resolver, planner and 8.3 tests pass;
  use-case tests run with fake ports; **ArchUnit layer tests pass**; a local integration test (tag
  `requires-tasm`) builds the Hello sample and a broken one.

### F2 — Run, Clean and dist in the core · M
- **Goal:** run in isolation and package.
- **Components:** `RunProject` use case; `dosbox` environment provider (dialects), `SessionWorkspace` (staging),
  `ExecutionSession` + exit sentinel and `DosDistPackager` in Infrastructure; `idearm-cli run`.
- **Dependencies:** F1.
- **Not implemented:** debugging, program output capture.
- **Result:** `idearm run` opens DOSBox-X with the program; `idearm build` generates a runnable `dist/` with `RUN.BAT`.
- **Done when:** the "program that tries to delete files in `src/`" test leaves the project intact; Stop leaves no
  processes behind; `dist/RUN.BAT` works from another folder; Architecture_Project_2 runs with its `.spr` files
  (used locally, never pushed to the repo).

### F3 — JavaFX workbench · L (can progress in parallel with F1–F2)
- **Goal:** VS Code-like visual skeleton.
- **Components:** main window built with **MVVM** (activity bar, explorer with FS watching, tabs, editor area,
  bottom Output/Problems panel, status bar), `CommandRegistry` + shortcuts + basic palette, `EditorComponent`
  (RichTextFX) with the MASM/TASM lexer from `idearm-language` for highlighting, light and dark themes, settings,
  **localization infrastructure** (English and Spanish bundles, live switch, persisted preference).
- **Dependencies:** F0 (S1, S5).
- **Not implemented:** build from the UI, completion, wizard.
- **Result:** open the Mastermind or Turtoria folder, edit and save with highlighting, in English or Spanish.
- **Done when:** NFR-03 is met with `loadmap.asm`; view models have unit tests without rendering; every existing
  action has a shortcut and appears in the palette; switching the language updates all visible text without a
  restart; the bundle key parity test passes.

### F4 — Integration and **v0.1 release (MVP)** · M
- **Goal:** the complete ideal flow in the UI.
- **Components:** Build/Run/Clean/Clean&Build/Stop commands wired to the use cases, streaming Output, Problems with
  double-click to line and squiggles, New Project wizard (profile → filtered toolchain → `MAIN.ASM` template),
  Project Properties (MVP), Tools/Doctor page, jpackage installer.
- **Dependencies:** F2, F3.
- **Not implemented:** everything listed under "Next".
- **Result:** v0.1 publishable on GitHub, without proprietary tools.
- **Done when:** the E2E scenarios in §26 pass on a clean Windows 11 machine with DOSBox-X and a TASM registered by
  the user.

### F5 — Second toolchain: MASM 6.11 + real projects · M → **v0.2**
- **Goal:** validate the architecture and migrate Mastermind.
- **Components:** `microsoft16` provider in `idearm-toolchain-dos` (ML 6.11 in `HostProcessToolRunner`, 16-bit LINK
  in `DosBoxToolRunner`, MASM/LINK parsers), `ImportProject` use case, multi-module, include dirs, exclude,
  incremental build, Debug/Release configurations, "Project DOS console" command, `.idearm/local.toml` override.
- **Dependencies:** F4.
- **Result:** Mastermind and Turtoria open, build and run in the IDE.
- **Done when:** **adding MASM did not modify the Presentation, Application or Domain modules** (only Infrastructure
  code and data registrations); the duplicate entry point from Extras.asm shows up as a readable diagnostic.

### F6 — Intelligent Assembly editor · L → **v0.3**
- **Goal:** productivity and learning inside the editor.
- **Components:** parser + multi-file symbol index (`idearm-language`), editor query use cases, outline, go to
  definition, references, symbol search, completion filtered by CPU and mode, hover from the knowledge base (~100
  initial 8086 instructions, English and Spanish), base conversion on hover, folding, snippets (importing
  Mastermind's), basic lint (termination, CPU baseline).
- **Dependencies:** F3 (F5 for multi-module).
- **Done when:** in Mastermind, "go to definition" on `desigual` jumps to the label and the "program without
  termination" lint appears; parser tests with every `.asm` from the three projects run without exceptions.

### F7 — L0 debugging + machine code panel · M → **v0.4**
- **Components:** `external` debug backend (TD for TASM, CV for MASM, free DEBUG.COM as fallback), `/zi /v` and
  `/Zi /CO` in the Debug configuration, LST/MAP generation, per-line "Machine code" panel from the LST,
  `dos-com-16` profile.
- **Dependencies:** F5.
- **Done when:** Debug opens TD with the program and its symbols; selecting a line shows its exact bytes from the LST.

### F8 — 8086 emulator engine (headless) · L
- **Components:** `idearm-emu8086`: 8086 decoder and executor, MZ/COM loader + PSP, INT 21h (console, sandboxed
  files), INT 10h (text and 13h), INT 16h, basic clock, 8086 disassembler, `SourceMap` from LST+MAP.
- **Dependencies:** F7 (LST/MAP).
- **Not implemented:** debugging UI.
- **Done when:** the chosen per-instruction test suite passes; Hello, Mastermind and a Turtoria subset produce the
  same text output as in DOSBox. **Documented go/no-go** to continue with F9 or bet on GDB-RSP.

### F9 — Integrated debugger · L → **v0.5**
- **Components:** `DebugProject` use case, gutter breakpoints, step into/over/out, continue, pause, stop, current
  line, registers and flags panels (hex/dec/bin), stack, hex memory, symbols/watch, disassembly, the program's
  virtual console inside the IDE.
- **Dependencies:** F8.
- **Done when:** stepping through Extras.asm shows CX incrementing and the digits on the stack, and a breakpoint at
  `imprimir:` stops execution.

### F10 — Educational assistant + terminal + polish · M → v0.6
- **Components:** "What does this instruction do?", flag and register diff per step, complete CPU-baseline lint, host
  PTY terminal, advanced command palette.
- **Dependencies:** F6, F9.

### F11 — First 32/64-bit target · L → v0.7+
- **Goal:** prove the model is not tied to DOS.
- **Decision by ADR** based on what the course requires: (a) NASM + Linux ELF64 in WSL2 or QEMU with gdbserver → own
  GDB-RSP client, or (b) Windows PE32/PE64 (UASM or ml/ml64 + linker) in Windows Sandbox or native with opt-in.
- **Components:** NASM or UASM provider (new Infrastructure module), `HostProcessToolRunner`, X86_32/64
  `RegisterSetDescriptor`, `GdbRspDebugBackend`, packager for the chosen format.
- **Done when:** a 64-bit "Hello" builds, runs isolated and is debugged with the same panels, without changes to the
  upper layers beyond new data (profiles, register descriptors).

### F12 — Future
External plugins (jar loading / `ModuleLayer`) once there are ≥3 real implementations per port, managed downloads,
exposing LSP and DAP, IDE for Linux and macOS, additional UI languages, AI.

```
F0 ─► F1 ─► F2 ─┐
 └──► F3 ───────┴► F4 (v0.1) ─► F5 (v0.2) ─┬► F6 (v0.3) ─────────────┐
                                           └► F7 (v0.4) ─► F8 ─► F9 (v0.5) ─► F10 ─► F11 ─► F12
```

---

## 23. Technical risks to resolve before starting

| # | Risk | Impact | Mitigation | When |
|---|---|---|---|---|
| R1 | Unreliable headless DOS builds (errorlevel, redirection, window, timings) | High | Resolved in F0: DOSBox-X `-silent`, reliable errorlevels, single session; never trust output existence (TLINK writes the EXE on failure) | F0 ✔ |
| R2 | TASM/TLINK error formats differ from assumptions; linkers give no line numbers | High | Real formats captured in F0 as `fixtures/`; linker symbols still need the language index to find a line | F0 ✔ / F6 |
| R3 | 8.3 names, paths with spaces or `ñ` (the user's files live under `G:\Mi unidad\...`) | High | Confirmed in F0: non-ASCII paths are not mountable → mandatory ASCII staging; DOSBox-X `lfn=true` for long names; 8.3 validation | F0 ✔ |
| R4 | 127-character DOS command-line limit | Medium | Response files verified in F0 for TASM, TLINK and LINK 5.31 | F0 ✔ |
| R5 | Licensing: TASM/MASM not redistributable; GPL if DOSBox-X is bundled | High (public repo) | ADR-004; nothing proprietary in the repo or CI; free toolchain for CI later | F0 |
| R6 | JavaFX editor not good enough | Medium | Resolved in F0: RichTextFX 0.11.7 types at p95 11 ms with per-paragraph highlighting; incubator `CodeArea` as fallback (ADR-005) | F0 ✔ |
| R7 | Original pom: `release 11` with JavaFX 25 (requires JDK 23+); WiX for jpackage | Low | S1 | F0 |
| R8 | Orphan DOSBox processes if the IDE crashes | Medium | Job Object via FFM proven in F0 (DOSBox-X is killed when its owner dies); cleanup on startup | F0 ✔ / F2 |
| R9 | No remote debugging API in official DOSBox | High for v0.5 | L0 external first; own emulator with go/no-go; GDB-RSP as an alternative | F7–F8 |
| R10 | Source ↔ address mapping (TD/CV formats are proprietary) | Medium | Confirmed in F0: TASM and ML listings give offset and bytes per line; samples in `fixtures/listings` | F0 ✔ |
| R11 | TLINK 7 and TD need RTM/DPMI16BI; TASM 3.2 in real mode with large files | Medium | Resolved in F0: TASM 3.2 and 4.1 build `loadmap.asm` (104 KB) in a 3.5 s session; `ToolRequirement.companions` declares RTM/DPMI16BI | F0 ✔ |
| R12 | UTF-8, BOM and CP437 with TASM/ML | Low | F0: UTF-8 bytes and LF endings accepted by TASM and ML; save without BOM; warn on non-ASCII strings | F0 ✔ |
| R13 | Scope (32/64, plugins) eats the project | High | Gated roadmap; ADR before F11 | Always |
| R14 | Integration tests with proprietary tools impossible in public CI | Medium | Local-only tagged tests; CI runs unit tests and fixtures only | F1 |
| R15 | The name "IDEARM" may suggest the ARM architecture | Low | Decide the name before v0.1 | F4 |
| R16 | UI text hard-coded or missing in one language | Low | All UI text through `Localization`; bundle key parity test from the first screen | F0/F3 |
| R17 | Layering adds boilerplate (use cases, ports, mapping) for a small team | Medium | Records instead of DTO/mapper frameworks; Presentation may read domain records; modules created lazily; ArchUnit keeps the rules cheap to follow | F1 |
| R18 | A third-party editor breaks on a JavaFX upgrade (RichTextFX 0.11.5 does not start on JavaFX 25) | Medium | Upgrade JavaFX and RichTextFX together; editor smoke test on every upgrade; incubator `CodeArea` as fallback (ADR-005) | F3 |

---

## 24. Explicit answers to the 20 questions

1. **Feasible?** Yes. Build, Run, dist and external debugging for DOS are straightforward; integrated debugging needs
   an own emulator (bounded and testable). 32/64-bit targets are feasible with GDB-RSP and VMs, at a higher
   isolation cost (§1, §14).
2. **MVP scope:** §20–§21 (TASM, `dos-exe-16`, Build/Run/Clean/Stop, Problems, staging, basic dist, EN/ES UI).
3. **First architecture:** the `dos-exe-16` profile: x86, 8086 CPU baseline, 16-bit code, DOS, MZ, OMF.
4. **TASM + TLINK + DOSBox?** Yes, a good choice (decided). It forces solving the hardest case first (the whole
   build inside DOS), and MASM 6.11 then validates the abstraction. Condition: the user supplies their own TASM copy.
5. **DOSBox or DOSBox-X?** DOSBox-X as the reference (LFN, `-silent`, active, more machines), keeping 0.74-3 and
   Staging compatible as dialects of the same provider.
6. **Toolchain:** a `ToolchainProvider` port per family (implemented in Infrastructure) → `ResolvedToolchain` in
   memory. It declares support (tuples), requirements, detectors and adapters. The project stores only `id` +
   version (§10).
7. **Assembler:** `AssemblerAdapter`: logical request → `ToolInvocation`, `HostKind` and `DiagnosticParser`. It does
   not run processes (§10).
8. **Linker:** an analogous `LinkerAdapter`; the toolchain defines it, and it is only replaced when the object
   format is compatible (advanced).
9. **Debugger:** `DebugBackendProvider` + a DAP-inspired `DebugSession`, with declared capabilities and panels
   generated from a `RegisterSetDescriptor`. Backends: external, emu8086 and GDB-RSP (§14).
10. **Execution environment:** `ExecutionEnvironmentProvider` with declared isolation and capabilities + a stateful
    `ExecutionSession`. **Separate** from `ToolRunner`, which decides where the tools run.
11. **16/32/64:** `codeMode` in `TargetProfile`, validated against `cpuBaseline`. Tools and environments declare the
    modes they support, the editor filters registers and instructions, and register descriptors are data. Never a
    scattered `switch(bits)` (§7).
12. **Configuration:** versioned `idearm.toml` (logical) + local `.idearm/` + global tool registry and settings.
    Everything physical is derived (§16).
13. **Technology:** Java 25 LTS + JavaFX 25, multi-module Maven organized in four layers; everything below
    Presentation is UI-free (§9, §18).
14. **Advanced editor:** `EditorComponent` (RichTextFX) in Presentation + a headless language model
    (`idearm-language`, Domain) with a bilingual knowledge base; the JavaFX incubator `CodeArea` as fallback (§15, ADR-005).
15. **External processes:** `ProcessService` (Infrastructure): arguments as a list (no shell string concatenation),
    explicit cwd and environment, reading on virtual threads, `CompletableFuture`, timeout, process-tree cancellation
    (`ProcessHandle.descendants()` + Job Object), per-tool charset (CP437 for DOS), events batched towards the UI, a
    single build task per project.
16. **Build/Run/Debug:** §9.5 and §11.
17. **`dist/`:** Release EXE + resources + relative `DOSBOX.CONF` + `RUN.BAT`; no toolchain or sources (§12).
18. **Core vs. plugins:** the four layers (model, rules, ports, use cases, workbench, editor, localization) are the
    core. Built-in tool families are Infrastructure modules that implement Domain ports and are discovered with
    `ServiceLoader`: TASM, MASM, DOSBox, emu8086, packagers. External plugins = future, after ≥3 implementations per
    port.
19. **What can be automated from Mastermind?** Everything in the §4 table: detection, mounts, batches, launching,
    capture, parsing, cleanup, running, debugging, console, doctor and packaging. What is **not** replicated:
    redistributing MASM and re-assembling on the target machine.
20. **Risks to solve first:** §23, especially R1–R5 (resolved in F0).

---

## 25. Requirements worth correcting or qualifying

| Original requirement | Problem | Alternative that keeps the intent |
|---|---|---|
| Choose "Architecture: 8086…x86-64" and "Mode: 16/32/64" separately | They are not independent (8086 is 16-bit only) and "x86" is a family, not a CPU | Target profile + advanced CPU baseline; the wizard shows only valid combinations |
| "TASM/MASM/NASM/FASM" with any architecture | TASM and MASM 6.11 do not generate 64-bit code; "MASM" is ambiguous (6.11 vs ml64) | Declarative matrix (§8) filters the wizard |
| DOSBox for 32/64-bit | The DOSBox family covers DOS 16 and 32 (extenders), not x86-64 or PE/ELF | Bochs/QEMU/WSL/Sandbox per profile (§8.3) |
| Register panels "on top of DOSBox" | Official builds have no remote debugging API | L0 external → L1 own emulator → L2 GDB-RSP |
| `dist/program.exe` ready to run | An MZ executable does not run on Windows x64 | Launcher + conf + required (or optionally bundled) emulator |
| Isolate "everything" | Assemblers and linkers do not run the user's code; isolating Windows targets with debugging is expensive | Isolate the **program**; per-project policy; native with opt-in |
| "Linker" as a free choice | Determined by the toolchain and the object format | Override only under Advanced, with validation |
| Plugins and 5 debuggers from the start | Overengineering | Internal ports + `ServiceLoader`; plugins in the future |
| Classic N-layer (business layer calling data access directly) | Rules would depend on DOSBox, TOML and processes | N-layer with dependency inversion (§9.2) |

---

## 26. Verification

**v0.1 acceptance E2E scenarios** (Windows 11, DOSBox-X, TASM registered by the user):
1. New Project `HELLO` (`dos-exe-16`, TASM) creates the structure, `idearm.toml` and a template `MAIN.ASM`.
2. **Run** → within ≤ 5 s the DOSBox-X window shows "Hello World"; Output shows the exact commands and
   "Program finished".
3. Introduce an undefined symbol → **Build** → Problems shows `MAIN.ASM:12 Undefined symbol …` and double-clicking
   navigates to the line.
4. **Stop** during Run closes DOSBox; Task Manager shows no orphan processes.
5. **Clean** deletes only `build/` and `dist/`.
6. **Build** generates `dist/`, and `RUN.BAT` runs the program from another folder.
7. A project located in a path with spaces and `ñ` works.
8. A test program that tries to delete `C:\SRC\MAIN.ASM` during Run does **not** alter the project.
9. The UI stays responsive (moving windows, typing) while building.
10. A local copy of Architecture_Project_2 runs with its `.spr` files and shows an 8.3 warning for `inv_bottom.spr`.
11. Switching the UI language to Spanish updates menus, panels and dialogs immediately, and the choice is kept after
    restarting the IDE.
12. The same build, run through `idearm-cli`, produces the same diagnostics as the UI (both use the same use cases).

**Test strategy:** unit tests per layer — Domain (model, resolver, planner, 8.3 rules, language model), Application
(use cases with fake ports), Infrastructure (parsers with real fixtures, TOML, PathMapper), Presentation (view
models without rendering, bundle key parity) · **architecture tests (ArchUnit)** enforcing the layer rules · public
CI runs everything that needs no proprietary tool · integration with proprietary tools only locally (tags
`requires-tasm` / `requires-masm`) · UI smoke tests with TestFX · the `idearm-cli --json` CLI as the E2E harness. The
user's projects are used **locally only**; the repo contains samples written from scratch and no proprietary
binaries.

---

## 27. Status and next steps

1. This document lives in the repo as `docs/technical-plan.md`, with decisions in `docs/adr/` (ADR-001…007).
   [`PLAN.md`](../PLAN.md) at the repository root is the short handoff brief for people and AI agents; it tracks the
   current status and next steps.
2. **Phase 0 is complete** (2026-09-14): results in [`spikes/REPORT.md`](../spikes/REPORT.md), ADR-002 and ADR-005;
   curated tool output in `fixtures/`. **F1 is next.**

**External sources consulted:**
[JavaFX 25 release notes](https://github.com/openjdk/jfx/blob/master/doc-files/release-notes-25.md) ·
[DOSBox-X command-line options](https://github.com/joncampbell123/dosbox-x/wiki/DOSBox%E2%80%90X%E2%80%99s-Command%E2%80%90Line-Options) ·
[DOSBox-X feature highlights (LFN)](https://dosbox-x.com/wiki/DOSBox%E2%80%90X%E2%80%99s-Feature-Highlights) ·
[DOSBox Staging LFN issue #2831](https://github.com/dosbox-staging/dosbox-staging/issues/2831) ·
[dosbox-x-gdb (fork)](https://github.com/hezi/dosbox-x-gdb) ·
[JWasm manual (-mz)](https://baron-von-riedesel.github.io/JWasm/Html/Manual.html) ·
[UASM](https://www.terraspace.co.uk/uasm.html) ·
[QEMU GDB usage](https://qemu-project.gitlab.io/qemu/system/gdb.html) ·
[Debugging 16-bit code on QEMU+GDB](https://gist.github.com/Theldus/4e1efc07ec13fb84fa10c2f3d054dccd) ·
[TASM "**Fatal**" ticket](https://sourceforge.net/p/guitasm8086/guitasmsupport/2/)
