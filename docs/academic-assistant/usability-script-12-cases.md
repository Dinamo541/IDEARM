# Usability script and validation of the 12 acceptance cases
## IDEARM Academic Assistant (phase AA-P7-04)

> **Canonical validation document and experimental evidence.**
> Every case has been validated with real automated tests run under Maven and the four-layer architecture (ADR-007,
> ADR-013, ADR-014).

---

## Summary matrix of the 12 acceptance cases

| # | Case / scenario | Context and input | Automated test evidence | State |
|:---:|---|---|---|:---:|
| **1** | Breaking down a composite operand | `mov ax, [bx+si+4]` in `dos-exe-16`, 8086 | `QueryExplainTest.deconstructsCompositeInstructionInRealMode` & `AddressingModeRuleTest` | **VERIFIED** |
| **2** | Colon notation | `DS:DX`, `DX:AX`, `label:`, `ES:[DI]` | `QueryExplainTest.distinguishesColonNotations` & `WorkbenchViewModelTest` | **VERIFIED** |
| **3** | Initializing the data segment | `mov ax, @data` and `mov ds, ax` in MASM/TASM | `AcceptanceCases3And8Test.testAcceptanceCase3AtData` | **VERIFIED** |
| **4** | Interrupt 21h services told apart | `INT 21h` with `AH=09h` and `AH=0Ah` | `InterruptServicesTest` & `EmulatorServicesContractTest` | **VERIFIED** |
| **5** | Comparisons of common pairs | `MOV`/`LEA`, `CMP`/`TEST`, `MUL`/`IMUL`, `DIV`/`IDIV` | `ComparativePairsTest.testAllComparativePairs` | **VERIFIED** |
| **6** | Semantic accuracy of flags | CF vs OF with the same data, INC/DEC preserve CF, SHL by count | `IndependentOracleSemanticsTest` | **VERIFIED** |
| **7** | Walking through CALL/RET with the stack and the ABI | Stack frame, return address, cdecl, stdcall, MS x64 32 B shadow space, SysV 128 B red zone | `ProcedureAndMemoryConceptsTest.testStackFrameAndCallingConventions` | **VERIFIED** |
| **8** | Switching the project on the fly | DOS 8086, Win32, Win64, Linux64 (long-mode exclusion) | `AcceptanceCases3And8Test.testAcceptanceCase8ProjectTargetSwitching` | **VERIFIED** |
| **9** | Cross-cutting search by concept | "dos puntos", "corchetes", "acarreo", "carry", "segmento extra", "stack", "interrupción 21h" | `TransversalSearchTest` (7/7 green) | **VERIFIED** |
| **10** | Precedence of user symbols | A local symbol/macro named like an instruction | `QueryEditorUseCasesTest` & `QueryExplainTest` | **VERIFIED** |
| **11** | An instruction the emulator does not implement | `FSQRT` (x87 FPU in emu8086) | `FamilyF15X87Test.acceptanceCase11FsqrtUnavailabilityInEmu8086` | **VERIFIED** |
| **12** | A third-party external dependency | `Irvine32` (by Kip Irvine, not distributed by IDEARM) | `BlockGFundamentalsAndEnvironmentTest.verifyAcceptanceCase12ThirdPartyIrvine32` | **VERIFIED** |

---

## Detail and execution evidence per case

### Case 1: selecting `mov ax, [bx+si+4]` in `dos-exe-16`, 8086
- **Student input:** selecting the line `mov ax, [bx+si+4]` in the editor in a `dos-exe-16` project.
- **Academic behavior:**
  - Structured breakdown:
    - Mnemonic `MOV`: data transfer.
    - Destination `AX`: 16-bit accumulator register.
    - Source `[bx+si+4]`: a memory operand in base + index + displacement mode on 16 bits.
  - Effective address (EA): `BX + SI + 4`.
  - Default segment: `DS` (because `BX` is the base register).
  - 20-bit physical address: `(DS * 16) + EA`.
  - Linter rule: the linter checks that modes invalid on 16 bits such as `[bx+bp]` or `[ax]` are rejected with
    clear teaching diagnostics.
- **Tests:** `io.github.dinamo541.idearm.language.knowledge.QueryExplainTest` and
  `io.github.dinamo541.idearm.language.linter.AddressingModeRuleTest`.

### Case 2: colon notation queries (`DS:DX`, `DX:AX`, `label:`, `ES:[DI]`)
- **Student input:** search queries or selecting tokens with a colon.
- **Academic behavior:**
  - `label:`: assemblable syntax. Defines a position in the source code.
  - `ES:[DI]`: assemblable syntax. An explicit segment override prefix on memory.
  - `DS:DX`: explanatory conceptual notation. The logical address of a buffer in the documentation of DOS
    services. An explicit statement that it "is not assemblable syntax".
  - `DX:AX`: explanatory conceptual notation. A register pair for a 32-bit integer value on 16 bits (dividend or
    product).
- **Tests:** `QueryExplainTest.distinguishesColonNotations` and `WorkbenchViewModelTest`.

### Case 3: `mov ax, @data` and `mov ds, ax` in MASM/TASM
- **Student input:** a question about initializing the data segment.
- **Academic behavior:**
  - `@data` is a predefined MASM/TASM symbol that resolves to the address of the `.DATA` data segment.
  - Explains why two instructions are needed: the x86 architecture has no instruction to move an immediate value
    straight into a segment register (`MOV Sreg, imm` does not exist).
  - Makes clear that NASM does not offer `@data` and uses plain section names.
- **Tests:** `AcceptanceCases3And8Test.testAcceptanceCase3AtData`.

