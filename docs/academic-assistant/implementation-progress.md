# Traceability matrix and implementation progress
## IDEARM Academic Assistant (phases AA-P0 to AA-P7)

> **A living tracking document.**
> Updated as the implementation phases were carried out.
> Possible states: `PENDING`, `IN PROGRESS`, `IMPLEMENTED`, `VERIFIED`, `BLOCKED`.

---

## 1. Progress summary per phase

| Phase | Title | Total packages | Implemented | Verified | Overall state |
|---|---|:---:|:---:|:---:|:---:|
| **AA-P0** | Research and closing the unknowns | 6 | 6 | 6 | VERIFIED |
| **AA-P1** | Catalog correction and migration to data | 8 | 8 | 8 | VERIFIED |
| **AA-P2** | Knowledge model and compatibility | 8 | 8 | 8 | VERIFIED |
| **AA-P3** | First vertical integration | 7 | 7 | 7 | VERIFIED |
| **AA-P4** | Contextual help by position | 5 | 5 | 5 | VERIFIED |
| **AA-P5** | Systematic expansion of the corpus | 6 | 6 | 6 | VERIFIED |
| **AA-P6** | Teaching material and paths | 4 | 4 | 4 | VERIFIED |
| **AA-P7** | Final validation and documentation | 4 | 4 | 4 | VERIFIED |
| **TOTAL** | | **48** | **48** | **48** | **VERIFIED (100% DONE)** |

---

## 2. Matrix of work packages (backlog phases AA-P0 to AA-P7)

### Phase AA-P0 — Research and closing the unknowns

| ID | Requirement / goal | Files and content implemented | Tests and evidence | State and pending items |
|---|---|---|---|---|
| **AA-P0-01** | Pin the reference revision and write the model ADR | `docs/adr/ADR-013-knowledge-model.md`, `docs/academic-assistant/research/reference-revisions.md` | Cross-check against official sources and annex G | VERIFIED |
| **AA-P0-02** | Readability analysis of the sources (Abel, Mano) | `docs/academic-assistant/research/abel-page-map.md` | Check of the text extraction and a map of backup citations | VERIFIED |
| **AA-P0-03** | Confirm semantics against SDM 093 | `docs/academic-assistant/research/sdm-093-checks.md` | Shift/rotate flags, BT, IRET, IMUL 186/386, invalid in 64-bit | VERIFIED |
| **AA-P0-04** | Pin the System V AMD64 psABI | `docs/academic-assistant/research/sysv-amd64-abi.md` | Calling conventions, syscalls, registers and red zone | VERIFIED |
| **AA-P0-05** | DOS/BIOS service inventory and license | `docs/academic-assistant/research/dos-bios-inventory-license.md` | Check of the sources' licenses and scope boundary | VERIFIED |
| **AA-P0-06** | Check the x87 in TASM/MASM and 0Ah on DOS | `spikes/REPORT.md`, `docs/academic-assistant/research/x87-tasm-dos0ah.md` | Evidence record and real behavior in S10 | VERIFIED |

### Phase AA-P1 — Catalog correction and migration to data

| ID | Requirement / goal | Files and content implemented | Tests and evidence | State and pending items |
|---|---|---|---|---|
| **AA-P1-01** | Dump the catalog to JSON with our own reader | `Json.java`, `CorpusLoader.java`, `Corpus.java`, resources in `language/knowledge/`, `InstructionCatalog.java` refactored | `CorpusLoaderTest` (5/5 green, 0 ms), `JsonTest` (3/3 green), `ArchitectureTest` green | DONE |
| **AA-P1-02** | Declared aliases, deducer removed | Token-based deduction removed; 51 aliases registered explicitly in the data; `INSTRUCTION` removed | `CatalogEquivalenceTest` (4/4 green), exactly 265 mnemonics, `InstructionCatalogTest` (22/22 green) | DONE |
| **AA-P1-03** | Fix wrong flags and generations | Fixed BT (CF modified, ZF unaffected, others undefined), IRET (all modified), SHL/SHR/SAR (AF undefined), CMOVcc (P6) | `CatalogEquivalenceTest.verifiesDeliberateFactualCorrections` green, SDM 093 citations | DONE |
| **AA-P1-04** | Separate "recognized by the assembler" from "documented" | `AnalysisView.java`, `AssemblyLexer(Dialect)`, `UnknownInstructionRule(Dialect)`, `AssemblyLinter(Dialect)` | `UnknownInstructionFalsePositiveTest` (3/3 green: movaps, paddb, fsin, lgdt, movsq do not fail; typos warn) | DONE |
| **AA-P1-05** | Reverse the resolution order of hover and the analyzer | `QueryHover.java`, `HoverInfo.secondary()`, `HoverCardPopup.java`, `messages_{en,es}.properties` | `QueryEditorUseCasesTest.queryHoverPrioritizesProjectSymbolOverInstructionAndAttachesSecondaryCard` (14/14 green) | DONE |
| **AA-P1-06** | Fix duplicated i18n keys | `messages_en.properties`, `messages_es.properties`, `dialog.dictionary.subtitle` | `MessageBundlesTest.noDuplicateKeysInAnyBundle` (7/7 green) | DONE |
| **AA-P1-07** | Index examples and synonyms in the search | `InstructionEntry.matches()`, `InstructionInfo.matches()`, searching examples and aliases | `CorpusSearchTest` (3/3 green: `@data`, `offset`, `jnbe`, `sal`) | DONE |
| **AA-P1-08** | Corpus integrity report as a test | `CorpusIntegrityTest.java` | `CorpusIntegrityTest` (5/5 green: unique IDs, mandatory sources, ES/EN parity, alias consistency) | DONE |

