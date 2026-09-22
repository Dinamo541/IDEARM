# IDEARM

A desktop IDE for x86 Assembly, made for students. Create a project, write your program, and press one key to
build, run, debug or package it. IDEARM drives the assembler, the linker, DOSBox and the debugger for you, and
shows every command it runs, so nothing about Assembly is hidden.

- **16-bit DOS programs** with your own TASM or MASM 6.11, built and run in DOSBox (0.74-3 by default, or
  DOSBox-X / Staging). Builds run without a window.
- **32-bit and 64-bit Windows programs** and **64-bit Linux programs** with NASM, GNU ld and GDB.
- **A visual debugger**: breakpoints, stepping, registers and flags, call stack, watches and a memory dump, with
  a built-in 8086 emulator for DOS programs (it reads the keyboard and stops on runtime errors) and GDB for
  native ones.
- **An editor that knows Assembly**: highlighting, completion, hover help for instructions and numbers, go to
  definition, references, and warnings for common mistakes such as a program that never returns to DOS. Opens
  UTF-8 and Windows-1252 sources and saves them in the same format.
- **Safe by default**: programs run from a copy, never from your project folder, and DOSBox is locked so a
  program cannot mount the rest of your disk.
- **English and Spanish** interface, switchable at any time; light and dark themes.

## Requirements

- Windows 10 or 11 (64-bit), or 64-bit Linux (verified on Ubuntu 24.04). macOS should work but has not been
  tested.
- To build IDEARM: JDK 25 and Maven 3.9.
- For DOS projects: DOSBox (0.74-3, DOSBox-X or Staging), and your own copy of TASM/TLINK or MASM 6.11. IDEARM
  never includes or downloads proprietary tools.
- For 32/64-bit projects: NASM, GNU ld and GDB. On Windows, with MSYS2:

```bash
pacman -S --needed mingw-w64-ucrt-x86_64-nasm mingw-w64-ucrt-x86_64-binutils mingw-w64-ucrt-x86_64-gdb
```

On Debian or Ubuntu, DOSBox and the native tools come from the distribution:

```bash
sudo apt install dosbox nasm binutils gdb
```

## Getting started

```bash
mvn install -DskipTests
```

```bash
mvn -f idearm-app/pom.xml javafx:run
```

Then open **Help → Tool Doctor...** to check that your tools were found (or register their folder), and create a
project with **File → New Project...**. The sample projects in `examples/` open directly.

A command line is also available after the build:

```powershell
.\scripts\idearm.ps1 build examples/hello64-nasm --config debug
```

On Linux: `sh scripts/idearm.sh build <project> --config debug`.

`scripts/package-native.ps1` produces a Windows installer or a portable application folder. On Linux, IDEARM runs
from source for now.

## Documentation

- [User guide](docs/user-guide.md): DOS and 32/64-bit workflows, the debugger, shortcuts, the command line.
- [Troubleshooting](docs/troubleshooting.md): what each build, link, run and debug problem means and how to fix it.
- [Keyboard shortcuts](docs/workbench-shortcuts.md).
- [PLAN.md](PLAN.md): project brief, architecture, status and verified facts, for contributors.
- [Technical plan](docs/technical-plan.md) and [architecture decisions](docs/adr/).

## Building and testing

```bash
mvn test
```

Tests that need real tools are tagged (`requires-tasm`, `requires-masm`, `requires-nasm`, `requires-dosbox`) and
skipped by default. With TASM, DOSBox, NASM, ld and GDB registered on your machine:

```bash
mvn test -Plocal-tools
```

Continuous integration (`.github/workflows/ci.yml`) runs the tests on Windows, and on Ubuntu with the real NASM,
GNU ld, GDB and DOSBox 0.74-3.

## License

IDEARM is released under the [MIT License](LICENSE). TASM, TLINK, Turbo Debugger, MASM, LINK and CodeView are
proprietary products of their owners; they are not part of this repository.
