# Phase 0 spike report

> **Date:** 2026-09-14 · **Phase:** F0 — Foundations and risk spikes · **Status:** complete (non-blocking open
> items at the end)
>
> **Machine:** Windows 11 Pro · Temurin 25.0.3 · Maven 3.9.16 · DOSBox-X 2026.08.31 (portable) · DOSBox 0.74-3 ·
> DOSBox Staging 0.82.2 · TASM 4.1 + TLINK 7.1 · TASM 3.2 + TLINK 3.01 · MASM 6.11 (ML + LINK 5.31)

## Summary

| Spike | Question | Answer | Recorded in |
|---|---|---|---|
| S1 | Does the JDK 25 + JavaFX 25 multi-module skeleton build, run and package? | Yes: build ≈ 10 s, smoke run with a live EN→ES switch, jlink image ≈ 107 MB. Installer not checked (WiX missing). | ADR-001 |
| S2 | Can TASM + TLINK run headless in DOSBox with reliable status and output? | Yes. DOSBox-X `-silent` builds one program in 0.9–1.5 s from Java; errorlevels are reliable; two shell quirks shape the design. | ADR-002 |
| S2b | Can the 126-character DOS command limit be avoided? | Yes: response files work for TASM, TLINK and LINK 5.31. | ADR-002 |
| S3 | What do real diagnostics, listings and maps look like? | Captured for TASM 3.2/4.1, TLINK 3.01/7.1, ML 6.11 and LINK 5.31, including warnings: 53 cases + 9 listing/map samples. | `fixtures/` |
| S4 | Host paths with spaces/non-ASCII characters; long file names | Not reliably mountable → ASCII-only staging is mandatory. Only DOSBox-X with `lfn=true` lets a DOS program open a long name. | ADR-002 |
| S5 | Is a JavaFX editor fast enough for a 104 KB source? | Yes: RichTextFX 0.11.7 with per-paragraph highlighting types at p95 11 ms. RichTextFX 0.11.5 does not start on JavaFX 25. | ADR-005 |
| S6 | Can DOSBox be stopped, and never orphaned when the IDE dies? | Yes: `destroy()` in ≈ 120 ms; a Job Object created through FFM kills DOSBox when its owner dies. Without it DOSBox is orphaned. | technical-plan §11 |
| R11 | Does a large real project build? | `loadmap.asm` builds with TASM 3.2 and 4.1 in a 3.5 s session; ML 6.11 rejects it with 15 errors. | technical-plan §8.1 |
| S7 | Secure mode, invisible DOSBox 0.74-3 builds, memory limits, ML inside DOSBox (2026-09-21); Linux (2026-09-22) | `config -securemode` blocks `MOUNT` in all three dialects; 0.74-3 builds invisibly with `SDL_VIDEODRIVER=dummy`, Staging crashes with it; `memsize` 1–63 works everywhere; ML 6.11 runs inside DOSBox 0.74-3. On Ubuntu 24.04 the whole suite, apt's DOSBox 0.74-3, NASM/ld/GDB and the IDE's start are verified. | ADR-002 |

---

## S1 — Build skeleton
- Reactor `idearm-parent` → `idearm-domain` (L3) + `idearm-app` (L1): `release 25`, JavaFX 25.0.2, AtlantaFX 2.1.0.
  The Enforcer requires JDK 25+ and Maven 3.9+ and bans JavaFX dependencies in the domain module.
- `mvn clean install` ≈ 10 s, including `MessageBundlesTest` (English/Spanish key parity).
- Smoke run (`IDEARM_SMOKE=1`): `SMOKE OK: Java 25.0.3 - JavaFX 25.0.2+4 | en="Light theme" es="Tema claro"`.
- `javafx:jlink` produces a ≈ 107 MB runtime image whose launcher passes the smoke test.
- JDK 25 needs `--enable-native-access=javafx.graphics` for JavaFX to start without a restricted-access warning.
- `ResourceBundle.Control` cannot be used from a named module, so each language has its own bundle; otherwise an
  English lookup falls back to the JVM default locale (Spanish on this machine).
- javac warns that the module name component `dinamo541` ends in digits (cosmetic).

## S2 — TASM and TLINK headless in DOSBox

