# Annex C — Content taxonomy and learning paths

> Part of [`expansion-plan.md`](expansion-plan.md). Covers phase 2 of the assignment and deliverable 4.
> The list of blocks in the assignment is a minimum; §C.9 records the topics the audit added.

---

## C.0 Navigation structure

The corpus is a network, not a tree, but it needs a tree to be navigated. The decision is **a tree of topics with
cross-links** **[proposal]**:

```
Academic Center
├── 1. Fundamentals                        (block G)
│   ├── 1.1 Numbers and bases
│   ├── 1.2 Signed integers and two's complement
│   ├── 1.3 Bits, bytes, words and byte order
│   ├── 1.4 Character codes
│   ├── 1.5 Boolean logic and masks
│   └── 1.6 From the circuit to the architecture  → links to 2.1 and points out the difference
├── 2. The machine                         (blocks B and C)
│   ├── 2.1 Registers                             → 6 groups, §C.3
│   ├── 2.2 Flags and status fields
│   ├── 2.3 Memory, segments and addressing        → §C.4
│   ├── 2.4 The stack
│   └── 2.5 Processor modes and the instruction cycle
├── 3. The language                        (block D)
│   ├── 3.1 Anatomy of a line
│   ├── 3.2 Signs and operators                   → glossary per dialect, §C.5
│   ├── 3.3 Data declaration
│   ├── 3.4 Directives per dialect
│   ├── 3.5 Macros and conditional assembly
│   └── 3.6 Labels, scope and symbol resolution
├── 4. Instructions                        (block A)
│   └── 18 families, §C.1                         → normalized sheet, §C.2
├── 5. The environment                     (block E)
│   ├── 5.1 Interrupts, exceptions and services   → §C.6
│   ├── 5.2 Index of vectors 00h–FFh
│   ├── 5.3 DOS and BIOS services
│   ├── 5.4 Windows API and ABI
│   └── 5.5 Linux syscalls and ABI
├── 6. From code to program                (block F)
│   ├── 6.1 Procedures, frames and calling conventions
│   ├── 6.2 Assembly, object, link, executable and loading
│   └── 6.3 Libraries, APIs and system services
└── 7. Guided paths                        (block G)
    └── 9 paths, §C.8
```

---

## C.1 Block A — Instruction reference

### C.1.1 Five things that are confused today

The catalog treats `MOV`, `REP` and `LOCK` as the same kind of object **[code]**. The corpus separates them with a
`kind` field:

| `kind` | What it is | Examples | Difference in treatment |
|---|---|---|---|
| `INSTRUCTION` | a CPU instruction | `MOV`, `IMUL`, `JMP` | has forms, flags, exceptions |
| `PREFIX` | an encoding prefix | `REP`, `REPE`, `REPNE`, `LOCK`, `ES:`… | has no flags of its own; declares **which instructions it is valid with** and what each combination does |
| `ASSEMBLER_ALIAS` | another name an assembler accepts | `SAL`→`SHL`, `JNBE`→`JA`, `RETN`→`RET`, `XLATB`→`XLAT` | points at a form, not at a mnemonic, and declares the dialect that accepts it |
| `PSEUDO_INSTRUCTION` | the assembler translates it into something else | `.EXIT`, `.STARTUP`, MASM `INVOKE` | declares what code it generates |
| `DIRECTIVE` | an instruction for the assembler, not for the CPU | `DB`, `EQU`, `SEGMENT`, `BITS` | lives in block D, not here |

### C.1.2 The 18 families

