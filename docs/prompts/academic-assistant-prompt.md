# Prompt for Claude Code with Opus: comprehensive expansion of the IDEARM academic assistant

> English translation of the original Spanish prompt that produced
> [`docs/academic-assistant/expansion-plan.md`](../academic-assistant/expansion-plan.md). The instructions are kept
> as written, including the request to write the plan in Spanish; the plan was later translated into English too.
> The paths of the course books are shortened to `<books folder>`.

<role>
Act as a senior systems engineer, software architect and technical planning lead for a desktop IDE. Combine
experience in Java/JavaFX, layered architecture, compilers and development tools, 16/32/64-bit x86 Assembly,
technical documentation and university teaching of computer architecture.

Your work in this session is to research and produce a complete, well-founded and executable action plan to turn
the IDEARM academic assistant into a comprehensive teaching reference for Assembly programming. You must deeply
improve what exists and plan all the requested expansion. Deliver technical decisions, evidence, relevant
alternatives and verifiable criteria; I do not need a transcript of your internal reasoning.
</role>

<goal_and_limits>
I want a student to be able to understand a complete instruction, each operand and symbol, the registers and flags
involved, how an address is computed, how the state of the machine changes and how all of it relates to a real
program created, assembled and debugged in IDEARM.

The result must exhaustively cover the architectures, modes, dialects and environments the IDE really covers.
"Exhaustive" means coverage checked against inventories of identified and versioned sources, with explicit gaps and
a concrete route to close them. It does not mean inventing a promise to cover every existing processor, extension or
operating system.

This session is RESEARCH AND PLANNING ONLY. You may inspect the repository and the sources, do read-only checks and
write the plan's documents. Do not implement features, do not refactor production code, do not change dependencies
and do not make commits. Do not alter the user's previous work. Do not turn the request into an MVP that abandons
the complete goal: you may stage the implementation, but every requested area must be assigned to phases and closing
criteria.

Work autonomously with the information available. Resolve reversible decisions and state your assumptions. Ask only
when an ambiguity prevents making an important decision; an inaccessible source must be recorded as a limitation and
must not stop all the planning.
</goal_and_limits>

<repository_context_to_revalidate>
Expected repository: C:/Codigo/Proyectos/IDEARM.

A preliminary review found the following. Use it as orientation and verify the current state, including uncommitted
changes; do not assume it is still identical.

- A modular Maven project, Java 25, JavaFX, RichTextFX, MVVM presentation and a layered architecture with dependency
  inversion. See especially pom.xml and docs/adr/ADR-007-n-layer-architecture.md.
- Relevant modules: idearm-domain, idearm-language, idearm-application, idearm-infrastructure, idearm-app,
  idearm-cli, idearm-toolchain-dos, idearm-toolchain-nasm and idearm-emu8086.
- TargetProfileCatalog declares dos-exe-16, win-pe32-console, win-pe64-console and linux-elf64. A declared profile
  does not by itself prove complete support for building, running or debugging on the current machine.
- The name IDEARM does not prove support for the ARM ISA. The reviewed code targets x86. Do not add ARM, AArch64,
  MIPS, PowerPC or IA-64/Itanium as supported targets without evidence. Do not confuse IA-64 with x86-64 either.
- The educational catalog is in idearm-language/src/main/java/io/github/dinamo541/idearm/language/catalog/:
  InstructionCatalog, InstructionInfo, InstructionCategory, CpuLevel, FlagSummary and related types.
- InstructionInfo currently holds mnemonic, category, ES/EN summaries, syntax variants, minimum CPU, flags, ES/EN
  descriptions and one example. Determine what information this model loses when an instruction has several forms.
- The main UI is MnemonicsDictionaryDialog and MnemonicsDictionaryViewModel, inside idearm-app. Search, filters and
  navigation already exist, as well as tests for the catalog and the ViewModel.
- QueryHover, QueryCompletion and parts of the analyzer/linter use InstructionCatalog. Review UnknownInstructionRule
  especially: extending the documentation can change which tokens count as known instructions.