Shell spike (`spikes/scripts/s2-tasm-dosbox.ps1`): one session per variant, 23 steps (12 assemblies, 2 fatal cases,
4 links, 1 program run, directory listings and a write attempt on a read-only mount).

| Variant | Session | Read-only mount |
|---|---|---|
| DOSBox-X, `-silent -exit -fastlaunch`, TASM 4.1 | 2.0–2.1 s, no window | Honored |
| DOSBox 0.74-3, `-exit -noconsole`, TASM 4.1 / 3.2 | 2.6–3.0 s, window flashes | **Ignored** |
| DOSBox Staging, `-noprimaryconf -nolocal -exit`, TASM 4.1 | 3.1–4.0 s, window flashes | Honored |

Java prototype (`spikes/java/DosBoxBuildSpike.java`), DOSBox-X headless, sources mounted read-only, link only when
every module assembled:

| Modules | Session | Outcome |
|---|---|---|
| HELLO | 1.48 s | Assembled and linked, `HELLO.EXE` 1470 B |
| ERRSYM | 0.90 s | Link skipped · `ERROR tasm <repo>\spikes\asm\ERRSYM.ASM:12 Undefined symbol: PRINTNUMBER` |
| EXTERN | 1.46 s | `ERROR tlink <repo>\spikes\asm\EXTERN.ASM Undefined symbol PRINTNUMBER` |
| DUPA + DUPB | 1.48 s | `ERROR tlink <repo>\spikes\asm\DUPB.ASM HELPER defined in module S:\DUPA.ASM is duplicated` |

Findings:
1. **Errorlevels are reliable:** TASM 0 = clean, 1 = errors, 2 = fatal; TLINK 1 on error (3.01 and 7.1).
2. **Shell quirk:** a redirection inside `IF` creates its target file even when the condition is false. The first
   prototype used `if errorlevel 1 echo x>FAIL.FLG` and therefore always skipped the link. Flags must be environment
   variables (`set FAILED=1` / `if "%FAILED%"=="1" goto end`).
3. **TLINK writes the .EXE even when linking fails** (`EXTERN.EXE`, `DUPA.EXE` were produced with exit code 1). Success
   must never be inferred from the existence of an output, which is what Turtoria's script does.
4. **TASM does not validate the CPU baseline:** under `.8086` it silently expands `push 5` and `shl ax, 3` into 8086
   sequences (`fixtures/listings/tasm-4.1/CPU186.LST`). ML 6.11 rejects both.
5. **Warnings need `/w2`:** `*Warning* S:\WARN.ASM(7) Reserved word used as symbol: LENGTH`,
   `*Warning* S:\WARN.ASM(13) Instruction can be compacted with override`. TLINK: `Warning: No stack`. Without `/w2`,
   TASM reported `mov ax, counter` (byte into word) as an **error** and ignored a repeated `.MODEL`.
6. TLINK duplicate-symbol messages name two modules; a parser must extract both.
7. LF-only line endings and UTF-8 bytes in comments and strings are accepted by TASM 3.2 and 4.1.

## S2b — Response files
Thirteen objects would need a 251-character TLINK command line (the limit is 126). One DOSBox-X session (2.07 s):
- `TASM @ASM.RSP` — works.
- `TLINK @LONG.RSP` (a single long line) — works.
- `TLINK @PLUS.RSP` (`+` continuation lines) — works.
- `LINK @MLINK.RSP` (LINK 5.31, `+` continuation lines) — works; LINK echoes its prompts
  (`Object Modules [.obj]: C:\MOBJ\HELLO.OBJ+`) into the log, so the parser must ignore those lines.
- The three executables printed "Hello World".

## S3 — Diagnostics, listings and maps
- **ML 6.11** runs natively in 20–60 ms per file. Errors: `path(line): error A2006: undefined symbol : printNumber`;
  warnings with `/W3`: `path(line): warning A4011: multiple .MODEL directives found : .MODEL ignored`; a missing `END`
  is `error A2088` reported on the last line. Exit code 1 on error.
- **LINK 5.31** (inside DOSBox): `C:\OBJ\EXTERN.OBJ(<source path>) : error L2029: 'printNumber' : unresolved external`;
  `LINK : warning L4021: no stack segment`; exit code 2 on error. Because MASM 6 makes every `PROC` public, two modules
  with `main PROC` fail with L2025 — the Mastermind `Extras.asm` case.