| Family | Contents | Traps the material must avoid |
|---|---|---|
| F-01 Transfer | `MOV`, `XCHG`, `MOVSX`, `MOVZX`, `BSWAP`, `CMOVcc`, `LEA`, `LDS`/`LES`/`LFS`/`LGS`/`LSS`, `XLAT`, segment `PUSH`/`POP` | `LEA` **computes**, it does not read memory. `MOV` cannot move an immediate into a segment register. `CMOVcc` is P6, not Pentium (A-03) |
| F-02 Integer arithmetic | `ADD`, `ADC`, `SUB`, `SBB`, `INC`, `DEC`, `NEG`, `CMP`, `MUL`, `IMUL`, `DIV`, `IDIV`, `CBW`/`CWD`/`CWDE`/`CDQ`/`CDQE`/`CQO` | CF ≠ OF. `INC`/`DEC` do not touch CF. `MUL` versus `IMUL` with 1, 2 and 3 operands and their different requirements. Division needs preparation and can raise an exception |
| F-03 Decimal arithmetic | `DAA`, `DAS`, `AAA`, `AAS`, `AAM`, `AAD` | **invalid in long mode**; packed versus unpacked BCD |
| F-04 Logic | `AND`, `OR`, `XOR`, `NOT`, `TEST` | `TEST` writes no destination; `NOT` does not touch the flags; `AND`/`OR`/`XOR` clear CF and OF and leave AF undefined |
| F-05 Shifts and rotations | `SHL`/`SAL`, `SHR`, `SAR`, `ROL`, `ROR`, `RCL`, `RCR`, `SHLD`, `SHRD` | effects **conditional on the count** (A-11, A-12); an immediate count > 1 requires the 80186 |
| F-06 Bit manipulation | `BT`, `BTS`, `BTR`, `BTC`, `BSF`, `BSR`, `POPCNT`, `LZCNT`, `TZCNT` | CF receives the bit (A-06); `BSF`/`BSR` leave the destination undefined when the operand is 0 |
| F-07 Comparison and condition | `CMP`, `TEST`, `SETcc`, the 32 `Jcc` and their aliases | "greater" (signed) versus "above" (unsigned): `JG` versus `JA`. Aliases in pairs |
| F-08 Jumps and calls | short/near/far/indirect `JMP`, `CALL`, `RET`, `RETF`, `RETN`, `IRET`/`IRETD`/`IRETQ` | `IRET` restores every flag (A-07); `RET n` cleans the stack; near versus far |
| F-09 Loops | `LOOP`, `LOOPE`/`LOOPZ`, `LOOPNE`/`LOOPNZ`, `JCXZ`, `JECXZ`, `JRCXZ` | they use CX/ECX/RCX according to the address size, not the mode |
| F-10 Stack | `PUSH`, `POP`, `PUSHA`/`PUSHAD`, `POPA`/`POPAD`, `PUSHF`/`PUSHFD`/`PUSHFQ`, `POPF`…, `ENTER`, `LEAVE` | `PUSH imm` requires the 80186; `PUSHA`/`POPA` **invalid in long mode**; the stack grows towards lower addresses |
| F-11 Strings | `MOVS`, `CMPS`, `SCAS`, `LODS`, `STOS` with the B/W/D/Q suffixes, and the `REP*` prefixes | implicit operands (`DS:[SI]`, `ES:[DI]`, CX, DF); only `CMPS` and `SCAS` affect the flags; `REP` versus `REPE`/`REPNE` |
| F-12 Port input/output | `IN`, `OUT`, `INS`, `OUTS` | depend on IOPL in protected mode; useless in Windows/Linux user space |
| F-13 Flag and CPU control | `STC`, `CLC`, `CMC`, `STD`, `CLD`, `STI`, `CLI`, `LAHF`, `SAHF`, `NOP`, `HLT`, `WAIT`, `UD2`, `CPUID`, `RDTSC` | `CLI`/`STI` and privilege; `LAHF`/`SAHF` only move the low byte |
| F-14 Interrupts | `INT`, `INT3`, `INTO`, `IRET`, `BOUND` | the `INT` instruction is not the service; the vector number is not the function (A-13) |
| F-15 x87 | load/store, arithmetic, comparison, constants, control, transcendental | register stack ST(0)–ST(7); **status word ≠ FLAGS** (A-28); `FSTSW AX` + `SAHF` as the bridge |
| F-16 System and protection | `LGDT`, `LIDT`, `LLDT`, `LTR`, `SGDT`…, `LAR`, `LSL`, `ARPL`, `VERR`, `VERW`, `CLTS`, `INVD`, `INVLPG`, `RDMSR`, `WRMSR`, `RSM`, `SYSENTER`/`SYSEXIT`, `SYSCALL`/`SYSRET`, `SWAPGS` | CPL 0; not runnable in a student program; `SYSCALL` is x86-64, `SYSENTER` is P6 |
| F-17 Atomics and synchronization | `XCHG`, `XADD`, `CMPXCHG`, `CMPXCHG8B`, `CMPXCHG16B`, `LOCK`, `SFENCE`/`LFENCE`/`MFENCE`, `PAUSE` | `XCHG` with memory is implicitly atomic; `LOCK` is only valid with a specific set |
| F-18 SIMD and other extensions | MMX, SSE…SSE4.2, AVX/AVX2/AVX-512, BMI, ADX, AES-NI, SHA, RDRAND | each with its CPUID bit; `MOVSD`/`CMPSD` homonyms of the string ones (A-14); treatment level in annex B §B.3.5 |