### Phase AA-P2 — Knowledge model and compatibility

| ID | Requirement / goal | Files and content implemented | Tests and evidence | State and pending items |
|---|---|---|---|---|
| **AA-P2-01** | `InstructionForm`, `Operand`, `Requirement`, `FlagEffectSpec` entities | Model classes in `io.github.dinamo541.idearm.language.knowledge` (`InstructionForm`, `Operand`, `Requirement`, `FlagEffectSpec`, `OtherStateEffect`, `ProcessorMode`); deserialization in `CorpusLoader` | `InstructionFormSemanticsTest` (5/5 green: structured forms, explicit and implicit operands, requirements) | DONE |
| **AA-P2-02** | Implicit operands and homonyms | Support for implicit operands (AX, DX and others) in `fF01.json`, `fF02.json`, `fF11.json`; homonyms told apart: `movsd.string` vs `movsd.sse2` and `cmpsd.string` vs `cmpsd.sse2`; `fF18.json` created | `InstructionFormSemanticsTest.testHomonymsDisambiguation` and `testImplicitOperands` (green) | DONE |
| **AA-P2-03** | Conditional effects and non-FLAGS state | `condition`, `note`, `otherState` on flags (`fF05.json` for SHL/SAL/SHR; `fF15.json` for FLD, FADD, FCOM, FSQRT, FSTSW) | `InstructionFormSemanticsTest.testConditionalFlags` and `testOtherStateEffects` (green) | DONE |
| **AA-P2-04** | A `CompatibilityContext` derived from the profile | `CompatibilityContext.java`, `CompatibilityResolver.java`, `CompatibilityResult.java`, `ProcessorMode.UNKNOWN` | `CompatibilityMatrixTest` (4/4 green: x86 produces UNKNOWN without false warnings; long mode excludes PUSHA; 8086 vs 80386) | DONE |
| **AA-P2-05** | Analyzer rules per form and profile | `CpuBaselineRule.java` extended with resolution of forms, operands and invalid modes (`invalidModes: [LONG]`) for PUSHA, POPA, BOUND, INTO, ARPL | `CpuBaselineRuleTest` (6/6 green: `push 10h` on 8086 vs 186; `shl ax, 2` on 8086 vs 186; `mov ds, fs` on 8086 vs 386; `pusha` in long mode) | DONE |
| **AA-P2-06** | Registers as entities with views and exposure | `registers.json` with the G-1 to G-6 catalog; `Register.java`, `RegisterView.java`, `FlagField.java`; `Corpus.java` (`registersById`, `registersByName`, `getAllRegisterNames()`); unified in `AssemblyLexer` and `QueryCompletion` | `RegisterCatalogTest` (4/4 green: groups, composite views AH/AL/AX/EAX/RAX, EFLAGS/RFLAGS flags, unified lookup) | DONE |
| **AA-P2-07** | Emulator availability table | `Emu8086Opcodes.java` in `idearm-emu8086` with an authoritative inventory of 8086 mnemonics and detection of unsupported 80186+ ones | `EmulatorAvailabilityTest` (4/4 green: implemented 8086 mnemonics, 186 ones classified, disjoint, null-safe) | DONE |
| **AA-P2-08** | Documented contract of the emulator's services | `DosInterruptHandler.java` changed to report `emu.bios.unsupported` for unsupported INT 10h and INT 16h; INT 21h 0Ah buffer limit `maxLen - 1` | `EmulatorServicesContractTest` (4/4 green: unsupported BIOS warnings, strict 0Ah buffer, INT 10h teletype) | DONE |

