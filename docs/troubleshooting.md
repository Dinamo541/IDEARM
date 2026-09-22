# Troubleshooting and diagnostics

When a build, a run or a debug session fails, IDEARM tells you in three places:

- the **status bar**, with a one-line summary;
- the **Problems** panel, with one row per problem (double-click opens the file at the line);
- the **Build Log** panel, with every command the IDE ran and everything the tools printed.

Start with **Problems**. If a row does not explain enough, read the **Build Log**: the exact command is there, and
you can run it yourself in a terminal. Problems written by the tools keep the tool's own words; problems written
by IDEARM follow the UI language, and hovering over one shows the English original.

Tool messages below were captured from TASM 4.1, TLINK 7.1, MASM 6.11, NASM 3.01 and GNU ld 2.46.

- [1. Tools not found](#1-tools-not-found)
- [2. Before the assembler runs](#2-before-the-assembler-runs)
- [3. Assembler errors](#3-assembler-errors)
- [4. Linker errors](#4-linker-errors)
- [5. Running the program](#5-running-the-program)
- [6. Debugging](#6-debugging)
- [7. Packaging](#7-packaging)
- [8. Getting more information](#8-getting-more-information)

---

## 1. Tools not found

Open **Help → Tool Doctor...** first. It lists the tools IDEARM will use, and **Files missing** marks a tool
whose files are gone.

| Message | Cause | Fix |
|---|---|---|
| *TASM was not found. Register your own copy with Add Tools Folder in the Tool Doctor before building.* (same for `tlink`, `ml`, `link`, `nasm`, `ld`) | The tool is not in any folder IDEARM searches. | In the Tool Doctor, click **Add Tools Folder...** and pick the folder with `TASM.EXE` (or its parent). From a terminal: `scripts/idearm.ps1 tools add <folder>`. |
| *The registered tasm program no longer exists: ...* | The folder was moved or deleted after you registered it. | Register the new folder. To forget every registered tool, delete `%APPDATA%\IDEARM\tools.toml`. |
| *TLINK needs RTM.EXE in its own folder.* (or `DPMI16BI.OVL`) | TLINK 4.x and later are DPMI programs and cannot start without these files. | Copy `RTM.EXE` and `DPMI16BI.OVL` from your TASM package next to `TLINK.EXE`. |
| *The registered version 3.1 does not meet the requirement >=3.2.* | `[toolchain] version` in `idearm.toml` asks for a newer tool. | Register a newer copy, or relax the requirement (for example `version = "*"`). |
| *The version of the registered assembler could not be read (unknown).* | The binary is compressed, so its version text is not visible. | Set `version = "*"` in `[toolchain]`. |
| *The registered ML.EXE is the wrong kind of program (DOS_REAL).* | MASM needs the Windows `ML.EXE` (6.11 runs on Windows) and the 16-bit DOS `LINK.EXE`. | Register the `BIN` folder of MASM 6.11, not a 32-bit `LINK.EXE` from Visual Studio. |
| *ML needs DOSXNT.EXE in its own folder.* | Where Windows programs cannot run (Linux), ML runs inside DOSBox through the DOS extender `DOSXNT.EXE` that MASM 6.11 ships in its `BIN` folder. | Register the complete `BIN` folder of MASM 6.11. |
| *No dosbox installation is registered. Install DOSBox 0.74-3, DOSBox-X or DOSBox Staging, or register its folder in the Tool Doctor.* | DOS builds, runs and debugging need DOSBox. | Windows: install DOSBox 0.74-3 (the default), or unzip DOSBox-X under `%LOCALAPPDATA%\IDEARM\tools`, or register its folder. Linux: `sudo apt install dosbox`. |
| *GDB (the GNU Debugger) was not found.* | 32/64-bit debugging needs GDB. | Windows: `pacman -S mingw-w64-ucrt-x86_64-gdb` in the MSYS2 UCRT64 terminal. Linux: `sudo apt install gdb`. |

A version shown as `unknown` means the program does not declare one; for DOSBox that is harmless. When several
DOSBox versions are installed, `environment = "dosbox"` uses DOSBox 0.74-3 first, then DOSBox-X, then Staging; the
Tool Doctor shows which one that is.

---

## 2. Before the assembler runs

IDEARM checks the project before starting any tool, so these problems show no tool output.

| Message | Cause | Fix |
|---|---|---|
| *DOS tools only accept 8.3 names (...): src/functions.asm* | A DOS source path has a part longer than eight characters, or an extension longer than three. | Rename it (`funcs.asm`). Folder names count too. |
| *Device names such as CON, PRN, AUX or NUL cannot be used in paths: ...* | Windows and DOS reserve these names, with any extension. | Rename the file (`con.asm` → `console.asm`). |
| *Two paths name the same file: src/A.asm and src/a.asm* | Two modules differ only in upper/lower case, or produce the same object file name. | Rename one of them. |
| *Source files must end in .asm: ...* | A module is not an `.asm` file. | Put include files in `[sources] include` folders, not in `modules`. |
| *The main source file does not exist: src/main.asm* | `[sources] entry` points to a missing file. | Fix `entry`, or use **Project → Properties...** to check the settings. |
| *idearm.toml is not valid: ...* | A typo in the project file: an unknown field, a string where a list is expected, a TOML syntax error. | The value after the colon names the field or the parser's complaint. |
| *This version of IDEARM does not support project format 2.* | The project was written by a newer IDEARM. | Update IDEARM. |
| *Another task is still running for this project; wait for it to finish or press Stop.* | A build, run or debug session is active. | Wait, or press **Stop** (`Shift+F5`). |
| *The temporary build folder needs an ASCII path without spaces; point IDEARM_STAGING_DIR to one: ...* | DOSBox cannot mount a folder whose path has spaces or accents. IDEARM already tries `%LOCALAPPDATA%\IDEARM\staging`, its short 8.3 name, `%ProgramData%\IDEARM\staging` and `C:\IDEARM\staging` (on Linux `~/.cache/idearm/staging`, then `/tmp/idearm-<user>/staging`); none of them could be used. | Set `IDEARM_STAGING_DIR`, for example to `C:\IdearmStaging`, and restart IDEARM. Native (NASM) projects are not affected. |
| *The build took too long and was stopped. A tool may be waiting for input.* | A DOS tool asked a question (LINK prompts when an argument is missing) or the machine is very slow. | Read the Build Log. For slow machines, set `IDEARM_BUILD_TIMEOUT_SECONDS` (default 60). |

---

## 3. Assembler errors

The Problems row shows the file and line; double-click it.

### TASM

```text
**Error** S:\ERRSYM.ASM(12) Undefined symbol: PRINTNUMBER
**Error** S:\ERRSYN.ASM(12) Illegal instruction
**Error** S:\ERRSYN.ASM(13) Need address or register
**Fatal** S:\NOEND.ASM(19) Unexpected end of file encountered
```

- *Undefined symbol*: a misspelled label or variable, or a symbol defined in another module that is missing an
  `EXTRN` here (and a `PUBLIC` there).
- *Illegal instruction*: a typo in the mnemonic, or an instruction the selected processor directive (`.8086`)
  does not allow.
- *Unexpected end of file*: the file has no `END` directive, or a `PROC`/`SEGMENT`/`MACRO` is never closed.

TASM reports `S:\...` paths because it runs inside DOSBox-X on a copy of your sources; IDEARM maps them back to
your files.

### MASM 6.11

```text
C:\...\CPU186.ASM(8): error A2001: immediate operand not allowed
C:\...\CPU186.ASM(9): error A2070: invalid instruction operands
```

The `A2xxx` code appears in the **Code** column; search for it together with "MASM 6.11" for a longer
explanation. `A2001` on an 8086 program usually means `PUSH 5` or `SHL AX, 4`, which need an 80186.

### NASM

```text
src/main.asm:5: error: instruction expected, found `movx eax'
src/main.asm:4: error: symbol `undefined_value' not defined
src/main.asm:2: warning: careful [-w+user]
nasm: fatal: unable to open input file `nothere.asm' No such file or directory
```

- NASM stops at the first kind of error it finds: fix the syntax errors first, then build again to see the
  symbol errors.
- *symbol ... not defined*: a missing label, or an external function without `extern`.
- NASM is case-sensitive: `Message` and `message` are different symbols.

### Educational warnings

After every DOS build IDEARM lists warnings such as *The procedure main never returns to DOS (MOV AH, 4Ch /
INT 21h, or .EXIT), so the program will not end cleanly.* They do not stop the build, but each one describes a
real bug: a program without an exit keeps running into whatever bytes follow it.

---

## 4. Linker errors

### TLINK and LINK

```text
Error: Undefined symbol PRINTNUMBER in module S:\EXTERN.ASM
Error: HELPER defined in module S:\DUPA.ASM is duplicated in module S:\DUPB.ASM
Warning: No stack
```

- *Undefined symbol ... in module*: the module uses a symbol that no linked module declares `PUBLIC`. Check that
  the defining file is listed in `[sources] modules` and declares the symbol `PUBLIC`.
- *... is duplicated*: two modules define the same public symbol; typically two files each have their own
  `main PROC` and `END main`. Only the entry module may name the entry point after `END`.
- *No stack*: the program has no `.STACK` directive (or `STACK` segment). Add `.STACK 100h`.

### GNU ld (32/64-bit)

```text
src/main.asm:31: undefined reference to `WriteFileX'
cannot find entry symbol main; defaulting to 0000000140001000
cannot find -lkernel33: No such file or directory
```

- *undefined reference to `WriteFile'*: the name does not exist in `kernel32`. Check the spelling; in 32-bit
  programs Windows functions carry their decoration: `_WriteFile@20`, `_ExitProcess@4`, `_GetStdHandle@4`.
  Functions from other DLLs (`user32`, `msvcrt`) are not linked by the IDE yet.
- *cannot find entry symbol main*: the program has no `global main` (64-bit and Linux) or `global _main`
  (32-bit Windows), or the label is spelled differently (`_start`, `Main`). ld only warns about this and still
  writes a program that would start at an arbitrary instruction, so IDEARM fails the build.

In a debug build the row points at the line of the failing `call`; in a release build ld only knows the file.

---

## 5. Running the program

| What you see | Cause | Fix |
|---|---|---|
| The console window opens and closes at once | The program ended; its window closes with it. | Use **Run → Run (Pause on Exit)**, or `keep-open = true` in `[run]`. |
| *Program finished (exit code -1073741819 (0xC0000005))* | The program crashed with an access violation: it read or wrote memory it does not own. | Debug it (`F5`) and step up to the crash. Common causes in 64-bit code: a misaligned stack (missing `sub rsp, 40` before a `call`), a missing shadow space, or a wrong pointer. |
| *Program finished (exit code 5)* | The value passed to `ExitProcess` (or `INT 21h`, function `4Ch`, in `AL`). | Nothing to fix when that is what your program returns. |
| Nothing printed in a DOS program | DOSBox closed before you could read it. | Use **Run (Pause on Exit)**. |
| *Declared runtime resource does not exist: data/map.txt* | `[resources] files` lists a missing file. | Fix the path; it is relative to the project folder. |
| *Resource files must be inside the project: ...* | A resource path leaves the project (`../`). | Copy the file into the project. |
| *Program arguments cannot contain & \| < > ^ % or quotes: ...* | The arguments are written into a batch file (DOS programs, and Windows programs in their console window), where these characters are commands. DOS arguments must also be plain ASCII. | Remove them from `[run] args`. |
| *A DOS program receives at most 126 characters of arguments; shorten [run] args.* | DOS keeps the command tail in 127 bytes. | Pass less, or read the values from a resource file. |
| *[run] cycles must be auto, max, max N%, fixed N or a number: ...* | `cycles` in `idearm.toml` is not a DOSBox speed setting. | Use `"auto"`, `"max"`, `"max 80%"`, `"fixed 3000"` or `"3000"`. |
| *[run] memsize must be between 1 and 63 MB: ...* | DOSBox 0.74-3 cannot give a program more than 63 MB. | Use a value from 1 to 63 (16 is the default). |
| *DOS programs can open data/inventory.spr (not an 8.3 name) only in DOSBox-X; choose DOSBox-X in Project Properties.* (information) | DOSBox 0.74-3 and Staging show long names only as 8.3 aliases (`INVENT~1.SPR`), so the program cannot open the name it asks for. | In **Project → Properties...** choose **DOSBox-X** as the run environment, or rename the file to 8.3. |
| A DOS program prints *This operation is not permitted in secure mode.* | IDEARM locks DOSBox after mounting the program's folder, so a program cannot `MOUNT` your disk. | Nothing to fix: declare the files the program needs in `[resources] files`. |
| *This machine cannot run linux-elf64 programs.* | Linux programs run on Linux. | Choose a Windows profile, or open the project on Linux. |
| A DOS program cannot open its data file | The program opens it relative to the current folder. | Declare it in `[resources] files`; it is copied to the same relative path next to the program. |
| Linux: a native program seems to hang | It is waiting for input: on Linux its console is the **Terminal Output** panel. | Type a line in the field below the output and press `Enter`. From the command line, type in the terminal. |
| Linux: the program runs and ends, but no DOSBox window ever appears | IDEARM was started without a graphical session (neither `DISPLAY` nor `WAYLAND_DISPLAY` is set), so DOSBox runs without a window. | Start IDEARM from your desktop session, not from an SSH or text console. |

A DOS program always runs from a copy, so it cannot change your project files. The same is true for native
programs, but they run directly on the operating system and can reach the rest of your disk.

---

## 6. Debugging

| What you see | Cause | Fix |
|---|---|---|
| *Breakpoint src/main.asm:12 could not be set: the line has no code, or the program was built without debug information.* | The line is a comment, a label alone, data, or an `equ`. | Move the breakpoint to an instruction. |
| The program runs to the end without stopping | No breakpoint is on an instruction that runs. | Check the Terminal Output panel for the message above. Without breakpoints, GDB stops at `main` and the emulator at the entry point. |
| The debugger stops, but no line is marked | Execution is inside Windows or a DLL, where there is no source. | **Step Out** (`Shift+F11`) or **Continue** (`F5`). |
| Watch shows `<error>` | The expression is not valid at this stop: a 64-bit register in a 32-bit program, an unknown label, or a label in the built-in emulator (which does not know label names). | See the watch syntax table in the [user guide](user-guide.md#66-watches). |
| Memory dump is empty | The address is not readable by the program (32/64-bit), or the text is not a hexadecimal number or register name. | Try `RSP`/`ESP`, or the value a watch such as `message` shows. |
| *No debugger can debug linux-elf64 programs on this machine.* | GDB debugs programs the current machine runs. | Debug Linux programs on Linux. |
| Turbo Debugger shows no source | The program was built in release, or TD cannot find the source file. | Debug builds add `/zi` and `/v` automatically; press `F5` rather than running `TD` by hand. |
| *Turbo Debugger (TD.EXE) was not found...* | TD is not next to the registered TASM. | Put `TD.EXE` in the TASM folder and register the folder again, or use `backend = "emu8086"`. |
| The IDE panels stay empty while debugging a DOS program | `backend = "external"` runs Turbo Debugger or CodeView, which show their own panels in the DOSBox window. | Use `backend = "emu8086"` to see registers, memory and watches in the IDE. |
| Linux: under GDB, a program that reads the keyboard gets nothing (0 bytes) | GDB's input carries the IDE's commands, so the program's input is empty while debugging. | Run it with `Ctrl+F5` to type input, or debug it with the input written into the program. |

### The built-in 8086 emulator (`backend = "emu8086"`)

When the program does something the emulator cannot continue from, the debugger **stops on that line**, marks it
with **▶**, and explains why in **Terminal Output**, **Problems** and the status bar. Inspect the registers and
memory; **Continue** then ends the program with exit code 255.

| Message | Cause | Fix |
|---|---|---|
| *The program is waiting for the keyboard: type in the Output panel.* (status bar) | The program reads a key or a line (`INT 21h` functions `01h`, `07h`, `08h`, `0Ah`, or `INT 16h`). | Type in the field below **Terminal Output**. Each key goes to the program as on a real PC: `Enter` ends a line, `Backspace` deletes; the program itself echoes what you type. |
| *Division by zero, or a quotient too large for its register (DIV/IDIV/AAM). The debugger stopped on that line; continuing ends the program.* | The divisor is 0, or the result does not fit: `DIV BL` needs a quotient below 256, `DIV BX` below 65 536. | Check the divisor before dividing, and clear `DX` (or `AH`) before an unsigned `DIV`. |
| *The built-in emulator does not implement opcode ...h. Debug this program with the external debugger (Turbo Debugger or CodeView).* | An instruction the 8086 does not have, or a jump that landed in data. | Check the jumps near the line; for 80186+ programs use the external debugger. |
| *The built-in emulator does not provide INT 33h, and the program installed no handler for it.* | The program calls a service the emulator does not have (mouse, timers, sound...). | Use the external debugger (`backend = "external"`). |
| *INT 21h function 09h found no '$' ending the string at ...* | The message passed in `DX` has no `$` terminator, so DOS would print memory until it finds one. | Add `'$'` at the end of the message (`msg db 'Hola', 13, 10, '$'`). |
| *The built-in emulator does not provide INT 21h function 3Dh; it returned without doing anything.* (warning) | File, date and other DOS services are not emulated; the program keeps running with the registers unchanged. | Debug programs that use them with the external debugger. |

---

## 7. Packaging

| Message | Cause | Fix |
|---|---|---|
| *Packaging needs a successful release build, which failed with 2 problem(s).* | Packaging always builds the release configuration first. | Fix the problems listed in Problems. |
| *The dist folder has files the IDE did not create; move them before packaging: ...* | `dist/` already existed and was not written by IDEARM. | Move your files elsewhere; IDEARM owns `dist/`. |
| *Programs for ... cannot be packaged yet.* | No packager exists for that target. | — |
| A `Resource ... exceeds DOS 8.3 naming` warning | A DOS resource has a long name. | The package still works with DOSBox-X (long file names are on); rename the file if the program must run on real DOS. |

---

## 8. Getting more information

- **Build Log** shows every command with its arguments. Copy one into a terminal to reproduce the problem
  outside the IDE.
- The command line prints the same problems: `scripts/idearm.ps1 build <project> --config debug` (on Linux
  `sh scripts/idearm.sh ...`), or with `--json` for a machine-readable result.
- `scripts/idearm.ps1 doctor` lists every tool, version and path IDEARM will use.
- Temporary build and run folders live in `%LOCALAPPDATA%\IDEARM\staging` (on Linux `~/.cache/idearm/staging`) and
  are removed after use. If one is left behind after a crash, it is deleted automatically after a few hours.
- If the IDE shows *IDEARM could not start.*, the dialog gives the reason below it; include it when you report
  the problem.
- When reporting a bug, include the Build Log, the Tool Doctor list and your `idearm.toml`.
