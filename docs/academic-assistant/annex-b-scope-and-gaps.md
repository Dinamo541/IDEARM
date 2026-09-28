# Annex B — Scope and compatibility matrix, target corpus and gap matrix

> Part of [`expansion-plan.md`](expansion-plan.md). Covers phase 1 §4–§6 and deliverables 2 and 3.

---

## B.1 The axes, and why none of them can be collapsed into another

The assignment forbids confusing architecture, generation, mode, extension, dialect, ABI, OS, toolchain and
emulator. These are the axes the corpus models separately, with the values IDEARM actually covers.

| Axis | Values in scope | Evidence that it must be kept separate |
|---|---|---|
| **ISA / architecture** | x86 (Intel/AMD) | `TargetProfile.architecture` is `"x86"` in all four profiles **[code]**. The name "IDEARM" is no evidence of ARM and there is no line aimed at another ISA |
| **CPU generation** | 8086/8088 · 80186/80188 · 80286 · 80386 · 80486 · P5 (Pentium) · **P6 (Pentium Pro/II)** · x86-64 | `CpuLevel` has no P6, so `CMOVcc` is listed as Pentium (A-03) |
| **Processor mode** | real · 16-bit protected · 32-bit protected · compatibility · long (64) | `TargetProfile.processorMode` already exists (`"real"`, `"protected"`, `"long"`) and is unused (A-15, H-05). Without this axis, `AAA` is offered in a 64-bit project (A-16) |
| **Operand size** | 8 · 16 · 32 · 64 bits | `mov ax, …` and `mov eax, …` have neither the same requirement nor the same encoding |
| **Address size** | 16 · 32 · 64 bits | `[bx+si]` is valid with a 16-bit address and invalid with 32; `[eax*4+d]` the other way round. Neither follows from the operand size |
| **Extension** | x87 · MMX · SSE · SSE2 · SSE3 · SSSE3 · SSE4.1 · SSE4.2 · AVX · AVX2 · AVX-512 · BMI1 · BMI2 · ADX · AES-NI · SHA · RDRAND/RDSEED · CLFLUSH/CLWB · MONITOR · TSX | "x86-64 CPU" implies none of them. Today the x87 is declared as 8086 because there is no axis (A-09) |
| **Privilege** | CPL 0 · any · IOPL-dependent | `IN`/`OUT`/`CLI` depend on IOPL in protected mode; `LGDT` requires CPL 0 |
| **Dialect + version** | MASM 6.11 · TASM 4.1 (MASM mode) · TASM 4.1 (IDEAL mode) · NASM **2.16.01 / 3.01 / 3.02** | Three NASM versions coexist in the repository and its documentation (§B.4). `mov ax, var` means different things depending on the dialect **[source: NASM 3.02 manual, ch. 2]** |
| **OS / ABI** | DOS (INT 21h) · Windows x86 (stdcall/cdecl) · Windows x64 (Microsoft ABI) · Linux x86-64 (System V AMD64, function ABI and syscall ABI kept apart) | The function ABI and the syscall ABI are different contracts: Win64 passes in RCX/RDX/R8/R9 **[source: Microsoft Learn, rev. 2026-05-21]**; the Linux syscall uses another set |
| **Binary format** | MZ · PE32 · PE32+ · ELF64 (object: OMF · COFF · ELF) | `TargetProfile.executableFormat` / `objectFormat` **[code]**. `COM` is **not** a supported profile even though the emulator has a `ComLoader` |
| **Toolchain** | `borland-tasm` · `microsoft-masm` · `nasm` | `BorlandToolchainProvider`, `Microsoft16ToolchainProvider`, `NasmToolchainProvider` **[code]** |
| **Host** | Windows · Linux | `NasmToolchainProvider.resolve` **throws** `toolchain.target.host` when the host is Windows and the target is Linux **[code]** |
| **Debug backend** | `emu8086` · `gdb` · `external` (Turbo Debugger / CodeView in DOSBox) | `DebugConfiguration`, `DebugCapability`. A "launch-only" backend declares no capability **[code]** |

---

## B.2 The seven independent states

A cell of the matrix is not a boolean. It has seven states, each with the values
`yes` · `partial` · `no` · `unk.` (unknown) · `n/a`:

