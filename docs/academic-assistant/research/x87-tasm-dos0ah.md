# Research Note: TASM/MASM x87 Behavior & DOS INT 21h/0Ah Contract (AA-P0-06)

> **Date:** 2026-09-25  
> **Status:** Verified and Closed  
> **Scope:** Closes compatibility matrix unknowns regarding x87 directives and buffered input behavior.

---

## 1. TASM 4.1 and MASM 6.11 x87 Coprocessor Handling

### Test Findings:
1. **Borland TASM 4.1 (MASM Mode):**
   - By default, TASM in standard MASM mode targets an 8086 CPU without floating-point coprocessor extensions.
   - Assembling x87 instructions (e.g., `FLD [var]`, `FSQRT`, `FADD`) without an enabling directive causes TASM error:
     `**Error** file.asm(line) Coprocessor instruction requires .8087 or .287 or .387`
   - Adding `.8087` (or `.387`) directive enables encoding of escape opcodes (`D8h`–`DFh`).
   - In TASM `IDEAL` mode, `.8087` is likewise required when the CPU baseline is restricted to 8086.
2. **Microsoft MASM 6.11:**
   - Similar behavior: `.8087` directive is required unless `.386/.387` or higher CPU directive is declared.
3. **Compatibility Matrix Consequence:**
   - The cell for x87 on DOS / TASM in `annex-b-scope-and-gaps.md` changes from `assemblable = unknown` to **`assemblable = yes (with the .8087/.387 directive)`**.
   - The academic assistant sheet for x87 instructions must instruct students to include `.8087` or `.387` in their source when targeting DOS toolchains.

---

## 2. INT 21h Function 0Ah Contract: MS-DOS vs emu8086

### 2.1 The Official MS-DOS Contract (Irvine §13.2.3, RBIL Table 01344)
- **Buffer Structure:**
  - `Offset 0` (Byte, IN): Buffer capacity `M` (maximum bytes allocated, including the terminating carriage return `0Dh`).
  - `Offset 1` (Byte, OUT): Actual character count `N` typed by the user (excluding `0Dh`).
  - `Offset 2..N+1` (Bytes, OUT): Typed ASCII characters.
  - `Offset N+2` (Byte, OUT): Carriage return `0Dh` (ASCII 13).
- **Critical Invariant:** Because the terminating `0Dh` must always fit within the `M` bytes, the user can type at most **`M - 1`** characters. If `M` is 5, the maximum user-typed text length is 4 characters.

### 2.2 The IDEARM Emulator Divergence (`idearm-emu8086`)
- In `DosInterruptHandler.java`, the loop condition previously checked `length < maxInput` rather than `maxInput - 1` before writing the character and subsequently appending `0Dh`.
- *Effect:* The emulator allowed typing `M` characters before appending `0Dh`, potentially writing `M + 1` bytes into memory.
- *Resolution:*
  1. Document this divergence in the knowledge base service sheet (`dos.int21.0ah` caveat).
  2. Emit educational warning and maintain precise compatibility tracking.
