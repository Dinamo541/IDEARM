# ADR-010: Declared debugger capabilities and the built-in emulator as the default

Date: 2026-09-23. Status: accepted. Supersedes the open decision **D1** of [`docs/action-plan.md`](../action-plan.md)
and implements **P2-02** and **P2-03**.

## Context

`DebugSession` declared everything beyond `exit`, `state` and `stop` as a default method that does nothing.
A backend could therefore implement four methods and still satisfy the port, which is what
`DosBoxDebugSession` does: Turbo Debugger and CodeView run the program inside a DOSBox window and report
nothing back, so `stepInto`, `stepOver`, `resume`, `registers` and `readMemory` all inherited empty bodies.

The workbench enabled its debug controls on `paused` alone. Since that backend was also the default for new and
imported DOS projects (`[debug] backend = "external"`), the out-of-the-box experience was: press F5, watch the
step buttons light up, press F10, and nothing happens, with a register table full of zeros. The built-in 8086
emulator did all of it correctly but was reachable only by editing `idearm.toml` by hand.

The technical plan (§14, FR-32) had also promised a disassembly view and hex/dec/bin bases that no code provided.

## Decision

- **A capability is declared, not assumed.** `DebugCapability` (`STEP`, `PAUSE`, `BREAKPOINTS`, `REGISTERS`,
  `MEMORY`, `WATCHES`, `CALL_STACK`, `PROGRAM_INPUT`, `DISASSEMBLY`) and `DebugSession.capabilities()`, empty by
  default. The emulator and GDB declare what they do; `DosBoxDebugSession` declares nothing. Every debug control
  in the panel, the menus, the toolbar and the palette is bound to a capability, and a session that declares
  none replaces the panels with an explanation of where the debugging is happening.
- **The built-in 8086 emulator is the default debugger of DOS projects** (decision D1), for new projects,
  imported ones, and any project whose `idearm.toml` does not say otherwise. It needs no proprietary tool, and
  every panel works with it. Turbo Debugger and CodeView stay available as an explicit choice.
- **The debugger is chosen in the user interface**, in the New Project wizard and in Project Properties, and
  saved to `[debug] backend`. When TD or CV is missing, the diagnostic names the emulator as the way out.
- **Pause exists** (`DebugSession.pause()`, F6). The emulator's run loops honour a pause flag. GDB needs
  `-gdb-set mi-async on` first: spike S9 showed that in GDB's default synchronous mode `-exec-interrupt`
  receives no reply at all while the program runs. On Windows the interrupt arrives on a thread the operating
  system injects, standing in `ntdll!DbgBreakPoint` with no source line, so the session selects the program's
  own thread before reporting where it stopped.
- **Breakpoints follow a live session** (`DebugSession.setBreakpoints`). They were a snapshot taken at launch,
  so one set during a pause did nothing until the next run.
- **The disassembler is a text-only decoder** (`Disassembler8086`), separate from `ModRmDecoder`, which computes
  effective addresses from live registers and must stay an execution concern.

## Consequences and limits

A project that keeps `backend = "external"` now says plainly that the IDE cannot step or show registers with
it, instead of offering controls that do nothing. Existing projects without a `[debug]` section change
debugger, from one that showed nothing to one that works; the setting is one dropdown away in Properties.

`DebugCapability` does not replace `EnvCapability`, which still describes *run* environments; P3-07 may still
remove that one. The application layer still selects the backend by comparing ids and toolchain names
(`DebugProject`), which rule 4 forbids and P3-01 is meant to fix; this change adds one more branch there, so
that debt grew.

The disassembler covers the instruction set `Cpu8086` executes and reports anything else as `DB xx`, one byte
long, so the panel stays aligned. It is verified against the machine code column of a real TASM 4.1 listing in
`fixtures/listings`, comparing mnemonic and length. GDB's disassembly comes from `-data-disassemble`.

Pause cannot interrupt an emulated program that is blocked waiting for a key; that program is already stopped
from the user's point of view and reports `WaitingForInput`.
