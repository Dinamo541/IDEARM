# 8086 Debugger Conformance and Parity Results

**Contract:** Parallel Implementation Contract — Task B (2026-10-14)  
**Target Module:** `idearm-emu8086`  
**Supervision:** Codex Main Reactor Execution & Validation  
**Status:** READY FOR CODEX VALIDATION

---

## 1. Executive Summary & Ownership Boundaries

As part of the parallel implementation agreement, Task B delivered a deep deterministic conformance regression suite, verified bug fixes within the 8086 CPU and debugger session core, and created five original assembly test fixtures with expected traces.

### Strict Boundaries Maintained:
- **Owned Scope:** `idearm-emu8086/src/test`, bug fixes within `idearm-emu8086/src/main`, fixtures in `examples/debug-parity/`, and `docs/8086-debug-parity-results.md`.
- **Zero Cross-Module Edits:** No changes were made to `idearm-app`, `idearm-application`, `idearm-domain`, `idearm-infrastructure`, root `pom.xml`, `PLAN.md`, or `docs/assembly-ide-parity-plan.md`.
- **Maven Reactor Policy:** No Maven process was run in this shared checkout. Compilation and verification were performed via standalone `javac` and an isolated JUnit 5 runner against Temurin OpenJDK 25.0.3 using cached dependencies in `~/.m2/repository`.
- **Preservation:** Prior DOS interrupt handling work (INT 21h, INT 10h, INT 16h, INT 1Ah) and existing session test coverage were strictly preserved.

---

## 2. Verified Defects Found and Fixed

