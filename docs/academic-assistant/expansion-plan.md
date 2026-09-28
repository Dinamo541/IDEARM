# IDEARM Academic Assistant expansion plan

> **Created:** 2026-09-25 · **Type:** research and planning, no implementation.
> **Assignment:** [`docs/prompts/academic-assistant-prompt.md`](../prompts/academic-assistant-prompt.md).
> **State of the audited tree:** branch `main` at `C:/Codigo/Proyectos/IDEARM`, with 98 modified uncommitted files
> (`git diff --stat`: 6913 insertions, 1389 deletions) and two untracked ADRs (ADR-010, ADR-011). **No existing
> file was modified and nothing was implemented.** The only thing added to the repository is this folder.
> **Language:** the assignment asked for the plan in Spanish, while ADR-006 §1 requires the repository's artifacts
> to be in English. The conflict was recorded as open decision [D-AA-01](annex-f-backlog-and-validation.md#d-aa-01);
> on 2026-09-27 the user chose English, and the whole folder was translated for release 2.0.0.

---

## 0. Index of documents and how to read them

| Document | Contents | Sections of the assignment it covers |
|---|---|---|
| **This file** | Diagnosis, decisions, summaries and the first implementation package | 1–11 (executive summary and decisions) |
| [`annex-a-audit-and-inventory.md`](annex-a-audit-and-inventory.md) | Reproducible inventory of the catalog, counting method and the 30 audit findings with their evidence | Phase 1 §1–§3 · deliverable 1 |
| [`annex-b-scope-and-gaps.md`](annex-b-scope-and-gaps.md) | Scope/compatibility matrix with seven independent states, target corpus, coverage units, gap matrix | Phase 1 §4–§6 · deliverables 2 and 3 |
| [`annex-c-taxonomy-and-paths.md`](annex-c-taxonomy-and-paths.md) | Complete A–G taxonomy, normalized sheet, learning paths | Phase 2 · deliverable 4 |
| [`annex-d-schemas-architecture-ui.md`](annex-d-schemas-architecture-ui.md) | Knowledge schemas, compatibility resolution, integration per module, migration, UI and contextual help | Phase 3 · deliverables 5, 6 and 7 |
| [`annex-e-content-samples.md`](annex-e-content-samples.md) | Six written content samples (IMUL, AX, segments, `:`/`[ ]`, INT 21h/0Ah, procedure with an ABI) | Deliverable 11 (samples) |
| [`annex-f-backlog-and-validation.md`](annex-f-backlog-and-validation.md) | Phased backlog with work packages, validation plan, acceptance cases, risks and open decisions | Phase 4 · deliverables 9 and 10 |
| [`annex-g-sources.md`](annex-g-sources.md) | Source catalog: what was really consulted, PDF/printed page mappings, limits and pending items | Phase 1 §6 · deliverable 8 |
| [`tools/Inventory.java`](tools/Inventory.java) | The inventory program used in this session, to reproduce the counts | Phase 1 §2 |

**Evidence convention.** Every claim of the plan carries a mark:

- **[code]** checked by reading or running the repository in this session;
- **[source]** backed by a documentary source cited with edition, section and page;
- **[proposal]** a design decision of this plan;
- **[assumption]** an unchecked claim, with the research task that must close it.

---

## 1. Diagnosis of the current state, with evidence from the code

### 1.1 Map of the existing academic assistant

Today the assistant is **a dictionary of mnemonics** and a set of queries that reuse the same catalog. There is no
knowledge entity other than an instruction.

| Piece | Real path | Role | Layer |
|---|---|---|---|
| Catalog | [`InstructionCatalog.java`](../../idearm-language/src/main/java/io/github/dinamo541/idearm/language/catalog/InstructionCatalog.java) | 1823 lines; 214 entries registered in a `static {}` with the `reg(...)` helper; maps `CATALOG`, `ALIASES`, `KNOWN_MNEMONICS`; queries `find`, `getForCpu`, `getByCategory`, `searchStartingWith`, `filter`, `knownMnemonics`, `suggest`, Damerau-Levenshtein distance | L3 |
| Data record | [`InstructionInfo.java`](../../idearm-language/src/main/java/io/github/dinamo541/idearm/language/catalog/InstructionInfo.java) | a 10-field `record`: mnemonic, category, EN/ES summary, syntax variants (`List<String>`), `minCpu`, `flags`, EN/ES description, one example; accent-insensitive `matches(query)` | L3 |
| Compatibility | [`CpuLevel.java`](../../idearm-language/src/main/java/io/github/dinamo541/idearm/language/catalog/CpuLevel.java) | an enum of 7 generations with an integer `level`; `isSupportedOn(String)` compares integers; `parseLevel(String)` guesses the generation from a free-form string | L3 |
| Flags | [`FlagSummary.java`](../../idearm-language/src/main/java/io/github/dinamo541/idearm/language/catalog/FlagSummary.java) · [`FlagEffect.java`](../../idearm-language/src/main/java/io/github/dinamo541/idearm/language/catalog/FlagEffect.java) | 9 flags × 5 effects (`MODIFIED`, `CLEARED`, `SET`, `UNDEFINED`, `UNAFFECTED`); a single table per mnemonic | L3 |
| Categories | [`InstructionCategory.java`](../../idearm-language/src/main/java/io/github/dinamo541/idearm/language/catalog/InstructionCategory.java) | 11 categories with an EN/ES name | L3 |
| Screen | [`MnemonicsDictionaryDialog.java`](../../idearm-app/src/main/java/io/github/dinamo541/idearm/app/view/MnemonicsDictionaryDialog.java) | 681 lines; a non-modal `Stage`, master-detail, search, category combo, CPU combo, previous/next, copy example, flag grid with tooltips, reacts to switching language on the fly | L1 |
| ViewModel | [`MnemonicsDictionaryViewModel.java`](../../idearm-app/src/main/java/io/github/dinamo541/idearm/app/viewmodel/MnemonicsDictionaryViewModel.java) | 186 lines; `ObservableList<InstructionInfo>` + filters + sequential navigation | L1 |
| Hover | [`QueryHover.java`](../../idearm-application/src/main/java/io/github/dinamo541/idearm/application/editor/QueryHover.java) | Resolution order: numeric literal → catalog → the project's symbol index; returns a `HoverInfo` | L2 |
| Completion | [`QueryCompletion.java`](../../idearm-application/src/main/java/io/github/dinamo541/idearm/application/editor/QueryCompletion.java) | Instructions filtered by CPU + 30 registers and 30 directives **hand-coded** in the class itself | L2 |
| Analyzer | [`UnknownInstructionRule.java`](../../idearm-language/src/main/java/io/github/dinamo541/idearm/language/linter/UnknownInstructionRule.java) · [`CpuBaselineRule.java`](../../idearm-language/src/main/java/io/github/dinamo541/idearm/language/linter/CpuBaselineRule.java) | `knownMnemonics()` is the single source of truth for "this is an instruction" (ADR-011); `CpuBaselineRule` compares `minCpu.level()` with `parseLevel(cpu)` | L3 |
| Lexer | [`AssemblyLexer.java`](../../idearm-language/src/main/java/io/github/dinamo541/idearm/language/lexer/AssemblyLexer.java) | `INSTRUCTIONS = InstructionCatalog.knownMnemonics()`; its own sets of 68 registers and 70 directives, **without telling dialects apart** | L3 |
| Highlighter | [`AssemblySyntaxHighlighter.java`](../../idearm-app/src/main/java/io/github/dinamo541/idearm/app/editor/AssemblySyntaxHighlighter.java) | Colors according to the lexer, so the catalog decides the color | L1 |
| i18n | [`messages_es.properties`](../../idearm-app/src/main/resources/io/github/dinamo541/idearm/app/i18n/messages_es.properties) · `_en` | 615 lines each; 33 dictionary keys (`dialog.dictionary.*`, `category.*`, `flag.*`) | L1 |

**Real reuse.** The catalog feeds five consumers with different contracts: the dictionary (reference), hover (a
short explanation), completion (a list of candidates), the lexer/highlighter (token classification) and two
analyzer rules (diagnostics with `ERROR` and `WARNING` severity). **[code]** This is what turns "adding academic
content" into a change in the editor's behavior: today any new catalog entry automatically goes into
`knownMnemonics()` and, therefore, into deciding which word is colored as an instruction and which word gets a red
underline.

### 1.2 Reproducible inventory: the numbers and how they were obtained

The counts were **not** estimated by reading the file. The `catalog` package was compiled into a temporary folder
(`javac -d <temp> idearm-language/.../catalog/*.java`) and [`tools/Inventory.java`](tools/Inventory.java) was run
against the resulting classes, that is against the real code, including the uncommitted changes. Annex A reproduces
the full output.

| Unit | Value | Note |
|---|---:|---|
| Primary entries (`getAll()`) | **214** | one per registered canonical mnemonic |
| Recognized mnemonics (`knownMnemonics()`) | **266** | the 214 plus 52 aliases |
| Derived aliases | **52** | **not declared**: deduced from the first token of each syntax variant |
| Syntax variant strings | **485** | free text, not structure; 68 entries have only one |
| Distinct flag tables | **20** | 158 entries (74 %) declare "no flag affected" |
| Examples | **214** | none empty; 108 are a single line |
| Categories | 11 | largest: `CONTROL_FLOW` (50); smallest: `IO_PORTS` (4), `BIT_MANIPULATION` (6) |
| Declared generations | 7 | 8086: 127 · 80186: 7 · 80286: 3 · 80386: 45 · 80486: 3 · Pentium: 21 · x86-64: 8 |

**The number of entries does not measure semantic coverage.** Three measurements show it **[code]**:

1. **One flag table per mnemonic, not per form.** `IMUL` has a single row of flags and a single `minCpu` for its
   1-, 2- and 3-operand forms, while Irvine says literally that the 8086/8088 processors only support the
   one-operand format **[source: Irvine 5th ed. (es), §7.4.2, printed 205 / PDF 237]**.
2. **Forms as text.** The 485 "variants" are strings (`"IMUL reg, reg, imm"`), not typed operands; nothing can
   validate them, search them by operand type, or attach flags or requirements of their own. And the string quoted
   matches no real form: the three-operand one is `IMUL r16, r/m16, imm8|imm16` and `IMUL r32, r/m32, imm8|imm32`
   **[source: Irvine §7.4.2, printed 205–206]**.
3. **The prose and the semantics can contradict each other within the same entry.** `BT` describes "copies the
   selected bit of the first operand into the carry flag (CF)" and at the same time declares `FlagSummary.none()`
   ([`InstructionCatalog.java:1337`](../../idearm-language/src/main/java/io/github/dinamo541/idearm/language/catalog/InstructionCatalog.java)).
   The source confirms the prose and refutes the table: "The CF flag contains the value of the selected bit"
   **[source: HTML rendering of the SDM, felixcloutier.com/x86/bt, consulted 2026-09-25]**. No test catches it.

### 1.3 The ten findings that shape the design

Annex A holds the 30 findings with their full evidence, severity and source. These ten are the ones that change
architecture decisions:

| # | Finding | Evidence | Design consequence |
|---|---|---|---|
| **H-01** | In a 32/64-bit project **the only active rule** is `UnknownInstructionRule`, and it marks any SIMD, advanced x87 or system mnemonic as an `ERROR`, suggesting another one. `movaps` → "No instruction is called 'MOVAPS'". Of 122 real mnemonics tested, **110 are unknown**; several suggestions are misleading (`PAUSE`→`PUSH`, `FSAVE`→`LEAVE`, `SALC`→`SAL`, `MWAIT`→`FWAIT`) | [`LintSource.java:29`](../../idearm-application/src/main/java/io/github/dinamo541/idearm/application/editor/LintSource.java) `portableLinter` only carries that rule; running `FalsePositives` (annex A §A.4) | The corpus's coverage **is not decoration**: today its absence produces false errors. The corpus must tell "recognized by the assembler" from "documented in depth", and the analyzer may only read the first |
| **H-02** | `INSTRUCTION` is a valid mnemonic. `LOCK` declares the variant `"LOCK instruction"` and the alias generator takes its first useful token | [`InstructionCatalog.java:1313-1314`](../../idearm-language/src/main/java/io/github/dinamo541/idearm/language/catalog/InstructionCatalog.java); `find("INSTRUCTION") → LOCK` | Aliases must be **declared**, with their dialect and encoding, never deduced from a display string |
| **H-03** | The compatibility axis is a single ordered integer. `getForCpu("x86-64")` returns **all 214** entries, including `AAA`, `AAD`, `AAM`, `AAS`, `DAA`, `DAS`, `PUSHA`, `POPA`, `INTO`, `BOUND`, `LDS`, `LES`, `ARPL`, which are invalid in long mode | `Inventory` output (annex A §A.3); `CpuLevel.isSupportedOn` | Compatibility needs independent axes (generation, mode, extension, privilege) and a three-valued answer with "unknown". Acceptance case 8 is impossible with the current model |
| **H-04** | `parseLevel` silently downgrades what it does not recognize to 8086: `"x86"`, `"32"`, `"unknown"` → level 0 → 127 of 214 entries and false CPU warnings. The field that feeds this is the TOML's `cpu`, free text | `CpuLevel.parseLevel`; `TargetSelection(String profile, String cpu)`; `Inventory` §"CpuLevel filtering behaviour" | The compatibility context must be built from the `TargetProfile` (which already has `codeMode` and `processorMode`), not from a string, and the unknown must be `UNKNOWN`, not 8086 |
| **H-05** | `processorMode` (`"real"`, `"protected"`, `"long"`) exists in the domain and **is never used**: it is only copied in `CompatibilityResolver` | `grep processorMode` → 3 occurrences, all construction | The mode axis is already modeled in L3; it only has to be passed along. No new target model needs inventing |
| **H-06** | Hover consults the catalog **before** the project's symbols, and receives neither file, line nor target | [`QueryHover.java:45-63`](../../idearm-application/src/main/java/io/github/dinamo541/idearm/application/editor/QueryHover.java); `WorkbenchViewModel.getHover(word, locale)` | Acceptance case 10 (a user macro named like an instruction) fails today. Contextual help needs a query with a position, not with a word |
| **H-07** | The hover word is extracted with `isWordChar` (letters, digits, `_ @ $ ? .`), so `:` and `[` **never** reach the help, and `21h` in `int 21h` is explained as "Numeric Literal … ASCII '!'" | [`RichTextFxEditorComponent.java:512-541`](../../idearm-app/src/main/java/io/github/dinamo541/idearm/app/editor/RichTextFxEditorComponent.java); `QueryHover.tryNumericConversion` | Cases 1, 2 and 4 require help by **position** and awareness of the instruction around the token |
| **H-08** | Three different, out-of-sync register lists: 68 in the lexer (including `R8`–`R15`, `RIP`, `SIL`), 30 in completion (no `R*`, no `FS/GS`), 10 in `CpuBaselineRule.REGS_32BIT` (which also calls `FS` and `GS` "32-bit registers") | `AssemblyLexer.REGISTERS`, `QueryCompletion.REGISTERS`, `CpuBaselineRule.REGS_32BIT` | Registers must be corpus entities, with partial views, and a single source for the three pieces |
| **H-09** | Directives are a flat set that mixes dialects: `.MODEL`, `ASSUME`, `PROC` next to `SECTION`, `RESB`, `TIMES`, `BITS`, `GLOBAL`. `%` is silently dropped, so `%macro` tokenizes as the `MACRO` directive | `AssemblyLexer.DIRECTIVES` (70 entries); `tokenizeLine` "Unknown character, skip ahead" | The dialect is a first-class axis of the corpus and the lexer. The Windows/Linux profiles use NASM and the editor offers them MASM syntax |
| **H-10** | The static initializer measures **9406 bytes** of bytecode for 214 entries (≈44 B/entry) against the 65535 B per-method limit; the `.class` already weighs 100 642 B | `javap -c -p` on the compiled class (annex A §A.6) | With the same model the wall arrives around **1490** entries; with the extended sheet (forms, operands, per-form flags, sources) it would arrive much sooner. **The corpus cannot remain hand-written Java code** |

### 1.4 What is kept

The assistant does not have to be rewritten. These pieces are correct and the plan preserves them **[proposal]**:

- **`FlagEffect` with five values**, including `UNDEFINED` distinct from `CLEARED`: the model already avoids the
  mistake the assignment forbids ("do not represent 'undefined' as 0"). It only has to be applied per form and
  six tables corrected.
- **The ADR-011 rule of a single source** for "is this an instruction?". The principle is kept and the criterion
  changes: not "it is in the corpus", but "the corpus declares that this assembler accepts it".
- **The bilingual contract per entry** (ADR-006 §8) and the dialog's on-the-fly language switch.
- **The useful behaviors of the dialog**: non-modal window, accent-insensitive search, filters with chips that
  navigate, previous/next, copy example, tooltips per flag.
- **The ADR-007 layer separation** and the ban on `Files`/`ProcessBuilder` in L3, verified by `ArchitectureTest`.
  The plan introduces no file-system I/O and no new dependencies in L3.
- **The project symbol index** (`ProjectSymbolIndex`, `SymbolKind`), which already tells macro, label, procedure,
  constant and variable apart: it is the basis for solving case 10 without inventing anything.

---

## 2. Scope and compatibility matrix, target corpus and limits

Full detail in [annex B](annex-b-scope-and-gaps.md). Summary of the decisions:

**ISA scope.** Only x86 (Intel/AMD), generations 8086/8088 · 80186/80188 · 80286 · 80386 · 80486 · P5 (Pentium) ·
**P6 (Pentium Pro/II, missing from `CpuLevel` today, which makes `CMOVcc` show up as Pentium)** · x86-64 (AMD64 /
Intel 64). **ARM, AArch64, MIPS, PowerPC and IA-64 are out**: the product's name is no evidence and there is not a
line of code aimed at them **[code]**. IA-64 (Itanium) appears in Stallings ch. 15 **[source: Stallings 7th ed.
(es), TOC, printed 563]** and will be documented, if at all, as a historical concept explicitly unrelated to x86-64.

**Axes of the matrix** (independent, never collapsed into one): ISA · generation · processor mode (real,
protected 16, protected 32, compatibility, long) · operand size · address size · extension (x87, MMX, SSE…SSE4.2,
AVX/AVX2/AVX-512, BMI, ADX, AES-NI, SHA, RDRAND) · privilege · dialect and version (MASM 6.11, TASM 4.1 MASM mode /
IDEAL mode, NASM 3.02) · OS/ABI · binary format · toolchain · host · debug backend.

**Seven independent states per cell**, with the values `yes` / `partial` / `no` / `unknown` / `n/a`: `documented` ·
`recognized` (lexer and highlighter) · `validated` (analyzer) · `assemblable` · `linkable` · `runnable` ·
`debuggable`. Annex B fills them in for the four profiles and for the cross-cutting families. Three summary rows
show why seven columns and not a boolean are needed **[code/source]**:

| Combination | doc. | recog. | valid. | assem. | link. | run. | debug. |
|---|---|---|---|---|---|---|---|
| 8086 integer · DOS · TASM 4.1 · emu8086 | partial | yes | partial | yes | yes | yes | yes |
| x87 · DOS · TASM 4.1 · emu8086 | partial | yes | **no** (declares `minCpu` 8086, so it never warns) | unk. | yes | yes | **no** (opcodes `D8`–`DF` missing → invalid opcode failure) |
| SSE2 · Windows x64 · NASM 3.02 · GDB | **no** | **no** | **no** (false error, H-01) | yes | yes | yes | partial (GDB lists them; the panel shows only 24 general registers) |

**Coverage units.** Instructions: *family* → *canonical mnemonic* → *alias per dialect* → **form** (the real unit of
semantics) → *encoding* (only where it teaches something) → *requirement*. Interrupts: *vector* (the complete
00h–FFh index, with the states `architectural`/`BIOS`/`DOS`/`reserved`/`environment-dependent`/`undocumented`/
`pending`) → *service* → *function* → *subfunction*. Registers: *architectural entity* → *partial view* → *field*.
The number of vectors does **not** measure function coverage and the plan never uses it that way.

**How "exhaustive" becomes measurable.** A **reference revision** is pinned (annex G): Intel SDM **revision 093**
(document 767375, page updated 2026-09-21) **[source]**, the **NASM 3.02** manual **[source]**, MASM 6.11, TASM 4.1,
the reference interrupt list, the Microsoft x64 ABI (revision 2026-05-21) **[source]** and the System V AMD64 psABI
maintained on GitLab (exact version to be pinned, task AA-P0-04). "Exhaustive" means: *every mnemonic of the A–Z
index of SDM 093 has an entry in the corpus with at least a minimal sheet, and the coverage report prints, per
family, how many sheets are complete, minimal, outline and missing*. Optional extensions have a row of their own in
the matrix and a declared treatment level; none is hidden under "etc.".

---

## 3. Gap matrix

The complete table (36 rows: requirement → current situation → change → phase → acceptance → source) is in
[annex B §B.5](annex-b-scope-and-gaps.md#b5-gap-matrix). An extract of the first ten by priority:

| ID | Requirement | Current situation | Change | Phase |
|---|---|---|---|---|
| G-01 | A real mnemonic must never be marked as an error | 110/122 real mnemonics unknown; the only active rule on 32/64 bits marks them `ERROR` (H-01) | Inventory of the mnemonics recognized per dialect + `recognizedBy` in the corpus + severity downgraded to `INFO` while the inventory is incomplete | AA-P1 |
| G-02 | Aliases are declared, not guessed | 52 deduced aliases, one false (`INSTRUCTION`) | An `aliases[]` field with a dialect; delete the deducer | AA-P1 |
| G-03 | Compatibility per form and per mode | one `minCpu` and one flag table per mnemonic | A `Requirement` per form with generation, valid/invalid modes, extension, privilege | AA-P2 |
| G-04 | Nothing invalid in long mode must be offered in a 64-bit project | all 214 entries are offered | `invalidModes` + a context derived from `TargetProfile` | AA-P2 |
| G-05 | Help by position, not by word | `getHover(word, locale)` | `QueryExplain(file, line, column)` + an operand parser | AA-P4 |
| G-06 | Project symbols win over the catalog | the catalog is consulted first | reverse the order and offer the instruction as a secondary card | AA-P1 |
| G-07 | Registers as entities with views | three hand-coded lists | a `Register` entity + `views[]` + `fields[]`, consumed by the lexer, completion and the debugger | AA-P2 |
| G-08 | Syntax and directives per dialect | a mixed flat set | a `SyntaxItem` entity with `dialect` and `version`; a lexer parameterized by dialect | AA-P3 |
| G-09 | Interrupt services with a contract | no entity; `INT` is just another entry | a `Service` entity with function/subfunction, inputs, outputs, errors, availability per backend | AA-P3 |
| G-10 | The corpus must be able to grow to thousands of entries | wall measured around 1490 entries (H-10) | data resources validated at build time, loaded without new dependencies | AA-P1 |

---

## 4. Content taxonomy and learning paths

Detail in [annex C](annex-c-taxonomy-and-paths.md). Decided structure **[proposal]**:

- **A. Instruction reference** — 18 families, and an explicit separation between CPU instruction, prefix,
  assembler alias, pseudo-instruction and directive (today `REP` and `LOCK` are catalog "instructions", just like
  `MOV`).
- **B. Registers and flags** — 6 groups (general and views, pointers/indexes, segment, execution control,
  x87/SIMD, system), with `privilege` and `availability` per entity.
- **C. Segments, memory and addressing** — includes the distinction the assignment requires: segment register ≠
  logical region ≠ section of the object format, and the separation of effective / logical / linear / physical
  address with the real-mode formula and its limits.
- **D. Symbols, operators and syntax** — a glossary **per dialect and context**: `:` has four different entries
  (label definition, `segment:offset`, segment override, register pair as notation), and each declares whether it is
  assemblable syntax or explanatory notation.
- **E. Interrupts, exceptions and services** — four separate kinds (the `INT` instruction, hardware interrupt,
  processor exception, environment service) and a complete vector index with states.
- **F. Labels, procedures and organization** — includes the cycle source → preprocessing → assembly → object →
  link → executable → loading → debugging, with the repository's real formats, and classifies Irvine32/Irvine16 as
  a **third-party library IDEARM does not distribute** **[source: Irvine, preface: the book supplies two versions of
  the link library]**.
- **G. Fundamentals and guided learning** — nine progressive paths with prerequisites, objectives, original
  exercises and explained solutions; Stallings and Mano support the fundamentals, with the warning that Mano's
  four-bit flip-flop register (figure 6-1, printed 218 / PDF 229 **[source]**) is **not** the architectural
  description of AX.

---

## 5. Knowledge schemas and compatibility resolution

Detail and JSON schemas in [annex D §D.1–D.4](annex-d-schemas-architecture-ui.md). Decisions:

**D-AA-02 · Ten typed entities with a stable identifier** **[proposal]**: `Instruction`, `InstructionForm`,
`Operand`, `Register` (with `views` and `fields`), `FlagEffectSpec`, `MemoryConcept`, `SyntaxItem`, `Service`,
`Example`, `Source`. Identifiers follow the scheme `x86.instr.imul`, `x86.instr.imul#form.r16-rm16-imm8`,
`x86.reg.ax`, `x86.flag.cf`, `dos.int21.0ah`, `syntax.masm.op.offset`, `concept.ea`. Identifiers are a contract: a
migration that changes them needs an equivalence table.

**D-AA-03 · Flags per form and per condition** **[proposal]**: `FlagEffectSpec` adds a `condition` and a `note`
field to the current five values, because there are effects that depend on the operand and not on the mnemonic:
"if the count is 0, the flags are not affected"; "OF is defined only for 1-bit shifts; otherwise it is undefined";
"for a non-zero count, AF is undefined" **[source: HTML rendering of the SDM, `sal:sar:shl:shr`, consulted
2026-09-25]**. The current catalog declares `AF = MODIFIED` for `SHL`/`SHR`/`SAR`: a confirmed error (finding A-11
of annex A).

**D-AA-04 · Three-valued compatibility** **[proposal]**: `CompatibilityContext` (isa, generation, mode,
extensions, privilege, dialect+version, OS/ABI, toolchain, backend) → `Availability { AVAILABLE, UNAVAILABLE,
UNKNOWN }` plus a reason code and `confidence { CERTAIN, LIKELY, UNVERIFIED }`. Hard rules:

1. A requirement the context cannot decide returns `UNKNOWN`, **never** `AVAILABLE` or `UNAVAILABLE`.
2. **The analyzer only emits a diagnostic with `UNAVAILABLE` + `CERTAIN`.** `UNKNOWN` and `LIKELY` produce no marks
   in the editor. That way, extending documentation cannot invent errors (an explicit requirement of the
   assignment).
3. The sheet always shows the three values with their reason, so the student sees "not available in the built-in
   emulator" separately from "does not exist in this mode".

**D-AA-05 · Semantics kept apart from prose** **[proposal]**: the semantic fields are neutral enums and structures,
in a single file per entity; the prose lives in `text/<lang>/<entity>.json` indexed by `id` + field name. A semantic
table is never duplicated per language (the assignment forbids it and ADR-006 §8 only requires the content to have
EN and ES). Three things are told apart: the **UI language** (ADR-006), the **content language** and a **missing
translation**, which the sheet points out with a mark of its own instead of showing English as if it were Spanish.

## 6. Architecture, integration per module and migration

Detail in [annex D §D.5–D.8](annex-d-schemas-architecture-ui.md). Decisions:

**D-AA-06 · Where the corpus lives: a new package in `idearm-language`, not a new module** **[proposal]**.
`io.github.dinamo541.idearm.language.knowledge` (model + loader + queries) with the data in
`idearm-language/src/main/resources/io/github/dinamo541/idearm/language/knowledge/`. The `catalog` package is kept
as a compatibility facade delegating to the new one, and is marked `@Deprecated` once every consumer has migrated.

*Discarded alternative:* a separate `idearm-knowledge` module. It gives a cleaner separation and would allow
exporting the corpus without the analyzer, but it costs a `module-info`, `provides/uses`, a new enforcer rule, new
rows in `ArchitectureTest` and one more dependency edge, without solving any current problem: the lexer and the
analyzer **must** read the corpus, so the edge exists anyway. *Trigger to reconsider it:* the corpus resources
exceed 2 MB compressed, or a second consumer appears that needs the corpus without the analyzer (for example a CLI
exporter).

**D-AA-07 · Storage format: JSON in the module's resources, our own reader, validation at build time**
**[proposal]**. Reasons:

- Hand-written Java code no longer scales (H-10, measured).
- ADR-007 and `ArchitectureTest` forbid `java.nio.file.Files` and `ProcessBuilder` in L3; they do **not**
  explicitly forbid `Class.getResourceAsStream` or new third-party dependencies, but L3 "uses only the JDK". Our own
  JSON reader (~200 lines, a strict subset: objects, arrays, strings, numbers, booleans, `null`) respects the rule
  without adding Jackson or SnakeYAML to any layer.
- Schema validation is **not done at run time** with a library: it is done with JUnit tests in the same module,
  which read the same resources and check identifier uniqueness, mandatory fields per type, referential integrity,
  translation parity and the absence of broken relations. An invalid corpus breaks `mvn test`, not the student's
  application.
- The files are split per entity type and per family, so a diff is reviewable and a syntax error stays localized.

*Discarded alternatives:* YAML (needs a library or a much larger parser); Markdown (good for prose, bad for
queryable semantics); generating Java from JSON at build time (back to the bytecode wall and duplicates the truth);
an embedded or remote database (breaks the offline requirement and adds dependencies).

**D-AA-08 · Lazy loading off the JavaFX thread** **[proposal]**. Today `AssemblyLexer.INSTRUCTIONS` is a static
field that forces the catalog to initialize when the class loads **[code]**; with a large corpus that would be a
pause on the first keystroke. The loader is exposed behind a lazy holder and the workbench startup warms it up on a
background thread (the project already has the pattern: `liveLintExecutor()` in `WorkbenchViewModel`). Measurable
target: **≤150 ms** to load the whole corpus and **≤5 ms** to resolve a mnemonic; they are measured with a tagged
performance test, not declared as facts.

**D-AA-09 · A common catalog with views, and a hard boundary towards the analyzer** **[proposal]**. Five views over
the same corpus: `ReferenceView`, `HoverView`, `CompletionView`, `AnalysisView`, `DebugView`. Only `AnalysisView`
feeds the lexer, highlighter and rules, and it only includes entries whose `recognizedBy` names the project's
assembler. A regression test pins the boundary: *adding a purely academic entry does not change the set
`AnalysisView.recognizedMnemonics(dialect)` returns*.

**Incremental migration in four cuts**, each with the suite green and no change in visible behavior except what is
fixed on purpose: (1) extract the 214 entries to JSON with an automatic dump and prove field-by-field equivalence;
(2) enrich the model with forms and requirements, keeping the facade; (3) move the consumers to the views;
(4) retire the facade. Detail and criteria in annex D §D.8.

---

## 7. User flows and sketches

Text sketches of the navigation, the sheet, the hover and the contextual help are in
[annex D §D.9–D.11](annex-d-schemas-architecture-ui.md#d9-academic-center-navigation). Summary:

- The mnemonics dialog becomes the **Academic Center**: a topic tree on the left, cross-cutting search with facets
  at the top, a sheet with a fixed index on the right, context chips that reflect the open project's profile,
  related links, history with back/forward and a button back to the editor.
- Hover shows **summary + what it does + one collapsed detail**, never a chapter; a link opens the full sheet in
  the center. Design limit: at most 12 visible lines.
- A new **contextual help by selection** (`Explain selection`): on `mov ax, [bx+si+4]` it breaks down
  prefix/mnemonic/operands, points out the default segment and the address calculation, and explains why `[bx+bp]`
  or `[ax]` are not valid 16-bit combinations.
- Examples offer to **open or create** a compatible project, declaring the toolchain and backend; consulting a sheet
  never runs code or overwrites the student's files.

---

## 8. Editorial strategy and sources

Detail, page mappings and pending items in [annex G](annex-g-sources.md). The essentials:

**What was really consulted in this session.** With `pdftotext` 4.00 (there is no renderer or OCR on this
machine):

| Source | Lookup performed | Result |
|---|---|---|
| Irvine, 5th ed. (es), 756 pp. | TOC (PDF 9–24) and body: §4.3 operators (PDF 126–130 = printed 94–98), §7.4 MUL/IMUL/DIV/IDIV (PDF 236–242 = printed 204–210), §13.2 INT 21h (PDF 471–477 = printed 439–445) | **Extractable and verified text.** Body offset: PDF = printed + 32 |
| Stallings, 7th ed. (es), 838 pp. | TOC (PDF 7–22) and §11.2 Pentium addressing modes (PDF 437–439 = printed 415–417) | **Verified.** Offset: PDF = printed + 22 |
| Mano, 3rd ed. (es), 535 pp. | Ch. 6, figure 6-1 "Four-bit register" (PDF 229 = printed 218) | **Verified.** The offset varies by section |
| Mano, highlighted copy | Same content, 535 pp.; identical extraction | The highlights **cannot be read** without a renderer. Recorded as academic context, not as an order to exclude topics |
| **Abel, 3rd ed., 283 pp.** | Extracting 20 pages → **20 bytes** of text | **Not readable in this session.** A scanned PDF; without `pdftoppm` or `tesseract` there is no OCR. A real limitation, assigned to task AA-P0-02 |
| Intel SDM | Official page | **Revision 093**, document 767375, updated 2026-09-21 |
| NASM | `nasmdoc2.html`, `nasmdoc3.html` | **NASM 3.02** manual; confirmed the rule `mov ax,foo` (address) versus `mov ax,[foo]` (contents) |
| Microsoft x64 ABI | Microsoft Learn | Revision 2026-05-21; RCX/RDX/R8/R9, XMM0–3, a 32 B shadow space that **the caller reserves**, a stack aligned to 16 B, return in RAX/XMM0 |
| System V AMD64 psABI | Search | Maintained repository located; **exact version not pinned** → task AA-P0-04 |
| `movabs` | GNU as documentation | A **GNU as** mnemonic, neither Intel's nor NASM's. The catalog declares it as an x86-64 instruction (finding A-08) |

**Proposed editorial workflow** (annex G §G.4): source lookup → original writing in English → technical review
against a second independent source → translation into Spanish → validating the example with the real toolchain
when it exists → versioning the corpus with `corpusVersion` and `revisionOf` per entry → coverage report. No page of
the books is copied into the product; redistributing the PDFs is not proposed; before importing any external catalog
there is a license check task (AA-P0-05).

---

## 9. Phased backlog

Detail (48 work packages with ID, goal, scope, affected paths, new files, dependencies, risk, size and acceptance
criterion) in [annex F §F.1–F.8](annex-f-backlog-and-validation.md#f1-phase-aa-p0--research-and-closing-the-unknowns).
Phases:

| Phase | Name | Observable result | Closes |
|---|---|---|---|
| **AA-P0** | Research and closing the unknowns | 6 research notes with a pinned source; no code | the unknowns that block decisions |
| **AA-P1** | Audit and correction of the current catalog + migration to data | The severity A findings fixed; the corpus in validated JSON; **the false errors of H-01 disappear** | G-01, G-02, G-06, G-10 |
| **AA-P2** | Knowledge model and compatibility | Forms, operands, requirements and registers as entities; a 64-bit project stops offering `AAA` | G-03, G-04, G-07 |
| **AA-P3** | First vertical integration | Syntax per dialect + interrupt services + a navigable Academic Center, with **one** family complete end to end (shifts and rotations) | G-08, G-09 |
| **AA-P4** | Contextual help by position | An operand parser and `QueryExplain`; cases 1, 2, 3 and 10 pass | G-05 |
| **AA-P5** | Systematic expansion of the corpus | A corpus closed against the reference revision, with a coverage report per family | the exhaustiveness goal |
| **AA-P6** | Teaching material and paths | Nine paths with exercises and solutions | block G of the taxonomy |
| **AA-P7** | Final validation and documentation | New ADRs, user guide, published coverage report | the definition of "done" |

Academic priority: **8086/DOS first**, because it is the course's profile and the only one with a built-in debugger.
But the 32/64-bit profiles are **not left without a plan**: their most serious defect (H-01) is fixed in AA-P1,
before any content expansion, because today they produce false errors.

---

## 10. Validation, risks and open decisions

Detail in [annex F §F.9–F.12](annex-f-backlog-and-validation.md#f9-validation-plan). Summary:

**Seven test classes**, none of which is satisfied by "the field exists":

1. **Corpus integrity** — unique identifiers, resolvable references and aliases, mandatory fields per type, every
   entry with at least one source, translation parity, no broken relations.
2. **Semantic accuracy with an independent oracle** — the flag tables and the requirements are checked against a
   source other than the corpus itself and other than the emulator. An explicit rule: **the emulator cannot be the
   oracle of the data the emulator uses**.
3. **Positive and negative compatibility** — per mode, extension, dialect, version and backend, with tests that pin
   both "must be available" and "must not be offered", and two non-regression tests dedicated to false analyzer
   errors and false availability.
4. **Assemblable examples** — the `requires-tasm` / `requires-masm` / `requires-nasm` / `requires-dosbox` tags the
   project already uses; static check, assembly, link, execution and debugging are kept apart. When the tool is
   missing the result is **`not run`**, never a simulated success.
5. **Expected states** of registers, memory and *defined* flags, with explicit initial conditions and no
   assertions about undefined flags.
6. **Regression of the catalog, search, hover, completion, lexer/analyzer, i18n and navigation**, plus the ADR-007
   architecture tests and a new one that forbids duplicated keys in the bundles (today
   `dialog.dictionary.description` is defined twice in both languages and the second one wins, so the title of the
   "Operational explanation" section is shown as a welcome text — finding A-19 **[code]**).
7. **Human editorial review** for what cannot be tested on its own: clarity, progression and pedagogical
   correctness, with a checklist per sheet. Long texts are not validated with fragile snapshots.

**Main risks** (mitigation and alternative in annex F §F.11): an editorial volume beyond what one person can do →
sheet levels (complete / minimal / outline) and an honest coverage report; a regression in the editor when the
corpus grows → the `AnalysisView` boundary and its tests; drift of sources and versions → a pinned reference
revision and a `sourceRevision` per entry; the temptation to use the emulator as the oracle → an explicit ban with a
corpus architecture test.

**Open decisions for the user** (none blocks getting started): the language of these documents versus ADR-006
(D-AA-01, since decided: English); the depth ceiling for SIMD/AVX (D-AA-10); whether the emulator should grow to a
complete 80186 and the x87 or be documented with a warning and a pointer to Turbo Debugger (D-AA-11); whether
`CpuLevel` is extended with P6 or replaced entirely (D-AA-12).

---

## 11. Recommended first implementation package and the definition of "done"

### 11.1 Start here: AA-P1-01 … AA-P1-06

The first package is **not** "add instructions". It is to stop giving false information and to put the data where
it can grow. It is the minimum work that already improves the product and enables everything else.

| ID | Goal | Size | Summary acceptance |
|---|---|---|---|
| **AA-P1-01** | Dump the 214 entries to JSON in the module's resources and read them with our own reader, without changing any public API | M | A test compares the loaded corpus field by field with the previous catalog (a dump frozen as a reference file) and there are no differences; `mvn test` green; `ArchitectureTest` still forbids `Files` in L3 |
| **AA-P1-02** | Declare aliases explicitly and delete the alias deducer | S | `find("INSTRUCTION")` is empty; the 51 legitimate aliases still resolve; a test pins the complete list |
| **AA-P1-03** | Fix the six wrong flag tables and the four wrongly declared generations, each with its citation | S | One test per fix, with the source in the comment; `BT` stops contradicting its own prose |
| **AA-P1-04** | Separate "recognized by the assembler" from "documented" and downgrade the unknown-mnemonic severity while the inventory is incomplete | M | In a 64-bit NASM project, `movaps`, `paddb`, `fsin`, `lgdt` and `movsq` produce no diagnostic; `muv ax,1` still produces an `ERROR` with the suggestion `MOV` |
| **AA-P1-05** | Reverse the resolution order of hover and add the secondary card "there is also an instruction called X" | S | A project macro called `MOV` shows the macro first; acceptance case 10 passes |
| **AA-P1-06** | A test for duplicated keys in the bundles and fixing `dialog.dictionary.description` | S | The test fails before the fix; the section title is "Operational explanation" again |

Prerequisite: **AA-P0-01** (pin the reference revision and write the knowledge model ADR), which is documentary and
touches no code.

### 11.2 A verifiable definition of "done" for the whole goal

The goal of the assignment is met when **all** of the following can be checked, not before:

1. **A measured corpus, not an estimated one.** The coverage report (`mvn -Pcoverage-report` or the equivalent
   command AA-P5-01 settles on) prints, per family and per entity type, how many entries are at the complete,
   minimal and outline level, and how many are missing against the A–Z index of SDM 093; no family appears as
   "etc.".
2. **Zero real mnemonics marked as errors.** The false-error non-regression test covers the complete list of
   mnemonics recognized by TASM 4.1, MASM 6.11 and NASM 3.02, and passes for the four profiles.
3. **Zero false availability.** For each profile, the set offered by `CompletionView` and the set validated by
   `AnalysisView` exclude what is invalid in that mode, and a test pins it instruction by instruction for the list
   of entries invalid in long mode that AA-P0-03 closes (at least the 15 of A-16).
4. **The twelve acceptance cases of the assignment** have an automated test or a signed usability test script, and
   annex F §F.10 records which of the two and its result.
5. **Every semantic claim has a source.** No corpus entry passes the integrity validation without at least one
   reference with work, edition, section and page, or a URL with a revision.
6. **No long text is validated with a snapshot.** The editorial checklist is signed per complete-level sheet.
7. **The states of the matrix are the measured ones.** The matrix of annex B is regenerated from the tests and the
   registry of detected tools, and the remaining `unknown` cells are assigned to a task.
8. **Performance and accessibility measured**, not promised: corpus load, search latency, hover latency, a full
   keyboard walkthrough, contrast in the light and dark themes, and ES/EN key parity.
9. **The editor's correctness is protected.** The boundary test shows that a new academic entry does not change the
   set of mnemonics the analyzer accepts.
10. **The project documentation reflects the result:** a knowledge model ADR, a compatibility resolution ADR, a new
    section in `docs/user-guide.md`, new rows in `docs/troubleshooting.md` and the status in PLAN.md §7.

---

## 12. Assumptions and real limits of this research

What could **not** be checked in this session, with the task that closes it:

| Limit | Effect | Task |
|---|---|---|
| **Abel is not readable**: a scanned PDF, and the machine has neither `pdftoppm`, ghostscript nor `tesseract` | The plan cites Abel in no technical claim; his planned contributions (historical environment, real-mode segmentation, keyboard services) are assigned, not taken for granted | AA-P0-02 |
| The highlights of Mano's PDF cannot be read without a renderer | Only the academic context note of the assignment is kept; it is **not** read as an order to exclude topics | AA-P0-02 |
| The Intel SDM was not downloaded (only revision 093 was pinned from the official page) | This plan's flag corrections rest on an unofficial HTML rendering of the SDM; each must be confirmed against the PDF of volume 2 before being fixed in the corpus | AA-P0-03 |
| Exact version of the System V AMD64 psABI not pinned | The Linux ABI content stays planned but without a reference revision | AA-P0-04 |
| Neither `mvn test` was run nor anything built | Every number of this plan comes from reading the code and running the separately compiled `catalog` package; there is **no** claim about the state of the suite | — |
| Three NASM versions coexist in the repository and the documentation (2.16.01 in the Ubuntu CI, 3.01 in the `NasmAssemblerAdapter` comment, 3.02 in the published manual) | The corpus's "dialect + version" axis is mandatory, not optional; the plan does not assume a single version | AA-P0-01 |
| Whether TASM 4.1 and MASM 6.11 accept the x87 without `.8087`/`.387` was not checked | The x87 row of the matrix has `assemblable = unknown` | AA-P0-06 |

**What this plan did not do, by assignment:** nothing was implemented, no production code was refactored, no
dependencies were changed, nothing was committed to git and no existing user file was touched. The temporary
artifacts (compiled classes, PDF extractions, inventory output) stayed in the session's temporary folder, outside the
repository.
