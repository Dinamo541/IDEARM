# ADR-013: Academic Knowledge Model, Separation of Views, and Data-Driven Architecture

Date: 2026-09-25. Status: accepted. Implements **AA-P0-01** and establishes the architectural foundation for **AA-P1–AA-P7**.

## Context

The initial implementation of the academic assistant consisted of an in-memory Java catalog (`InstructionCatalog`) containing 214 hand-coded instructions with 10 fields each. A static initializer constructed all entries at class loading time, generating approximately 9.4 KB of bytecode out of the JVM 64 KB method limit (`<clinit>`).

Furthermore, five distinct consumers (`AssemblyLexer`, `AssemblySyntaxHighlighter`, `UnknownInstructionRule`, `QueryHover`, and `QueryCompletion`) all directly queried `InstructionCatalog.knownMnemonics()` or `find()`. This tightly coupled academic documentation with editor behavior:
1. Valid x86/x64 instructions (e.g., SSE, AVX, system, or x87 instructions) not yet documented in the catalog were falsely flagged as compiler errors (`lint.unknown-instruction`) with confusing typo suggestions (e.g., `PAUSE` → `PUSH`, `MOVAPS` → `MOV`).
2. Syntax variant strings were parsed with heuristic token extraction, resulting in synthetic false aliases (such as `INSTRUCTION` pointing to `LOCK`).
3. Compatibility was flattened to a single linear `CpuLevel` integer, failing to distinguish between execution modes (real mode vs protected mode vs 64-bit long mode), processor extensions (SSE, AVX), or dialects (MASM, TASM, NASM). Instructions forbidden in long mode (e.g., `AAA`, `DAA`, `PUSHA`, `INTO`) were offered as valid completions in 64-bit projects.
4. The hover lookup prioritized the catalog over user symbols, leading to incorrect explanations when a user defined a macro or label matching an instruction name.

## Decision

- **Data-Driven Knowledge Repository in Layer 3 (`idearm-language`):** The knowledge base is extracted into resource files under `io/github/dinamo541/idearm/language/knowledge/`. A lightweight, pure-JDK JSON parser (`Json.java`, ~250 lines) parses entities without adding external dependencies (SnakeYAML, Jackson, etc.), adhering strictly to ADR-007 (pure JDK in Layer 3, no `java.nio.file.Files`, no `ProcessBuilder`).
- **Stable Hierarchical Entity Model:** Entities are typed and identified by stable URN-style strings (`x86.instr.<name>`, `x86.instr.<name>#form.<operands>`, `x86.reg.<name>`, `syntax.<dialect>.<type>.<name>`, `dos.int21.<func>`, `concept.<name>`).
  - `Instruction`: Canonical mnemonic, family (F-01 to F-18), pedagogical level, prerequisites, explicitly declared aliases, related instructions, pitfalls, and sources.
  - `InstructionForm`: Syntax by dialect, explicit/implicit operands, requirement, flag effects by form, availability by backend.
  - `Operand`: Position, kind (`REG`, `REG_MEM`, `IMM`, `MEM`, `IMPLICIT_REG`, etc.), sizes, access (`READ`, `WRITE`, `READ_WRITE`), default segment, sign/zero extension.
  - `Register`: Size in bits, hierarchy (parent register, bit offset, subregister views such as EAX → AX → AH/AL), architectural constraints, partial write semantics (zero-extension of 32-bit writes in 64-bit mode), flag fields.
  - `SyntaxItem`: Dialect-specific directives, operators (`OFFSET`, `PTR`), symbols (`@data`), punctuation (`:`, `[]`), distinguishing assemblable syntax from explanatory notation.
  - `Service`: Interrupt vector, selector (`AH=09h`, `AH=0Ah`), input/output contracts, buffer layouts, backend availability caveats.