---

## C.2 Normalized instruction sheet

Mandatory (**M**), recommended (**R**) and optional (**o**) fields. The semantic fields are structures; the prose
fields live in the text resources per language (annex D §D.4).

### Header (per mnemonic)

| Field | Level | Contents |
|---|---|---|
| `id` | M | `x86.instr.imul` — stable, a contract |
| `mnemonic`, `kind` | M | canonical name and class (§C.1.1) |
| `family` | M | F-01…F-18 |
| `aliases[]` | M if any | `{name, dialect, version, note}` — **declared**, not deduced (A-01) |
| `pedagogicalLevel` | M | `BASIC` / `INTERMEDIATE` / `ADVANCED` |
| `prerequisites[]` | R | identifiers of earlier concepts or instructions |
| `summary` | M | one understandable sentence |
| `description` | M | what it does and what it is for |
| `homonyms[]` | M if any | another reading of the same mnemonic (A-14) |
| `related[]` | R | `{id, relation}` with `relation ∈ {ALTERNATIVE, PAIR, PREREQUISITE, CONTRAST, SEE_ALSO}`; the reverse index is generated |
| `pitfalls[]` | R | common mistakes |
| `counterExamples[]` | R | code that looks valid and is not, with the reason |
| `sources[]` | M | at least one; an entry without a source does not validate |

### Per form (`InstructionForm`, the real unit of semantics)

| Field | Level | Contents |
|---|---|---|
| `id` | M | `x86.instr.imul#form.r16-rm16-imm8` |
| `syntax[]` | M | `{dialect, version, text}` — the same form written the way each assembler writes it |
| `operands[]` | M | ordered list of `Operand` |
| `implicitOperands[]` | M if any | operands that do not appear in the syntax (A-05) |
| `requirement` | M | `Requirement` (annex D §D.3) |
| `flags[]` | M | one `FlagEffectSpec` per flag, with a condition when there is one |
| `otherState[]` | R | stack, control flow, x87 status word, MXCSR |
| `operation` | M at the complete level | pseudocode |
| `operationPlain` | M at the complete level | the same operation in accessible prose |
| `exceptions[]` | R | `{vector, condition, modes}` |
| `encoding` | o | opcode, ModRM, REX, immediate size — only where it teaches something |
| `availability[]` | M | per backend: `emu8086`, `gdb`, `external`; with a reason |
| `notes[]` | o | special cases per form and mode |

### `Operand`

```
{ position, kind ∈ {REG, REG_MEM, MEM, IMM, MOFFS, REL, SEGREG, ST_I, XMM, IMPLICIT_REG, IMPLICIT_MEM},
  sizes[] ∈ {8,16,32,64,80,128,256,512}, access ∈ {READ, WRITE, READ_WRITE},
  role ∈ {EXPLICIT, IMPLICIT}, defaultSegment, extension ∈ {NONE, SIGN, ZERO, TRUNCATE},
  constraints[] }
```

### Examples (`Example`)

| Field | Contents |
|---|---|
| `kind` | `FRAGMENT` (illustrative) or `RUNNABLE` (full program) — the distinction the assignment requires |
| `level` | `BASIC` / `INTERMEDIATE` / `ADVANCED` |
| `context` | profile, dialect, toolchain and backend needed |
| `initialState` | relevant registers, memory and flags before |
| `code` | the code, with a per-line explanation |
| `finalState` | registers, memory and **defined** flags after; nothing is ever claimed about an undefined flag |
| `verification` | `NOT_RUN` / `STATIC` / `ASSEMBLED` / `LINKED` / `EXECUTED` / `DEBUGGED`, with date and tool |

`verification = NOT_RUN` is a legitimate and visible value. Evidence is not simulated.

---

## C.3 Block B — Registers and flags

### C.3.1 Six groups