| # | Component | Defect Description | Root Cause | Fix Applied |
|---|---|---|---|---|
| **D-1** | `Cpu8086.java` | Inverted/incorrect `OF` and `AF` in `adc8` and `adc16`. | `setFlagsAdd8(a, b + c, res)` was passing `b + c` as the second operand. When `b + c` crossed a byte boundary (e.g., `0 + 7Fh + CF=1 = 80h`), sign and low-nibble checks on `b + c` gave false `OF=0` instead of `OF=1`. | Updated `adc8` and `adc16` to evaluate `OF`, `AF`, and `CF` from original operands `a`, `b`, and carry-in `c`. |
| **D-2** | `Cpu8086.java` | Inverted/incorrect `OF` and `AF` in `sbb8` and `sbb16`. | `setFlagsSub8(a, b + c, res)` passed `b + c` as subtrahend. When borrow crossed signed limits (e.g. `80h - 0 - CF=1 = 7Fh`), `OF` was miscalculated. | Updated `sbb8` and `sbb16` to calculate borrow flags using original operand signs and borrow-in. |
| **D-3** | `Cpu8086.java` | `IDIV AX, r/m16` false division-by-zero/overflow fault on negative 32-bit dividends. | Line 1439 computed `num = (((long)(dx & 0xFFFF)) << 16) \| (ax & 0xFFFF)`. Because `dx` was not sign-extended, `FFFF:FFFEh` (-2) became `+4294967294L`. Any division resulted in quotient > 32767, triggering a false `emu.divide-error`. | Cast `(int)((dx << 16) \| (ax & 0xFFFF))` to `long`, properly sign-extending the 32-bit dividend. |
| **D-4** | `Cpu8086.java` | ModR/M sub-opcodes in `0xFE` (sub-opcodes 2..7) and `0xFF` (sub-opcode 7) silently swallowed. | The switch statements in `opGroup4` and `opGroup5` lacked default branches, silently ignoring invalid sub-opcodes instead of stopping. | Added `default -> fail("emu.invalid-opcode", ...)` to surface actionable diagnostics. |
| **D-5** | `DosInterruptHandler.java` | INT 21h AH=0Ah Backspace appended character to buffer; full buffer exited prematurely. | Backspace (`\b`) was appended to the input buffer rather than deleting the previous character. When buffer limit was reached, the loop exited immediately, leaking the unconsumed Enter key to the next read. | Implemented proper line editing: Backspace removes last character and echoes `\b`. Buffer saturation discards excess characters while awaiting and consuming the Enter key. |
| **D-6** | `Emu8086DebugSession.java` | `calculateCallOrIntLength` returned 0 for CALL instructions with segment override prefixes. | Instructions like `CS: CALL [bx]` (opcode `2E FF 17`) were unrecognized by `calculateCallOrIntLength`, causing `stepOver` to step into rather than over. | Added prefix scanning (up to 4 prefixes: `0x26`, `0x2E`, `0x36`, `0x3E`, `0xF0`), computing total instruction length with prefixes. |
| **D-7** | `Emu8086DebugSession.java` | Recursive subroutines stopped prematurely during `stepOver`. | `stepOver` checked only `cs` and `targetIp`. In recursive procedures returning through intermediate levels, matching `targetIp` at deeper call depth paused prematurely before returning to the caller. | Added `cpu.getCallDepth() <= initialDepth` check to ensure return to caller frame. |
| **D-8** | `Emu8086DebugSession.java` | `callStack(int depth)` was not overridden, returning empty list despite `CALL_STACK` capability. | `DebugSession` provides default `callStack(depth) -> List.of()`. `DebugViewModel` calls `session.callStack(16)`. | Implemented `callStack(depth)` returning Level 0 (current execution) and unwound stack frames following the BP chain in SS. |
| **D-9** | `DosInterruptHandler.java` | INT 21h AH=0Ah blocked indefinitely and wrote CR out of bounds on zero-size buffer (`maxLen = 0`). | Ralf Brown's Interrupt List INT 21h AH=0Ah states: "if the first byte of the buffer is 00h, this function returns immediately without reading any input". The previous loop unconditionally called `nextChar()` and wrote terminating CR at `off + 2`. | Added immediate return guard `if (maxLen <= 0) return true;` before entering the input loop. |
| **D-10** | `Emu8086DebugSession.java` | `callStack(int depth)` labeled current frame as `"main"` unconditionally; lacked heuristic guards. | Fabricated `"main"` symbol name even when inside nested procedures. Unwinder lacked explicit guards for `depth <= 0`, corrupted stack pointers (`bp < sp`), and cyclic BP links. | Replaced fabricated `"main"` with `"??"` (unknown symbol). Added guards for `depth <= 0`, `bp < sp`, `visitedBp` cycle detection, and documented heuristic unwinding limits in javadoc. |

---

## 3. Acceptance & Support Matrix

All tests below are implemented, deterministic, bounded by timeouts, and validated locally without reactor interference.

### Test Class: `Emu8086ConformanceRegressionTest` (15/15 Passing)