| State | Means | Who decides it today |
|---|---|---|
| **documented** | the corpus explains the semantics with a source | `InstructionCatalog` |
| **recognized** | the lexer and the highlighter treat it as a valid instruction/register/directive | `AssemblyLexer` via `knownMnemonics()` |
| **validated** | the analyzer correctly decides whether it is acceptable in that context | `CpuBaselineRule`, `UnknownInstructionRule` |
| **assemblable** | the profile's assembler produces an object | TASM / ML / NASM |
| **linkable** | the profile's linker produces an executable | TLINK / LINK 5.31 / GNU ld |
| **runnable** | the program runs in the profile's environment | DOSBox / host |
| **debuggable** | it can be stopped, inspected and stepped | emu8086 / GDB / TD-CV |

Rule of use: **a state is never inferred from another.** NASM assembling `movaps` does not make it "validated";
the catalog documenting `FSQRT` does not make it "debuggable".

---

## B.3 Matrix by profile and family

The four profiles come from `TargetProfileCatalog` **[code]**:

```
dos-exe-16        x86 · 8086   · 16 bits · real      · DOS     · MZ    · small · OMF
win-pe32-console  x86 · 80386  · 32 bits · protected · Windows · PE32  · flat  · COFF
win-pe64-console  x86 · x86-64 · 64 bits · long      · Windows · PE32+ · flat  · COFF
linux-elf64       x86 · x86-64 · 64 bits · long      · Linux   · ELF64 · flat  · ELF
```

### B.3.1 `dos-exe-16` (TASM 4.1 or MASM 6.11, DOSBox, `emu8086` backend by default)

| Family | doc. | recog. | valid. | assem. | link. | run. | debug. | Notes |
|---|---|---|---|---|---|---|---|---|
| 8086 integer (transfer, arithmetic, logic, jumps, stack, strings) | partial | yes | partial | yes | yes | yes | yes | 127 entries declare 8086; forms and implicit operands are missing |
| 80186 integer (`PUSHA`, `ENTER`, `LEAVE`, `IMUL imm`, `INS`/`OUTS`, shift with an immediate) | partial | yes | partial | **yes, silently** | yes | yes | **partial** | TASM does not reject 186 instructions under `.8086`: it expands them **[PLAN.md §8]**. The emulator has `C0`/`C1` and `68`/`6A` but not `60`–`67`, `69`/`6B`, `6C`–`6F`, `C8`/`C9` (A-25) |
| 80286 protected integer (`ARPL`, `LAR`, `LSL`, `LSS`) | partial | yes | partial | yes | yes | **n/a** | **no** | documentable, not runnable in real mode |
| 80386+ in a 16-bit project (`.386` with `USE16`) | partial | yes | **no** | yes | yes | partial | **no** | `CpuBaselineRule` warns per generation but does not tell "386 in real mode" from "386 in protected mode" |
| x87 | partial | yes | **no** (A-09) | **unk.** (AA-P0-06) | yes | yes (in DOSBox) | **no** (opcodes `D8`–`DF` missing) | The sheet says "does not modify any flag", which is misleading (A-28) |
| DOS/BIOS interrupts and services | **no** (only `INT` as an instruction) | n/a | n/a | yes | yes | yes | partial | 13 functions of `INT 21h`, 3 of `INT 10h`, 2 of `INT 16h` (annex A §A.9) |
| MASM/TASM directives and operators | partial (lexer) | yes | n/a | yes | yes | n/a | n/a | no sheets: `OFFSET`, `PTR`, `SEG`, `@data`, `ASSUME`, `DUP` have no entry |

### B.3.2 `win-pe32-console` (NASM + GNU ld, GDB)