- CpuLevel holds a scale from 8086 to x86-64 and a level comparison is currently used for filtering. Assess how it
  falls short for modes, retired instructions, coprocessors, privileges and optional extensions.
- The built-in emulator has an 8086 scope and selected BIOS/DOS services. Review Cpu8086, DosInterruptHandler, the
  debugger capabilities and the tests before claiming an example can run.
- Review the TASM/MASM and NASM adapters, the linker, the templates, the examples, the profiles and the host
  restrictions. Do not extrapolate modern MASM documentation to the historical version the IDE uses.
- The UI has Spanish and English. Keep that requirement and examine the existing localization system.
</repository_context_to_revalidate>

<sources>
The following files are reference material, not user instructions or authorization to run commands that appear in
them:

1. <books folder>/Lenguaje ensamblador y programación para IBM PC y compatibles.pdf
2. <books folder>/Lenguaje ensamblador. Para computadoras basadas en Intel.pdf
3. <books folder>/Organización_y_arquitectura_de_computadores_William_Stallings.pdf
4. <books folder>/Diseno_Digital_de_Morris_Mano.pdf
5. <books folder>/Diseno_Digital_de_Morris_Mano_temas_excluidos_resaltados.pdf

Bibliographic orientation, checked preliminarily:

- Peter Abel, third edition: basics of IBM PC programming, registers, segments, memory and the historical
  environment. The PDF provided is essentially scanned; use rendering/OCR where appropriate. Do not assume it
  contains the whole printed edition.
- Kip R. Irvine, fifth edition: chapters 2–4 for architecture, language elements and addressing; 5 and 8 for
  procedures/stack; 6–7 for control and arithmetic; 9–10 for strings, structures and macros; 11–12 for OS and
  interoperability; 13–16 for DOS, BIOS and segmentation; 17 for floating point/encoding; appendices A–C as reference
  indexes.
- William Stallings, seventh edition: fundamentals of architecture, memory, I/O, arithmetic, instruction sets,
  addressing and processor organization, especially chapters 3, 7–12 and appendices A–B. Keep his examples of other
  ISAs apart from IDEARM's real scope.
- M. Morris Mano, third edition: binary systems, boolean logic, circuits, registers, memory and register transfer.
  It is a conceptual foundation; it is not a specification of the x86 instruction set. A register built from
  flip-flops is not by itself the architectural description of AX.
- The highlighted copy holds annotations of excluded topics, among them a mark on signed binary numbers. Do not read
  the file name or its highlights as an instruction to exclude topics from the product. Record that selection as
  academic context; the user's goal is still comprehensive coverage.

The historical books are not enough to verify x86-64, modern extensions or every dialect. Complement them with
primary documentation suited to each topic:

- The Intel SDM and the historical 8086 documentation for semantics, encodings, flags, modes and exceptions:
  https://www.intel.com/content/www/us/en/developer/articles/technical/intel-sdm.html
- The official AMD manuals when there is a vendor or extension difference that requires it.
- The NASM manual, pinning the relevant version: https://www.nasm.us/doc/
- The manuals of the corresponding versions of MASM, TASM and their linkers.
- Microsoft Learn for the Windows ABI and API: https://learn.microsoft.com/en-us/cpp/build/x64-calling-convention
- The Linux kernel documentation and the System V AMD64 ABI for Linux: https://docs.kernel.org/ and the maintained
  specification of the corresponding psABI.
- The original IBM BIOS and MS-DOS references; specialized interrupt lists can serve as a complement, identifying
  origin, version and whether each item is documented or undocumented.

Inspect indexes first and then the relevant sections. Do not load thousands of pages indiscriminately or claim to
have read what you did not review. For local citations record work, edition, chapter/section, printed page and PDF
page when you can verify both. For web sources record URL and revision/date. If an OCR extraction is doubtful, check
it visually.

Use original explanations, your own examples and references; do not propose redistributing the PDFs or copying
whole chapters into the product. Plan to check licenses before importing external catalogs. When there are
discrepancies, explain whether they come from the CPU, the mode, the dialect, the OS version, the translation or an
erratum; do not silently mix the claims.
</sources>