| Group | Entities | Mandatory notes of the material |
|---|---|---|
| G-1 General and their views | AX/BX/CX/DX with AH/AL/BH/BL/CH/CL/DH/DL; EAX–EDX; RAX–RDX; R8–R15 with R8D/R8W/R8B | **overlap**: writing AL does not touch AH but does change AX and EAX; writing EAX **zeroes** the upper 32 bits of RAX; conventional uses (counter in CX, accumulator in AX) versus roles the ISA imposes (CX in `LOOP`, CL in shifts) |
| G-2 Pointers and indexes | SP, BP, SI, DI and their E*/R* views, SPL/BPL/SIL/DIL | SI/DI as implicit operands of the string instructions; BP and the default stack segment; on 64 bits the new 8-bit registers require REX and **exclude** AH/BH/CH/DH in the same instruction (G-19) |
| G-3 Segment | CS, DS, SS, ES (+ FS, GS from the 386) | **six segment registers, not four** **[source: Stallings §11.2, printed 416: "There are six segment registers"]**; in long mode CS/DS/ES/SS have base 0 and FS/GS keep a base |
| G-4 Execution control | IP/EIP/RIP; FLAGS/EFLAGS/RFLAGS with their fields | CF, PF, AF, ZF, SF, TF, IF, DF, OF and, from the 286/386, IOPL, NT, RF, VM, AC, VIF, VIP, ID. **The debugger masks to 16 bits** (A-29), so the sheet says what is visible and what is only explained |
| G-5 x87 and SIMD | ST(0)–ST(7), control, status and tag words, FIP/FDP; MM0–MM7; XMM/YMM/ZMM; MXCSR; K0–K7 | circular stack and `TOP`; C0–C3 of the status word; MM*i* overlaps ST(*i*); no IDE panel shows them today (A-29) |
| G-6 System | CR0–CR4, CR8; DR0–DR7; GDTR, LDTR, IDTR, TR; MSRs (per family); CPUID leaves | CPL 0 privilege; documented as theory with `exposedBy = []` |

### C.3.2 Register sheet

`id` · `name` · `group` · `sizeBits` · `parent` and `views[]` (with a bit offset) · `fields[]` (for the flag and
control registers) · `aliases[]` · `conventionalUse` · `architecturalUse` (imposed by the ISA, with the list of
instructions that impose it) · `accessConstraints` (privilege, mode, encoding) · `writeSemantics` (what happens to
the rest of the register when a view is written) · `requirement` · `exposedBy[]`
(`emu8086` / `gdb` / `external` / `none`) · `relatedInstructions[]` (generated: who reads it, who writes it) ·
`examples[]` · `sources[]`.

### C.3.3 Flags

A sheet per flag with: name, bit, meaning, what sets and clears it, a **reverse index split into "instructions that
read it" and "instructions that write it"** (G-20), the classic mistake associated with it, and a minimal example
with the state before and after. The four sheets the assignment asks to handle with particular care:

- **CF versus OF** — `CF` is unsigned carry; `OF` is signed overflow. The material uses the same pair of values to
  show all four possible combinations.
- **AF** — only meaningful with BCD; undefined almost everywhere else. It is never presented as 0.
- **DF** — the direction of the string instructions; `CLD`/`STD`; the mistake of leaving it set.
- **IF and TF** — privilege and interaction with the debugger: single-stepping itself uses TF.

---

## C.4 Block C — Segments, memory and addressing

### C.4.1 The three things called "segment" (which are not the same)

A dedicated concept sheet, because this is the central misunderstanding of the course **[proposal]**:

| Concept | What it is | Example |
|---|---|---|
| **Segment register** | six CPU registers that select a base | `CS`, `DS`, `SS`, `ES`, `FS`, `GS` |
| **Logical region of the program** | an area the programmer declares | `.DATA`, `.CODE`, `MY_SEG SEGMENT`, `section .text` |
| **Section of the object format** | an entry of the OMF/COFF/ELF file | `.text`, `.data`, `.bss` of an ELF |

An explicit statement of the material: **a program can have more logical regions than segment registers.** The six
registers only say which ones are reachable *at a given moment*. This answers the point the assignment raises twice.

### C.4.2 Addresses

A sheet with the four terms and how they relate: **effective address** (what the addressing mode computes),
**logical address** (`segment:offset`), **linear address** (segment base + effective) and **physical address**
(after paging, if any) **[source: Stallings §11.2, printed 415 and figure 11.2, printed 416]**.