| Family | doc. | recog. | valid. | assem. | link. | run. | debug. | Notes |
|---|---|---|---|---|---|---|---|---|
| 80386 integer | partial | yes | **no** (A-18: only the unknown-mnemonic rule runs) | yes | yes | yes | yes | |
| SSE/SSE2/MMX/AVX | **no** | **no** | **false error** (A-18) | yes | yes | yes | partial | GDB exposes them; the panel shows 16 general registers (A-29) |
| System (`LGDT`…) | **no** | **no** | false error | yes | **n/a** | n/a | n/a | useless in user space; documented as such |
| NASM directives (`SECTION`, `RESD`, `TIMES`, `%macro`, `$`, `$$`) | **no** | partial (mixed with MASM, `%` dropped: A-23) | n/a | yes | yes | n/a | n/a | The editor offers MASM directives in a NASM file |
| Windows API (`kernel32`) | **no** | n/a | n/a | yes | **partial** | yes | yes | "Windows linking supports `kernel32`; other libraries not yet" **[README]**; PE32 needs `--enable-stdcall-fixup` **[PLAN.md §8]** |
| x86 stdcall/cdecl ABI | **no** | n/a | n/a | n/a | n/a | n/a | n/a | The generated template uses `_WriteFile@20` without explaining the decoration **[code]** |

### B.3.3 `win-pe64-console` (NASM + GNU ld, GDB)

Same as B.3.2, with these differences **[code + PLAN.md §8]**:

- **Debugging:** `-f win64` only supports `cv8` information, which GDB 17.2 does not read. The adapter emits ELF
  with DWARF and links it into a PE; that is why `NasmAssemblerAdapter.format()` returns `elf64` when `debugInfo`
  is true. State `debuggable = yes`, with the reason documented.
- **Win64 ABI:** RCX/RDX/R8/R9 and XMM0–3; a 32-byte shadow space that **the caller reserves**; a stack aligned
  to 16 bytes outside the prologue and epilogue; return in RAX or XMM0; volatile RAX, RCX, RDX, R8–R11, XMM0–XMM5
  **[source: Microsoft Learn, "x64 calling convention", rev. 2026-05-21]**. The generated template does
  `sub rsp, 40` and does not explain it: 32 of shadow space plus 8 to restore the alignment the `call` broke.
- **Long mode:** at least 15 catalog entries are invalid here and are offered anyway (A-16); the final list is
  closed by AA-P0-03.

### B.3.4 `linux-elf64` (NASM + GNU ld, GDB)

| State | Windows as host | Linux as host |
|---|---|---|
| assemblable | yes (`nasm -f elf64`) | yes |
| **linkable** | **no** — `NasmToolchainProvider.resolve` throws `toolchain.target.host`: "MSYS2/MinGW ld only writes PE" **[code]** | yes |
| runnable | no | yes (verified on Ubuntu 24.04 **[PLAN.md §8]**) |
| debuggable | no | partial: "interactive input under GDB is limited" **[README]** |
| ABI | System V AMD64: function and syscall are different contracts; the template uses `syscall` with `eax`=1/60 **[code]** | same |

Consequence for the content: a Linux syscall sheet must declare `host = Linux` as well as the profile, or the
student on Windows will see an example they cannot build. Acceptance case 12 depends on this.

### B.3.5 Cross-cutting families: decided treatment level

No extension is left out. Each one has a row, a level and a phase **[proposal]**:

| Family | Level in the closed corpus | Phase | Reason |
|---|---|---|---|
| Base integer (8086 → x86-64) | **complete sheet** per form | AA-P2, AA-P5 | core of the course |
| x87 | **complete sheet** | AA-P5 | chapter 17 of Irvine; the catalog already has 23 entries |
| System and tables (`LGDT`, `LIDT`, `LMSW`, `SLDT`, `STR`, `VERR`, `CLTS`, `INVLPG`, `RDMSR`, `WRMSR`, `RSM`, `SWAPGS`) | **minimal sheet** + protected-mode concept | AA-P5 | they are studied, not run in a student program |
| MMX | **minimal sheet** | AA-P5 | historical; needed so no false errors are marked |
| SSE, SSE2 | **complete sheet** for the scalar and move subset; **minimal** for the rest | AA-P5 | SSE2 is the basis of floating point on x86-64 |
| SSE3, SSSE3, SSE4.1, SSE4.2 | **outline** (identifier, family, CPUID bit, availability) | AA-P5 | outside the course; essential for the inventory |
| AVX, AVX2, AVX-512 | **outline** with a note on the VEX/EVEX encoding | AA-P6 | same |
| BMI1, BMI2, ADX, AES-NI, SHA, RDRAND/RDSEED, TSX, CET, APX | **outline** | AA-P6 | same |
| Undocumented (`SALC`, `ICEBP`, `INT1`) | **minimal sheet marked "undocumented"** with the source of the claim | AA-P5 | the assignment requires being able to mark "undocumented" without inventing |