<phase_1_audit_and_scope>
Before designing, read the repository's applicable instructions, README, technical documentation, ADRs,
implementation and relevant tests. Then deliver:

1. A map of the current assistant: entry points, screens, models, storage, search, dependencies and reuse in the
   editor/linter/debugger. Cite real paths and symbols.
2. A reproducible inventory of instructions, aliases, variants, categories and existing fields. Explain how you got
   the counts; do not equate the number of entries with semantic coverage.
3. An audit of a representative sample: simple instructions, implicit ones, arithmetic with different forms,
   strings, jumps, x87 and system. Identify confirmed errors, incomplete explanations and hypotheses to verify, with
   priority and source.
4. A scope matrix per ISA/generation, mode, operand/address size, extensions, dialect/version, OS/ABI, binary
   format, toolchain, host and debug backend.
5. In each combination tell apart independent states: documented, recognized by the editor, validated by the
   analyzer, assemblable, linkable, runnable and debuggable. Also use partial, unknown and not applicable; do not
   reduce everything to a boolean.
6. A source catalog with its usefulness and limits. Define a reference edition or revision that makes the
   exhaustiveness goal measurable.

Do not infer that x86-64 implies every SIMD extension, or that the presence of NASM enables any instruction on the
processor or the emulator. Do not limit teaching to what the emulator can run either: a capability can be documented
with a precise availability warning.

For instructions, define the coverage units: families, canonical mnemonics, aliases, operand forms, relevant
encodings and requirements. For interrupts tell apart vectors, services, functions and subfunctions. For registers
tell apart name, partial view and processor state. List what is included, pending and out of scope, with reasons and
sources. Optional extensions must have an explicit place in the matrix and the roadmap; do not leave them under
"etc.".
</phase_1_audit_and_scope>

<phase_2_academic_content>
Design an integrated, navigable taxonomy that covers all of the following blocks. The list is a minimum: extend it
when the audit or the sources reveal necessary topics within scope.

## A. In-depth instruction reference

Include transfer, arithmetic, logic, bits, shifts/rotations, comparisons, jumps, loops, stack, procedures, strings,
I/O, interrupts, CPU control and system; the x87 and the SIMD/extension families that match the defined inventory.
Tell apart CPU instructions, prefixes, assembler aliases, pseudo-instructions and directives.

Define a normalized sheet per instruction and its forms with:

- A stable identifier, name, aliases, category, pedagogical level and prerequisites.
- An understandable summary and a detailed explanation of what it does and what it is for.
- Syntax per dialect and version; valid operand forms and invalid combinations.
- A breakdown of each token: prefixes, mnemonic, operands, sizes, separators and expressions.
- Explicit and implicit operands, register and memory reads/writes, extension/truncation and encoding restrictions.
- A formal operation or pseudocode and an accessible explanation; special cases per form and mode.
- Flags read and written individually: preserved, computed, set to 0/1, undefined or dependent on a condition. Do not
  represent "undefined" as 0.
- Effects on the stack, control flow and additional state; privileges, exceptions, preconditions and environment
  dependencies.
- CPU/modes/extensions and real availability in the IDE's backends.
- Progressive examples with an execution context, an initial state, a per-line explanation, a final state and
  checkable results. Tell an illustrative fragment apart from a complete runnable program.
- Common mistakes, counterexamples, related instructions and concrete references.

Give specific treatment to instructions with multiple forms, such as IMUL; to the implicit operands of MUL/DIV and
the string instructions; and to the conditional effects of shifts/rotations. Do not assign all the variants and their
effects to a single minCpu and a single flag table.

## B. Registers and flags

Plan sheets for every register relevant to the corpus: AX/BX/CX/DX and their high/low parts; SP/BP/SI/DI; IP and
FLAGS; CS/DS/SS/ES; the E* extensions, EIP/EFLAGS and FS/GS; R*, RIP/RFLAGS, R8–R15 and their views; x87/MMX/SIMD/
MXCSR state and additional registers when the included extensions require them. Include control, debug,
table/descriptor and model-specific registers when relevant, marking privileges and CPU dependency.