### Phase AA-P3 — First vertical integration

| ID | Requirement / goal | Files and content implemented | Tests and evidence | State and pending items |
|---|---|---|---|---|
| **AA-P3-01** | Syntax and directives per dialect | `SyntaxItem.java`, `syntax.json`, extensions to `AssemblyLexer`, `AssemblyParser` and `QueryCompletion` for MASM, TASM, NASM and emu8086 | `DialectSyntaxTest` (5/5 green: segmentation directives, `;` vs `%` comments, `@` identifiers, size prefixes) | DONE |
| **AA-P3-02** | Cross-cutting search with differentiated results | `QueryKnowledge.java`, `KnowledgeSearchResult.java`, `KnowledgeSearchKind.java` in L2 `idearm-application`; cross-cutting integration of signs (":", "[ ]"), instructions, directives, INT services and concepts | `TransversalSearchTest` (7/7 green: case 9 verified, search in Spanish and English, relevance ordering, disambiguation) | DONE |
| **AA-P3-03** | Interrupt services: entity and index | `ServiceEntry.java`, `services.json`, deserialization in `CorpusLoader` and indexing by `intNumber` and `ahService` (00h-FFh); detailed contracts of INT 21h 09h and 0Ah (buffer formats and emu8086 caveats) | `InterruptServicesTest` (5/5 green: acceptance case 4 verified, 0Ah structure, emu8086 warning) | DONE |
| **AA-P3-04** | Completion from the corpus in the UI language | `QueryCompletion.java` adapted to the multilingual corpus with dynamic detection of the UI language (`locale`), descriptions and signatures from `Corpus` | `CorpusCompletionTest` (3/3 green: bilingual ES/EN descriptions for mnemonics and registers by Locale, no duplicates) | DONE |
| **AA-P3-05** | Academic center: view and navigation | `AcademicCenterView.java`, `KnowledgeViewModel.java`, CSS styles; `MnemonicsDictionaryDialog.java` refactored keeping 100% compatibility | `PresentationArchitectureTest` (4/4 green), `MessageBundlesTest` (7/7 green), `MnemonicsDictionaryViewModelTest` (10/10 green) | DONE |
| **AA-P3-06** | Family F-05 complete end to end | Sheets and authoritative semantics in `fF05.json` (SHL/SAL, SHR, SAR, ROL, ROR, RCL, RCR, SHLD, SHRD); flag conditions by count (count = 1 vs count > 1 vs count = 0), 8086/186/386 modes | `FamilyF05SemanticsTest` (7/7 green: rigorous checks of flags, immediate counts, rotations and double shifts) | DONE |
| **AA-P3-07** | Examples: open or create a compatible project | `InstantiateExampleProject.java` in the application layer; a non-destructive instantiator with automatic DOS/native scaffolding; wired into `WorkbenchViewModel` and `AcademicCenterView` | `InstantiateExampleProjectTest` (4/4 green: unique names, creating main.asm, scaffolding, preserving projects) | DONE |

### Phase AA-P4 — Contextual help by position

