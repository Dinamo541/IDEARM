# Research Note: System V AMD64 psABI Specification (AA-P0-04)

> **Date:** 2026-09-25  
> **Status:** Verified and Closed  
> **Authority:** System V Application Binary Interface AMD64 Architecture Processor Supplement (Draft Version 1.0)

---

## 1. User-Space Function Calling Convention

### 1.1 Argument Passing Registers

| Argument Sequence | Integer / Pointer | Floating Point / Vector |
|---|---|---|
| Argument 1 | `RDI` | `XMM0` |
| Argument 2 | `RSI` | `XMM1` |
| Argument 3 | `RDX` | `XMM2` |
| Argument 4 | `RCX` | `XMM3` |
| Argument 5 | `R8` | `XMM4` |
| Argument 6 | `R9` | `XMM5` |
| Arguments 7+ | Pushed on stack right-to-left | `XMM6`, `XMM7` (then stack) |

*Varargs Notice:* For variable argument lists, `AL` must hold the number of vector registers used (0 to 8).

### 1.2 Register Preservation Rules

- **Callee-Saved (Preserved):** `RBX`, `RSP`, `RBP`, `R12`, `R13`, `R14`, `R15`.
- **Caller-Saved (Volatile / Scratch):** `RAX`, `RCX`, `RDX`, `RSI`, `RDI`, `R8`, `R9`, `R10`, `R11`, and all vector/MMX registers.
- **Return Values:** Integers returned in `RAX` (and `RDX` for 128-bit); floats in `XMM0` (and `XMM1`).

### 1.3 Stack Alignment and The Red Zone

- **Stack Alignment:** The stack pointer `RSP` must be **16-byte aligned** immediately before a `CALL` instruction is executed. Upon function entry, `RSP` is therefore offset by 8 bytes (the return address).
- **The 128-Byte Red Zone:** A 128-byte area below `RSP` (from `[RSP - 1]` to `[RSP - 128]`) is reserved and guaranteed not to be clobbered by signal handlers or interrupts. Leaf functions may utilize this area for temporary storage without moving `RSP`.
- **Shadow Space:** Unlike Microsoft x64, System V **does not** use shadow space (home space).

---

## 2. Linux Kernel Syscall Convention (`syscall`)

*Authority: Linux x86-64 Kernel Architecture Documentation & man 2 syscall*

The system call interface differs critically from user-space function calls:

| Role | Syscall Register | Contrast with Function ABI |
|---|---|---|
| **Syscall Number** | `RAX` | Selects kernel service (`sys_read=0`, `sys_write=1`, `sys_exit=60`) |
| **Argument 1** | `RDI` | Same |
| **Argument 2** | `RSI` | Same |
| **Argument 3** | `RDX` | Same |
| **Argument 4** | **`R10`** | **`RCX` cannot be used**: `syscall` CPU instruction clobbers `RCX` (stores return `RIP`) |
| **Argument 5** | `R8` | Same |
| **Argument 6** | `R9` | Same |
| **Destroyed by Hardware** | `RCX`, `R11` | `RCX` receives old RIP; `R11` receives old RFLAGS |
| **Return Code** | `RAX` | Negative values in range `[-4095, -1]` represent `-errno` |

---

## 3. Comparative Summary: Windows x64 vs Linux System V x64

| Property | Windows x64 ABI (`src.ms-abi.x64`) | Linux System V AMD64 (`src.sysv-abi.x64`) |
|---|---|---|
| Integer Args | `RCX`, `RDX`, `R8`, `R9` (4 regs) | `RDI`, `RSI`, `RDX`, `RCX`, `R8`, `R9` (6 regs) |
| Shadow Space | **Mandatory 32 bytes** allocated by caller | **None** |
| Red Zone | **None** (unsafe below RSP) | **128 bytes** available for leaf functions |
| Syscall Opcode | Direct syscall numbers unstable; use Win32 API | `syscall` with stable Linux system call table |