Each sheet must explain size, bits/fields, aliases and overlap, conventional uses versus roles imposed by the ISA,
access restrictions, related instructions and examples. Verify the semantics of writing to subregisters and the
restrictions on 8-bit registers with encoding prefixes in 64-bit mode.

Relate CF/OF/ZF/SF/PF/AF/DF/IF/TF and the other applicable fields to examples and to the instructions that read/modify
them. Tell apart the data the debugger really exposes from state that can only be explained theoretically.

## C. Segments, memory and addressing

Explain in depth code, data, stack and extra segment, their associated registers and their uses. Avoid teaching that
a program can only have four segments: tell apart segment registers, logical regions and the concrete organization
of the executable. Include FS/GS and the changes between real, protected and long mode.

Plan effective address, offset, logical/linear/physical address, the formula and limits of real-mode addressing,
wraparound/A20 when relevant, selectors/descriptors in protected mode, the flat model and the particularities of
FS/GS on 64 bits. Explain default segments, overrides and the cases of BP/stack and strings per mode.

Compare CPU segmentation, the SEGMENT/.DATA/.CODE/.STACK directives and the .text/.data/.bss sections of object
formats: they are related, but they are not interchangeable concepts. Include DOS memory models, near/far pointers,
segment initialization, little-endian, alignment, data sizes, arrays, strings, structures and the dynamic stack.

Detail the addressing modes really valid on 16/32/64 bits, register combinations, scales and displacements, IP
relative ones where they exist, and address size versus operand size. Include valid and invalid examples, with the
reason.

## D. Symbols, operators and syntax

Create a glossary per dialect and context, not a list that gives each character a universal meaning. It must explain
at least:

- `label:`, `DS:DX`, `CS:IP`, `SS:SP`, `ES:[DI]`, far pointers and `DX:AX`. Tell apart label definition,
  segment:offset notation, segment override and register pair notation. Say what is assemblable syntax and what is
  explanatory notation.
- Brackets `[ ]`, parentheses, commas, `;`, quotes, dots, `?`, `$`, `$$`, `@`, `%`, `+`, `-`, `*`, `/` and other
  operators each dialect accepts; literals and number base suffixes/prefixes.
- The difference between an address, an immediate value and memory contents; between the LEA calculation and a
  memory read; between expressions evaluated at assembly time and operations executed by the CPU.
- OFFSET, SEG, PTR, TYPE, LENGTH/LENGTHOF, SIZE/SIZEOF and related operators, verifying versions and semantics.
- Declarations DB/DW/DD/DQ and others, DUP, EQU, uninitialized data, alignment, ORG, includes, public/external
  symbols and conditional assembly.
- CPU/mode/model/segmentation directives, PROC/ENDP, ASSUME, macros and structures in MASM/TASM, versus BITS,
  SECTION, GLOBAL, EXTERN, TIMES and the NASM preprocessor, according to confirmed support.

Analyze explicitly the difference between `mov ax, variable` and `mov ax, [variable]` across dialects; do not promise
universal equivalence. Include ambiguities by context and the resolution of project symbols.

## E. Interrupts, exceptions and services

Tell apart the INT instruction, a hardware interrupt, a processor exception and a BIOS/DOS service. Explain vectors,
handlers, the stack/saved state, returning with IRET, masking and differences per mode. Do not confuse a vector
number with the function or subfunction selected in registers.

Define an inventory per environment and version: architectural vectors, exceptions, BIOS/DOS services and the
included documented extensions. The 00h–FFh vector index must be able to mark reserved, environment-dependent,
undocumented or pending without inventing a service for each number. The number of vectors does not measure function
coverage.

