# Expected Trace: 05-faults-and-traps

This document specifies the fault surfacing and lifecycle progression when illegal or trapping
instructions are executed.

## Trapping Scenarios

1. **Software Breakpoint (`INT 3` opcode `0xCC`):**
   - Pauses debugger at the instruction immediately following `INT 3`.
   - Generates `DebugEvent.Paused` with `AX = 1000h`.
   - On `resume()`, program proceeds smoothly to `mov bx, 2000h` without faulting.
2. **Division Error (`DIV BL` with `BL = 0`):**
   - State becomes `Cpu8086.State.FAULTED`.
   - Generates `DebugEvent.Problem` with code `"emu.divide-error"`, `Severity.ERROR`, and exact line mapping.
   - Pauses at the `DIV` instruction (`CS:IP` points directly at the dividing instruction).
   - Subsequent `resume()` or `step()` ends session with exit code 255 (`FAULT_EXIT_CODE`) and diagnostic message.
3. **Invalid Opcode (`0x0F` or undefined ModR/M):**
   - Generates `DebugEvent.Problem` with code `"emu.invalid-opcode"` and hex opcode in arguments.
   - Halts execution cleanly.
4. **Unsupported Interrupt (`INT 33h` unset vector):**
   - Generates `DebugEvent.Problem` with code `"emu.interrupt.unsupported"`.
   - Prevents branching into memory segment 0000:0000.
