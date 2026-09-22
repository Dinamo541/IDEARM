# ADR-002 — Reference DOS environment (DOSBox / DOSBox-X / Staging)

- **Status:** Accepted (evidence from Phase 0 spikes S2, S2b, S4 and S6); **amended 2026-09-21** (spike S7, see
  [Amendment 1](#amendment-1--dosbox-074-3-by-default-invisible-builds-and-secure-mode-2026-09-21))
- **Date:** 2026-09-14
- **Reference:** `docs/technical-plan.md` §8.3, §11, §21 · `spikes/REPORT.md`

## Context
DOSBox plays two different roles: running DOS tools (TASM, TLINK, 16-bit LINK) headless, and running the user's
program in isolation. The machine has DOSBox 0.74-3 (2019), and Turtoria uses DOSBox Staging 0.82.2.

## Decision
**DOSBox-X as the reference environment**, implemented as one `dosbox` provider (Infrastructure layer) with
*dialects* (DOSBox-X, Staging, 0.74-3) that declare their capabilities. DOSBox 0.74-3 and Staging stay supported as
compatibility dialects.

## Evidence
Measured with DOSBox-X 2026.08.31 portable (SHA-256 matched GitHub's published digest), DOSBox 0.74-3 and DOSBox
Staging 0.82.2, using TASM 4.1/TLINK 7.1, TASM 3.2/TLINK 3.01 and MASM 6.11's LINK 5.31.

| Capability | DOSBox-X | DOSBox 0.74-3 | Staging 0.82.2 |
|---|---|---|---|
| Headless session (23 steps) | **Yes** (`-silent -exit -fastlaunch`), 2.0–2.1 s | Window flashes (`-exit -noconsole`), 2.5–3.0 s | Window flashes (`-exit`), 3.1–4.0 s |
| Assemble + link one program from Java in one session | 0.9 s (assemble only) – 1.5 s (assemble + link) | — | — |
| `mount X "path" -ro` | Honored | **Ignored: writes allowed** | Honored |
| Mount a host path with spaces and non-ASCII characters (conf in ANSI / UTF-8) | Mounted but files not visible / invalid drive | Works with an ANSI conf only | Invalid path with both |
| DOS program opening `inv_bottom.spr` (classic 3Dh / LFN 716Ch) | FAIL / FAIL by default; **OK / OK with `[dos] lfn=true`** | FAIL / FAIL | FAIL / FAIL |
| Long host names seen by DOS | 8.3 aliases (`LONGFI~1.ASM`); long names listed with `lfn=true` | 8.3 aliases | 8.3 aliases |
| Shell message language | English | English | Follows the host language (Spanish on this machine) |
| `>` redirection of tool and program output | Yes | Yes | Yes |
| Errorlevels (TASM 0/1/2, TLINK 1, LINK 5.31 2) | Reliable | Reliable | Reliable |

## Consequences for the design
1. Tool sessions and Run sessions mount **only an ASCII-only staging folder** (e.g. under `%LOCALAPPDATA%\IDEARM`);
   project folders are never mounted directly. If the staging root itself would contain non-ASCII characters (e.g. a
   user profile name), the IDE must pick a fallback root.
2. Generated confs for DOSBox-X set `[dos] lfn=true`, so programs that open long resource names (like
   Architecture_Project_2's `loadmap.asm`) work. 8.3 warnings remain, because other dialects, real DOS and `dist/`
   users may not have LFN.
3. The IDE never parses DOS shell output (Staging localizes it); it only reads tool logs and `.RC` files.
4. Generated batch files never put a redirection inside `IF`: the shell opens the target file before evaluating the
   condition, so `if errorlevel 1 echo x>FLAG` creates `FLAG` even when the condition is false. Flags are
   environment variables (`set FAILED=1`, `if "%FAILED%"=="1" goto end`).
5. Success is decided by the errorlevel, never by the existence of the output file: **TLINK writes the .EXE even
   when linking fails**. The runner deletes stale outputs before a build and discards outputs of failed steps.
6. Command lines longer than 126 characters use response files, which work for TASM (`@FILE`), TLINK (one long line
   or `+` continuation lines) and LINK 5.31 (`+` continuation; LINK echoes its prompts into the log, which the
   parser must ignore).
7. DOSBox 0.74-3 cannot guarantee read-only tool mounts; with that dialect the IDE warns that isolation is reduced.
8. A Windows Job Object must own every DOSBox process: without it, DOSBox keeps running when the IDE dies (S6).
9. **The `[autoexec]` section stays short.** DOSBox copies it into a 4 KiB AUTOEXEC.BAT and aborts the session with
   `E_Exit: SYSTEM:Autoexec.bat file overflow` when it does not fit, which the 1..255 errorlevel ladder does on its
   own. Autoexec only mounts the drive and calls a generated batch (`BUILD.BAT` for a build, `RUN.BAT` for a run);
   everything else lives in that batch.
10. **Batches are invoked with `call`.** A batch started without it never returns, so the `exit` that closes the
   emulator never runs and the session stays at a DOS prompt with the IDE waiting for it. Generated batches end
   with `exit` as well.
11. A Run session records the program status through the same ladder, into an `EXITCODE.TXT` sentinel: DOSBox's own
   exit code says nothing about the program. Verified with a program ending in `AH=4Ch, AL=7`, reported as 7.
12. Files the IDE writes for a session use lower-case names (`build.bat`, `run.bat`, `debug.bat`, `s001.rsp`),
   which DOS matches case-insensitively. Files DOS creates come out upper case, so artifacts are copied out of the
   session under the names the build plan spells before they are published.

## Verification
- [x] Headless builds work in all three dialects; DOSBox-X is the fastest and the only one without a window.
- [x] Errorlevels and `>` redirection are reliable.
- [x] Read-only mounts: honored by DOSBox-X and Staging, ignored by 0.74-3.
- [x] Paths with spaces and non-ASCII characters are not reliably mountable → ASCII staging is mandatory.
- [x] Long names: only DOSBox-X with `lfn=true` lets a DOS program open them.
- [x] Response files for TASM, TLINK and LINK 5.31 (13 objects, 251-character equivalent command).
- [x] Job Object kills DOSBox-X when the owning process dies abruptly; `Process.destroy()` stops it in ~120 ms.
- [x] Autoexec overflow reproduced with a 255-line ladder (DOSBox-X 2026.08.31) and fixed by moving it into
      `C:\RUN.BAT`; the session then runs, reports exit code 7 in 2.1 s and leaves no orphan process.
- [ ] Isolate whether the failed non-ASCII mounts are caused by the spaces, the non-ASCII characters or both
      (not needed by the design, which avoids both).
- [ ] Program exit status mechanism for Run sessions (sentinel file) — F2.
- [ ] MASM LINK reported "4 errors detected" for the duplicate-symbol case but only 2 lines were captured; check
      whether part of its output bypasses DOS redirection.

## Amendment 1 — DOSBox 0.74-3 by default, invisible builds and secure mode (2026-09-21)

**Context.** Students already have DOSBox 0.74-3, and the user asked for it to be the default for Build, Run and
Debug, on Windows and Linux. Spike S7 (`spikes/REPORT.md`) measured what that needs.

**Decision.**
1. **Automatic order: DOSBox 0.74-3, then DOSBox-X, then Staging** (`DosBoxDialects.PREFERENCE`). `[run]
   environment = "dosbox"` (the default) takes the first one installed; a project can still force
   `dosbox-0.74`, `dosbox-x` or `dosbox-staging`. 0.74-3 and Staging both ship as `dosbox`/`dosbox.exe`, so the
   detector reads the version text inside the binary (up to 64 MB) instead of trusting the folder name.
2. **Builds never show a window.** 0.74-3 runs with `SDL_VIDEODRIVER=dummy` and `SDL_AUDIODRIVER=dummy` (plus
   `[mixer] nosound=true`); DOSBox-X keeps `-silent`. Staging crashes with the dummy driver, so a build uses the
   project's dialect when it is installed, otherwise the first installed dialect that builds invisibly, and only
   then any installed one (`DosBoxResolution.forBuild`). `-noconsole` is passed only on Windows.
3. **Secure mode in every session.** `config -securemode` is the last `[autoexec]` line after the IDE's own mounts
   (build, run and debug): the program or tool can no longer `MOUNT`, `IMGMOUNT` or `BOOT` a host folder. Verified
   in the three dialects.
4. **Validated settings.** `[run] cycles` (`auto`, `max`, `max N%`, `fixed N`, `N`), `memsize` 1–63 (0.74-3 clamps
   64 to 63) and DOS `args` (printable ASCII without `& | < > ^ % "`, at most 126 characters) are checked before
   any configuration is written (`RunSettings`), since a line break would add commands of its own.
5. **Staging root chosen automatically** (consequence 1 above): the first ASCII folder without spaces among
   `IDEARM_STAGING_DIR`, `%LOCALAPPDATA%\IDEARM\staging`, its 8.3 short name, `%ProgramData%\IDEARM\staging`,
   `%SystemDrive%\IDEARM\staging`; on Linux `$XDG_CACHE_HOME` or `~/.cache/idearm/staging`, then a private
   `/tmp/idearm-<user>/staging` (`StagingLocation`).

**Consequences.**
- Consequence 7 no longer reduces isolation in practice: tools and sources are copies inside the session folder,
  so a mount that 0.74-3 leaves writable only changes throwaway copies, and secure mode stops new mounts.
- Long resource names still need DOSBox-X (`[dos] lfn=true`); with another dialect Run adds an INFO problem
  (`run.resource.lfn`) that points to Project Properties.
- MASM on hosts that cannot run Win32 programs (Linux) runs ML 6.11 inside DOSBox with the `DOSXNT.EXE` extender
  from the MASM `BIN` folder; on Windows ML keeps running on the host.
- The DOS environment is no longer Windows-only: the provider passes the X11/Wayland display variables to DOSBox,
  and apt's `dosbox` (0.74-3) is found on `PATH` or in `/usr/games`.

**Verification.**
- [x] Secure mode blocks `mount` after the IDE's mounts in 0.74-3, DOSBox-X 2026.08.31 and Staging 0.82.2 (S7).
- [x] 0.74-3 builds TASM HELLO with no window through the SDL dummy drivers; Staging crashes with them (S7).
- [x] `memsize` 1–63 works in every dialect (S7).
- [x] ML 6.11 assembles inside DOSBox 0.74-3 with DOSXNT (S7).
- [x] A five-byte `.COM` ending with exit code 7 reports 7 through the automatic dialect
      (`DosBoxRunIntegrationTest`, tag `requires-dosbox`, DOSBox 0.74-3 on Windows).
