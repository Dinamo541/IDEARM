# Research Note: Reference Revisions and Standards (AA-P0-01)

> **Date:** 2026-09-25  
> **Status:** Verified and Closed  
> **Scope:** Establishes the authoritative reference editions and versions for IDEARM's Academic Knowledge Base.

---

## 1. Primary Specification Sources

| Domain / Subject | Authority Document | Version / Revision | Citation Key | Verification Notes |
|---|---|---|---|---|
| **x86 Instruction Set Architecture (All Generations)** | Intel 64 and IA-32 Architectures Software Developer's Manual (SDM) | Document 767375, **Revision 093**, updated 2026-09-21 | `src.intel-sdm.093` | Primary authority for instruction encodings, operand forms, CPUID feature flags, and architectural flag effects. |
| **NASM Dialect & Directives** | The Netwide Assembler Official Manual | **Version 3.02** | `src.nasm.3.02` | Reference manual for NASM syntax, `%macro` preprocessor, memory dereference semantics `[var]` vs address `var`, and directives (`SECTION`, `GLOBAL`, `RESB`, `BITS`). |
| **NASM Environment Coexistence** | Project Runtime & CI Toolchains | Ubuntu CI: 2.16.01; Adapter: 3.01; Published: 3.02 | `src.nasm.compat` | Core syntax is identical across 2.16.x–3.02; dialect specification targets 3.02 while maintaining backward compatibility markers. |
| **MASM Dialect & Directives** | Microsoft Macro Assembler Programmer's Guide | **Version 6.11** | `src.masm.6.11` | Reference for segment directives (`.DATA`, `.CODE`, `.STACK`), memory models (`.MODEL SMALL`), operators (`OFFSET`, `PTR`, `LENGTHOF`, `SIZEOF`), and pre-defined symbols (`@data`, `@code`). |
| **TASM Dialect & Modes** | Borland Turbo Assembler User's & Reference Guide | **Version 4.1** (MASM & IDEAL mode) | `src.tasm.4.1` | Authority for TASM compatibility with MASM, plus TASM IDEAL mode (`IDEAL` keyword) and coprocessor directives (`.8087`, `.287`, `.387`). |
| **Academic Fundamentals & Intel x86 Assembly** | Kip R. Irvine: *Assembly Language for x86 Processors* | **5th Edition (Spanish translation)**, Pearson Educación | `src.irvine.5e-es` | Primary textbook for curriculum alignment: IMUL forms (§7.4), INT 21h (§13.2), register structure (§2.2), memory operators (§4.3). Body offset: PDF page = Print page + 32. |
| **Computer Architecture & Memory Systems** | William Stallings: *Computer Organization and Architecture* | **7th Edition (Spanish translation)**, Prentice Hall | `src.stallings.7e-es` | Authority for addressing modes (§11.2), effective/linear/physical address calculation, segmentation vs paging, CPU instruction cycle. Body offset: PDF page = Print page + 22. |
| **Digital Logic & Hardware Foundations** | M. Morris Mano: *Digital Design* | **3rd Edition (Spanish translation)**, Pearson Educación | `src.mano.3e-es` | Authority for hardware-level foundation (registers with D flip-flops, §6-1; binary bases, two's complement, §1). Used with clear distinction between gate-level circuits and architectural x86 registers. |
| **Windows x64 Calling Convention & ABI** | Microsoft Learn Developer Documentation | Revision **2026-05-21** (ms.date: 2025-03-19) | `src.ms-abi.x64` | Fastcall calling convention: RCX, RDX, R8, R9; 32-byte shadow space allocated by caller; 16-byte stack alignment prior to CALL; return in RAX/XMM0. |
| **System V AMD64 psABI (Linux / BSD)** | System V Application Binary Interface AMD64 Architecture Processor Supplement | **Version 1.0 (Draft)**, maintained under gitlab.com/x86-psABIs | `src.sysv-abi.x64` | Function ABI: RDI, RSI, RDX, RCX, R8, R9; 128-byte Red Zone; Syscall ABI: RAX (number), RDI, RSI, RDX, R10, R8, R9; return in RAX. |
| **MS-DOS & PC BIOS Services** | MS-DOS Programmer's Reference & Ralf Brown's Interrupt List (RBIL) | Release 61 / MS-DOS 6.22 | `src.dos-bios.ref` | Authority for vector index (00h–FFh) status, INT 21h functions (01h–62h), INT 10h video services, INT 16h keyboard services. |

---

## 2. Decision Log Summary (D-AA-01 to D-AA-09)

1. **D-AA-01 (Documentation Language):** Documentation of the academic assistant follows Spanish as requested for course alignment, with ADRs and public codebase comments in English per repository conventions (ADR-006).
2. **D-AA-02 (Entities & Identifiers):** Stable IDs using hierarchic URNs (`x86.instr.<name>`, `x86.reg.<name>`, `syntax.<dialect>.<type>.<name>`, `dos.int21.<func>`).
3. **D-AA-03 (Flag Specifications):** Condition and notes attached per form; distinction between FLAGS/EFLAGS/RFLAGS and x87 FPU Status Word.
4. **D-AA-04 (Ternary Compatibility):** `AVAILABLE`, `UNAVAILABLE`, and `UNKNOWN`. Only `UNAVAILABLE + CERTAIN` produces linter error diagnostics.
5. **D-AA-05 (Separation of Semantics and Prose):** Data structures are language-neutral in JSON; text strings reside in `text/{en,es}/` resource bundles.
6. **D-AA-06 (Corpus Location):** Package `io.github.dinamo541.idearm.language.knowledge` in `idearm-language`, with data in module resources.
7. **D-AA-07 (Storage & Parser):** Zero-dependency pure JDK JSON parser in Layer 3 adhering strictly to ADR-007 (no `Files` or `ProcessBuilder` in L3).
8. **D-AA-08 (Lazy Initialization):** Virtual thread or background warm-up; static access in lexer avoids freezing UI thread.
9. **D-AA-09 (Consumer Views):** Five clean views (`ReferenceView`, `HoverView`, `CompletionView`, `AnalysisView`, `DebugView`) guaranteeing that adding academic entries never introduces false linter errors.
