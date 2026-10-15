# Expected Trace: 02-call-ret-nested

This document specifies the call stack frames, call depths, and step stepping boundaries
for the nested procedure calls fixture.

## Call Chain Sequence

```
main (depth 0)
  └─ proc_level1 (depth 1, pushes BP, reads parameter [BP+4])
       └─ proc_level2 (depth 2, pushes BP, chains frame)
            └─ proc_leaf (depth 3, adds 20h, executes RET)
```

## Step Behaviors

1. **Step Over from main on `call proc_level1`:**
   - Evaluates subroutine in its entirety, stopping at `mov bx, ax` with `AX = 0010h + 5 + 10 + 20 = 0033h (51 decimal)`.
   - Call depth returns to 0 without stopping at intermediate frames.
2. **Step Into from main on `call proc_level1`:**
   - Pauses on first instruction of `proc_level1` (`push bp`).
   - Call depth increments to 1.
3. **Step Out from `proc_leaf`:**
   - Resumes until `RET` at depth 3 returns control to `proc_level2` immediately after `call proc_leaf`.
   - Call depth becomes 2.
4. **Recursive Countdown Stack:**
   - `proc_countdown` called with `CX=3`: reaches recursive depths 1, 2, 3, and 4.
   - At deepest recursion (`CX=0`), stack contains 4 nested frames chained through `[BP]`.
   - Stepping out from innermost frame unwinds to depth 3, then 2, then 1, and back to main.