Real mode: `physical = segment × 16 + offset`, a limit of 1 MiB + 64 KiB − 16 B, and the *wraparound* with the A20
gate as a historical note. Protected mode: selector → descriptor → base and limit. Long mode: a flat model with
CS/DS/ES/SS at base 0 and FS/GS as the exception.

### C.4.3 Addressing modes per address size

A table of **valid and invalid combinations, with the reason** (G-24):

| 16-bit address | Valid | Invalid and why |
|---|---|---|
| base | `[BX]`, `[BP]` | `[AX]`, `[CX]`, `[DX]`, `[SP]`: the 8086 only accepts BX and BP as a base |
| index | `[SI]`, `[DI]` | `[BX+BP]`: two bases; `[SI+DI]`: two indexes |
| base+index | `[BX+SI]`, `[BX+DI]`, `[BP+SI]`, `[BP+DI]` | any other pair |
| with a displacement | `[BX+SI+4]`, `[BP+6]` | — |
| scale | — | `[SI*2]`: there is no scale with a 16-bit address |

With a 32/64-bit address: any general register as a base, any except ESP/RSP as an index, a scale of 1/2/4/8 and a
displacement of 0/8/32 bits **[source: Stallings figure 11.2]**; on 64 bits, RIP-relative addressing as well
(`default rel` in NASM, which the IDE template already uses **[code]**).

Default segment and override: DS for most, **SS when the base is BP or SP**, ES for the destination of the string
instructions, CS for code. Each rule with its example and its counterexample.

### C.4.4 Data organization

DOS memory models (`tiny`, `small`, `medium`, `compact`, `large`, `huge`) and what they mean for the program;
`near` and `far` pointers; DS initialization (why `mov ax, @data` / `mov ds, ax` and not `mov ds, @data`);
little-endian byte order, with a memory dump example; alignment; `BYTE`/`WORD`/`DWORD`/`QWORD`/`TBYTE` sizes;
arrays; strings; structures; the stack as dynamic memory.

---

## C.5 Block D — Symbols, operators and syntax

**Principle of the glossary: no character has a universal meaning.** Each entry is `(sign, dialect, context)` and
declares whether it is **assemblable syntax** or **explanatory notation** (the distinction acceptance case 2 asks
for).

### C.5.1 The colon: four different entries

| Entry | Example | What it is | Assemblable? |
|---|---|---|---|
| `syntax.common.colon.label` | `loop_top:` | defines a label at that position in the code | **yes** |
| `syntax.common.colon.segoverride` | `ES:[DI]`, `SS:[BP+4]` | segment override prefix on a memory operand | **yes** |
| `syntax.common.colon.logicaladdress` | `CS:IP`, `SS:SP` | notation for a logical address `segment:offset` | **no**: it is documentation notation |
| `syntax.common.colon.registerpair` | `DX:AX`, `EDX:EAX` | notation for a pair of registers that form a double-width value | **no** |

`DS:DX` deserves a note of its own: in the documentation of DOS services it names the pair that forms the address
of the buffer, and it is not syntax that is written in an instruction. Case 2 requires exactly these four answers.

### C.5.2 Brackets, parentheses and the rest

| Sign | Entries per dialect | Note |
|---|---|---|
| `[ ]` | MASM/TASM: memory contents, and also an added displacement (`[BX]` ≡ `BX` in some contexts); NASM: **required** to access memory | NASM: "any access to the *contents* of a memory location requires square brackets" **[source: NASM 3.02 manual, ch. 2]** |
| `( )` | grouping in expressions evaluated at assembly time | not a memory access |
| `,` | operand separator; destination first in Intel syntax | contrast with AT&T, only as a note |
| `;` | comment to the end of the line | all three dialects |
| `' '` `" "` | character and string literals | MASM/TASM/NASM differ in escapes |
| `.` | directive prefix (`.MODEL`) and local label prefix in some dialects | the lexer already treats it specially **[code]** |
| `?` | uninitialized data (`DW ?`) | versus NASM `RESW 1` |
| `$` | the current assembly position | `message_len equ $ - message` in the NASM template **[code]** |
| `$$` | start of the current section (NASM) | does not exist in MASM/TASM |
| `@` | a valid identifier character; `@data` is a **predefined MASM/TASM symbol** | acceptance case 3 |
| `%` | NASM preprocessor (`%macro`, `%define`, `%if`) | today the lexer drops it (A-23) |
| `+ - * /` | expression operators evaluated **at assembly time** | versus `ADD`/`SUB`/`MUL`/`DIV`, which the CPU executes |
| `:` | §C.5.1 | |
| `< >` | literal macro arguments in MASM/TASM | the lexer does not recognize them |
| `&` | concatenation in MASM macros | |