- **Ternary Compatibility Resolution:** The compatibility engine evaluates a `CompatibilityContext` against an entity `Requirement`, returning `AVAILABLE`, `UNAVAILABLE`, or `UNKNOWN`, accompanied by a reason code and confidence level (`CERTAIN`, `LIKELY`, `UNVERIFIED`).
  - *Hard Rule 1:* `UNKNOWN` never converts to `AVAILABLE` or `UNAVAILABLE`.
  - *Hard Rule 2:* The linter only emits diagnostics on `UNAVAILABLE + CERTAIN`. Absence of full documentation never causes editor error marks.
- **Consumer Views and Editor Guardrails:** Rather than sharing raw collections, five dedicated views guard access to the knowledge base:
  - `ReferenceView`: Full access for the Academic Center UI (topics, forms, examples, guides).
  - `HoverView`: Concise summary, active form, implicit operands, and link to full sheet.
  - `CompletionView`: Context-filtered candidate list matching project target and dialect.
  - `AnalysisView`: Strict boundary for lexer, highlighter, and linter rules, exposing only mnemonics declared in `recognizedBy[]` for the active assembler.
  - `DebugView`: Hardware register/flag mapping and backend exposure status.
- **Inverted Hover Resolution:** Symbol resolution checks `ProjectSymbolIndex` first. If a user definition exists, it is displayed as primary with a secondary link note ("An x86 instruction with this name also exists").
- **Separation of Semantics and Localization:** Semantics are stored language-neutrally. Human prose resides in localized bundles (`text/{en,es}/`). Missing translations fall back gracefully with a visible indication rather than silently blending languages.

## Alternatives Considered and Rejected

1. **Separate Maven Module (`idearm-knowledge`):** Rejected due to additional JPMS configuration and circular dependency requirements (lexer and linter must query the recognized mnemonics). The knowledge package cleanly belongs inside `idearm-language` under Layer 3.
2. **Third-Party JSON/YAML Parsers:** Rejected to preserve Layer 3 purity and comply with ADR-007 / enforcer bans.
3. **Markdown-Only Corpus:** Rejected because structured semantic querying (by operand size, mode, registers, flags) cannot be robustly evaluated from freeform markdown.
4. **Code Generation during Maven Build:** Rejected as it reproduces the `<clinit>` 64 KB bytecode ceiling.
5. **Chatbot / LLM Runtime Backend:** Rejected to maintain complete offline availability, deterministic verified citations, zero token latency, and exact academic traceability.

## Consequences

- The bytecode size limit problem is eliminated permanently: data resides in resource streams.
- Expanding documentation or adding experimental forms cannot break the linter or create false typos in user code.
- 64-bit projects cease receiving 16-bit real-mode suggestions for incompatible instructions.
- The catalog can grow to thousands of entries without degrading startup time, leveraging lazy initialization and background thread warm-up.

## Addendum (2026-09-27): single-language fields and the translation table

The "semantics are language-neutral" rule above was not what the corpus held. About 300 prose texts live in
fields that have no language pair (a register's `conventionalUse` and `writeSemantics`, a flag's `meaning`, a
service's input, buffer and output `meaning`, an operand's `note`, `pitfalls`, `caveat`, `reason` and others), and
most were written in Spanish, so the English UI showed Spanish in the Academic Center and in register hovers.

Splitting each of those fields into an `En`/`Es` pair would have changed about fifteen records and every consumer.
Instead, `text/translations.json` is a translation table keyed by the original text, gettext style:
`{"<original>": {"en": "...", "es": "..."}}`. `Corpus.localize(text, locale)` returns the text in the UI language,
and a text the table does not know is returned unchanged. The records stay as they are; the places that display
those fields call `localize`.

`CorpusTranslationTest` walks every semantic resource and fails when a prose value of one of those fields has no
entry with both languages, so a new corpus text cannot reach the UI in one language only. Code examples
(`example`, `starterCode`, `solutionCode`) are left out on purpose: they are source code, and their comments stay as
the author wrote them.