### Case 4: `INT 21h` with `AH=09h` and `AH=0Ah`
- **Student input:** hover or help on DOS interrupt calls.
- **Academic behavior:**
  - Tells apart `AH=09h` (print a `$`-terminated string) and `AH=0Ah` (read a string into a structured buffer).
  - Breaks down the 0Ah buffer structure: byte 0 (maximum size), byte 1 (characters read), bytes 2+ (the actual
    characters).
  - Includes a technical warning about the buffer emulation in `emu8086`.
- **Tests:** `InterruptServicesTest` and `EmulatorServicesContractTest`.

### Case 5: comparisons of common pairs
- **Student input:** comparing conceptually related instructions.
- **Academic behavior:**
  - `MOV` vs `LEA`: `MOV` dereferences memory and reads data; `LEA` computes the effective address in the AGU
    without using the data bus or touching memory.
  - `CMP` vs `TEST`: `CMP` subtracts and sets the ordering flags; `TEST` performs a logical AND and clears CF and
    OF.
  - `MUL` vs `IMUL`: `MUL` is unsigned with one operand; `IMUL` is signed and accepts 1, 2 and 3 operands.
  - `DIV` vs `IDIV`: they require preparing the dividend (clearing DX for `DIV`, sign-extending with
    `CBW`/`CWD`/`CDQ` for `IDIV`) and the divide-by-zero / overflow exception (#DE) is documented.
- **Tests:** `ComparativePairsTest.testAllComparativePairs`.

### Case 6: semantic accuracy with an independent mathematical oracle
- **Student input:** checking the flags after arithmetic and shift operations.
- **Academic behavior:**
  - Shows with identical operands (`FFh + 01h` vs `7Fh + 01h`) how CF and OF work independently for unsigned and
    signed values.
  - Checks that `INC`/`DEC` modify OF, SF, ZF, AF, PF but leave the carry flag CF untouched.
  - Checks that multi-bit shifts leave OF undefined according to SDM 093, without making false assertions.
- **Tests:** `IndependentOracleSemanticsTest`.

### Case 7: walking through CALL/RET with the stack and ABI conventions
- **Student input:** tracing procedure calls.
- **Academic behavior:**
  - Explains the LIFO stack, `CALL` pushing the return address, anchoring the frame with `mov bp, sp`, and
    parameters at `[bp+4]`, `[bp+6]`.
  - Explains `RET n` for the callee cleaning up the stack.
  - Details the conventions: `cdecl`, `stdcall`, Microsoft x64 (a mandatory 32-byte shadow space, 16-byte
    alignment), and System V AMD64 (a 128-byte red zone).
- **Tests:** `ProcedureAndMemoryConceptsTest.testStackFrameAndCallingConventions`.

### Case 8: switching the project on the fly and the compatibility matrix
- **Student input:** switching the profile between DOS 8086, Win32, Win64 and Linux64.
- **Academic behavior:**
  - On DOS 8086: `PUSHA` (80186+), `BSWAP` (80486+) and `SYSCALL` (x86-64) are `UNAVAILABLE`.
  - On Win32: `PUSHA` and `BSWAP` become `AVAILABLE`; `SYSCALL` stays `UNAVAILABLE`.
  - On Win64 / Linux64: `SYSCALL` becomes `AVAILABLE`; the 15 instructions forbidden in long mode (`AAA`, `DAA`,
    `PUSHA`, `POPA`, `INTO`, `BOUND`, `LDS`, `LES` and the others) are marked `UNAVAILABLE`.
- **Tests:** `AcceptanceCases3And8Test.testAcceptanceCase8ProjectTargetSwitching`.

### Case 9: cross-cutting searches by concept
- **Student input:** text searches, in the Spanish a student types: "dos puntos" (colon), "corchetes" (brackets),
  "acarreo" (carry), "carry", "segmento extra" (extra segment), "stack", "interrupción 21h" (interrupt 21h).
- **Academic behavior:**
  - Returns unified results grouping instructions, concepts, registers and syntax, insensitive to case and
    accents.
- **Tests:** `TransversalSearchTest` (7/7 green).

### Case 10: precedence of user symbols
- **Student input:** a project where a local identifier (label or macro) matches a mnemonic or directive.
- **Academic behavior:**
  - Hover gives priority to the user symbol declared in the project, showing its file and line, and moves the
    catalog sheet to a secondary card.
- **Tests:** `QueryEditorUseCasesTest` and `QueryExplainTest`.

### Case 11: an instruction the emulator does not implement (`FSQRT`)
- **Student input:** a question about `FSQRT` (floating-point square root).
- **Academic behavior:**
  - Declares `minCpu: "8087"` (math coprocessor).
  - Warns that it is unavailable in `emu8086` with the exact reason (emu8086 does not implement the full x87 FPU
    coprocessor).
  - Offers teaching alternatives (an integer Newton-Raphson approximation, or building for an external backend
    with DOSBox/GDB).
- **Tests:** `FamilyF15X87Test.acceptanceCase11FsqrtUnavailabilityInEmu8086`.

### Case 12: a third-party external dependency (`Irvine32`)
- **Student input:** using `INCLUDE Irvine32.inc` or calls to `WriteString`.
- **Academic behavior:**
  - Explicitly identifies Irvine32 as a third-party teaching library written by Kip Irvine.
  - States that **IDEARM DOES NOT DISTRIBUTE OR INCLUDE IRVINE32**.
  - Guides the student through setting up external include (`include`) and library (`lib`) paths in the project
    properties.
- **Tests:** `BlockGFundamentalsAndEnvironmentTest.verifyAcceptanceCase12ThirdPartyIrvine32`.

---
*Conclusion of phase AA-P7-04: the 12 acceptance cases are fully verified by continuous unit and integration tests.*