Definition of the three levels:

- **complete** — every mandatory field of the normalized sheet (annex C §C.2), typed forms, per-form flags,
  verified progressive examples, sources.
- **minimal** — identifier, name, family, summary, one explanation, forms without an operand breakdown,
  requirement, flags when they apply, one source. No mandatory runnable examples.
- **outline** — identifier, name, family, extension, CPUID bit, requirement, `recognizedBy`, availability, and the
  explicit sentence "not covered in depth in this version" with the reason. **It is enough for the editor not to
  mark a false error**, which is its main purpose.

---

## B.4 Reference revision: what makes "exhaustive" measurable

| Subject | Reference revision | State |
|---|---|---|
| x86 semantics, encoding, flags, modes, exceptions | **Intel SDM revision 093**, document 767375, official page updated 2026-09-21 | version pinned; PDF not downloaded (AA-P0-03) |
| Vendor differences | AMD manuals for anything AMD-specific | to be pinned (AA-P0-03) |
| NASM dialect | **NASM 3.02 manual** (nasm.us/doc) | pinned. **Watch out:** the Ubuntu CI uses 2.16.01 and the `NasmAssemblerAdapter` comment says 3.01 **[code + PLAN.md §8]**: the corpus records the requirement per version, not "NASM" |
| MASM dialect | MASM 6.11 (the version the IDE invokes) | to be pinned (AA-P0-01). **Modern MASM documentation is not extrapolated** |
| TASM dialect | TASM 3.2 / 4.1, MASM mode and IDEAL mode | to be pinned (AA-P0-01) |
| Linkers | TLINK 3.01 / 7.1, LINK 5.31, GNU ld 2.46 | versions known to the repository **[PLAN.md §8, fixtures/diagnostics]** |
| DOS/BIOS services | original IBM BIOS and MS-DOS references, plus a specialized interrupt list stating origin, version and whether each item is documented | to be pinned (AA-P0-05, includes the license) |
| Windows x64 ABI | Microsoft Learn, "x64 calling convention", revision 2026-05-21 | pinned |
| Linux x86-64 ABI | System V AMD64 psABI maintained at `gitlab.com/x86-psABIs/x86-64-ABI` | repository located, **exact version not pinned** (AA-P0-04) |
| Course books | Irvine 5th ed. (es) · Stallings 7th ed. (es) · Mano 3rd ed. (es) · Abel 3rd ed. | three verified, Abel not readable in this session (annex G) |

**Operational definition.** The corpus is exhaustive with respect to this revision when the coverage report shows
that: (1) every mnemonic in the A–Z index of SDM 093 has an entry of level ≥ outline; (2) every mnemonic that
TASM 4.1, MASM 6.11 or NASM 3.02 accept has a correct `recognizedBy`; (3) the families marked "complete" have all
their forms; (4) vectors 00h–FFh are classified; (5) every remaining gap is a row of the report with its reason and
its task. The absolute number of sheets is **not** a criterion.

---

## B.5 Gap matrix

`R` = requirement of the assignment · phase `AA-Pn` of annex F.