### C.5.3 Three distinctions with a sheet of their own

1. **Address, immediate value and memory contents.** `mov ax, OFFSET msg` (address), `mov ax, 5` (immediate),
   `mov ax, [msg]` / `mov ax, msg` depending on the dialect (contents).
2. **`LEA` versus a read.** `lea dx, [bx+si+4]` computes; `mov dx, [bx+si+4]` reads. Same operand, different
   result.
3. **Assembler expression versus CPU operation.** `equ 4*8` is resolved at assembly time and costs no cycles;
   `imul ax, 32` is done by the CPU.

### C.5.4 `mov ax, variable` versus `mov ax, [variable]`

A comparison sheet that **does not promise universal equivalence** (an explicit requirement of the assignment):

| Dialect | `mov ax, var` | `mov ax, [var]` |
|---|---|---|
| **NASM 3.02** | loads **the address** of `var` | loads **the contents** |
| **MASM 6.11 / TASM MASM mode** | loads **the contents** (the address is asked for with `OFFSET var`) | also the contents |

The source for the NASM column: the manual explains that it avoids MASM's ambiguity, where the same syntax could
generate different code depending on how the symbol had been declared, and that this is why NASM needs no
`OFFSET` **[source: NASM 3.02 manual, ch. 2]**. The MASM column is closed against the MASM 6.11 manual (task
AA-P0-01); meanwhile the sheet cites Irvine §4.3.1 for `OFFSET`
**[source: Irvine, printed 94: the OFFSET operator returns the distance of a variable from the start of its
enclosing segment]**.

### C.5.5 Directives and operators

Per dialect and with a version. The corpus minimum:

- **Data:** `DB`/`DW`/`DD`/`DQ`/`DT` and MASM `BYTE`/`WORD`/`DWORD`; `DUP`; `?`; `EQU` and `=`; `ORG`; `ALIGN`/
  `EVEN`; `LABEL`; `STRUC`/`ENDS`; `RECORD`. NASM: `DB`…`DO`, `RESB`…`RESZ`, `TIMES`, `EQU`, `$`, `$$`.
- **MASM/TASM operators:** `OFFSET`, `SEG`, `PTR`, `TYPE`, `LENGTH`/`LENGTHOF`, `SIZE`/`SIZEOF`, `THIS`, `WIDTH`,
  `MASK`, `HIGH`/`LOW`, `SHORT`, `NEAR`/`FAR`. It records that `LENGTH` and `SIZE` are the **legacy forms** of
  `LENGTHOF` and `SIZEOF` **[source: Irvine, printed 94]** and that the full listing is in the book's MASM reference
  appendix (§G.3 explains the letter discrepancy between the body and the index).
- **Segmentation and model:** `.MODEL`, `.STACK`, `.DATA`, `.CODE`, `.STARTUP`, `.EXIT`, `SEGMENT`/`ENDS`,
  `ASSUME`, `GROUP`; versus NASM `SECTION`, `BITS`, `DEFAULT REL`, `GLOBAL`, `EXTERN`, `COMMON`.
- **CPU:** `.8086`…`.686`, `.8087`/`.287`/`.387`, TASM `P386`/`P486`, `IDEAL` and `QUIRKS`; NASM `CPU`.
  A mandatory note: **TASM does not reject 80186 instructions under `.8086`; it expands them** **[PLAN.md §8]**, so
  the directive is no guarantee and the validation has to live in the IDE.
- **Procedures and macros:** `PROC`/`ENDP`, `MACRO`/`ENDM`, `LOCAL`, `INVOKE`, `PROTO`; versus the NASM
  preprocessor (`%macro`, `%define`, `%if`, `%include`).