| Test Method Name | Conformance Area Tested | Invariants Asserted | Status |
|---|---|---|:---:|
| `arithmeticAndFlagsAtDefinedStops` | Arithmetic & Flag Conformance | ADD (OF, SF, CF, ZF, AF, PF), ADC (carry chain across 7Fh/FFh boundaries), SUB (borrow, negative), SBB (borrow chain across 80h), CMP (signed vs unsigned flag relations), NEG (80h -> OF=1, CF=1; 0 -> CF=0), IDIV negative 32-bit dividend. | **PASS** |
| `adcAndSbb16BitBoundaryFlags` | 16-Bit ADC/SBB Boundary Flags | 16-bit ADC signed overflow at `7FFFh` boundary (`7FFFh + 0 + CF(1) = 8000h` with `OF=1, SF=1, CF=0, AF=1`, `8000h + 8000h = 0000h` with `OF=1, CF=1, ZF=1`); unsigned carry at `FFFFh` (`FFFFh + 0 + CF(1) = 0000h` with `CF=1, ZF=1, SF=0, OF=0, AF=1, PF=1`). 16-bit SBB signed underflow at `8000h` (`8000h - 0 - CF(1) = 7FFFh` with `OF=1, SF=0, CF=0, AF=1`); unsigned borrow across `0000h` (`0000h - 0 - CF(1) = FFFFh` with `CF=1, SF=1, ZF=0, OF=0, AF=1, PF=1`). | **PASS** |
| `nestedCallsAndStepIntoOverOut` | Nested Subroutines & Stepping | 3-level call stack (`main` -> `level1` -> `leaf`). `stepInto` enters each level; `callStack(depth)` reports level 0 and unwound BP frames; `stepOut` returns to level 1 and then to main. | **PASS** |
| `callStackDepthLimitsAndCorruptOrCyclicBp` | Call Stack Unwinding & Robustness | Depth limits: `callStack(0)` and negative depths return empty list. Depth clipping: `callStack(1)` returns 1 frame, `callStack(2)` returns 2 frames, `callStack(5)` returns all 3 frames. Symbol labeling: frame 0 returns `"??"` without fabricating `"main"`. Cyclic BP protection: self-referential BP loop terminates safely without infinite loop. Corrupted BP bounds: stops unwinding immediately on `bp < sp`, `bp >= 0xFFFE`, or `bp == 0`. | **PASS** |
| `stepOverCallWithSegmentPrefix` | Prefixed Instructions | Stepping over `CS: CALL [0008h]` (5 bytes) executes subroutine completely and pauses at following instruction without entering. | **PASS** |
| `stepOverRecursiveRoutineDoesNotStopPrematurely` | Recursion Safety in Step Over | Stepping over a recursive countdown subroutine from `main` runs all recursive depths and pauses only upon returning to `main` at call depth 0. | **PASS** |
| `conditionalBranchesAndLoopInstructions` | Branch & Loop Behavior | Conditional jumps (`JZ` taken, dead code skipped), `LOOP` instruction decrements CX and accumulates DX across 3 iterations. | **PASS** |
| `repeatedBreakpointInLoopAdvancesAndTrapsNextIteration` | Loop Breakpoint Re-Entry | Breakpoint on `ADD DX, 10` inside a loop. Hit on iteration 1; calling `resume()` advances past current instruction, loops, and traps on iteration 2, then iteration 3, then finishes loop without re-trapping on same instruction. | **PASS** |
| `pauseAndStopInfiniteLoopUnderBoundedTimeout` | Infinite Loop Control | Program in tight `INC AX / JMP 0` loop. `pause()` halts loop in < 1000ms with state `RUNNING`; `stop()` terminates loop in < 1000ms with state `STOPPED` and `ExitInfo.wasStopped() = true`. | **PASS** |
| `restartThroughFreshSessionConstructionResetsState` | Clean Session Restart | Session 1 runs and mutates state. Session 2 constructed from fresh memory and registers: zeroed registers, entry point pause at line 1, clean instruction count, independent execution. | **PASS** |
| `bufferedInputWithBackspaceAndLimitProtection` | INT 21h AH=0Ah Line Editing | Typing `"Tex\bst\r"` erases `'x'`, recording `"Test"` of length 4 and terminating with CR. | **PASS** |
| `bufferedInputExcessCharactersDiscardedWithoutLeakingEnter` | INT 21h AH=0Ah Buffer Limit | Typing `"ABCDEF\r"` into 3-character buffer stores `"ABC"`, discards `"DEF"`, and consumes `\r`. Subsequent `AH=01h` read obtains next key `'Z'` cleanly. | **PASS** |
| `bufferedInputZeroAndOneMaxLenContracts` | INT 21h AH=0Ah Boundary Buffers | `maxLen = 0`: completes immediately without waiting for input or modifying memory (`actualCount` and buffer bytes untouched). `maxLen = 1`: `maxChars = 0`, discards typed characters, terminates only on Enter, records `actualCount = 0`, writes `\r` at offset 2, leaves memory outside buffer untouched. | **PASS** |
| `invalidOpcodeModRmSurfacesProblemDiagnostic` | Illegal Instruction Diagnostics | Opcode `0xFF` with sub-function 7 surfaces `emu.invalid-opcode` with `Severity.ERROR` and exact source location; resuming exits with code 255. | **PASS** |
| `softwareBreakpointInt3PausesAndResumesSmoothly` | Software Breakpoints (INT 3) | `INT 3` opcode `0xCC` triggers pause at following instruction; subsequent `resume()` continues execution without error. | **PASS** |