- LINK printed "There were 4 errors detected" for DUPA + DUPB but only two error lines were captured (open item).
- Listings from TASM and ML give the line number, offset and bytes of every source line; maps give the segment
  layout, publics and entry point.
- `spikes/scripts/export-fixtures.ps1` exported 53 diagnostic cases (`fixtures/diagnostics/index.csv` with exit
  codes), 5 listings and 4 maps, replacing host paths with `C:\IDEARM-FIXTURE`.

## S4 — Host paths and long file names
Folder `My Drive\naïve café ñ` mounted as `P:`; `FOPEN.EXE` opens `inv_bottom.spr` from `D:` through the classic
(3Dh) and LFN (716Ch) DOS APIs.

| Variant | Conf encoding | Mount of the non-ASCII path | Classic 3Dh | LFN 716Ch |
|---|---|---|---|---|
| DOSBox 0.74-3 | ANSI (1252) | **Works** | FAIL | FAIL |
| DOSBox 0.74-3 | UTF-8 | `Illegal Path.` | FAIL | FAIL |
| Staging 0.82.2 | ANSI / UTF-8 | `Ruta inválida.` (localized) | FAIL | FAIL |
| DOSBox-X | ANSI | Mounted, but `File not found` | FAIL | FAIL |
| DOSBox-X | UTF-8 | `Invalid drive specification` | FAIL | FAIL |
| DOSBox-X, `[dos] lfn=true` | UTF-8 | `Invalid drive specification` | **OK** | **OK** |

Consequences: mount only an ASCII-only staging folder; enable `lfn=true` in DOSBox-X confs; keep 8.3 warnings for
other dialects, real DOS and `dist/`; never parse DOS shell output, because Staging localizes it.

## S5 — Editor
`loadmap.asm`: 104 612 bytes, 4839 lines. Each edit is measured on the JavaFX thread from the change until the next
layout pulse.

| Measure | RichTextFX 0.11.7 | JavaFX incubator `CodeArea` 25.0.2 |
|---|---|---|
| Load (`replaceText` / `setText`) | 90 ms | 16 ms |
| Highlight the whole file once | 55 ms compute + 37 ms apply | 78 ms decorating every paragraph (rendering decorates lazily) |
| **Typing in one paragraph** (p50 / p95 / max) | **6 / 11 / 34 ms** | 26 / 33 / 58 ms |
| Jumping across the file and typing (p50 / p95 / max) | 40 / 148 / 369 ms (scrolls to every edit) | 56 / 120 / 406 ms (no public scroll-to-caret method found; not comparable) |
| Re-highlighting the whole file on every keystroke | 50 / 59 / 79 ms | — |