Give special depth to INT 10h, 16h, 20h, 21h and, according to the documented scope, 13h, 1Ah, 2Fh, 33h and related
services. For each service: function/subfunction selector, inputs, outputs, registers/flags affected or preserved per
the contract, buffers, data format, errors, version, requirements and an example. Mark exactly what is implemented in
DosInterruptHandler and what requires another environment.

Treat the Windows API, the calling ABI and Linux syscalls separately. Do not present INT 21h as a general mechanism
for modern native applications, nor the syscall number as a universal property of x86. Do not promise IDE support for
every OS API: define the corpus and its dependencies.

## F. Labels, procedures and program organization

Explain local/global labels, scope, symbol resolution, jumps, procedures, CALL/RET, near/far calls where they apply,
parameter passing, return values, preserved registers, the stack, frames, recursion and calling conventions.

Tell apart user procedures, macros, library procedures, APIs and OS services. The Irvine32/Irvine16 routines are
library dependencies; do not classify them as CPU instructions or assume they come with IDEARM.

Compare the DOS, Windows x86, Windows x64 and System V AMD64 conventions only for relevant combinations. Cover
alignment, register preservation, stack cleanup, shadow space or red zone where they apply, with their conditions.
Tell apart the function ABI and the syscall ABI.

Explain source program, preprocessing, assembly, object, symbols/relocations, linking, executable, loading and
debugging. Relate COM/MZ/PE/ELF to the real support, without turning the COM format into a supported profile just
because a loader exists.

## G. Fundamentals and guided learning

Integrate number bases, two's complement, sign, overflow/carry, bits/bytes/words, ASCII/encoding, boolean logic,
masks, arithmetic, floating point when it applies and the instruction cycle. Use Stallings and Mano to explain
fundamentals with links to the code.

Propose progressive paths: reading an instruction; data/registers; memory/segments; conditions/loops;
stack/procedures; environment services; complete programs; debugging; advanced topics. Each path must have
prerequisites, objectives, examples, original exercises and explained solutions. Separate basic/intermediate/advanced
depth without hiding the exhaustive reference.
</phase_2_academic_content>

<phase_3_architecture_and_integration>
Propose an evolution compatible with the existing architecture. Avoid a general rewrite or unnecessary dependencies.
Justify what is kept, migrated, fixed and added.

1. Knowledge model: typed entities for instructions, forms, operands, registers, fields/flags, segments, concepts,
   syntax/directives, services, examples and sources. Relations through stable IDs, contextualized aliases and useful
   two-way links.
2. Compatibility context: ISA, mode, extensions, privileges, dialect/version, OS/ABI, toolchain and backend. Specify
   the rules and the handling of "unknown". Do not base compatibility only on an ordering of generations.
3. Separation between technical semantics and localized teaching text. Avoid copying semantic tables for each
   language. Tell apart the UI language, the content language and a missing translation.
4. Storage and distribution: compare staying in Java with structured resources and other reasonable alternatives.
   Recommend one according to volume, validation, editing, performance and dependency rules. If you choose
   JSON/YAML/Markdown, explain where it is loaded and validated without introducing I/O or forbidden libraries in the
   Domain. Do not assume a remote database.
5. A common catalog with views suited to reference, hover, completion, analysis and debugging. Avoid an academic
   entry about a non-runnable topic wrongly validating code. Define an explicit separation between documented
   knowledge and the capabilities of the parser/validator.
6. Editorial workflow: extracting or consulting sources, original writing, technical review, translation, example
   validation, versioning, migrations and updating references. Propose schemas, validators and coverage reports.
7. UI: turn the dictionary into a coherent academic center. Describe navigation by topic, cross-cutting search,
   contextual filters, a sheet index, related links, history and a way back to the editor. Keep useful existing
   behaviors.
8. Contextual help: querying mnemonics, registers, directives, operators, operands, labels and calls with the
   project's context. Respect the user's macros and symbols. Define what can be solved with the current
   lexer/AST/index and what would require extending them.
9. Teaching experience: an initial summary and expandable detail; diagrams of subregisters, memory/segments, the
   stack and before/after states when they add value. Do not describe a simulation as a real execution of the
   backend. Do not make a hover show a whole chapter.