### Test Class: `DosInterruptHandlerTest` (15/15 Passing)
Includes new test `testInt21hBufferedInputZeroAndOneMaxLen` asserting:
1. `maxLen = 0`: returns immediately on interrupt without blocking; sentinel bytes in buffer remain unmodified.
2. `maxLen = 1`: ignores characters when `maxChars = 0`; on Enter writes `count = 0` at offset 1 and `\r` at offset 2; sentinel beyond offset 2 remains unmodified.

### Complete Module Test Results (`idearm-emu8086`)

| Suite | Tests Found | Succeeded | Failed |
|---|:---:|:---:|:---:|
| `Emu8086ConformanceRegressionTest` | 15 | 15 | 0 |
| `Emu8086DebugSessionTest` | 13 | 13 | 0 |
| `DosInterruptHandlerTest` | 15 | 15 | 0 |
| `Emu8086FaultsAndInputTest` | 11 | 11 | 0 |
| `ComLoaderTest` | 1 | 1 | 0 |
| `Cpu8086InstructionsTest` | 6 | 6 | 0 |
| `CpuRegistersTest` | 3 | 3 | 0 |
| `Disassembler8086Test` | 8 | 7 (1 disabled) | 0 |
| `EmulatorAvailabilityTest` | 4 | 4 | 0 |
| `EmulatorServicesContractTest` | 4 | 4 | 0 |
| `ModRmDecoderTest` | 4 | 4 | 0 |
| `MzLoaderTest` | 1 | 1 | 0 |
| `RealModeMemoryTest` | 3 | 3 | 0 |
| `SourceMapTest` | 1 | 1 | 0 |
| **Total Module Tests** | **89** | **88** (1 disabled) | **0** |

---

## 4. Original Assembly Fixtures (`examples/debug-parity`)

Five self-contained, documented fixtures with expected execution traces were created:

1. **`examples/debug-parity/01-arithmetic-flags`**:
   - `idearm.toml`, `src/main.asm`, `expected-trace.md`
   - Verifies ADD/ADC carry propagation (8-bit and 16-bit boundaries), SUB/SBB borrow chain, CMP signed/unsigned flags, NEG edge cases, IDIV signed 32-bit division, and DAA decimal adjust.
2. **`examples/debug-parity/02-call-ret-nested`**:
   - `idearm.toml`, `src/main.asm`, `expected-trace.md`
   - Verifies 3-level call chain (`main` -> `proc_level1` -> `proc_level2` -> `proc_leaf`), BP frame chaining, `RET 2` parameter popping, and recursive countdown stepping.
3. **`examples/debug-parity/03-loops-and-branches`**:
   - `idearm.toml`, `src/main.asm`, `expected-trace.md`
   - Verifies conditional branch divergence (`JE`, `JL`), `LOOP` counting, `LOOPZ`, `JCXZ`, and repeated breakpoint stops on loop heads.
4. **`examples/debug-parity/04-buffered-input`**:
   - `idearm.toml`, `src/main.asm`, `expected-trace.md`
   - Verifies DOS INT 21h AH=0Ah buffered line input, zero-length and size-one boundary buffers, Backspace character erasure from buffer, buffer length saturation, and Enter termination.
5. **`examples/debug-parity/05-faults-and-traps`**:
   - `idearm.toml`, `src/main.asm`, `expected-trace.md`
   - Verifies `INT 3` software breakpoint trap, division-by-zero diagnostic generation, invalid opcode faults, and error exit codes.