- RichTextFX 0.11.5 fails at startup on JavaFX 25: `TextFlowExt overrides final method TextFlow.getUnderlineShape`
  (RichTextFX issue #1282). Versions 0.11.6/0.11.7 fix it.
- The incubator control needs `--add-modules jfx.incubator.richtext`, prints an incubator warning, and
  javafx-maven-plugin 0.0.8 does not put `jfx-incubator-*` artifacts on the module path (the spike was launched with
  `java --module-path` directly).
- Decision (ADR-005): RichTextFX 0.11.7 behind `EditorComponent` with per-paragraph highlighting; the incubator
  `CodeArea` is the fallback.

## S6 — Process tree
- `Process.destroy()` on a DOSBox-X window: ended in 117 ms (exit code 1), no descendant processes.
- Simulated IDE crash (`Runtime.halt`) **without** a Job Object: DOSBox-X kept running (orphan). **With** a Job Object
  (`JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE`, created through the FFM API, no JNA): DOSBox-X was terminated.
- Child output must be redirected (discarded or read): inherited handles keep the parent's pipes open.

## Large real source (risk R11)
- `loadmap.asm` assembled with TASM 4.1 and TASM 3.2 (`/zi /l /w2`) and linked with TLINK 7.1 and 3.01 in one DOSBox-X
  session: 3.51 s, every exit code 0, `/w2` warnings such as `":" operator ignored` and `Instruction can be compacted
  with override`. Both maps report entry point `0000:126E`, the same as the map committed in Architecture_Project_2.
- The same file with native ML 6.11: exit code 1, 15 errors (A2070 invalid instruction operands, A2032 invalid use
  of register, A2047 empty (null) string). TASM's MASM mode and MASM 6.11 are **not** interchangeable.

## S7 — Secure mode, invisible builds, memory and ML inside DOSBox (2026-09-21, Windows 11)
Script: `spikes/scripts/s7-dosbox-platform.ps1`; raw results in `spikes/out/s7-summary.txt`.
- **Secure mode.** A batch that runs `mount D <host folder>` after `config -securemode` (last `[autoexec]` line
  after the mounts) gets "This operation is not permitted in secure mode." in DOSBox 0.74-3, DOSBox-X 2026.08.31 and
  Staging 0.82.2 (Staging prints it in the host language); `D:` never appears and the batch keeps running. Without
  secure mode the same batch mounts the host folder in all three. The 0.74-3 manual documents the same behavior
  for `MOUNT`, `IMGMOUNT` and `BOOT`.
- **Invisible builds** (TASM 4.1 + TLINK 7.1, HELLO, window detected through the process main window handle):
  DOSBox 0.74-3 shows a window by default (2.1 s) and none with `SDL_VIDEODRIVER=dummy` + `SDL_AUDIODRIVER=dummy`
  (1.8 s, artifacts and exit codes correct). DOSBox-X `-silent` never shows one (1.2 s). Staging shows one by
  default (3.3 s) and **crashes** with the dummy driver (exit `0xC0000409`, nothing built).
- **Memory.** `memsize=64` is clamped to 63 MB by 0.74-3 (MEM reports 63 296 KB free in both cases); DOSBox-X
  honors 64; Staging accepts 1 and 63. The IDE therefore accepts 1–63.
- **ML 6.11 inside DOSBox 0.74-3** (MASM `BIN` mounted as `T:`, DOSXNT extender found through `PATH=T:\`): HELLO
  assembles (exit 0) and ERRSYM reports `S:\ERRSYM.ASM(12): error A2006: undefined symbol : printNumber` with exit 1,
  so the MASM toolchain can run entirely inside DOSBox where the host cannot run Win32 programs.
- **Identifying the dialect.** Both 0.74-3 and Staging ship as `dosbox.exe`. Their version text (`DOSBox 0.74-3`,
  `dosbox-staging 0.82.2`) is in the binary but beyond the first 512 KB that `BinaryScanner.scanVersion` reads, and
  neither has a Windows version resource.
- `-noconsole` is documented as Windows-only (0.74-3 manual).
- **Linux (2026-09-22, Ubuntu 24.04 in Docker: JDK 25.0.4, Maven 3.9.16, apt `dosbox` 0.74-3-5build2, NASM 2.16.01,
  GNU ld 2.42, GDB 15.1; reproduced by the `linux` job of `.github/workflows/ci.yml` under `xvfb-run`).**
  `mvn verify` without the TASM/MASM tags is green (506 tests, 16 Windows-only skipped). A five-byte `.COM` ending
  with code 7 reports 7 through the automatic dialect, which is apt's 0.74-3. The detector finds `/usr/bin/dosbox`,
  `gdb` and `ld` on `PATH`. A `linux-elf64` project builds, runs with its output and keyboard through the CLI
  (`echo Ana | sh scripts/idearm.sh run`), and stops on a breakpoint under GDB. With no display at all, apt's
  DOSBox does not fail: SDL falls back and it runs without a window (exit 0). The desktop IDE starts under Xvfb
  (EN/ES smoke, editor shortcuts, screenshots); the maximize-restore and minimize checks of the visual smoke fail
  there, with and without Openbox, so window controls still need a real Linux desktop.
- Found on Linux and fixed: the explorer allowed `MAIN.asm` next to `main.asm` (one file on Windows and DOS);
  two view-model tests relied on Windows ignoring letter case; a program debugged with GDB inherited GDB's MI
  input and blocked the session when it read the keyboard (its input is now `/dev/null`).

---

## Consequences for F1
1. **`DosBoxToolRunner`:** DOSBox-X `-silent -exit -fastlaunch`; ASCII-only staging; tools mounted `-ro`;
   `[dos] lfn=true`; errorlevel ladder into `.RC` files; environment-variable flags; delete stale outputs before
   building and discard outputs of failed steps; timeout; response files beyond 126 characters.
2. **Parsers** (tested against `fixtures/`): TASM `**Error**` / `**Fatal**` / `*Warning*` with and without a line;
   TLINK `Error:` / `Warning:` with zero, one or two modules; ML `path(line): error|warning Annnn: message`;
   LINK `obj(source) : error Lnnnn: message` and `LINK : warning Lnnnn: message`, ignoring echoed prompt lines.
3. **`PathMapper`:** DOS drive paths → staging mirror → project file (case-insensitive); ML and LINK report host
   paths inside the staging folder.
4. **`ProcessService`:** one Job Object per session; output always redirected.
5. **Lint (F6):** CPU-baseline checks come from the IDE, because TASM does not report them.
6. **Project model:** a project is bound to one toolchain; MASM-mode TASM sources are not guaranteed to build with ML.

## Open items (non-blocking)
| Item | Phase |
|---|---|
| jpackage installer: WiX Toolset is not installed (installing it needs the user's approval) | F4 |
| Prove that the Enforcer JavaFX ban fails the build; extend it to every non-Presentation module; add ArchUnit | F1 |
| Visual confirmation that DOSBox-X `-silent` never shows a window | F1 |
| Program exit status (sentinel) for Run sessions | F2 |
| LINK 5.31 "4 errors detected" versus 2 captured lines | F5 |
| Isolate spaces versus non-ASCII characters in the failed mounts (the design avoids both) | — |
| javac warning about the digits in the module name component `dinamo541` | Before publishing |

## How to reproduce
```powershell
mvn clean install
$env:IDEARM_SMOKE = '1'; mvn -f idearm-app/pom.xml javafx:run
pwsh -NoProfile -File spikes/scripts/s2-tasm-dosbox.ps1 -Dialect dosboxx -DosBox <dosbox-x.exe> -ToolDir <TASM 4.1 bin> -Label tasm41-dosboxx
pwsh -NoProfile -File spikes/scripts/s2-tasm-dosbox.ps1 -Dialect dosbox074 -DosBox <dosbox.exe> -ToolDir <TASM 3.2 dir> -Label tasm32-dosbox074
pwsh -NoProfile -File spikes/scripts/s3-masm611.ps1
pwsh -NoProfile -File spikes/scripts/s2b-response-files.ps1
pwsh -NoProfile -File spikes/scripts/s4-paths.ps1
pwsh -NoProfile -File spikes/scripts/export-fixtures.ps1
pwsh -NoProfile -File spikes/scripts/s7-dosbox-platform.ps1
java spikes/java/DosBoxBuildSpike.java <dosbox-x.exe> <TASM 4.1 bin> spikes\asm spikes\out\java-hello HELLO
java --enable-native-access=ALL-UNNAMED spikes/java/ProcessTreeSpike.java crash <dosbox-x.exe> spikes/java/ProcessTreeSpike.java
mvn -f spikes/editor/pom.xml compile javafx:run "-Dspike.file=<loadmap.asm>"
mvn -f spikes/editor-incubator/pom.xml compile dependency:build-classpath "-Dmdep.outputFile=<cp.txt>"
java --module-path <contents of cp.txt> --add-modules javafx.controls,jfx.incubator.richtext -cp spikes/editor-incubator/target/classes io.github.dinamo541.idearm.spikes.editorincubator.IncubatorEditorSpike <loadmap.asm>
```

Linux (S7): the `linux` job of `.github/workflows/ci.yml`, or the same steps in a container of the
`maven:3.9-eclipse-temurin-25` image (Ubuntu 24.04) with `--cap-add=SYS_PTRACE --security-opt seccomp=unconfined`
so GDB can trace:

```bash
apt-get update && apt-get install -y --no-install-recommends nasm binutils gdb dosbox xvfb xauth libgtk-3-0t64 libgl1 libxtst6 libxxf86vm1
xvfb-run -a mvn -B verify -DexcludedGroups=requires-tasm,requires-masm
mvn -B install -DskipTests
IDEARM_SMOKE=1 IDEARM_SMOKE_PROJECT=$PWD/examples/hello xvfb-run -a mvn -B -f idearm-app/pom.xml compile javafx:run
```
