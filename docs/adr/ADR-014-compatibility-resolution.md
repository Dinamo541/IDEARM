# ADR-014: Compatibility Resolution Engine and Context-Driven Validation

Date: 2026-09-26. Status: accepted. Implements **AA-P2-01**, **AA-P2-02**, **AA-P5-03**, **AA-P5-05**, **AA-P7-03**.

## Context

The previous academic catalog and validation engine represented CPU requirements using a single integer (`CpuLevel` 0 to 7) corresponding to generations from 8086 to 64-bit. This linear hierarchy failed to model real-world x86 architecture constraints:

1. **Multidimensional Constraints:** Compatibility depends not just on the microarchitecture release date, but on the active **Processor Mode** (Real Mode, 16-bit Protected Mode, 32-bit Protected Mode, 64-bit Long Mode), **CPU Features** (MMX, SSE, SSE2, AVX), **Assembler Dialect** (MASM, TASM, NASM), and the **Execution Backend** (`emu8086`, `gdb`, `external`).
2. **Long Mode Invalidation:** Instructions valid on a 32-bit CPU (e.g., BCD instructions `AAA`, `DAA`, or 16-bit stack instructions `PUSHA`, `POPA`, `BOUND`, `LDS`, `LES`) are completely invalid opcodes in 64-bit Long Mode. A linear integer model could not flag these without breaking 16-bit/32-bit projects.
3. **Emulator Feature Boundaries:** Integrated educational emulators (such as `emu8086`) execute 8086 real-mode instructions but do not implement the full x87 FPU coprocessor (e.g. `FSQRT` generates an invalid opcode exception). Students writing code for the emulator need clear advance warnings with alternatives, while projects targeted for external toolchains (DOSBox, real hardware) should not be blocked.
4. **False Positive Prevention:** When a feature or generation is unspecified (or unknown), the IDE must never generate false-positive error squiggles or incorrect typo corrections.

## Decision

- **`CompatibilityContext` Record:** Defines the target environment:
  - `CpuGeneration`: Target CPU (`I8086` to `X86_64`, including coprocessors `I8087`, `I80287`, `I80387`).
  - `ProcessorMode`: `REAL`, `PROTECTED_16`, `PROTECTED_32`, `COMPATIBILITY`, `LONG`, `UNKNOWN`.
  - `features`: Set of required CPU features (`MMX`, `SSE`, `SSE2`, `AVX`, etc.).
  - `dialect`: Active dialect (`MASM`, `TASM`, `NASM`, `COMMON`).
  - `backend`: Target runtime (`emu8086`, `gdb`, `dosbox`, `external`, `none`).
- **`Requirement` Semantic Entity:** Defined on `InstructionForm` and instructions:
  - `minGeneration`: Lowest CPU generation supporting the instruction form.
  - `features`: Required instruction set extensions.
  - `validModes` and `invalidModes`: Explicit whitelist and blacklist of processor modes.
  - `privilege`: Privilege ring (`RING0`, `RING3`, `ANY`).
- **Ternary Availability Resolution:**
  - `AVAILABLE`: All conditions (generation, mode, features, backend) are fully satisfied.
  - `UNAVAILABLE`: At least one condition is definitively violated (accompanied by an authoritative reason code).
  - `UNKNOWN`: The context lacks sufficient information to confirm or deny compatibility (e.g. target CPU is generic `x86` or feature set is null).
- **Hard Rule for Linter Diagnostics:**
  - The `AssemblyLinter` and `CpuBaselineRule` **only** emit diagnostics on `UNAVAILABLE` states.
  - An `UNKNOWN` state **never** raises an error or warning diagnostic, guaranteeing zero false positives for undocumented or future extensions.
- **Strict 64-bit Long Mode Exclusions:**
  - The 15 instructions removed in 64-bit submode (`AAA`, `AAD`, `AAM`, `AAS`, `DAA`, `DAS`, `PUSHA`, `POPA`, `INTO`, `BOUND`, `LDS`, `LES`, `ARPL`) declare `invalidModes: ["LONG"]`. In `LONG` mode, compatibility resolution strictly yields `UNAVAILABLE` with an explanatory diagnostic.

## Consequences

- Editor hover and code completion dynamically adapt to project configuration changes in real time.
- False positive typo suggestions and spurious unknown instruction errors are eliminated.
- Academic transparency: students receive exact architectural reasons why an instruction is not runnable in their chosen environment (e.g., `FSQRT` in `emu8086`), alongside actionable alternatives.