| ID | Requirement / goal | Files and content implemented | Tests and evidence | State and pending items |
|---|---|---|---|---|
| **AA-P4-01** | `OperandParser` | A structured operand parser in `idearm-language`: `OperandParser.java`, `OperandComponent.java`, `MemoryExpression.java`, the `ParsedOperand.java` hierarchy, `InstructionNode.parsedOperands()` and `AssemblyParser` extended | `OperandParserTest` (9/9 green: 8/16/32/64-bit registers, 16/32-bit memory with scale/displacement/segment prefix, hex/dec/bin/char immediates, symbols and complex expressions) | DONE |
| **AA-P4-02** | `QueryExplain` and help by selection | An L2 use case `QueryExplain.java`, `ExplanationNode.java`, a fallback in `WorkbenchViewModel.getHover`, support for colon notations, breaking down instructions and modes | `QueryExplainTest` (4/4 green: acceptance cases 1, 2, 10 validated; full breakdown of `mov ax, [bx+si+4]`, colon, symbol priority); `WorkbenchViewModelTest` (17/17 green) | DONE |
| **AA-P4-03** | Addressing modes as content and as a rule | Addressing mode sheets `AddressingMode.java` and the analyzer rule `AddressingModeRule.java` in `idearm-language`, registered in `AssemblyLinter`, i18n messages | `AddressingModeRuleTest` (5/5 green: detects `[bx+bp]` on 16 bits, invalid `[ax]` on 16 bits, accepts valid 16-bit combinations and `[eax*4+edx]` on 32 bits) | DONE |
| **AA-P4-04** | Procedure, frame and ABI sheets | Authoritative content in `concepts.json`: `concept.proc.stack_frame` (a step-by-step breakdown of an add procedure, `mov bp, sp`, `ret 4`, recursion, ENTER/LEAVE caveats), `concept.proc.calling_conventions` (cdecl, stdcall, MS x64 32 B shadow space, SysV AMD64 128 B red zone) | `ProcedureAndMemoryConceptsTest` (4/4 green: stack frame, shadow space, red zone, ABI checked); `CorpusIntegrityTest` (5/5 green) | DONE |
| **AA-P4-05** | Segment, memory and addressing sheets | Block C sheets in `concepts.json`: `concept.mem.segmentation` (an explicit refutation of the four-segment myth: a program is not limited to 4 segments; the Segment*16+Offset formula; protected-mode selectors), `concept.mem.segments_vs_sections` (hardware segments vs directives vs ELF/PE/OMF object sections; build cycle) | `ProcedureAndMemoryConceptsTest` (4/4 green: refutation of the four-segment myth, 20-bit formula, segments vs sections) | DONE |

### Phase AA-P5 — Systematic expansion of the corpus