10. Examples: offer to open or create a compatible project, assemble/debug and inspect states. Declare the toolchain,
    dependencies and backend. Consulting a sheet must not run code or overwrite the student's files.
11. Non-functional requirements: offline operation for the distributed content, search/load performance, package
    size, memory use, task cancellation, not blocking the JavaFX thread, keyboard, accessibility, light/dark themes
    and ES/EN. Propose measurable targets and how to measure them; do not present targets as current measurements.

"Academic assistant" does not necessarily mean a chatbot. The baseline solution must solve reference and teaching
with verifiable content. Only propose generative AI, RAG or external services if there is a demonstrated need; set
them apart as an option with their own cost, maintenance and validation.
</phase_3_architecture_and_integration>

<mandatory_acceptance_cases>
Use these scenarios to check that the design is sufficient. Specify context, expected content and validation;
mentioning them is not enough:

1. A student selects `mov ax, [bx+si+4]` in an 8086 context. The help identifies each part, size, address
   calculation, default segment, memory read, destination register and flags; it explains why other register
   combinations may be invalid.
2. The student looks up `DS:DX`, `DX:AX`, `label:` and a segment override. They get four contextualized
   explanations, without presenting the colon as universal concatenation or every notation as valid code.
3. They look up `mov ax, @data` and `mov ds, ax` in MASM/TASM. They understand the symbol, assembler/linker, segment
   address, initialization and limitations; the same example is not offered as universal NASM.
4. They look up `INT 21h` with AH=09h and with AH=0Ah. They navigate to different services, understand DS:DX and its
   buffer contracts, termination and I/O, and see the backend's availability. The outputs are taken from the
   documented contract, not from assumptions.
5. They compare MOV/LEA, CMP/TEST, MUL/IMUL and DIV/IDIV. They can tell apart reads/writes, sign, implicit operands,
   forms and flags. A division example shows the necessary preparation and error cases.
6. An arithmetic example tells CF from OF; another with INC/DEC shows the treatment of CF; a shift example tells
   effects apart by count. The results are checked and assertions about undefined flags are avoided.
7. A student walks through CALL/RET and observes the stack, return address, parameters and preserved registers. The
   material tells instructions apart from calling conventions and applies the chosen ABI.
8. They switch the project between DOS 8086, Windows x86/x64 and Linux x64. The content adapts compatibility,
   syntax and services without removing the possibility of consulting theory from other contexts. Check instructions
   unavailable in long mode and optional extensions that require more than "x86-64 CPU".
9. They search "dos puntos" (colon), "corchetes" (brackets), "acarreo" (carry), "carry", "segmento extra" (extra
   segment), "interrupción 21h" (interrupt 21h), "stack" and an alias. They get relevant, differentiated entries,
   keeping the meaning of punctuation when it is the query.
10. A user macro or label shares its name with an academic entry. The help resolves the context without
    automatically turning the symbol into an instruction or introducing false linter errors.
11. A documented instruction is not implemented in the emulator. The sheet says so, allows studying its semantics
    and offers a compatible environment only if it has been verified.
12. An example depends on Irvine32, an assembler version or a library the project does not have. The dependencies
    are explained and a relevant alternative is offered when there is one; it is not announced as immediately
    runnable.
</mandatory_acceptance_cases>

<phase_4_validation_and_execution_plan>
Define tests and reviews that prove correctness, not just the presence of fields or a high number of sheets:

- Corpus integrity: unique IDs, valid references/aliases, mandatory fields per type, sources, translation
  consistency, variants and no broken relations.
- Semantic accuracy: cases with expected effects checked against independent sources; do not use the same catalog
  or emulator as the only oracle of their own data.
- Compatibility: positive and negative tests per mode, extension, dialect, version and backend; false availability
  and false linter errors.
- Assemblable examples: validation with real toolchains where available, separating static tests, assembly, link,
  execution and debugging. Record "not run" when tools are missing; do not simulate evidence.