| ID | Requirement | Current situation (evidence) | Change | Phase | Acceptance | Source |
|---|---|---|---|---|---|---|
| G-01 | A real mnemonic is never marked as an error | 110/122 unknown (annex A §A.4); the only active rule on 32/64 bits (A-18) | `recognizedBy` per dialect + downgraded severity while the inventory is open | AA-P1 | In a NASM x64 project, `movaps`/`paddb`/`fsin`/`lgdt`/`movsq` produce no diagnostic; `muv ax,1` is still an `ERROR` | NASM 3.02 manual |
| G-02 | Aliases are declared | `INSTRUCTION` invented (A-01); aliases without a requirement (A-02) | `aliases[]` with a dialect; delete the deducer | AA-P1 | `find("INSTRUCTION")` is empty; `IRETQ` requires long mode | SDM `iret:iretd:iretq` |
| G-03 | Compatibility and flags per form | one `minCpu` and one table per mnemonic (A-04, A-11) | `InstructionForm` + `Requirement` + `FlagEffectSpec` with a condition | AA-P2 | Three-operand `IMUL` warns on 8086; `shl ax,1` and `shl ax,cl` differ in OF/AF | Irvine §7.4.2; SDM `sal:sar:shl:shr` |
| G-04 | Nothing invalid in long mode is offered on 64 bits | 214/214 offered (A-16) | `invalidModes` + context from `TargetProfile` | AA-P2 | The entries of the list AA-P0-03 closes (≥15) do not appear in completion and fail validation | SDM vol. 2 (AA-P0-03) |
| G-05 | Help by position | `getHover(word, locale)`; `:` and `[` unreachable (A-21) | `QueryExplain(file, line, column)` + operand parser | AA-P4 | Cases 1 and 2 of the assignment pass | — |
| G-06 | Project symbols win over the catalog | catalog first (A-20) | reverse the order + secondary card | AA-P1 | Case 10 passes: a user macro `MOV` is explained as a macro | — |
| G-07 | Registers as entities | three lists out of sync (A-23) | `Register` + `views[]` + `fields[]`, a single source | AA-P2 | Lexer, completion and the debugger panel read the same list; a set-equality test | Irvine ch. 2; Mano ch. 6 |
| G-08 | Syntax per dialect | a flat set; `%` dropped (A-23) | `SyntaxItem` with `dialect`/`version`; parameterized lexer | AA-P3 | In a NASM file, `ASSUME` is not colored as a directive and `%macro` is | NASM 3.02 manual ch. 4 |
| G-09 | Services with a contract | no entity | `Service` with function/subfunction/inputs/outputs/errors/version/availability | AA-P3 | Case 4 passes: `AH=09h` and `AH=0Ah` lead to different services with their contracts | Irvine §13.2; interrupt list |
| G-10 | The corpus can grow | wall measured (A-27) | data in resources, validated at build time | AA-P1 | 1000 synthetic entries load and validate without touching class-format limits | measurement of §A.6 |
| G-11 | Tell instruction, prefix, alias, pseudo-instruction and directive apart | `REP` and `LOCK` are "instructions" | a `kind` field | AA-P2 | The `REP` sheet says "prefix" and explains which instructions it is valid with | SDM vol. 2 |
| G-12 | Explicit and implicit operands | not modeled (A-05) | `Operand` with `role` and `access` | AA-P2 | The `MUL r/m16` sheet marks AX as an implicit input and DX:AX as the output | Irvine §7.4.1 |
| G-13 | Operation pseudocode | prose only | an `operation` field with pseudocode and an accessible explanation | AA-P2 | Every complete sheet has both | SDM vol. 2 |
| G-14 | Exceptions, privileges and preconditions | missing | `exceptions[]`, `privilege`, `preconditions[]` | AA-P2 | The `DIV` sheet documents the divide exception and its condition | SDM vol. 2 |
| G-15 | Progressive examples, fragment versus full program | one example per entry, 108 single-line | `Example` with `kind ∈ {FRAGMENT, RUNNABLE}`, initial and final state, requirements | AA-P3 | No `RUNNABLE` example without a profile, a toolchain and an expected result | — |
| G-16 | Common mistakes and counterexamples | missing | `pitfalls[]`, `counterExamples[]` | AA-P3 | The `MOV` sheet explains why `mov ds, 1000h` is not valid | Irvine §4.1.4 |
| G-17 | Related instructions and two-way links | missing | `related[]` with a relation type; generated reverse index | AA-P3 | `MOV` links to `LEA`, `XCHG`, `MOVSX`; and `LEA` to `MOV` | — |
| G-18 | Registers: size, fields, aliases, overlap | missing | a complete `Register` entity | AA-P2 | The AX sheet shows AH/AL and explains that writing AL does not touch AH | Irvine §2.2.2 |
| G-19 | Semantics of writing subregisters and the 8-bit register limit on 64 bits | missing | `writeSemantics` and `encodingConstraints` fields | AA-P5 | The `AH` sheet explains why `AH` and `R8B` cannot coexist in an instruction with REX | SDM vol. 2 (AA-P0-03) |
| G-20 | Flags linked to the instructions that read/write them | only a table per instruction | reverse index flag → instructions | AA-P2 | The CF sheet lists who reads it and who writes it, separately | — |
| G-21 | What the debugger exposes versus what is only explained | not distinguished | `exposedBy[]` per register and field | AA-P2 | The `ST(0)` sheet says no panel shows it today | A-29 |
| G-22 | Segments: do not teach "only four segments" | missing | separate concepts: segment register / logical region / object section | AA-P3 | The sheet says so explicitly and illustrates it with a `.MODEL small` and a `section .text` | Stallings §11.2 (six segment registers) |
| G-23 | Effective / logical / linear / physical address, with formula and limits | missing | a concept sheet with the real-mode formula, wraparound and A20 | AA-P3 | Case 1 requires the calculation | Stallings §11.2, fig. 11.2 |
| G-24 | Valid addressing modes per address size | missing | `AddressingMode` with valid and invalid combinations and their reason | AA-P4 | The help explains why `[bx+bp]` is not valid and `[bx+si]` is | Stallings §11.2; NASM manual ch. 3 |
| G-25 | Glossary of symbols per dialect and context | missing | `SyntaxItem` with `contexts[]`; `:` has four entries | AA-P3 | Case 2 passes | NASM manual ch. 2; Irvine §4.3 |
| G-26 | `mov ax, variable` versus `mov ax, [variable]` | missing | a comparison sheet per dialect, without promising equivalence | AA-P3 | The sheet quotes the NASM rule and the MASM rule separately | NASM 3.02 manual ch. 2 |
| G-27 | `OFFSET`, `SEG`, `PTR`, `TYPE`, `LENGTH(OF)`, `SIZE(OF)` with a version | lexer tokens only | operator sheets with dialect and version | AA-P3 | The `LENGTHOF` sheet notes that `LENGTH` is the legacy form | **Irvine §4.3, printed 94: MASM still supports the legacy directives LENGTH and SIZE** |
| G-28 | Vectors, services, functions and subfunctions kept apart | `INT` is one entry | a four-level hierarchy + an index 00h–FFh with states | AA-P3 | The index marks `reserved`, `undocumented` and `pending` without inventing services | interrupt list (AA-P0-05) |
| G-29 | Tell `INT`, hardware interrupt, exception and service apart | mixed | four distinct `kind` values in `Service`/`Instruction` | AA-P3 | The `INT 21h` sheet does not describe the #GP exception as a "service" | SDM vol. 3 |
| G-30 | Windows API, ABI and Linux syscalls on their own | missing | service entities with `platform` and `abi`; a bounded and declared corpus | AA-P6 | The `INT 21h` sheet says it does not work for modern native programs; none promises to cover the whole API | Microsoft Learn; psABI |
| G-31 | Procedures, frames, conventions and recursion | missing | concepts + ABI sheets compared only for the relevant combinations | AA-P4 | Case 7 passes, with the profile's ABI applied | Irvine ch. 5 and 8; Microsoft Learn |
| G-32 | Irvine32/Irvine16 as a library, not as an ISA | absent | an external library entity with the note "IDEARM does not distribute it" | AA-P6 | Case 12 passes | **Irvine, preface: the book supplies two versions of the link library** |
| G-33 | Source → object → link → load cycle | missing | a concept path tied to the repository's real formats | AA-P4 | The path uses MZ/PE/ELF and does not turn COM into a supported profile | `TargetProfile`, `ComLoader` |
| G-34 | Numeric and logic fundamentals tied to the code | missing | block G of the taxonomy | AA-P6 | Every concept links to at least one instruction and one example | Mano ch. 1 and 6; Stallings ch. 9 |
| G-35 | Content available offline, with measured performance | in-memory catalog, not measured | packaged resources + tagged performance tests | AA-P7 | Load ≤150 ms, search ≤50 ms, hover ≤30 ms, measured and published | NFR of the technical plan |
| G-36 | Accessibility, keyboard, themes and ES/EN in the academic center | partial (the dialog already switches language) | full keyboard walkthrough, verified contrast, key parity | AA-P7 | Signed usability script and a green parity test | ADR-006 |