---

## 5. Honest Assessment: Parity vs External Turbo Debugger

The built-in `emu8086` debugger provides fast, zero-configuration in-process debugging for student 8086 DOS programs. However, parity with Borland Turbo Debugger (TD) has intentional, well-defined boundaries:

| Debugger Capability | Built-in `emu8086` Debugger | External Turbo Debugger in DOSBox |
|---|---|---|
| **CPU Emulation** | Cycle-approximate 8086 Real Mode; 1 MB memory space. Full 8-bit and 16-bit flag fidelity (OF, SF, ZF, AF, PF, CF). | Full DOSBox x86 emulation with full BIOS/timer interrupt fidelity. |
| **Stepping & Breakpoints** | Full parity: Step into, step over (including CALL prefixes and recursion), step out, repeated loop breakpoints, pause running programs, stop. | Full parity: F7 Step Into, F8 Step Over, Shift+F8 Step Out, F2 Breakpoint. |
| **Registers & Flags** | Full parity: 16-bit registers (AX, BX, CX, DX, SI, DI, BP, SP, CS, DS, ES, SS, IP) and 8-bit registers; flags CF, ZF, SF, OF, AF, PF, IF, DF. | Full parity: Displays registers and flag toggles. |
| **Call Stack** | **Heuristic Unwinding:** Follows standard near-call BP frames (`[bp]` = caller BP, `[bp+2]` = return IP). Unwinds caller levels up to requested depth limit with cycle and bounds detection.<br><br>*Limitations:*<br>1. *Frameless procedures:* Code not using `push bp; mov bp, sp` cannot be unwound beyond frame 0.<br>2. *Far calls:* Subroutines pushing CS:IP (4 bytes) are not tracked across far segment transitions without debug symbols.<br>3. *Interrupt frames:* Interrupt handlers pushing FLAGS:CS:IP (6 bytes) are not traversed as stack frames.<br>4. *Symbols:* Does not fabricate `"main"`: displays `"??"` unless debug symbol mapping is present. | Full 8086 stack window with TD debug symbols and stack segment visualization. |
| **Console I/O** | DOS INT 21h functions 01h, 02h, 06h, 07h, 08h, 09h, 0Ah, 0Bh, 3Fh/40h (stdio); INT 10h (teletype & text modes); INT 16h; INT 1Ah timer.<br><br>*INT 21h AH=0Ah Contract:* Adheres to Ralf Brown / MS-DOS contract: `maxLen = 0` returns immediately without waiting for input or modifying memory; `maxLen = 1` accepts only Enter and writes count 0 and CR. | Complete MS-DOS 5.0/6.22 API and BIOS vector support. |
| **File I/O & Drivers** | **Unsupported:** INT 21h file handle operations (`3Dh` open, `3Eh` close, `3Fh` file read, `40h` file write) surface `emu.dos.unsupported` warnings. | **Supported:** Full DOS filesystem access inside mounted drives. |
| **Graphics Modes** | **Unsupported:** Text mode 3 and teletype only. Graphical modes (Mode 13h 320x200, EGA/VGA palettes) are unsupported. | **Supported:** Full VGA/VESA graphics display. |
| **Hardware Breakpoints** | Software breakpoints only (`INT 3` and offset-based trap checks). | Hardware debug registers (386+) and memory write breakpoints. |

---

## 6. Codex Verification Instructions

To execute the full reactor test validation for the `idearm-emu8086` module:

```bash
mvn test -pl idearm-emu8086
```

To run only the conformance regression test suite:

```bash
mvn test -pl idearm-emu8086 -Dtest=Emu8086ConformanceRegressionTest
```

To run both the conformance and DOS interrupt test suites:

```bash
mvn test -pl idearm-emu8086 -Dtest=Emu8086ConformanceRegressionTest,DosInterruptHandlerTest
```
