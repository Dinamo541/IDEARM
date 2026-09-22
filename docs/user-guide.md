# IDEARM user guide

IDEARM is a desktop IDE for x86 Assembly. You create a project, write your program, and press one key to build,
run, debug or package it. The IDE drives the assembler, the linker, DOSBox and the debugger for you, and shows
every command it runs in the **Build Log** panel, so nothing about Assembly is hidden.

This guide covers:

1. [Before you start](#1-before-you-start)
2. [A tour of the workbench](#2-a-tour-of-the-workbench)
3. [Registering your tools](#3-registering-your-tools)
4. [16-bit DOS projects (TASM or MASM)](#4-16-bit-dos-projects-tasm-or-masm)
5. [32-bit and 64-bit projects (NASM, GNU ld, GDB)](#5-32-bit-and-64-bit-projects-nasm-gnu-ld-gdb)
6. [The visual debugger](#6-the-visual-debugger)
7. [Keyboard shortcuts](#7-keyboard-shortcuts)
8. [The command line](#8-the-command-line)
9. [Where IDEARM keeps things](#9-where-idearm-keeps-things)

When something fails, see the [troubleshooting guide](troubleshooting.md).

---

## 1. Before you start

| You want to... | You need |
|---|---|
| Use the IDE | Windows 10 or 11 (64-bit), or 64-bit Linux (verified on Ubuntu 24.04). macOS should work but has not been tested. |
| Build 16-bit DOS programs with TASM | Your own copy of **TASM** and **TLINK** (3.x, 4.x or 5.x), and **DOSBox** (0.74-3 is used first; DOSBox-X or Staging also work) |
| Build 16-bit DOS programs with MASM | Your own copy of **MASM 6.11** (`ML.EXE`, the 16-bit `LINK.EXE` and, on Linux, `DOSXNT.EXE`), and **DOSBox** |
| Debug DOS programs in Turbo Debugger or CodeView | `TD.EXE` next to your TASM, or `CV.EXE` next to your MASM |
| Build 32/64-bit Windows programs | **NASM**, **GNU ld** and **GDB**, for example from [MSYS2](https://www.msys2.org/) |
| Build 64-bit Linux programs | **NASM**, **GNU ld** (binutils) and **GDB** from your Linux distribution |
| Build IDEARM from source | JDK 25 and Maven 3.9 |

IDEARM never includes or downloads TASM or MASM: they are proprietary, so you register the copy you already
have (§3). DOSBox, NASM, binutils and GDB are free; install them from their official sites or your
distribution's packages.

To install the 32/64-bit tools with MSYS2, open the **MSYS2 UCRT64** terminal and run:

```bash
pacman -S --needed mingw-w64-ucrt-x86_64-nasm mingw-w64-ucrt-x86_64-binutils mingw-w64-ucrt-x86_64-gdb
```

IDEARM finds them in `C:\msys64\ucrt64\bin` without further setup.

On Debian or Ubuntu, one command installs DOSBox 0.74-3 and the native tools:

```bash
sudo apt install dosbox nasm binutils gdb
```

### Which DOSBox IDEARM uses

With the default `environment = "dosbox"`, IDEARM uses the first one installed of **DOSBox 0.74-3**, DOSBox-X and
DOSBox Staging, for building, running and debugging. **Project → Properties... → Run Environment** picks one on
purpose; choose DOSBox-X when your program opens files with long names (§4.4). Builds run without a DOSBox window,
except when a project chooses DOSBox Staging on purpose: Staging cannot run hidden, so its window flashes briefly.

### Starting IDEARM from source

```bash
mvn install -DskipTests
```

```bash
mvn -f idearm-app/pom.xml javafx:run
```

`scripts/package-native.ps1` builds a Windows installer (or a portable app image when WiX is not installed). On
Linux, run IDEARM from source as above; a packaged Linux build is not provided yet.

---

## 2. A tour of the workbench

- **Explorer** (left): every file and folder of the project, as on disk. Right-click, or use the buttons at the
  top, to create, rename or delete files and folders. Files the IDE creates always get lower-case extensions.
- **Document outline** (below the explorer): the segments, procedures and labels of the open file.
- **Editor** (center): syntax highlighting for MASM/TASM and NASM, completion (`Ctrl+Space`), hover help for
  instructions, registers and numbers (hex/decimal/binary), go to definition (`F12`) and find references
  (`Shift+F12`).
- **Bottom panel**: **Problems**, **Build Log** (every command the IDE ran and what the tools printed),
  **Terminal Output**, **References**, **Debugger** and **Terminal**.
- **Status bar**: what the IDE is doing, the target profile and CPU, the toolchain, the caret position, and the
  encoding of the active file (see below).
- **Top right**: the play/debug/stop buttons, the light/dark theme switch and the **EN/ES** language switch.
  The language changes immediately, including messages already on screen.
- **Command palette** (`Ctrl+Shift+P`): every command, searchable.

### File encodings

IDEARM opens sources saved as UTF-8 and also older ones saved as **Windows-1252** (what Notepad calls "ANSI" on
a Spanish or English Windows), so accents in comments are shown correctly. The status bar shows how the active
file is stored, for example `UTF-8 · LF` or `windows-1252 · CRLF`, and saving keeps that encoding, the byte order
mark if there was one, and the line endings. If you type a character Windows-1252 cannot store, the file is
saved as UTF-8 instead and the status bar says so. Saving never leaves a half-written file: the new text is
written next to the file and then replaces it.

### The Problems panel

Build errors, linker errors and educational warnings are listed together. Double-click a row to open the file at
that line. Messages written by the tools (TASM, MASM, NASM, ld) keep the tool's own words, so you can search for
them online; messages written by IDEARM itself follow the language you selected, and hovering over one shows the
original English text.

---

## 3. Registering your tools

Open **Help → Tool Doctor...** to see every tool the IDE will use: its role, version, kind of program
(`DOS_REAL`, `DOS_DPMI`, `WIN32_CONSOLE`, `WIN64`), path, and whether its files are still there.

IDEARM looks for tools on its own in:

- the folders listed in the `IDEARM_TOOL_PATH` environment variable (separated by `;` on Windows and `:` on
  Linux), and their subfolders;
- `%LOCALAPPDATA%\IDEARM\tools` and its subfolders (a good place to unzip DOSBox-X); on Linux
  `~/.local/share/idearm/tools`;
- common install folders (`C:\TASM`, `C:\masm\MASM611\BIN`, `C:\Program Files (x86)\DOSBox-0.74-3`,
  `C:\Program Files\DOSBox-X`, `C:\msys64\ucrt64\bin`, `C:\Program Files\NASM`, ...; on Linux `/usr/games`);
- every folder on the system `PATH` (where `apt` installs DOSBox, NASM, ld and GDB).

If your TASM or MASM lives anywhere else, click **Add Tools Folder...** and pick the folder (or the folder
above it; three levels of subfolders are searched). The tools found there are remembered in
`%APPDATA%\IDEARM\tools.toml` (on Linux `~/.config/idearm/tools.toml`) and are preferred over the ones found
automatically. The same can be done from the command line:

```powershell
.\scripts\idearm.ps1 tools add "D:\Courses\Assembly\TASM\BIN"
```

On Linux: `sh scripts/idearm.sh tools add ~/courses/assembly/TASM/BIN`.

Versions are read without running DOS programs (they cannot run on 64-bit Windows or on Linux). DOSBox 0.74-3 and
DOSBox Staging both ship as `dosbox`, so IDEARM reads the version text inside the program to tell them apart.
NASM, ld, gcc and GDB are asked for their version. TLINK 4.x and later also need `RTM.EXE` and `DPMI16BI.OVL` in
the same folder; the Doctor shows **Files missing** when a companion file is gone.

---

## 4. 16-bit DOS projects (TASM or MASM)

### 4.1 Creating a project

**File → New Project...** (`Ctrl+Shift+N`):

| Field | Choose |
|---|---|
| Project Name | Letters, digits, `_`, `-` and `.`. The project folder gets this name. |
| Location | The folder that will contain the project folder. |
| Target Profile | `dos-exe-16`: a DOS `.exe` with 16-bit code. |
| Baseline CPU | `8086` for course work. The educational warnings use it to flag instructions your CPU does not have. |
| Toolchain | `borland-tasm` (TASM + TLINK) or `microsoft-masm` (MASM 6.11). |

The IDE creates:

```text
hello/
├── idearm.toml        the project description
├── src/main.asm       a program that prints a greeting and returns to DOS
└── .gitignore         keeps build/ and dist/ out of version control
```

Already have a folder of `.asm` files? Use **File → Import Project...**: IDEARM finds the sources, proposes the
entry file and writes `idearm.toml` for you.

### 4.2 The project file

`idearm.toml` describes *what* to build, never *where your tools are*, so the same project works on any machine:

```toml
schema = 1

[project]
name = "HELLO"
version = "0.1.0"

[target]
profile = "dos-exe-16"
cpu = "8086"

[toolchain]
id = "borland-tasm"
version = ">=3.2"          # an exact version, ">=" a version, or "*"

[sources]
entry = "src/main.asm"     # the module with the program's entry point
modules = ["src/*.asm"]    # other modules to assemble and link (optional)
include = ["include"]      # folders searched by INCLUDE (optional)
exclude = []               # files the patterns above should skip (optional)

[resources]
files = ["data/map.txt", "sprites"]   # copied next to the program for Run, Debug and dist

[build.debug]
debug-info = true
listing = true
map = true

[build.release]
debug-info = false
listing = true
map = true

[run]
environment = "dosbox"     # DOSBox 0.74-3 first, then DOSBox-X, then Staging; force one with "dosbox-0.74", "dosbox-x" or "dosbox-staging"
isolation = "required"
keep-open = true           # the DOSBox window waits for a key when the program ends
cycles = "auto"            # DOSBox speed: "auto", "max", "max 80%", "fixed 3000" or a number (optional)
memsize = 16               # memory in MB, 1 to 63 (optional)
args = []                  # the program's command line: plain ASCII, at most 126 characters (optional)

[debug]
backend = "external"       # "external": Turbo Debugger / CodeView in DOSBox · "emu8086": built-in debugger

[dist]
launcher = true
zip = false
```

DOS tools only understand **8.3 names**: up to eight letters, digits, `_` or `-`, a dot, and up to three more.
IDEARM checks every source path before building and tells you which one is too long.

### 4.3 Building

**Project → Build Project** (`F7`) builds the *debug* configuration. **Clean & Build** (`Shift+F7`) deletes the
previous results first; **Clean** removes only `build/` and `dist/`.

What happens:

1. The sources and the tools are copied to a temporary folder outside the project
   (`%LOCALAPPDATA%\IDEARM\staging`, or `~/.cache/idearm/staging` on Linux). DOSBox cannot use a folder whose
   path has spaces or accents, so when your user folder has one IDEARM picks another folder by itself (§9).
2. DOSBox starts without a window and runs TASM and TLINK (for MASM: ML on Windows and LINK inside DOSBox; on
   Linux both run inside DOSBox, ML through the `DOSXNT.EXE` extender from your MASM `BIN` folder), with debug
   information in the debug configuration (`/zi` + `/v`, or `/Zi` + `/CO`).
3. The results are copied back only when the build succeeds:

```text
build/debug/
├── obj/main.obj
├── lst/main.lst      listing: the machine code of every line
├── map/main.map      linker map
└── bin/main.exe
```

A build that fails, or that you stop with `Shift+F5`, never leaves half-written files in the project. Run,
Debug and Package reuse the last build when no source, include file or setting changed since.

After each build, IDEARM also checks your sources for common mistakes and lists them as warnings, for example:

- a `main` procedure that never returns to DOS (`MOV AH, 4Ch` / `INT 21h`);
- an instruction the selected CPU does not have (`PUSH 5`, `SHL AX, 4` on an 8086);
- a 32-bit register (`EAX`) in an 8086 program;
- a jump or call to a label that does not seem to exist.

### 4.4 Running

**Run → Run** (`Ctrl+F5`) builds if needed and starts the program in a DOSBox window.
**Run (Pause on Exit)** keeps the window open until you press a key, whatever `keep-open` says.

The program runs from a copy of `bin/` and the declared resources, never from your project folder, so a program
that deletes or overwrites files cannot damage your sources. Resources keep their path relative to the project:
`data/map.txt` is at `C:\DATA\MAP.TXT` inside DOSBox, next to the program. After mounting that folder IDEARM
locks DOSBox (secure mode), so the program cannot `MOUNT` other folders of your disk. The status bar shows the
exit code when the program ends; **Stop** (`Shift+F5`) closes DOSBox.

DOSBox 0.74-3 and Staging show long file names only as 8.3 aliases (`INVENT~1.SPR`), so a program that opens
`inventory.spr` finds it only in DOSBox-X. When a declared resource has a long name and another DOSBox runs the
program, the Problems panel says so; choose **DOSBox-X** in **Project → Properties...**, or rename the file.

`[run] cycles`, `memsize` and `args` are checked before DOSBox starts; a value DOSBox would not accept, or that
could smuggle commands into its configuration, stops the run with a message in Problems.

### 4.5 Packaging

**Project → Package (dist/)...** builds the *release* configuration and writes:

```text
dist/
├── main.exe
├── data/map.txt       your resources, with the same layout as in Run
├── dosbox.conf        mounts the dist folder as C: and runs the program
├── run.bat            finds DOSBox-X (or DOSBox) and starts the program on Windows
└── readme.txt         how to run it elsewhere
```

With `zip = true` the IDE also writes `dist/<name>-<version>.zip`. The package never contains TASM, MASM or
your sources.

---

## 5. 32-bit and 64-bit projects (NASM, GNU ld, GDB)

### 5.1 Creating a project

In **New Project**, choose:

| Target Profile | Program | Baseline CPU | Toolchain |
|---|---|---|---|
| `win-pe64-console` | 64-bit Windows console program | `x86-64` | `nasm` |
| `win-pe32-console` | 32-bit Windows console program | `80386` | `nasm` |
| `linux-elf64` | 64-bit Linux program (offered when IDEARM runs on Linux) | `x86-64` | `nasm` |

The generated `src/main.asm` prints a greeting through the Windows API (or, for Linux, the `write` and `exit`
system calls) and exits with code 0. Read it: it shows the calling convention of each target.

**64-bit Windows** passes the first four arguments in `RCX`, `RDX`, `R8` and `R9`, the rest on the stack after
32 bytes of *shadow space* that the caller reserves, and expects the stack aligned to 16 bytes at each `call`:

```nasm
global main
extern GetStdHandle
extern WriteFile
extern ExitProcess

main:
    sub rsp, 40                 ; shadow space, keeps the stack aligned
    mov ecx, -11                ; STD_OUTPUT_HANDLE
    call GetStdHandle
    ...
    mov qword [rsp + 32], 0     ; fifth argument of WriteFile
    call WriteFile
    xor ecx, ecx
    call ExitProcess
```

**32-bit Windows** (stdcall) pushes the arguments from last to first. Symbols carry a leading underscore and,
for the Windows API, `@` and the size of the arguments in bytes; the entry label is `_main`:

```nasm
global _main
extern _GetStdHandle@4
extern _WriteFile@20
extern _ExitProcess@4

_main:
    push -11
    call _GetStdHandle@4
    ...
```

The entry label must be declared `global`: `main` for 64-bit and Linux programs, `_main` for 32-bit Windows.

### 5.2 Building

`F7` runs, from the project folder:

```text
nasm -f win64 -l <lst> -o <obj> src/main.asm                   (release)
nasm -f elf64 -g -F dwarf -l <lst> -o <obj> src/main.asm       (debug)
ld -m i386pep -e main --subsystem console -o <exe> -Map=<map> <obj> -L<System32> -lkernel32
```

For 32-bit programs the formats are `win32` / `elf32` and the linker uses `-m i386pe -e _main` with the 32-bit
system libraries (`SysWOW64`). Debug builds are assembled as ELF objects on purpose: GDB cannot read the
CodeView information NASM attaches to Windows objects, but it reads DWARF, and ld links ELF objects into the same
Windows program.

The outputs are written to a temporary folder and copied into `build/<configuration>/obj`, `lst`, `map` and
`bin` when the build succeeds, exactly as for DOS projects. The Windows system DLLs are linked directly, so no C
runtime or import library is needed.

### 5.3 Running

On Windows, `Ctrl+F5` starts the program in its **own console window**, the way a DOS program opens in DOSBox.
The program runs from a copy of `bin/` and its resources in the staging folder. The status bar shows the exit
code (`ExitProcess` argument, or the `exit` system call's status on Linux). With **Run (Pause on Exit)**, or `keep-open = true`, the window waits for a key before
closing, so you can read the output. **Stop** ends the program and everything it started.

On Linux the program has no window of its own: what it prints appears in **Terminal Output**, and when it
reads the keyboard you type a line in the field below the output and press `Enter` (the line is sent with its
newline, as a terminal would). From the command line, the program uses the terminal you started it from.

Native programs run directly on the operating system, without an emulator: only run code you understand.

### 5.4 Packaging

**Package (dist/)** copies the release program and its resources (same layout as in Run) into `dist/`, plus
`dist/<name>-<version>.zip` when `zip = true`.

### 5.5 Project settings for native targets

```toml
[run]
environment = "host"
isolation = "native"
keep-open = true

[debug]
backend = "gdb"
```

### 5.6 Linux targets

`linux-elf64` programs are built with NASM and GNU ld, and debugged with GDB, on a Linux machine; IDEARM offers
this profile when it runs on Linux. Building, running (with keyboard input) and stopping on a breakpoint were
verified on Ubuntu 24.04 with NASM 2.16, GNU ld 2.42 and GDB 15.1. Under GDB the program's input is empty (a
read returns 0 bytes, as after `Ctrl+D`), because GDB's own input carries the IDE's commands; run the program
with `Ctrl+F5` when it needs the keyboard. On Windows, the MSYS2 linker can only produce Windows
programs, so IDEARM explains this instead of building.

---

## 6. The visual debugger

### 6.1 Which debugger runs

| Project | `[debug] backend` | What happens on Start Debugging |
|---|---|---|
| DOS | `emu8086` | IDEARM's built-in 8086 emulator runs the program; every panel described below works. It stops at the entry point first. |
| DOS | `external` (default) | Turbo Debugger (TASM) or CodeView (MASM) opens inside a DOSBox window with your program and its symbols. You debug there; the IDE panels stay empty. |
| 32/64-bit | `gdb` | GDB runs the program (in its own console window on Windows); every panel works. It runs until the first breakpoint (or stops at `main` when there is none). |

The built-in emulator covers the 8086 instruction set and the console services student programs use: `INT 21h`
character and string input/output and program exit, `INT 10h` teletype output and video mode calls, and `INT 16h`
keyboard input. Programs that open files, draw graphics, or depend on timers or sound are better debugged with
the external backend.

### 6.2 Breakpoints

Click the margin left of a line number, or press `F9` on the line, to toggle a breakpoint (a red dot).
Breakpoints are kept per project in `.idearm/breakpoints.json`, and **Run → Clear All Breakpoints** removes them.

Put breakpoints on lines that contain an instruction. A breakpoint on a comment, a label alone or a data line
cannot stop the program; GDB says so in the **Terminal Output** panel.

### 6.3 Controlling the program

| Action | Key | Toolbar |
|---|---|---|
| Start Debugging / Continue | `F5` | Continue |
| Step Over (runs a whole `CALL` or `INT`) | `F10` | Step Over |
| Step Into | `F11` | Step Into |
| Step Out of the current procedure | `Shift+F11` | Step Out |
| Stop | `Shift+F5` | Stop |
| Restart Debugging | `Ctrl+Shift+F5` | |

The line about to run is marked with **▶** in the editor margin. The program's output appears in the DOSBox
window (external backend), in **Terminal Output** (built-in emulator, and GDB on Linux) or in the program's
console window (GDB on Windows).

### 6.4 Registers

The **REGISTERS** table shows each register in hexadecimal and decimal. Values that changed since the last stop
are shown in red.

- DOS programs show `AX BX CX DX SI DI BP SP CS DS ES SS IP FLAGS`, and one badge per flag
  (`CF ZF SF OF PF AF IF DF`), lit when the flag is set and highlighted when it changed.
- 64-bit programs show `RAX`...`R15`, `RIP`, `EFLAGS` and the segment registers; 32-bit programs show
  `EAX`...`ESP`, `EIP`, `EFLAGS` and the segment registers. The flag badges read `EFLAGS`.

### 6.5 Call stack

The **CALL STACK** tab lists, for GDB sessions, the active procedures from the innermost one:
`#0 0x00007ff65f0c102e in main at C:\...\src\main.asm:31`. The built-in emulator shows the words on the stack
instead: `[SP+02] 1014:0102 = 0000`.

### 6.6 Watches

In the **WATCHES** tab, type an expression and click **Add**. Watches are evaluated again at every stop.
Numbers are shown in decimal and hexadecimal: `4660 (0x1234)`.

| You write | Built-in 8086 emulator | GDB (32/64-bit) |
|---|---|---|
| A register | `AX`, `DL`, `SI`, `FLAGS` | `RAX`, `ecx`, `R8` |
| Register arithmetic | `BX+SI+2`, `100h-1` | `rax + 8` |
| Memory, word or pointer size | `[BX+2]`, `[DS:DX]` (through `DS`, or `SS` when `BP` is used) | `[RSP+32]` (8 bytes in 64-bit programs, 4 in 32-bit) |
| Memory of a given size | `byte [SI]`, `word [DI]` | `byte [message]`, `dword [count]`, `qword [rsp]` |
| A label | — | `message` gives its address |
| GDB syntax | — | anything with `$`, casts or parentheses, e.g. `(char)$al` |

`<error>` means the expression could not be evaluated at this stop (for example `RSP` in a 32-bit program).

### 6.7 Memory dump

The **MEMORY DUMP** tab shows 64 bytes in hexadecimal and ASCII. Type an address and press `Enter` or the
arrow button.

- DOS programs use `segment:offset` in hexadecimal. The first view is the program's data segment (`DS`) at
  offset `0000`.
- 32/64-bit programs use one address: a hexadecimal number (`403000`, `0x403000` or `403000h`) or a register
  name (`RSP`, `rip`). The first view is the stack (`RSP` or `ESP`). An address the program cannot read shows
  nothing.

### 6.8 Hover while paused

While the built-in emulator is paused, hovering over a variable defined with `DB` or `DW` shows its current
value, next to the usual instruction and number help.

### 6.9 Typing for the program (built-in emulator)

When the program reads the keyboard (`INT 21h` functions `01h`, `07h`, `08h` or `0Ah`, or `INT 16h`), the
**Terminal Output** tab opens with a field below the output, and the status bar says the program is waiting.
Type there: each key goes to the program at once, as on a real PC. `Enter` ends a line, `Backspace` deletes the
last character, and the program itself echoes what you type (function `08h`, for example, does not). Try it with
`examples/hello-input`, which asks for your name and greets you.

### 6.10 When the program fails (built-in emulator)

A division by zero, an instruction the emulator does not have, an interrupt with no handler, or an `INT 21h`
function `09h` string without its `$` would make a real PC crash or hang. The emulator instead **stops on that
line**: the line is marked with **▶**, the reason is written in **Terminal Output** and **Problems**, and the
status bar shows it. Look at the registers and memory to find the cause; **Continue** or a step then ends the
program with exit code 255. A DOS function the emulator does not provide (files, for example) only adds a
warning to Problems, and the program goes on. The [troubleshooting guide](troubleshooting.md#the-built-in-8086-emulator-backend--emu8086)
explains each message.

---

## 7. Keyboard shortcuts

| Key | Action |
|---|---|
| `Ctrl+Shift+N` | New project |
| `Ctrl+O` / `Ctrl+S` / `Ctrl+Shift+S` | Open file / Save / Save As |
| `F7` / `Shift+F7` | Build / Clean & Build |
| `Ctrl+F5` | Run |
| `F5` | Start debugging, or continue when paused |
| `F9` | Toggle breakpoint |
| `F10` / `F11` / `Shift+F11` | Step over / into / out |
| `Shift+F5` | Stop the running task |
| `Ctrl+Shift+P` or `F1` | Command palette |
| `Ctrl+Space` / `F12` / `Shift+F12` | Completion / Go to definition / Find references |
| `Ctrl+B` / `Ctrl+J` | Toggle sidebar / bottom panel |

The complete editor list is in [workbench-shortcuts.md](workbench-shortcuts.md).

---

## 8. The command line

Everything the IDE builds can also be built from a terminal, which is handy for scripts and for checking a
project without opening the IDE. After `mvn install -DskipTests`:

```powershell
.\scripts\idearm.ps1 build examples/hello --config debug
```

On Linux (and macOS) use `sh scripts/idearm.sh` with the same commands. There, `run` shows the program's output
in the terminal and passes what you type to it.

| Command | What it does |
|---|---|
| `build <dir> [--config debug\|release] [--json]` | Builds (release by default) |
| `run <dir> [--release] [--json]` | Builds if needed and runs the program |
| `dist <dir> [--json]` | Builds the release configuration and fills `dist/` |
| `clean <dir> [--json]` | Removes `build/` and `dist/` |
| `import <dir> [--toolchain <id>] [--json]` | Writes `idearm.toml` for an existing folder of sources |
| `doctor` | Lists the tools IDEARM will use |
| `tools add <folder>` | Registers the tools found in a folder |

---

## 9. Where IDEARM keeps things

| Location (Windows) | Location (Linux) | Contents |
|---|---|---|
| `<project>/idearm.toml` | same | The project description; commit it. |
| `<project>/build/`, `<project>/dist/` | same | Generated; Clean deletes them. |
| `<project>/.idearm/` | same | Breakpoints and a lock file for this machine; do not commit it. |
| `%APPDATA%\IDEARM\tools.toml` | `~/.config/idearm/tools.toml` | The tools you registered. Delete it to forget them. |
| `%APPDATA%\IDEARM\recent.json` | `~/.idearm/recent.json` | Recent projects and files. |
| `%LOCALAPPDATA%\IDEARM\staging` | `~/.cache/idearm/staging` | Temporary build and run folders; old ones are removed automatically. |
| `%LOCALAPPDATA%\IDEARM\tools` | `~/.local/share/idearm/tools` | A place for tools you unzip yourself, such as DOSBox-X; searched automatically. |

DOSBox can only use a temporary folder whose path is plain ASCII without spaces. When your user folder has a
space or an accent (`C:\Users\Juan Pérez`), IDEARM uses the first of these that works: the short 8.3 name of
`%LOCALAPPDATA%\IDEARM\staging`, `%ProgramData%\IDEARM\staging`, then `C:\IDEARM\staging`; on Linux,
`/tmp/idearm-<user>/staging`, readable only by you.

| Environment variable | Effect |
|---|---|
| `IDEARM_TOOL_PATH` | Extra folders to search for tools, separated by `;` on Windows and `:` on Linux. |
| `IDEARM_STAGING_DIR` | Another temporary folder; it must be outside your projects and, for DOS builds, have an ASCII path without spaces. |
| `IDEARM_BUILD_TIMEOUT_SECONDS` | How long a build may take before it is stopped (60 seconds by default). |
