# Expected Trace: 03-loops-and-branches

This document specifies the trace of conditional branch outcomes and repeated breakpoint hits
during loop execution.

## Branch Decisions

1. `CMP AX, 10` followed by `JE branch_equal`:
   - `ZF=1`, jump is taken. Dead branch `mov bx, 0DEADh` is skipped.
2. `CMP AX, 20` followed by `JL branch_less`:
   - `SF != OF`, jump is taken. Dead branch `mov bx, 0BEEFh` is skipped.
3. `JCXZ jcxz_taken` with `CX=0`:
   - `CX == 0`, jump is taken. `mov ax, 0FFFFh` is skipped.

## Repeated Breakpoint Invariant

A breakpoint is placed at the head of the loop on `add dx, ax` (offset of `loop_start`):

| Hit # | CX before execution | DX before execution | Action | Next state |
|---|---|---|---|---|
| **1st Stop** | `CX=4` | `DX=0` | User resumes (continue) | Executes `add dx, ax`, `loop loop_start`, traps at hit 2 |
| **2nd Stop** | `CX=3` | `DX=5` | User resumes (continue) | Executes `add dx, ax`, `loop loop_start`, traps at hit 3 |
| **3rd Stop** | `CX=2` | `DX=10` | User resumes (continue) | Executes `add dx, ax`, `loop loop_start`, traps at hit 4 |
| **4th Stop** | `CX=1` | `DX=15` | User resumes (continue) | Executes `add dx, ax`, `CX` becomes 0, loop terminates! |
| **After Loop** | `CX=0` | `DX=20` | Normal flow continues | Program advances past loop without re-trapping |

**Crucial Invariant:** When stopped at a breakpoint on `add dx, ax`, calling `resume()` MUST advance execution past the current instruction and not immediately pause on the exact same instruction without making progress.
