# Expected Trace: 01-arithmetic-flags

This document specifies the exact deterministic register and flag state at each breakpoint/pause stop
for the `01-arithmetic-flags` fixture running on the 8086 CPU.

| Stop # | Description | Instruction Executed | Register State | Flags (CF, ZF, SF, OF, AF, PF) |
|---|---|---|---|---|
| **Stop 1** | Signed Overflow | `ADD AX, 1` (from `7FFFh`) | `AX=8000h` | `CF=0, ZF=0, SF=1, OF=1, AF=1, PF=0` |
| **Stop 2** | Carry & Zero | `ADD AX, 1` (from `FFFFh`) | `AX=0000h` | `CF=1, ZF=1, SF=0, OF=0, AF=1, PF=1` |
| **Stop 3** | ADC with Carry | `ADC BX, 0` (with `CF=1`) | `BX=0001h` | `CF=0, ZF=0, SF=0, OF=0, AF=0, PF=0` |
| **Stop 4** | SUB Borrow | `SUB CX, 10` (from `0005h`) | `CX=FFFBh` | `CF=1, ZF=0, SF=1, OF=0, AF=1, PF=0` |
| **Stop 5** | SBB Borrow Chain | `SBB DX, 1` (from `1000h`, `CF=1`) | `DX=0FFEh` | `CF=0, ZF=0, SF=0, OF=0, AF=0, PF=1` |
| **Stop 6** | CMP Signed vs Unsigned | `CMP AL, 5` (where `AL=FBh` / -5) | `AL=FBh` (unmodified) | `CF=0, ZF=0, SF=1, OF=0, AF=0, PF=1` (`SF != OF` -> `JL`) |
| **Stop 7** | NEG of 80h (-128) | `NEG AL` (from `80h`) | `AL=80h` | `CF=1, ZF=0, SF=1, OF=1, AF=0, PF=0` |
| **Stop 7b** | NEG of 0 | `NEG BL` (from `00h`) | `BL=00h` | `CF=0, ZF=1, SF=0, OF=0, AF=0, PF=1` |
| **Stop 8** | IDIV Negative Dividend | `IDIV BX` (`DX:AX = -2`, `BX = 2`) | `AX=FFFFh (-1), DX=0000h` | Flags undefined on IDIV per x86 spec |
| **Stop 9** | DAA Decimal Adjust | `DAA` (after `38h + 45h = 7Dh`) | `AL=83h` | `CF=0, ZF=0, SF=1, OF=undefined, AF=1, PF=0` |