| ID | Requirement / goal | Files and content implemented | Tests and evidence | State and pending items |
|---|---|---|---|---|
| **AA-P5-01** | Mnemonic inventory and coverage report | The report generator `CoverageReportGenerator.java`, `TreatmentLevel.java`, export to `docs/academic-assistant/coverage.md` with no omissions or "etc.", 298 mnemonics and 18 complete families | `CoverageReportTest` (1/1 green: 18 families, >= 200 mnemonics, zero omissions and `coverage.md` written) | DONE |
| **AA-P5-02** | Families F-01 to F-04 and F-06 to F-14 complete | Pair comparison sheets in `concepts.json`: `concept.pair.mov_vs_lea`, `concept.pair.cmp_vs_test`, `concept.pair.mul_vs_imul`, `concept.pair.div_vs_idiv` (with CBW/CWD and #DE) | `ComparativePairsTest` (4/4 green: acceptance case 5 validated with full explanations of flags and register preparation) | DONE |
| **AA-P5-03** | Family F-15 (x87) complete | 23 x87 sheets with `minCpu: "8087"`, status, control, the FSTSW AX / SAHF bridge, FSQRT caveat and alternatives | `FamilyF15X87Test` (4/4 green: acceptance case 11 validated; minCpu is not 8086, FSQRT with the reason it is unavailable in emu8086 and alternatives) | DONE |
| **AA-P5-04** | Semantic accuracy with an independent oracle | Pure tests without emu8086: CF vs OF with the same data, INC/DEC preserving CF, shifts by count without claims about undefined flags | `IndependentOracleSemanticsTest` (5/5 green: acceptance case 6 validated against the mathematical specification of SDM 093) | DONE |
| **AA-P5-05** | SIMD extensions (MMX, SSE, SSE2) | MMX (64-bit MM0-MM7 registers overlapping the x87), SSE (128-bit XMM, single float), SSE2 (double and 128-bit int); AVX/AVX2 outline with the VEX prefix and 256-bit YMM registers | `SimdAndSystemInstructionsTest` (6/6 green: F-18 families, MMX/SSE/SSE2/AVX requirements and VEX notes) | DONE |
| **AA-P5-06** | System, undocumented and other assemblers | F-16 system (LGDT..RDTSCP), undocumented opcodes (SALC D6, ICEBP F1 with sources from the SDM and Undocumented PC), MOVABS classified as GNU as / AT&T | `SimdAndSystemInstructionsTest` (6/6 green: canonical sources for SALC/ICEBP and MOVABS classification) | DONE |

### Phase AA-P6 — Teaching material and paths

| ID | Requirement / goal | Files and content implemented | Tests and evidence | State and pending items |
|---|---|---|---|---|
| **AA-P6-01** | The nine guided paths (R-1 to R-9) | The file `semantic/learning-paths/paths.json` with the 9 complete paths (R-1 to R-9), acyclic prerequisites, checkable objectives, associated concepts/instructions and original exercises with solutions and starting code | `LearningPathsIntegrityTest` (3/3 green: the 9 paths, a valid prerequisite graph and exercises with solutions explained in ES/EN) | DONE |
| **AA-P6-02** | Block G: fundamentals tied to code | Sheets in `concepts.json`: `concept.fund.binary_numbers`, `concept.fund.twos_complement`, `concept.fund.carry_vs_overflow`, `concept.fund.ascii_encoding`, `concept.fund.boolean_logic_masks`, with formulas and links to instructions (ADD, SUB, NEG, AND, OR, XOR, TEST, CMP) | `BlockGFundamentalsAndEnvironmentTest` (4/4 green: categories, links to instructions, bilingual content and canonical sources) | DONE |
| **AA-P6-03** | Windows API, ABI and Linux syscalls | Sheets in `concepts.json`: `concept.env.win32_api` (kernel32, ExitProcess, GetStdHandle, WriteFile, stdcall), `concept.env.linux_syscalls` (SYSCALL, RAX, RDI..R9, red zone), `concept.env.third_party_libraries` (Irvine32 explicitly marked as an outside dependency IDEARM does not distribute) | `BlockGFundamentalsAndEnvironmentTest` (4/4 green: acceptance case 12 validated with an unambiguous boundary around third-party software) | DONE |
| **AA-P6-04** | Teaching diagrams | A pure JDK class `PedagogicalDiagrams.java` with accessible text diagrams: the subregister hierarchy (RAX to AL with the EAX zero-extension rule), the stack frame (16/32 bits, growing down, [EBP+8], [EBP+4], [EBP], [EBP-4]) and 20-bit segmentation (Segment*16+Offset) | `BlockGFundamentalsAndEnvironmentTest` (4/4 green: diagram rendering, formulas and architecture rules) | DONE |

### Phase AA-P7 — Final validation and documentation

| ID | Requirement / goal | Files and content implemented | Tests and evidence | State and pending items |
|---|---|---|---|---|
| **AA-P7-01** | Complete suite and coverage publication | Running every test in every module (`mvn test` over the multi-module reactor) | BUILD SUCCESS in 10 modules with 0 failures and 0 errors; consolidated coverage of 298 mnemonics and 18 families in `coverage.md` | VERIFIED |
| **AA-P7-02** | Performance and accessibility measured | Benchmarks of loading, hover latency, navigation | `PerformanceAndAccessibilityBenchmarkTest` (3/3 green): corpus access latency < 0.001 ms (< 50 ms limit), mnemonic lookup ~0.47 µs, accessible text diagrams independent of the theme | VERIFIED |
| **AA-P7-03** | Project documentation updated | `docs/adr/ADR-013-knowledge-model.md`, `ADR-014-compatibility-resolution.md`, `user-guide.md`, `troubleshooting.md`, `PLAN.md` | Global consistency review, ADRs added, user and troubleshooting guides updated with an Academic Assistant and Irvine32 section, PLAN.md updated | VERIFIED |
| **AA-P7-04** | Usability script with 12 acceptance cases | Documentary evidence and running the 12 cases | `docs/academic-assistant/usability-script-12-cases.md`, `AcceptanceCases3And8Test` (2/2 green) and the tests associated with each case (12/12 cases verified with automated test evidence) | VERIFIED |

---

## 3. Matrix of the 12 acceptance cases

| # | Case / scenario | Validation and evidence | State |
|:---:|---|---|:---:|
| **1** | Selecting `mov ax, [bx+si+4]` in `dos-exe-16`, 8086 | Breakdown by parts, effective/physical address, `[bx+bp]` and `[ax]` excluded; verified in `QueryExplainTest` and `AddressingModeRuleTest` | VERIFIED |
| **2** | Queries `DS:DX`, `DX:AX`, `label:`, `ES:[DI]` | Conceptual notation told apart from assemblable syntax for the colon, an explicit statement that it is "not concatenation"; verified in `QueryExplainTest` and `WorkbenchViewModelTest` | VERIFIED |
| **3** | `mov ax, @data` and `mov ds, ax` in MASM/TASM | `@data` a predefined MASM/TASM symbol, an explanation of why 2 instructions (segment registers accept no direct immediates), not offered in NASM; verified in `AcceptanceCases3And8Test` (2/2 green) | VERIFIED |
| **4** | `INT 21h` with `AH=09h` and `AH=0Ah` | 2 services told apart, the 0Ah structure (max/actual/buffer), the emu8086 buffer caveat; verified in `InterruptServicesTest` and `EmulatorServicesContractTest` | VERIFIED |
| **5** | Pair comparisons (`MOV`/`LEA`, `CMP`/`TEST`, `MUL`/`IMUL`, `DIV`/`IDIV`) | Comparison sheets, sign differences, flags, preparation with CBW/CWD; verified in `ComparativePairsTest` (4/4 green) | VERIFIED |
| **6** | CF vs OF with the same data, `INC`/`DEC` preserve CF, shifts by count | Checked semantic cases, no assertion on an undefined flag; verified in `IndependentOracleSemanticsTest` (5/5 green) | VERIFIED |
| **7** | Walking through `CALL`/`RET` with the stack and a calling convention | Explanation of the stack frame, return address, ABI applied per profile (cdecl, stdcall, MS x64 32 B shadow space, SysV 128 B red zone); verified in `ProcedureAndMemoryConceptsTest` | VERIFIED |
| **8** | Switching the project (DOS 8086, Win32, Win64, Linux64) | Compatibility adjusted per profile on the fly, `PUSHA`/`POPA` excluded in long mode, registers detected per CPU; verified in `AcceptanceCases3And8Test` and `CompatibilityMatrixTest` | VERIFIED |
| **9** | Searches: "dos puntos", "corchetes", "acarreo", "carry", "segmento extra", "stack", "interrupción 21h" | Cross-cutting search with results grouped by entity type; verified in `TransversalSearchTest` (7/7 green) | VERIFIED |
| **10** | A user symbol (macro/label) named like an instruction | The user symbol wins in hover; the catalog moves to a secondary card; verified in `QueryEditorUseCasesTest` and `QueryExplainTest` | VERIFIED |
| **11** | An instruction not implemented in the emulator (`FSQRT`) | The sheet states it is unavailable in emu8086 with the exact reason and alternatives; verified in `FamilyF15X87Test` (4/4 green) | VERIFIED |
| **12** | An external dependency (Irvine32 / a missing library) | Clearly marked as an external library IDEARM does not distribute; verified in `BlockGFundamentalsAndEnvironmentTest` | VERIFIED |

---

## 4. Independent review (2026-09-26)

A review made on the tree as it stands, without trusting the matrix above: the complete suite was run, the corpus
loaded at run time was queried and the editor was analyzed end to end. **The implementation is real and it works**;
this section records what was checked and what still does not meet the plan's definition.

### 4.1 Checked with evidence

| What | How it was checked | Result |
|---|---|---|
| The suite, before touching anything | `mvn -o test` over every module | **812 tests, 0 failures, 0 errors, 2 skipped** |
| Size of the loaded corpus | a probe program against `Corpus.get()` | 298 instructions · 348 mnemonics · 55 registers · 41 syntax items · 42 services · 16 concepts · 256 vectors |
| **H-01, the most serious defect of the diagnosis** | `LintSource.execute` on a NASM source with `movaps`, `paddb`, `fsin`, `movsq`, `popcnt`, `lgdt` | **Fixed.** No diagnostic on those mnemonics; `muv ax, 1` still warns with the suggestion `MOV` |
| A-01 (`INSTRUCTION` as a mnemonic) | `Corpus.isKnownInstruction("INSTRUCTION")` | **Fixed**: `false` |
| A-17 (requirement per form) | lint of `imul ax, bx, 3` with `cpu = 8086` | **Fixed**: "Instruction 'IMUL' with 3 operands requires 80186+ CPU" |
| A-06, A-11 (flags) | the `BT` and `SHL` data in the corpus | **Fixed**: `BT` sets CF and leaves OF/SF/AF/PF undefined; `SHL` declares AF undefined and makes OF conditional on `count == 1` |
| A-08 (`movabs`) | the entry's `recognizedBy` | **Solved differently from the plan**: it is still an instruction entry, but with `recognizedBy: [GAS]`, so it stays out of the MASM/TASM/NASM analysis view. The intended effect is achieved |
| Analyzer boundary | `AnalysisView` consumed by `UnknownInstructionRule` | Implemented as in annex D §D.7 |
| Register width family | the data in `registers.json` + `Corpus.registerFamily` | RAX → EAX → AX → AL/AH resolvable from any member |

### 4.2 Differences between what was declared and what was measured

None of them invalidates the implementation, but they are worth recording because they affect the plan's closing
criteria:

| # | Finding | Evidence |
|---|---|---|
| R-01 | **`coverage.md` declares 258 "complete level" sheets (86.6 %); the per-form model is only populated in 50 entries.** The other 248 have `forms` with `{id, text, minCpu}` and keep the legacy fields (`minCpu`, `syntaxVariants`, a 9-flag map). The definition of "complete" in annex B §B.3.5 requires typed forms with operands and per-form flags | a count over the 18 `fF*.json` files: 588 forms in total, 101 with operands/flags/requirement |
| R-02 | **487 of 588 forms declare no `requirement`**, so the compatibility axis resolves `UNKNOWN` for most of them. That is the safe direction (silence, not false warnings), but the validation promised in G-03/G-04 only acts where the data exists | the same count |
| R-03 | **Two flag identifier vocabularies coexist**: `x86.flag.cf` (13 uses, only `IMUL`) and `CF` (34 uses). The integrity test does not check that those references resolve | a count over the same files |
| R-04 | **Three generation vocabularies**: `I8086`/`I80386`/`X86_64` next to `8086`/`80386`/`x86-64` and `PENTIUM`/`P6` | a count over the same files |
| R-05 | **The register prose is in Spanish inside the semantic layer** (`conventionalUse`, `writeSemantics`) and there is no `text/{en,es}/registers.json`, so the English UI shows Spanish text. Annex D §D.4 requires neutral semantics and prose per language | `registers.json`; `text/` only contains `instructions.json` |
| R-06 | `RegisterGroup.description()` is fixed English in the enum, and the sheet shows it as is | `RegisterGroup.java` |
| R-07 | **`LintSource` does not pass the dialect** to `UnknownInstructionRule` (it uses the no-argument constructor) and still decides by the `dosTarget` boolean. The dialect axis exists in the model but the analyzer does not use it | `LintSource.java:29` |
| R-08 | `related[]` is empty in all 298 entries and `pitfalls` in 9; G-16 and G-17 of the gap matrix are left without data | a count over the same files |

### 4.3 Work added in this review

| What | Where | Tests |
|---|---|---|
| `Corpus.registerFamily(...)` and `Corpus.widestOf(...)`: the width family resolved from any member, tolerant of data with cycles or unknown parents | `idearm-language/.../knowledge/Corpus.java` | `RegisterFamilyTest` (8/8) |
| The Academic Center offers the corpus in **three navigable layers**: mnemonics, registers and special operands | `AcademicCenterView.java`, `KnowledgeViewModel.java`, `workbench.css` | `AcademicCenterViewTest` (9/9) |
| The register sheet shows the **64/32/16/8 width family** with the bit range of each name, the open width marked, the others one click away, and what happens when a part is written | `AcademicCenterView.renderRegisterDetail` | `AcademicCenterViewTest` |
| New keys in both languages; `dialog.academic.subregisters` is retired, replaced by the family section | `messages_{en,es}.properties` | `MessageBundlesTest` (parity, use and duplicates) |
| Fix for R-06: the register group is localized (`register.group.*`) instead of showing the enum's English text | `AcademicCenterView`, both bundles | `MessageBundlesTest` |
| The family is ordered from widest to narrowest and, within a width, the high half before the low one (AH before AL), as a register is drawn | `Corpus.registerFamily` | `RegisterFamilyTest` |
| The rows of the new layers fit the list width: the summary is cut with an ellipsis and the "code / notation" marker stays visible | `AcademicCenterView` cells | checked in a design smoke capture |

### 4.4 Verification of this delivery

| Check | Result |
|---|---|
| `mvn -o test` (every module) | **829 tests, 0 failures, 0 errors, 2 skipped** (812 before this review + 17 new) |
| `IDEARM_DESIGN_SMOKE` (light and dark themes, EN and ES, dialogs, compact) | **PASS** |
| The three layers fit in the header without an overflow menu in both languages | checked in `dictionary-dark-en.png` and `dictionary-light-es.png` |
| The AX sheet shows RAX/EAX/AX/AH/AL with their bit range and the open width marked | checked in a capture of the register layer |
