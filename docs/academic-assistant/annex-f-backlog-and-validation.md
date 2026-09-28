# Annex F — Phased backlog, validation plan, risks and decisions

> Part of [`expansion-plan.md`](expansion-plan.md). Covers phase 4 of the assignment and deliverables 9 and 10.
>
> **Sizes:** `S` ≤ half a day · `M` 1–2 days · `L` 3 days or more, on the same scale as
> [`docs/action-plan.md`](../action-plan.md). No dates are given: there is no basis for estimating them.
> **General definition of done:** the one in `docs/action-plan.md` ("Definition of done") applies, that is a
> regression test that fails before and passes after, `mvn test` green, keys in both languages, a row in
> `docs/troubleshooting.md` for each new diagnostic code, and an entry in PLAN.md §7.

Index: [F.1 AA-P0](#f1-phase-aa-p0--research-and-closing-the-unknowns) ·
[F.2 AA-P1](#f2-phase-aa-p1--catalog-correction-and-migration-to-data) ·
[F.3 AA-P2](#f3-phase-aa-p2--knowledge-model-and-compatibility) ·
[F.4 AA-P3](#f4-phase-aa-p3--first-vertical-integration) ·
[F.5 AA-P4](#f5-phase-aa-p4--contextual-help-by-position) ·
[F.6 AA-P5](#f6-phase-aa-p5--systematic-expansion-of-the-corpus) ·
[F.7 AA-P6](#f7-phase-aa-p6--teaching-material-and-paths) ·
[F.8 AA-P7](#f8-phase-aa-p7--final-validation-and-documentation) ·
[F.9 Validation](#f9-validation-plan) · [F.10 Acceptance cases](#f10-the-twelve-acceptance-cases) ·
[F.11 Risks](#f11-risks-mitigations-and-discarded-alternatives) ·
[F.12 Decisions](#f12-open-decisions)

---

## F.1 Phase AA-P0 — Research and closing the unknowns

No task in this phase writes code. Each one produces a research note in `docs/academic-assistant/research/` with
the source, the date and the answer. **Observable result of the phase:** six unknowns of the plan go from
`[assumption]` to `[source]`.

#### AA-P0-01 · Pin the reference revision and write the model ADR — M · low risk
**Goal.** Write down, with exact versions, which document decides each subject, and record the plan's decisions
D-AA-02 … D-AA-09 in an ADR.
**Scope.** Includes: the MASM 6.11 manual, the TASM 3.2/4.1 manuals (MASM mode and IDEAL mode), the NASM version the
corpus takes as its reference versus the ones the repository uses (2.16.01 in CI, 3.01 in the
`NasmAssemblerAdapter` comment, 3.02 in the published manual). Does not include: downloading the SDM (AA-P0-03).
**Affects.** `docs/adr/` (new ADR), `docs/academic-assistant/annex-g-sources.md`.
**New files.** `docs/adr/ADR-013-knowledge-model.md`.
**Depends on.** nothing. **Blocks.** AA-P1-01.
**Deliverable.** An accepted ADR + a complete reference revision table.
**Acceptance.** (1) Every subject in the table of annex B §B.4 has a version, a consultation date and a location.
(2) The ADR lists the discarded alternatives of §D.5 with their reason. (3) It is decided which NASM version is the
corpus reference and how the others are expressed.

#### AA-P0-02 · Make Abel's PDF readable — S · low risk
**Goal.** Be able to cite Abel with the printed page and the PDF page.
**Scope.** Install or locate a renderer (poppler with `pdftoppm`) and OCR, visually check at least the sections on
segmentation, memory, keyboard and screen, and record the page mapping. Text from the book is **not** copied into
the product.
**Affects.** nothing in the code. **New files.** `research/abel-page-map.md`.
**Depends on.** nothing. **Risk.** OCR of an old scan can be poor: if it is, it is recorded as a permanent limit
and the historical content leans on Irvine and Stallings.
**Acceptance.** Five visually verified citations with their printed and PDF page, or a signed limit note.

#### AA-P0-03 · Confirm the plan's semantic claims against SDM 093 — M · low risk
**Goal.** Close the semantic `[assumption]`s: flags of shifts and rotations, flags of `BT` and `IRET`, the
generation of each `IMUL` encoding (`0F AF` → 386, `69`/`6B` → 186), zeroing of the upper 32 bits when writing a
32-bit register in long mode, the AH/BH/CH/DH restriction with a REX prefix, and the list of instructions invalid in
long mode.
**Scope.** Volume 2 (A–Z reference) and volume 1 (modes). Does not include: reading the whole SDM.
**Affects.** nothing in the code. **New files.** `research/sdm-093-checks.md`.
**Depends on.** nothing. **Blocks.** AA-P1-03, AA-P2-01, AA-P2-04.
**Acceptance.** Every claim has an SDM 093 volume, section and page, or is marked "not found" with what was looked
for.

#### AA-P0-04 · Pin the System V AMD64 psABI — S · low risk
**Goal.** The exact version of the document that decides the Linux ABI, and an explicit separation between the
function ABI and the syscall ABI.
**Affects.** nothing. **New files.** `research/sysv-amd64-abi.md`.
**Acceptance.** A specific version or revision cited, plus the register table of both conventions and the
conditions of the *red zone*.

#### AA-P0-05 · DOS/BIOS service inventory and license check — M · **high** risk
**Goal.** Decide which source the inventory of vectors and functions comes from, and whether its license allows
using it as a reference (not as copied content).
**Scope.** Original IBM BIOS and MS-DOS references, and specialized interrupt lists, identifying the origin,
version and documented or undocumented nature of each fact. **Explicitly includes** reading the license before
importing any catalog.
**Risk.** If the license does not allow deriving an inventory, the corpus is built from the books and the official
documentation, with less coverage and a limit note.
**Acceptance.** A written decision with the license quoted, and a service coverage plan consistent with it.

#### AA-P0-06 · Check the x87 in TASM 4.1 and MASM 6.11, and function 0Ah on real DOS — S · low risk
**Goal.** Close two `unknown` cells of the matrix: whether the assemblers accept x87 instructions without
`.8087`/`.387`, and whether real DOS accepts `maximum` or `maximum − 1` characters in `INT 21h`/`0Ah`.
**Scope.** A manual test with the real tools in DOSBox, recorded in `spikes/REPORT.md` as rule 6 of PLAN.md
requires.
**Acceptance.** Two new rows in `spikes/REPORT.md` with the command, the output and the conclusion.

---

## F.2 Phase AA-P1 — Catalog correction and migration to data

**Observable result:** the severity A defects disappear (no real mnemonic is marked as an error; hover respects the
user's symbols; the sheet title is correct again) and the corpus lives in validated data. **Compatibility:** the
public API of `catalog` does not change in this phase.

#### AA-P1-01 · Dump the catalog to data resources with our own reader — M · medium risk
**Goal.** The 214 entries and their aliases live in JSON inside the module, read by our own code, without changing
any public signature.
**Scope.** Includes: a minimal JSON reader (objects, arrays, strings with escapes, numbers, booleans, `null`), a dump
of the current entries, lazy loading with a holder, warm-up at startup, and changing `AssemblyLexer.INSTRUCTIONS`
from an initialized field to a lazy query. Does not include: changing the model (AA-P2).
**Affects.** `idearm-language/.../catalog/InstructionCatalog.java` (starts delegating),
`idearm-language/.../lexer/AssemblyLexer.java:43`, `WorkbenchBootstrap` (warm-up).
**New files.** `language/knowledge/Json.java`, `language/knowledge/CorpusLoader.java`,
`language/knowledge/Corpus.java`, resources `knowledge/semantic/instructions/*.json`,
`knowledge/text/{en,es}/instructions.json`, and in tests `CorpusLoaderTest`, `CorpusIntegrityTest`.
**Depends on.** AA-P0-01. **Blocks.** almost everything else. **Risk.** medium: it is the change with the largest
surface.
**Deliverable.** The corpus loaded from resources, identical behavior.
**Acceptance.** (1) Before the change, the output of `tools/Inventory.java` is frozen as
`src/test/resources/expected-catalog.txt`; afterwards, a test regenerates it and compares **byte by byte** with no
differences. (2) `ArchitectureTest` stays green (no reference to `java.nio.file.Files` in L3). (3) A tagged
performance test measures the load and publishes it. (4) No new dependency in any `pom`.

#### AA-P1-02 · Declared aliases, deducer removed — S · low risk
**Goal.** `INSTRUCTION` stops being a mnemonic and the 51 legitimate aliases are written down.
**Affects.** `InstructionCatalog.extractMnemonicFromVariant` (deleted), `reg(...)`, corpus resources.
**Depends on.** AA-P1-01.
**Acceptance.** (1) `find("INSTRUCTION")` is empty. (2) A test pins the full list of 265 known mnemonics and fails
if an undeclared one appears. (3) Every alias declares its dialect.

#### AA-P1-03 · Fix the wrong flag tables and generations — S · low risk
**Goal.** The corpus claims nothing false about flags.
**Scope.** `BT`/`BTS`/`BTR`/`BTC` (CF receives the bit; OF, SF, AF, PF undefined), `IRET` (all flags),
`SHL`/`SHR`/`SAR`/`SHLD`/`SHRD` (AF undefined), `CMOVcc` (P6 generation), and a review of the 20 distinct tables
with their citation.
**Affects.** corpus resources; `CpuLevel` gains the P6 value (or is replaced, per D-AA-12).
**Depends on.** AA-P0-03, AA-P1-01.
**Acceptance.** (1) One test per fix, with the SDM citation in the comment. (2) A test that requires the prose of
an entry not to contradict its table in the verifiable cases (at least: if the description mentions CF, the table
cannot declare CF preserved). (3) No undefined flag is represented as 0.

#### AA-P1-04 · Separate "recognized by the assembler" from "documented" — M · **high** risk
**Goal.** No real mnemonic produces a false error, while real typing mistakes are still caught.
**Scope.** A `recognizedBy[]` field per entry; an initial inventory of the mnemonics recognized by TASM 4.1,
MASM 6.11 and NASM 3.02, even at the outline level; `AnalysisView` with the boundary of §D.7; while the inventory is
not closed, the severity of `lint.unknown-instruction` drops from `ERROR` to `INFO` and its text becomes "IDEARM
does not know this word; if it is an instruction from an extension, the assembler will accept it".
**Affects.** `UnknownInstructionRule`, `LintSource.portableLinter`, `AssemblyLexer`, `AssemblySyntaxHighlighter`
(indirectly), the `diagnostic.lint.unknown-instruction*` keys and `docs/troubleshooting.md`.
**New files.** `language/knowledge/AnalysisView.java`; resources `knowledge/semantic/instructions/f18-simd.json` and
the outlines of the missing families; `UnknownInstructionFalsePositiveTest`.
**Depends on.** AA-P1-01. **Risk.** high: it changes the visible behavior of the editor. Mitigation: the
false-positive test uses the list of `tools/FalsePositives.java` as its base and extends it.
**Acceptance.** (1) In a `win-pe64-console` project, none of the 110 mnemonics of annex A §A.4 produces a
diagnostic. (2) `muv ax, 1` still produces a diagnostic with the suggestion `MOV`. (3) `suggest()` does not propose
a mnemonic when the word is a known directive of the dialect. (4) The new severity and text are translated and
documented.

#### AA-P1-05 · Reverse the resolution order of hover and the analyzer — S · medium risk
**Goal.** A project symbol wins over a corpus entry.
**Scope.** `QueryHover`: the symbol index before the corpus, with a secondary card "there is also an instruction
called X"; `UnknownInstructionRule`: look at the declared symbols and the index before querying the corpus.
**Affects.** `QueryHover.execute`, `UnknownInstructionRule.check:57-60`, `HoverInfo` (a field for the secondary
card), `HoverCardPopup`.
**Depends on.** AA-P1-01.
**Acceptance.** (1) With a project macro called `MOV`, hover shows the macro first and the instruction as
secondary. (2) No new diagnostic appears on a symbol declared in the project. (3) Acceptance case 10 covered by a
test.

#### AA-P1-06 · Duplicated i18n keys — S · low risk
**Goal.** The "Operational explanation" title is shown again.
**Affects.** `messages_es.properties:551,606`, `messages_en.properties:551,606`, `MessageBundlesTest`.
**Acceptance.** (1) The new test fails before the fix. (2) No key is defined twice in any bundle. (3) The sheet
section shows the correct title.

#### AA-P1-07 · Index the examples and the synonyms in the search — S · low risk
**Goal.** "@data" finds the entry that uses it.
**Scope.** `matches` also queries the example and a list of synonyms declared per entry.
**Affects.** `InstructionInfo.matches` (or its replacement in the corpus), resources.
**Acceptance.** "@data", "offset", "jnbe" and "sal" (as an alias, not as a substring) return the right entries; the
accidental-substring search is kept apart from the alias search in the result.

#### AA-P1-08 · Corpus integrity report as a test — S · low risk
**Goal.** An invalid corpus breaks the build, not the application.
**Scope.** Unique identifiers, resolvable references and aliases, mandatory fields per type and level, at least one
source per entry, ES/EN parity of the mandatory keys, no broken relations, no cycles in the prerequisites.
**New files.** `CorpusIntegrityTest` (extended), `CorpusCoverageReportTest`.
**Acceptance.** The test fails with a corpus that has a source removed, an identifier duplicated or a reference
broken.

---

## F.3 Phase AA-P2 — Knowledge model and compatibility

**Observable result:** a 64-bit project stops offering `AAA`; `IMUL ax,bx,3` warns in an 8086 project; the sheets
show forms, implicit operands and per-form flags; registers are entities.
**Compatibility:** the `catalog` facade still exists and synthesizes `InstructionInfo` from the first form.

#### AA-P2-01 · `InstructionForm`, `Operand`, `Requirement`, `FlagEffectSpec` entities — L · medium risk
**Goal.** Model the real unit of semantics.
**Scope.** The four types with the schema of §D.2; the generation scale extended with P6; the `Feature`,
`ProcessorMode`, `Privilege` enums; migrating the 214 entries to at least one form each, with the complete forms of
families F-02, F-05 and F-11 (arithmetic, shifts and strings).
**Affects.** the `knowledge` package, resources, the `catalog` facade.
**Depends on.** AA-P0-03, AA-P1-01. **Blocks.** AA-P2-04, AA-P2-05.
**Acceptance.** (1) `IMUL` has the 13 encodings grouped into three families with their requirement. (2) The
existing tests keep passing unmodified. (3) No form without a source.

#### AA-P2-02 · Implicit operands and homonyms — M · low risk
**Goal.** `MUL`, `DIV`, the string instructions and `XLAT` declare what they read and write without writing it, and
`MOVSD`/`CMPSD` have their two readings.
**Acceptance.** (1) The `MUL r/m16` sheet marks AX as an implicit input and DX:AX as the output. (2) `MOVSD`
presents both readings told apart by extension, and the search returns both.

#### AA-P2-03 · Conditional effects and non-FLAGS state — M · low risk
**Goal.** Express "if the count is 0 the flags do not change", "OF only on 1 bit", "depends on the gate type", and
tell the flags register apart from the x87 status word.
**Affects.** `FlagEffectSpec.condition`, `otherState[]`, the dialog's flag grid
(`MnemonicsDictionaryDialog.createFlagGrid`) and the `dialog.dictionary.flags.none` text.
**Acceptance.** (1) The `SHL` sheet shows the written condition, not a bare `M`. (2) An x87 entry stops saying
"does not modify any flag" and says which status word it modifies. (3) `INT` shows the mode dependency.

#### AA-P2-04 · A `CompatibilityContext` derived from the profile, with `UNKNOWN` — M · medium risk
**Goal.** Replace the free-form string with a typed context, and stop the unknown from turning into 8086.
**Scope.** The context of §D.3; building it from `TargetProfile` + `ToolchainSelection` + `DebugConfiguration` +
`NativeHost`; the optional `features` field in the project; the resolution algorithm.
**Affects.** `CpuLevel.parseLevel` (removed from the main path), `QueryCompletion`, `CpuBaselineRule`,
`WorkbenchViewModel:976,990`, `LiveLintCoordinator.LintContext`.
**Depends on.** AA-P2-01.
**Acceptance.** (1) `cpu = "x86"` no longer filters to 127 entries: it produces `UNKNOWN` and generates no
warnings. (2) A 64-bit project offers no entry from the list of instructions invalid in long mode that AA-P0-03
closes. (3) A project without declared `features` produces no diagnostic about extensions.

#### AA-P2-05 · Analyzer rules per form and per profile — M · **high** risk
**Goal.** The compatibility warning is correct and the native profiles have useful rules.
**Scope.** `CpuBaselineRule` resolves the concrete form from the operands; the `FS`/`GS` label is fixed;
`push offset x` is detected as an immediate; a warning for an instruction invalid in the project's mode is added;
`LintSource` builds the rule set from the profile, not from a `dosTarget` boolean.
**Affects.** `CpuBaselineRule`, `LintSource`, `AssemblyLinter`, diagnostic keys, `docs/troubleshooting.md`.
**Depends on.** AA-P2-01, AA-P2-04, AA-P4-01 (operand parser) for the operand case.
**Risk.** high: it can introduce new false positives. Mitigation: every new rule comes with its negative test
("must not warn on this correct code").
**Acceptance.** (1) `imul ax, bx, 3` warns with `cpu = 8086` and does not warn with `cpu = 80186`. (2) `iretd`
warns on 8086. (3) `shl ax, COUNT` with `COUNT EQU 1` does **not** warn. (4) The `FS`/`GS` message does not call
them 32-bit registers.

#### AA-P2-06 · Registers as entities, with views, fields and exposure — L · medium risk
**Goal.** A single source of registers for the lexer, completion, sheets and the debugger.
**Scope.** Groups G-1 to G-4 complete (general, pointers, segment, execution control) with views and fields; G-5
and G-6 at the minimal level; `exposedBy[]` per register; sheets for the nine flags with a reverse index.
**Affects.** `AssemblyLexer.REGISTERS`, `QueryCompletion.REGISTERS`, `CpuBaselineRule.REGS_32BIT`,
`DebugViewModel.bitWidth` and the panel's list of names.
**Acceptance.** (1) A test checks that the three old lists match the corpus one. (2) The AX sheet describes the
overlap and the partial-write semantics. (3) The `ST(0)` sheet declares that no panel exposes it.

#### AA-P2-07 · Emulator availability table, generated and verified — M · low risk
**Goal.** `availability.emu8086` is not written by hand and does not drift.
**Scope.** The emulator exposes the list of opcodes it implements; a test compares that list with what the corpus
claims, and fails if they diverge. Does not include extending the emulator (D-AA-11).
**Affects.** `idearm-emu8086` (a new read-only method), corpus resources.
**Acceptance.** (1) The test detects when someone implements `ENTER` without updating the corpus, and the other way
round. (2) The `FSQRT` sheet explains the invalid-opcode failure with the reason.

#### AA-P2-08 · Documented contract of the emulator's services — S · low risk
**Goal.** Record the divergences between the emulator and the documented contract as data, not as surprises.
**Scope.** The 18 implemented functions (annex A §A.9) with their `caveat`; the warning missing from `INT 10h` and
`INT 16h`.
**Affects.** `DosInterruptHandler.handleInt10/handleInt16` (add the warning), resources.
**Acceptance.** (1) A program that uses `INT 10h` function 13h gets a warning in the Problems panel. (2) The
`INT 21h`/`0Ah` sheet shows the extra-character divergence.

---

## F.4 Phase AA-P3 — First vertical integration

**Observable result:** a navigable Academic Center, with **one family complete from end to end** (F-05 shifts and
rotations: complete sheets, syntax per dialect, verified examples, contextual help, related services), plus the
glossary of signs and the `INT 21h` services.

#### AA-P3-01 · Syntax and directives per dialect — L · medium risk
**Goal.** The editor stops offering MASM syntax in a NASM file.
**Scope.** A `SyntaxItem` entity with the directives, operators, signs and predefined symbols of the three
dialects; `AssemblyLexer(Dialect)`; support for `%` and the NASM preprocessor in the lexer; `STRUC`/`ENDSTRUC`.
**Affects.** `AssemblyLexer.DIRECTIVES`, `tokenizeLine`, `AssemblySyntaxHighlighter`, `QueryCompletion.DIRECTIVES`.
**Depends on.** AA-P2-04 (to know the project's dialect).
**Acceptance.** (1) In a file of the `dos-exe-16` profile, `section` is not colored as a directive; in a
`win-pe64-console` one, it is. (2) `%macro` is recognized. (3) The `OFFSET` sheet declares dialect and version, and
mentions that `LENGTH` and `SIZE` are the legacy forms.

#### AA-P3-02 · Cross-cutting search with differentiated results — M · low risk
**Goal.** "colon", "brackets", "carry", "extra segment", "interrupt 21h" (and their Spanish equivalents) and an
alias return relevant entries **told apart by type**.
**Scope.** A search index over every entity, with synonyms in ES and EN and the signs as text; grouped results;
punctuation keeps its meaning when the query is the punctuation.
**Affects.** a new `QueryKnowledge` in L2; the search field of the center.
**Acceptance.** Acceptance case 9 covered by a test with the eight queries of the assignment.

#### AA-P3-03 · Interrupt services: entity, vector index and sheets — L · medium risk
**Goal.** `INT 21h` with `AH=09h` and with `AH=0Ah` lead to two different services with their contract.
**Scope.** `Service`/`ServiceFunction`/`ServiceSubfunction`; the 00h–FFh index with the seven states; complete
sheets of the 18 functions the emulator implements plus the course ones; exceptions 00h–14h; separating the `INT`
instruction, the hardware interrupt, the exception and the service.
**Depends on.** AA-P0-05.
**Acceptance.** Acceptance case 4 covered; the vector index invents no service for any number.

#### AA-P3-04 · Completion from the corpus, in the UI language — M · low risk
**Goal.** Remove the hand-coded lists and the forced English.
**Affects.** all of `QueryCompletion`, `CompletionItem`, `CompletionPopup`.
**Acceptance.** (1) With the UI in Spanish, the completion descriptions are in Spanish. (2) The candidates respect
the context (nothing `UNAVAILABLE`). (3) Aliases and 64-bit registers are offered when appropriate.

#### AA-P3-05 · Academic center: view, ViewModel and navigation — L · medium risk
**Goal.** Turn the dialog into the center of §D.9 without losing any current behavior.
**Scope.** Topic tree, context facets, sheet index, history, home, deep links, a way back to the editor, sheets per
entity type. Keeps: a non-modal window, accent-insensitive search, filtering chips, back/forward, copying an
example, switching language on the fly.
**Affects.** `MnemonicsDictionaryDialog` → `AcademicCenterView`, `MnemonicsDictionaryViewModel` →
`KnowledgeViewModel`, i18n keys, `workbench.css`, `WorkbenchMenuBar`.
**Acceptance.** (1) Each behavior on the keep list has a test or a step in the usability script. (2) The whole
walkthrough works without a mouse. (3) The ViewModel does not depend on the view (`PresentationArchitectureTest`).

#### AA-P3-06 · Family F-05 complete from end to end — L · low risk
**Goal.** Prove the model with a whole family: `SHL`/`SAL`, `SHR`, `SAR`, `ROL`, `ROR`, `RCL`, `RCR`, `SHLD`,
`SHRD`.
**Scope.** Every form, flags with a condition, operands, progressive examples **verified with the real tools**
where they exist, common mistakes, counterexamples, related entries and sources.
**Acceptance.** (1) Acceptance case 6 covered by the shift part. (2) Each example has its real verification mark
(`ASSEMBLED` or `NOT_RUN`, never simulated). (3) A signed editorial review.

#### AA-P3-07 · Examples: open or create a compatible project — M · medium risk
**Goal.** A runnable example offers a project to run it in, without touching the student's files.
**Scope.** An "open example" action that creates a new project in a folder the user chooses, with the profile,
toolchain and backend the example declares; it never overwrites; it never runs anything when a sheet is consulted.
**Affects.** `CreateProject` (reused, not modified), the academic center, `WorkbenchViewModel`.
**Acceptance.** (1) Consulting a sheet writes nothing to disk. (2) The dialog declares the toolchain and the
dependencies before creating. (3) If the tool is missing, it says so and does not create a project that cannot be
built.

---

## F.5 Phase AA-P4 — Contextual help by position

**Observable result:** selecting `mov ax, [bx+si+4]` produces the complete explanation of §D.11.

#### AA-P4-01 · `OperandParser` — L · medium risk
**Goal.** Turn an operand string into a tree with positions.
**Scope.** Segment override, size prefix (`byte ptr`, `word`), memory expression with base, index, scale and
displacement, register, immediate, symbol reference, and the position of each part in the line. Validates
combinations according to the address size.
**Affects.** `AssemblyParser.extractOperands` (now returns typed operands), `InstructionNode`.
**Risk.** medium: the parser works per line and there are dialects. Mitigation: the tree accepts `UNKNOWN` nodes
without failing.
**Acceptance.** (1) `[bx+si+4]`, `ES:[di]`, `word ptr [bp-2]`, `[eax*4+table]` are parsed correctly. (2) An operand
that is not understood produces `UNKNOWN`, never an exception or a diagnostic.

#### AA-P4-02 · `QueryExplain` and help by selection — L · medium risk
**Goal.** The query with a position, with the new resolution order.
**Scope.** `QueryExplain(filePath, line, column, context)` returning a tree of explanations; an "Explain selection"
action in the editor and the menu; hover uses the same query.
**Affects.** a new use case in L2, `WorkbenchViewModel.getHover`, `RichTextFxEditorComponent` (a request with a
position as well as a word), `HoverCardPopup`.
**Depends on.** AA-P4-01, AA-P2-04.
**Acceptance.** Acceptance cases 1 and 2 covered by tests; `21h` inside `int 21h` explains the service, not the
ASCII character.

#### AA-P4-03 · Addressing modes as content and as validation — M · low risk
**Goal.** Explain why a combination is not valid, and warn about it.
**Scope.** An `AddressingMode` entity with valid and invalid combinations per address size and their reason; an
analyzer rule that uses the same data.
**Acceptance.** (1) `[bx+bp]` produces a diagnostic with the reason "two base registers". (2) The sheet lists the
four valid 16-bit combinations and the 32/64-bit ones. (3) No false positive on `[eax*4+d]` with a 32-bit address.

#### AA-P4-04 · Procedure, frame and convention sheets — M · low risk
**Goal.** Cover block F with the conventions of the four profiles.
**Depends on.** AA-P0-04.
**Acceptance.** Acceptance case 7 covered; the sheet applies the ABI of the open profile and tells instruction and
convention apart.

#### AA-P4-05 · Segment, memory and addressing sheets — M · low risk
**Goal.** Cover the whole of block C.
**Acceptance.** (1) The sheet explicitly states that a program is not limited to four segments. (2) It compares CPU
segmentation, directives and object sections without presenting them as interchangeable. (3) `COM` does not appear
as a supported profile.

---

## F.6 Phase AA-P5 — Systematic expansion of the corpus

**Observable result:** the coverage report shows closure against the reference revision.
**A method, not a title:** no task in this phase says "add the rest of the instructions". Each one has a starting
inventory, a writing method, a review oracle and a closing criterion.

#### AA-P5-01 · Mnemonic inventory and coverage report generator — M · low risk
**Goal.** Know, in numbers, what is missing.
**Scope.** The list of mnemonics of the A–Z index of SDM 093 and of those the three assemblers accept, as a test
resource; a report per family and type with the columns complete / minimal / outline / missing.
**Depends on.** AA-P0-03, AA-P0-01.
**Acceptance.** The report is generated during the build and its result is published in
`docs/academic-assistant/coverage.md`.

#### AA-P5-02 · Families F-01 to F-04 and F-06 to F-14 at the complete level — L (several packages) · medium risk
**Goal.** Close the whole core.
**Method.** Per family: (1) list of mnemonics and forms from the inventory; (2) original writing in English;
(3) technical review against a second independent source; (4) translation; (5) examples with real verification or
`NOT_RUN`; (6) a signed editorial review.
**Acceptance.** Per family: the report marks it complete; no form without a source; no flag claim without a
citation.

#### AA-P5-03 · Family F-15 (x87) at the complete level — L · medium risk
**Scope.** Includes the 21 missing instructions of annex A §A.4, the register stack, the status and control words,
and the `FSTSW AX` + `SAHF` bridge.
**Acceptance.** Acceptance case 11 covered with `FSQRT`; no x87 entry declares `minCpu` 8086.

#### AA-P5-04 · Semantic accuracy with an independent oracle — M · **high** risk
**Goal.** Check that the flag tables and the requirements are true, without using the corpus itself or the emulator
as the oracle.
**Scope.** A table of cases written from the sources, with the expected initial and final state, checked against
**real** execution on the available backends when the case is runnable; differential tests between the emulator
and GDB where both exist.
**Risk.** high: this is where the temptation to use the emulator as the truth is greatest. Mitigation: a corpus
architecture test forbids a case file from citing the emulator as a source.
**Acceptance.** (1) At least one case per distinct flag table. (2) No assertion about a flag declared undefined.
(3) Non-runnable cases are marked `NOT_RUN` with their reason.

#### AA-P5-05 · SIMD and other extensions, at the decided level — L · low risk
**Scope.** MMX and SSE/SSE2 according to annex B §B.3.5; the rest as outlines with their CPUID bit.
**Depends on.** D-AA-10 (depth ceiling).
**Acceptance.** No family appears in the report as "etc."; every outline declares why it is not in depth.

#### AA-P5-06 · System, undocumented and other-dialect instructions — M · low risk
**Scope.** F-16 at the minimal level; `SALC`, `ICEBP`, `INT1` marked "undocumented" with the source of the claim;
`movabs` and friends as syntax entries of another dialect with an empty `recognizedBy`.
**Acceptance.** `movabs` is not listed as an x86 instruction and its sheet explains which assembler it belongs to.

---

## F.7 Phase AA-P6 — Teaching material and paths

#### AA-P6-01 · The nine paths — L · low risk
**Scope.** Prerequisites, objectives, linked contents, examples, **original exercises** and explained solutions for
R-1 … R-9 of annex C §C.8.
**Acceptance.** Each path has its checkable objective written as a task the student does, not as a list of topics
read.

#### AA-P6-02 · Block G: fundamentals tied to the code — M · low risk
**Scope.** Number bases, two's complement, overflow and carry, bits/bytes/words, ASCII, boolean logic and masks,
floating point, the instruction cycle; each concept with at least one link to an instruction and to an example.
Uses Stallings and Mano, with the warning about the level of abstraction.
**Acceptance.** No concept without a link to code; the "circuit versus architecture" note is present in the
register sheet.

#### AA-P6-03 · Windows API, ABI and Linux syscalls, with a bounded corpus — M · medium risk
**Scope.** The set IDEARM really links (`kernel32` on Windows) and a declared set of Linux syscalls, with their
dependencies; third-party libraries (Irvine32/Irvine16) as a separate entity.
**Acceptance.** Acceptance case 12 covered; no sheet promises to cover the whole system API.

#### AA-P6-04 · Teaching diagrams — M · low risk
**Scope.** Subregisters, memory and segments, stack and frame, state before and after, **only where they add
value**. A simulation is never described as a real execution of the backend.
**Acceptance.** Each diagram has an accessible text alternative and works in the light and dark themes.

---

## F.8 Phase AA-P7 — Final validation and documentation

#### AA-P7-01 · Complete suite and publishing the report — M · low risk
**Acceptance.** The seven test classes of §F.9 green or with their exception justified; `coverage.md` published.

#### AA-P7-02 · Performance and accessibility measured — M · low risk
**Acceptance.** The eleven targets of §D.12 measured, with the real number next to the target; none presented as a
fact without a measurement.

#### AA-P7-03 · Project documentation — M · low risk
**Scope.** The knowledge model ADR (already created in AA-P0-01) and the compatibility resolution ADR; a new
section in `docs/user-guide.md`; new rows in `docs/troubleshooting.md`; the status in PLAN.md §7; the decision on
the language of these documents (D-AA-01).
**Acceptance.** `MessageBundlesTest` green; each new diagnostic code with its row; PLAN.md updated.

#### AA-P7-04 · Usability script with the twelve cases — M · low risk
**Acceptance.** The twelve cases of §F.10 run with a student or reviewer, with the result recorded.

---

## F.9 Validation plan

### The seven test classes

| Class | What it checks | How | Where it lives |
|---|---|---|---|
| **1. Corpus integrity** | unique identifiers; resolvable references, aliases and prerequisites; mandatory fields per type and level; ≥1 source per entry; ES/EN parity of the mandatory keys; no broken relations or cycles | a JUnit test that reads the resources | `idearm-language`, `CorpusIntegrityTest` |
| **2. Semantic accuracy** | that what the corpus claims is true | a table of cases written **from the sources**; comparison with real execution where it exists; using the corpus or the emulator as their own oracle is **forbidden** | `CorpusSemanticsTest` + tagged tests |
| **3. Compatibility** | positive and negative per mode, extension, dialect, version and backend | two symmetric suites: "must be available" and "must not be offered"; plus two suites dedicated to **false analyzer errors** and **false availability** | `CompatibilityMatrixTest`, `UnknownInstructionFalsePositiveTest`, `AvailabilityTest` |
| **4. Assemblable examples** | that a `RUNNABLE` example assembles, links, runs and can be debugged | the existing tags `requires-tasm` / `requires-masm` / `requires-nasm` / `requires-dosbox`; **five separate levels**: static, assembled, linked, run, debugged | integration tests per toolchain |
| **5. Expected states** | registers, memory and **defined** flags after an example | explicit initial conditions; differential emulator/GDB tests when both exist; no assertion about an undefined flag | `emu8086` and `infrastructure` |
| **6. Regression** | catalog, search, hover, completion, lexer, parser, analyzer, i18n, navigation, architecture | the existing suites extended + the two boundary tests of §D.7 + a duplicated-key test | every module |
| **7. Editorial review** | clarity, progression, pedagogical correctness | a checklist per complete-level sheet, signed; long-text snapshots are **not** used as the only guarantee | `docs/academic-assistant/editorial-checklist.md` |

### Rules the assignment requires and the suite makes enforceable

1. **When the tool is missing, the result is `not run`.** The project already excludes the `requires-*` tags by
   default; the coverage report tells "verified" from "not run" and never adds them together.
2. **The emulator is not the oracle of its own data.** A corpus architecture test: no semantic case file can cite
   `idearm-emu8086` as a source.
3. **The presence of a field is not a proof.** Class 1 checks presence; only class 2 checks truth, and the phase's
   closing criterion requires both.
4. **No long text is validated with a snapshot.** Class 7 is human and signed per sheet.

### Real repository commands that are reused

```bash
mvn test                      # excludes the requires-* tags
mvn install -Plocal-tools     # with TASM, DOSBox, NASM, ld and GDB registered
mvn test -Pmasm               # a profile that excludes only requires-masm
IDEARM_VISUAL_SMOKE=1         # visual desktop smoke, for UI changes
```

It is proposed **[proposal]** to add a `-Pcorpus-report` profile that generates `coverage.md`, and an
`idearm kb validate` / `idearm kb coverage` CLI command to review the corpus without opening the UI.

**An explicit distinction for this session:** the only checks **performed** are those of annex A (compiling the
`catalog` package, running `Inventory` and `FalsePositives`, measuring `<clinit>` with `javap`, PDF extractions and
web lookups). Everything in this section is **planned**. Neither `mvn test` nor any tool of the build chain was run.

---

## F.10 The twelve acceptance cases

For each one: context, expected content, how it is validated and the task that delivers it.

| # | Context | Expected content | Validation | Task |
|---|---|---|---|---|
| **1** | `mov ax, [bx+si+4]` selected; profile `dos-exe-16`, `cpu = 8086`, TASM | breakdown of each part; 16-bit size set by AX; `[ ]` = contents; BX base and SI index; displacement added at assembly time; DS segment by default (SS if it were BP); effective and physical address; AX a destination that is written; `MOV` touches no flags; why `[bx+bp]` and `[ax]` are not valid | a `QueryExplain` test that compares the explanation tree with the expected one, node by node + a usability script step | AA-P4-01, AA-P4-02, AA-P4-03 |
| **2** | queries on `DS:DX`, `DX:AX`, `label:` and `ES:[DI]` | four different explanations; the first two marked **notation, not code**; the last two marked assemblable syntax; none presents `:` as concatenation | a test requiring four distinct entries with correct `notation` and `assemblable` + sample [E.4](annex-e-content-samples.md#e4-signs-sheet-the-colon-and-the-brackets) | AA-P3-01, AA-P3-02 |
| **3** | `mov ax, @data` and `mov ds, ax` in a MASM/TASM project | `@data` is a **predefined assembler symbol** standing for the address of the data segment; the linker fixes it; why two instructions are needed; why there is no `mov ds, @data`; the example is **not** offered as NASM | a test that the `@data` sheet declares `dialect ∈ {masm, tasm}` and does not appear in a NASM context | AA-P3-01 |
| **4** | `INT 21h` with `AH=09h` and with `AH=0Ah` | two different services; the contract of each; `DS:DX` explained; `$` termination versus a structure with a count; availability in the emulator with the `0Ah` divergence; outputs taken from the documented contract | a test that resolves `int 21h` with each AH value to different services + sample [E.5](annex-e-content-samples.md#e5-service-function-int-21h-function-0ah) | AA-P3-03, AA-P2-08 |
| **5** | compare `MOV`/`LEA`, `CMP`/`TEST`, `MUL`/`IMUL`, `DIV`/`IDIV` | reads and writes; signed versus unsigned; implicit operands; forms; flags; a division example with the preparation (`CBW`/`CWD`) and its error cases | a comparison view with a test requiring the four pairs + a semantic case per pair | AA-P2-01, AA-P2-02, AA-P5-02 |
| **6** | three examples: arithmetic CF/OF, `INC`/`DEC` and CF, a shift depending on the count | CF and OF told apart with the same data; `INC`/`DEC` do not touch CF; the shift changes effect with the count; checked results; **no assertion about an undefined flag** | three semantic cases run in the emulator with an explicit initial state | AA-P3-06, AA-P5-04 |
| **7** | walking through `CALL`/`RET` in the debugger | stack, return address, parameters, preserved registers; instruction / convention distinction; the profile's ABI applied | a step-by-step debugging script + sample [E.6](annex-e-content-samples.md#e6-a-procedure-with-a-stack-and-an-abi) | AA-P4-04 |
| **8** | switching the project between DOS 8086, Windows x86, Windows x64 and Linux x64 | the content adapts compatibility, syntax and services; **the theory of other contexts can still be consulted**; instructions unavailable in long mode and extensions that require more than "x86-64 CPU" are checked | a `CompatibilityMatrixTest` suite with the four configurations and the AA-P0-03 list of instructions invalid in long mode, plus an extension that requires `features` | AA-P2-04, AA-P2-05 |
| **9** | searching "dos puntos" (colon), "corchetes" (brackets), "acarreo", "carry", "segmento extra" (extra segment), "stack", "interrupción 21h" and an alias | relevant results, **grouped by type**; punctuation keeps its meaning when it is the query | a test with the eight queries and the expected type of the first results | AA-P3-02, AA-P1-07 |
| **10** | a user macro or label named after a corpus entry | the help resolves the project symbol first; offers the instruction as a secondary card; **no** new diagnostic appears | a test with a macro called `MOV` and another called `INSTRUCTION` | AA-P1-02, AA-P1-05 |
| **11** | a documented instruction the emulator does not run (`FSQRT`) | the sheet says so with the reason; the semantics can be studied; a compatible environment is offered **only if it has been verified** | a test that compares `availability.emu8086` with the real opcode table | AA-P2-07, AA-P5-03 |
| **12** | an example that depends on Irvine32, on an assembler version or on a missing library | the dependencies are explained; a relevant alternative is offered when there is one; it is **not** announced as runnable | a test that no `RUNNABLE` example without its registered tools offers the run action | AA-P3-07, AA-P6-03 |

---

## F.11 Risks, mitigations and discarded alternatives

| Risk | Probability · impact | Mitigation | Alternative if it happens |
|---|---|---|---|
| **The editorial volume exceeds what one person can do** | high · high | three sheet levels; an outline is enough to avoid false errors; an honest coverage report per family | freeze the complete level on the course families and leave the rest at minimal, stating it in the documentation |
| **Growing the corpus breaks the editor** | medium · high | the `AnalysisView` boundary with its two tests; downgraded severity while the inventory is open; every new rule with a negative test | revert the specific rule; the corpus is not touched |
| **Using the emulator as the oracle of its own data** | medium · high | an explicit ban with a corpus architecture test; semantic cases written from the sources | mark the affected cases as `UNVERIFIED` until there is a source |
| **Version drift** (three NASMs, two TASMs, historical MASM) | high · medium | a pinned reference revision (AA-P0-01) and a `sourceRevision` per entry; the dialect+version axis is mandatory | document the specific version in which it was verified and mark the others as unverified |
| **The license of the interrupt inventory does not allow deriving it** | medium · medium | AA-P0-05 checks it **before** importing anything | build the inventory from the books and the official documentation, with less coverage declared |
| **Abel's PDF never becomes readable** | medium · low | the plan already works without it; its contribution is assigned, not taken for granted | lean on Irvine and Stallings and record the limit |
| **The corpus's performance degrades startup or typing** | medium · medium | lazy loading off the JavaFX thread; measurable targets; the lexer's static field is fixed in AA-P1-01 | an index precomputed at build time (only the recognized mnemonics, which is what the lexer needs on the first keystroke) |
| **Migration M1 introduces an invisible difference** | medium · high | a frozen dump and a byte-by-byte comparison before and after | revert to the Java version and repeat with the dump as the reference |
| **Incomplete translation** | high · low | parity required only for mandatory fields; a visible "not translated" mark | publish in English with the mark, never silently mix languages |

### Discarded design alternatives, with the reason

| Alternative | Why it is discarded |
|---|---|
| A separate `idearm-knowledge` Maven module | the cost of JPMS, the enforcer and the architecture tests without solving any current problem; the dependency edge exists anyway because the lexer must read the corpus. **Trigger to reconsider it:** resources > 2 MB compressed or a second consumer without an analyzer |
| YAML or TOML for the corpus | would need a library (forbidden in L3) or a hand-written parser much larger than the JSON one |
| Markdown as the corpus format | good for prose, useless for queryable and validatable semantics |
| Generating Java from JSON at build time | returns to the measured bytecode ceiling and duplicates the source of truth |
| An embedded or remote database | breaks offline use and adds dependencies; the assignment explicitly rules it out |
| A run-time schema validation library | validation belongs to the build: an invalid corpus must break `mvn test`, not the student's application |
| Duplicating the semantic tables per language | the assignment forbids it and it would double the cost of every fix |
| A chatbot or RAG as the baseline solution | none of the twelve situations requires it; it breaks offline use and verifiability (annex D §D.13) |
| Keeping `CpuLevel` as the only axis | shown to be insufficient: A-03, A-09, A-16 |
| Deducing aliases from the syntax strings | shown to be dangerous: A-01 |

---

## F.12 Open decisions

None of them prevents starting with AA-P0 and AA-P1. Each one carries this plan's recommendation.

#### D-AA-01
**Language of these documents.** The assignment asked for the plan in Spanish; ADR-006 §1 requires the repository's
artifacts to be in English, and a project memory note confirms it.
**Recommendation (at the time):** keep this plan in Spanish as a working document of the course and, once its
content settles, move the decisions into English ADRs (AA-P0-01 and AA-P7-03), which are the permanent artifacts.
If strict consistency is preferred, the whole folder is translated into English.
**Decided (2026-09-27):** the user chose strict consistency; the whole folder was translated into English for
release 2.0.0.

#### D-AA-10
**Depth ceiling for SIMD and AVX.** The outline level is enough for the editor not to mark false errors, but it
does not teach the instructions.
**Recommendation:** outline for SSE3 onwards and for all of AVX in the baseline version; the complete level only
for the scalar subset of SSE/SSE2, which is the one that appears in x86-64 floating point. Revisit after student
feedback.
**Affects:** AA-P5-05.

#### D-AA-11
**Does the emulator grow?** Today it is "8086 plus a few 80186 opcodes" and it does not run the x87 (A-25).
**Recommendation:** complete the 80186 (`60`–`67`, `69`, `6B`, `6C`–`6F`, `C8`, `C9`) because it is cheap and removes
surprises in course code; do **not** implement the x87 in the baseline version, and document it with the precise
warning plus a pointer to Turbo Debugger or CodeView. If the course really uses floating point, the x87 becomes a
phase of its own with its own plan.
**Affects:** AA-P2-07, AA-P5-03.

#### D-AA-12
**Is `CpuLevel` extended or replaced?** P6 is needed, and the generation axis can no longer be the only one.
**Recommendation:** replace it with `CpuGeneration` inside `knowledge`, and keep `CpuLevel` as a `@Deprecated`
facade during the migration, because the dialog, completion and two rules use it today.
**Affects:** AA-P1-03, AA-P2-01.

#### D-AA-13
**Should the debugger panel show the x87 and SIMD?** GDB exposes them (92 registers on amd64) and the panel shows 24
on purpose (A-29).
**Recommendation:** not in this expansion; the corpus only declares what each backend exposes. Adding an optional
tab to the panel is debugger work, not academic assistant work, and it has its own UI cost.
**Affects:** AA-P2-06.

#### D-AA-14
**Is the `features` field added to the project format?** Without it, extensions are always `UNKNOWN` and never
validated; with it, the project format changes (although compatibly).
**Recommendation:** add it as an optional field in `[target]`, with absence = `UNKNOWN`. An old project still opens
and gets no new diagnostics.
**Affects:** AA-P2-04.
