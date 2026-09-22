# ADR-008 — First 32/64-bit target (NASM, GNU Linker, Windows PE and Linux ELF)

- **Status:** Accepted (user approval of F11 plan)
- **Date:** 2026-09-16
- **Reference:** `docs/technical-plan.md` §7, §8, §1054-1061; `PLAN.md` §7 (Phase F11)

## Context
IDEARM was initially built around 16-bit real-mode DOS assembly programs using Borland TASM and Microsoft MASM 6.11, executed in DOSBox and an in-process 8086 emulator (`idearm-emu8086`).

Phase F11 introduces the first 32-bit and 64-bit targets to prove that the architecture is general-purpose and not coupled to 16-bit DOS. 32/64-bit code cannot run inside DOSBox or in our 8086 emulator. Furthermore, toolchains for 32/64-bit (such as NASM and GCC/GNU ld) are native host executables generating PE32, PE32+ (PE64), or ELF binaries.

## Options considered
1. **Option A: NASM + Linux ELF64 via WSL2:** Excellent isolation for university students, standard GDB-RSP debugging, but requires a configured WSL2 distribution on the host.
2. **Option B: NASM + Windows PE32/PE64:** Direct native assembly on Windows using NASM and GNU `ld` (or `gcc`), running natively under Win32 Job Object lifecycle supervision. Highly accessible on student Windows machines without WSL dependencies.
3. **Option C: Hybrid Support (NASM for Windows PE32/PE64 and Linux ELF64):** NASM is capable of emitting object files for multiple formats (`-f win64`, `-f win32`, `-f elf64`). The IDE defines target profiles for both Windows PE and Linux ELF, with native execution handled via `HostExecutionEnvironmentProvider` (managed by Win32 Job Objects to guarantee process termination) and extensible to WSL/containers.

## Decision
We adopt **Option C (Unified NASM Toolchain with Windows PE and Linux ELF target profiles)**:

1. **Target Profiles:**
   - `win-pe64-console`: x86 / x86-64 / 64-bit long mode / Windows PE32+ / flat model / COFF objects (Default 64-bit profile on Windows).
   - `win-pe32-console`: x86 / 80386 / 32-bit protected mode / Windows PE32 / flat model / COFF objects.
   - `linux-elf64`: x86 / x86-64 / 64-bit long mode / Linux ELF64 / flat model / ELF objects.
2. **Toolchain:**
   - A dedicated vertical infrastructure slice: `idearm-toolchain-nasm`.
   - Assembler: Netwide Assembler (NASM) via `NasmAssemblerAdapter`, invoking `nasm` with format options (`-f win64`, `-f win32`, `-f elf64`) and debug symbols (`-gcv8`, `-g -F dwarf`).
   - Linker: GNU `ld` or `gcc` via `GnuLinkerAdapter`.
   - Parsers: `NasmDiagnosticParser` (parsing `file:line: error/warning: message`) and `GnuLinkerDiagnosticParser`.
3. **Tool Execution:**
   - `HostProcessToolRunner`: Executes host-native build tools (`nasm`, `ld`, `gcc`) directly via `ProcessService` without DOSBox, keeping tool outputs and exit codes intact.
4. **Program Execution & Isolation:**
   - `HostExecutionEnvironmentProvider`: Runs native Windows PE console executables (`.exe`) wrapped in a Win32 Job Object so that terminating the IDE or clicking Stop in the UI terminates the process tree immediately.
5. **Distribution Packaging:**
   - `NativeDistPackager`: Packages the compiled native binary and declared project resources into `dist/` without emulation wrappers.

## Consequences
- (+) IDEARM can build and run modern 32-bit and 64-bit x86/x86-64 Assembly programs.
- (+) Validates ADR-007 (N-layer architecture with dependency inversion): adding NASM and 32/64-bit profiles touches only Infrastructure and profile data, without changing existing 16-bit DOS toolchains.
- (+) Works out of the box with MSYS2 / MinGW environments already present on student machines.
- (−) Native host execution has weaker isolation than VM emulation; this is mitigated by Win32 Job Object tree termination to avoid runaway background processes.

## Addendum (2026-09-17): what implementation and real tools changed

Verified with NASM 3.01, GNU ld 2.46 and GDB 17.2 from MSYS2 UCRT64.

- **Linker:** only GNU `ld` is used. MSYS2's ld cannot write ELF, so `linux-elf64` builds require a Linux host and
  are refused on Windows with `toolchain.target.host`. For Windows targets ld links the system DLLs directly
  (`-L<System32|SysWOW64> -lkernel32`), with `-e main` (PE32+) or `-e _main` (PE32) and `--subsystem console`;
  PE32 adds `--enable-stdcall-fixup`.
- **Debug information:** NASM attaches only CodeView (`cv8`) to COFF objects and GDB does not read it. Debug
  builds for Windows therefore assemble ELF objects with DWARF (`-f elf64|elf32 -g -F dwarf`), which ld links into
  the same PE program; release builds keep `win64`/`win32`.
- **Program console:** native programs run in their own console window (`cmd /c start "" /wait cmd /c run.cmd`,
  still inside the Job Object); the batch returns the exit code through a file because `start` does not.
- **Debugger:** GDB/MI over stdio (see F12) with the program in its own console (`new-console on`). Events are
  delivered on a separate thread, only general-purpose registers are shown, and watches written in NASM syntax are
  translated to GDB expressions.
- **Staging:** native builds run from the project folder with outputs redirected to a staging session and
  published into `build/<configuration>` only on success, as DOS builds are.