- Expected states of registers/memory/defined flags, with explicit initial conditions. Use differential tests when
  they add evidence and the environment allows it.
- Regression of the catalog, search, hover, completion, lexer/parser/linter, i18n and navigation; the corresponding
  architecture tests.
- A defined human or technical editorial review for claims that cannot be tested automatically. Avoid validating
  long texts with fragile snapshots as the only guarantee.
- Usability tests with the scenarios above, accessibility and performance measurement.

Look at the repository's real commands and profiles to propose the future validations. In this session clearly tell
apart the checks performed from the tests you are planning.

Break the implementation down into phases and work packages small enough for another engineer to carry out without
reinterpreting the intent. For each task include ID, goal, scope, affected existing paths/symbols, proposed new files,
dependencies, risk, a justified relative size, deliverable and acceptance criterion. Do not invent dates or exact
estimates without a basis.

Include: audit and fixes of the current catalog; model and compatibility; incremental migration; a first
representative vertical integration; systematic expansion until the corpus is closed; contextual navigation and
teaching material; final validation and documentation. Prioritizing 8086/DOS for its academic relevance is valid,
but it does not leave the existing 32/64-bit profiles without a plan.

Each phase must deliver a usable improvement and preserve compatibility or describe its migration. Define risks,
mitigations, discarded alternatives with reasons and decision points. Assign the unknowns to concrete research
tasks. A title such as "add all the remaining instructions" without an inventory, a generation/review method and
validation is not a sufficient task.
</phase_4_validation_and_execution_plan>

<deliverables>
Write the result in Spanish, with consistent technical terminology. Organize the plan with these sections:

1. Diagnosis of the current state with evidence from the code.
2. Scope and compatibility matrix; target corpus, coverage units and limits.
3. Gap matrix: requirement → current situation → change → phase → acceptance → source.
4. Complete content taxonomy and learning paths.
5. Proposed knowledge schemas and compatibility/context resolution.
6. Architecture, integration per module and migration strategy.
7. User flows and text sketches of navigation/sheets/contextual help.
8. Editorial strategy and sources, with what was really consulted and what remains pending.
9. Phased backlog with dependencies and closing criteria.
10. Validation plan, acceptance cases, risks and open decisions.
11. Recommended first implementation package and a verifiable definition of "done" for the complete goal.

Include written content samples to validate the model, without generating the complete encyclopedia yet: an
instruction with several forms, a register with subregisters, a segments sheet, a colon/brackets sheet, an INT 21h
function and a procedure with a stack/ABI. State which forms each sample covers and its sources. The plan must show
enough detail to assess the promised educational quality.

If the working mode allows writing documentation, save the main document in
docs/academic-assistant/plan-ampliacion.md (now `expansion-plan.md`) and the annexes that are really needed in that folder. Do not overwrite
existing files without reviewing them or modify the project's general plans for this task. If the planning mode
prevents writing files, deliver the complete document in the answer or in the plan file that mode allows.

If the volume requires several files or sessions, keep an index, decisions and explicit pending items; do not
silently cut requirements or claim the goal is complete when areas are missing. The final session summary must point
out where the plan is, the main decisions and the real limitations of the research.
</deliverables>

<final_review>
Before delivering, check that:

- You have planned both the deep improvement of the current dictionary and all the new requested areas.
- "All" can be checked against versioned sources and inventories, and every gap has a treatment.
- You do not confuse architecture, CPU generation, mode, extension, dialect, ABI, OS, toolchain and emulator.
- You do not take for granted that four segment registers mean only four possible segments.
- You do not confuse directives, macros, libraries, APIs, interrupts and CPU mnemonics.
- Every important claim tells apart evidence from the code, a documentary source, a proposal and an assumption.
- The integration protects the correctness of the editor/linter and respects the existing layers.
- Every phase has an observable result and concrete acceptance, and the total scope stays covered.
- You have preserved the existing changes and not started production implementation.

Start now by inspecting the repository and the accessible sources. Then deliver the complete, well-founded plan. Do
not answer only with a proposal of how you would make the plan.
</final_review>
