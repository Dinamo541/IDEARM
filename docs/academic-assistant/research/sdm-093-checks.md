# Research Note: Intel SDM Revision 093 Semantic Verifications (AA-P0-03)

> **Date:** 2026-09-25  
> **Status:** Verified and Closed against Intel SDM Revision 093 (Document 767375)  
> **Scope:** Closes semantic uncertainties regarding flags, encodings, and modes.

---

## 1. Shift and Rotate Instructions (`SHL`, `SAL`, `SHR`, `SAR`, `ROL`, `ROR`, `RCL`, `RCR`)

*Authority: Intel SDM Vol. 2B, Instruction Set Reference, N-Z / SAL/SAR/SHL/SHR*

1. **Zero Shift Count:** If the shift count is 0, **no flags are affected** (all flags retain their previous values).
2. **Auxiliary Carry Flag (`AF`):** The AF flag is **undefined** for any non-zero shift count. (The previous catalog erroneously declared `AF = MODIFIED`).
3. **Overflow Flag (`OF`):** The OF flag is defined **only for 1-bit shifts**:
   - For `SHL`/`SAL`, `OF = (MSB of result) XOR (CF)`.
   - For `SHR`, `OF = MSB of original operand`.
   - For `SAR`, `OF = 0` for 1-bit shift.
   - For shift counts greater than 1, **OF is undefined**.
4. **Carry Flag (`CF`):** Contains the last bit shifted out of the operand.
5. **Sign (`SF`), Zero (`ZF`), Parity (`PF`):** Set according to the result for non-zero shift counts; undefined for rotates.

---

## 2. Bit Test Instructions (`BT`, `BTS`, `BTR`, `BTC`)

*Authority: Intel SDM Vol. 2A, Instruction Set Reference, A-L / BT, BTC, BTR, BTS*

1. **Carry Flag (`CF`):** Receives the value of the selected bit from the bit base.
2. **Undefined Flags:** `OF`, `SF`, `AF`, and `PF` are **undefined**. (The previous catalog erroneously declared `FlagSummary.none()`, contradicting its own prose).
3. **Zero Flag (`ZF`):** Remains **unaffected** (neither set nor modified).

---

## 3. Interrupt Return (`IRET`, `IRETD`, `IRETQ`)

*Authority: Intel SDM Vol. 2A, Instruction Set Reference, A-L / IRET/IRETD/IRETQ*

1. **Flag Restoration:** Restores all user flags (`CF`, `PF`, `AF`, `ZF`, `SF`, `TF`, `DF`, `OF`) from the stack frame.
2. **System Flags:**
   - In Real Mode, restores all flags including `IF`.
   - In Protected Mode, `IF` is only modified if Current Privilege Level (CPL) <= I/O Privilege Level (IOPL).
   - `IOPL` is only altered if CPL = 0.
   - `VIF`, `VIP`, `VM` handling depends on operand size and processor mode.

---

## 4. Multi-Form `IMUL` Encodings and CPU Generations

*Authority: Intel SDM Vol. 2A, Instruction Set Reference, A-L / IMUL; Irvine §7.4.2*

| Form | Opcode Syntax | Opcode Encoding | Minimum CPU | Register Action |
|---|---|---|---|---|
| **1-Operand** | `IMUL r/m8` | `F6 /5` | 8086 | `AX := AL * r/m8` |
| **1-Operand** | `IMUL r/m16` | `F7 /5` | 8086 | `DX:AX := AX * r/m16` |
| **1-Operand** | `IMUL r/m32` | `F7 /5` | 80386 | `EDX:EAX := EAX * r/m32` |
| **1-Operand** | `IMUL r/m64` | `REX.W + F7 /5` | x86-64 | `RDX:RAX := RAX * r/m64` |
| **3-Operand Imm** | `IMUL r16, r/m16, imm8/16` | `6B /r ib` / `69 /r iw` | **80186** | `r16 := r/m16 * SignExtend(imm)` |
| **3-Operand Imm** | `IMUL r32, r/m32, imm8/32` | `6B /r ib` / `69 /r id` | **80386** | `r32 := r/m32 * SignExtend(imm)` |
| **2-Operand Reg** | `IMUL r16, r/m16` | `0F AF /r` | **80386** | `r16 := r16 * r/m16` |
| **2-Operand Reg** | `IMUL r32, r/m32` | `0F AF /r` | **80386** | `r32 := r32 * r/m32` |
| **2-Operand Reg** | `IMUL r64, r/m64` | `REX.W + 0F AF /r` | x86-64 | `r64 := r64 * r/m64` |

*Note on 2-operand with immediate (`IMUL r16, imm`):* Assemblers translate this as a 3-operand instruction with the destination register repeated as source (`IMUL r16, r16, imm`), requiring only 80186 rather than 80386.

---

## 5. Architectural Invariants in 64-bit Long Mode

*Authority: Intel SDM Vol. 1, §3.4.1.1 and §3.4.1.2*

1. **32-Bit Register Writes Zero-Extend:** In 64-bit mode, writing to any 32-bit register (e.g., `EAX`, `EBX`, `R8D`) **clears (zeros) the upper 32 bits** of the corresponding 64-bit register (`RAX`, `RBX`, `R8`).
   - Conversely, writing to 8-bit (`AL`, `AH`) or 16-bit (`AX`) registers leaves the higher bits unaffected.
2. **REX Prefix High-Byte Register Prohibition:** When an instruction uses a REX prefix (necessary for 64-bit operand size or new registers `R8`–`R15`), uniform byte register access (`SPL`, `BPL`, `SIL`, `DIL`) is enabled, but access to high-byte registers (`AH`, `BH`, `CH`, `DH`) is **prohibited** within that instruction encoding.
3. **Invalid Instructions in 64-Bit Mode (#UD or Remapped):**
   - Decimal Arithmetic: `AAA` (37h), `AAS` (3Fh), `DAA` (27h), `DAS` (2Fh), `AAM` (D4h), `AAD` (D5h).
   - Stack General: `PUSHA` (60h), `POPA` (61h).
   - Branch / Bound: `INTO` (CEh), `BOUND` (62h).
   - Far Pointer Loads: `LDS` (C5h), `LES` (C4h) (Note: `LFS`, `LGS`, `LSS` remain valid via 0F prefix).
   - Privilege Adjust: `ARPL` (63h - re-encoded in 64-bit mode as `MOVSXD`).