- **Symbols and modules:** `PUBLIC`, `EXTRN`/`EXTERN`, `INCLUDE`, `INCLUDELIB`, conditional assembly.

---

## C.6 Block E — Interrupts, exceptions and services

### C.6.1 Four different things

| Concept | What causes it | Sheet |
|---|---|---|
| The `INT n` instruction | the program | instruction (F-14) |
| Hardware interrupt | a device, through the controller | concept + vector index |
| Processor exception | a condition of the execution itself (#DE, #UD, #GP…) | service of kind `EXCEPTION` |
| Environment service | a handler installed by the BIOS, DOS or the OS | service of kind `BIOS`/`DOS` |

The material explains the whole mechanism: the real-mode vector table (4 bytes per vector, address `n × 4`, which
is exactly what the emulator's functions `25h` and `35h` do **[code]**), what is saved on the stack, the return
with `IRET` and why it restores the flags, masking with IF and CLI/STI, and the differences in protected mode (IDT
with interrupt and trap gates, and the effect on IF that the note of A-13 explains).

### C.6.2 Index of vectors 00h–FFh

256 rows, each with `status ∈ {ARCHITECTURAL, BIOS, DOS, RESERVED, ENVIRONMENT_DEPENDENT, UNDOCUMENTED, PENDING}`.
**A service is not invented for every number.** The number of vectors does not measure function coverage, and the
report counts them separately.

### C.6.3 Services with a contract

Hierarchy `Service` → `function` → `subfunction`. Fields per function: selector (register and value), inputs,
outputs, registers and flags **affected or preserved according to the documented contract**, buffer format,
errors, the DOS/BIOS version that introduced it, environment requirements, an example and `availability` per
backend. Decided depth:

| Vector | Depth in the closed corpus |
|---|---|
| `INT 21h` | **complete** for the 13 the emulator implements and for the ones the course uses (`01h`, `02h`, `06h`–`0Bh`, `09h`, `0Ah`, `25h`, `2Ah`–`2Dh`, `30h`, `35h`, `3Ch`–`42h`, `4Ch`, `4Dh`); **minimal** for the rest of the inventory |
| `INT 10h` | **complete** for `00h`, `02h`, `06h`, `09h`, `0Eh`, `0Fh`, `13h`; minimal for the rest |
| `INT 16h` | **complete** (`00h`–`02h`, `10h`, `11h`) |
| `INT 20h` | complete |
| `INT 13h`, `1Ah`, `11h`, `12h`, `15h` | minimal |
| `INT 2Fh`, `33h` | minimal, noting that `33h` is a **driver** (mouse), not BIOS |
| Exceptions `00h`–`14h` | complete: name, condition, whether it pushes an error code, modes |
| The rest | index with a state |

A mandatory mark per function: **what `DosInterruptHandler` implements versus what another environment requires**
(annex A §A.9 is the input). And an explicit separation: the Windows API, the calling ABI and Linux syscalls are
distinct blocks; `INT 21h` is **not** presented as a general mechanism for modern native programs, and the syscall
number is **not** presented as a property of x86.

---

## C.7 Block F — Labels, procedures and organization

Contents: global and local labels (`@@`, `.loc`, scope per procedure), resolution of the project's symbols (the
`ProjectSymbolIndex` already tells the five classes apart **[code]**), jumps and their reach, `CALL`/`RET`, `near`
and `far` calls, parameter passing (registers, stack, global variables), return values, preserved registers, the
stack frame with BP/EBP/RBP, recursion, and `ENTER`/`LEAVE`.

**A classification the assignment requires keeping separate:**

| Class | Examples | Note |
|---|---|---|
| user procedure | `MAIN PROC` | |
| macro | NASM `%macro`, MASM `MACRO` | expanded at assembly time; not a call |
| library procedure | Irvine32 `WriteString` | **a third-party library; IDEARM does not distribute it** |
| OS API function | `WriteFile` from `kernel32` | linked; only `kernel32` is supported **[README]** |
| OS service | `INT 21h/09h`, Linux `syscall` 1 | not a function call |

**Calling conventions**, only for the combinations relevant to the profiles:

| Profile | Convention | Points the sheet must cover |
|---|---|---|
| `dos-exe-16` | course practice: registers and stack with `RET n` | who cleans the stack; preserving `PUSH`/`POP` |
| `win-pe32-console` | `cdecl` and `stdcall` | name decoration (`_WriteFile@20` in the template **[code]**), who cleans |
| `win-pe64-console` | Microsoft x64 ABI | RCX/RDX/R8/R9 and XMM0–3; **a 32 B shadow space the caller reserves**; stack aligned to 16 B; volatile RAX, RCX, RDX, R8–R11, XMM0–XMM5; non-volatile RBX, RBP, RDI, RSI, RSP, R12–R15, XMM6–XMM15; return in RAX/XMM0 **[source: Microsoft Learn, rev. 2026-05-21]** |
| `linux-elf64` | System V AMD64 | function ABI (RDI, RSI, RDX, RCX, R8, R9) **versus** syscall ABI (RDI, RSI, RDX, R10, R8, R9 and the number in RAX); the 128 B *red zone* and its conditions |

**Build cycle:** source → preprocessing → assembly → object (OMF/COFF/ELF) → symbols and relocations → link →
executable (MZ/PE32/PE32+/ELF64) → loading → debugging, illustrated with the repository's real artifacts
(`build/debug/obj`, `lst`, `map`, `bin`). An explicit note: **`COM` is not a supported profile** even though the
emulator has a `ComLoader` **[code]**.

---

## C.8 Block G — Learning paths

Nine paths. Each with prerequisites, objectives, linked contents, examples, **original exercises** and explained
solutions. The exhaustive reference stays reachable at all times: the paths are a door, not a filter.

| # | Path | Prerequisites | Checkable objective | Level |
|---|---|---|---|---|
| R-1 | **Reading an instruction** | none | The student breaks down `mov ax, [bx+si+4]`, naming each part, the size, the default segment and the address calculation | basic |
| R-2 | **Data and registers** | R-1 | Declares data, chooses the right register and explains the AX/AH/AL overlap | basic |
| R-3 | **Memory and segments** | R-2 | Computes a physical address in real mode and tells a segment register, a logical region and a section apart | basic→intermediate |
| R-4 | **Conditions and loops** | R-2 | Chooses between `JG` and `JA` with the reason, and writes one loop with `LOOP` and another with `CMP`+`Jcc` | basic→intermediate |
| R-5 | **Stack and procedures** | R-4 | Follows `CALL`/`RET` in the debugger, identifies the return address and builds a frame with BP | intermediate |
| R-6 | **Environment services** | R-3 | Uses `INT 21h` `09h` and `0Ah` respecting the contract, and explains what they would do on Windows or Linux | intermediate |
| R-7 | **A complete program** | R-5, R-6 | Creates, assembles, links, runs and packages a program that reads and writes | intermediate |
| R-8 | **Debugging** | R-7 | Sets a breakpoint, steps instruction by instruction, reads flags and memory, and explains a failure | intermediate |
| R-9 | **Advanced topics** | R-8 | Compares signed and unsigned arithmetic, uses the x87, and explains why an instruction is not available in their profile | advanced |

Three levels of depth, with the rule that **the level does not hide the reference**: the complete sheet is always
reachable from the summary, and the summary never contradicts the sheet.

---

## C.9 Topics added by the audit, not listed in the assignment

The assignment asks for the list to be extended when the audit justifies it. These seven topics are added
**[proposal]**:

| Topic | Why the audit requires it |
|---|---|
| **Mnemonic homonyms** (`MOVSD`, `CMPSD` string versus SSE2) | A-14: today a sheet explains the wrong reading |
| **Mnemonics of other dialects** (GNU as `movabs`; MASM `retn`/`retf`) | A-08: the catalog declares one of them as an x86 instruction |
| **Undocumented instructions** (`SALC`, `ICEBP`, `INT1`) | the assignment requires being able to mark "undocumented"; without an entry, the analyzer marks them as errors |
| **What each debugger exposes** | A-29: the corpus must tell theory apart from observable data |
| **Limits of the built-in emulator as a topic of its own** | A-25: the student needs to know why their `FSQRT` fails with "invalid opcode" |
| **Discrepancies between sources as content** | function `09h` of `INT 21h` returns `AL='$'` in the emulator and "nothing" in Irvine (§A.9): the student learns more from the discrepancy explained than hidden |
| **Difference between an assembler expression and a CPU operation** | A-24: the search does not tell `$`, `EQU` and `ADD` apart, and neither does the student |
