# Annex D — Knowledge schemas, architecture, integration and UI

> Part of [`expansion-plan.md`](expansion-plan.md). Covers phase 3 of the assignment and deliverables 5, 6 and 7.

---

## D.1 The ten entities and the identifier scheme

```
Instruction ──1:N── InstructionForm ──1:N── Operand
     │                    │
     │                    ├──1:N── FlagEffectSpec ──N:1── FlagField (of a Register)
     │                    ├──1:1── Requirement
     │                    └──1:N── Example
     ├──N:N── Instruction        (related[], with a relation type; the reverse is generated)
     ├──N:N── Register           (reads / writes; generated from Operand)
     └──1:N── Source

Register ──1:N── RegisterView          (AH, AL, EAX… with a bit offset)
    └────1:N── FlagField               (CF, ZF… for FLAGS/EFLAGS; C0–C3 for the x87 status word)

SyntaxItem   (sign, operator, directive, predefined symbol) — key (id, dialect, context)
Service ──1:N── ServiceFunction ──1:N── ServiceSubfunction
MemoryConcept  (effective address, segmentation, memory model, stack, object format…)
Example, Source
```

### Identifier scheme (a contract: changing them requires an equivalence table)

| Type | Pattern | Examples |
|---|---|---|
| Instruction | `x86.instr.<mnemonic>` | `x86.instr.imul`, `x86.instr.movsd.string`, `x86.instr.movsd.sse2` |
| Form | `<instruction id>#form.<operands>` | `x86.instr.imul#form.rm8`, `x86.instr.imul#form.r16-rm16-imm8` |
| Register | `x86.reg.<name>` | `x86.reg.ax`, `x86.reg.rflags`, `x86.reg.st0` |
| View | `x86.reg.<parent>#view.<name>` | `x86.reg.ax#view.al` |
| Flag or field | `x86.flag.<name>` | `x86.flag.cf`, `x86.flag.iopl`, `x87.status.c3` |
| Syntax | `syntax.<dialect>.<class>.<name>` | `syntax.masm.op.offset`, `syntax.nasm.pp.macro`, `syntax.common.colon.label` |
| Service | `<environment>.<vector>.<function>[.<subfunction>]` | `dos.int21.09h`, `dos.int21.0ah`, `bios.int10.0eh`, `cpu.exception.00h` |
| Concept | `concept.<name>` | `concept.effective-address`, `concept.real-mode-segmentation` |
| Source | `src.<work>.<edition>` | `src.irvine.5e-es`, `src.intel-sdm.093`, `src.nasm.3.02` |

Homonyms carry a discriminating suffix (`movsd.string` / `movsd.sse2`) and each one declares the other in
`homonyms[]` (A-14).

---

## D.2 Concrete schemas

The data is written in JSON, one file per entity type and family. A real, complete example of a form, with the
values the audit showed to be necessary:

```json
{
  "id": "x86.instr.imul",
  "kind": "INSTRUCTION",
  "mnemonic": "IMUL",
  "family": "F-02",
  "pedagogicalLevel": "INTERMEDIATE",
  "prerequisites": ["concept.signed-integers", "x86.instr.mul", "x86.reg.ax"],
  "aliases": [],
  "homonyms": [],
  "related": [
    { "id": "x86.instr.mul",  "relation": "CONTRAST" },
    { "id": "x86.instr.idiv", "relation": "PAIR" },
    { "id": "x86.instr.shl",  "relation": "ALTERNATIVE" }
  ],
  "forms": [
    {
      "id": "x86.instr.imul#form.rm16",
      "syntax": [
        { "dialect": "masm",  "version": "6.11", "text": "IMUL r/m16" },
        { "dialect": "tasm",  "version": "4.1",  "text": "IMUL r/m16" },
        { "dialect": "nasm",  "version": "3.02", "text": "imul r/m16" }
      ],
      "operands": [
        { "position": 1, "kind": "REG_MEM", "sizes": [16], "access": "READ",
          "role": "EXPLICIT", "defaultSegment": "DS" }
      ],
      "implicitOperands": [
        { "kind": "IMPLICIT_REG", "register": "x86.reg.ax", "sizes": [16], "access": "READ",
          "note": "multiplicand" },
        { "kind": "IMPLICIT_REG", "register": "x86.reg.dx", "sizes": [16], "access": "WRITE",
          "note": "high half of the product" },
        { "kind": "IMPLICIT_REG", "register": "x86.reg.ax", "sizes": [16], "access": "WRITE",
          "note": "low half of the product" }
      ],
      "requirement": {
        "minGeneration": "I8086",
        "features": [],
        "validModes":   ["REAL", "PROTECTED_16", "PROTECTED_32", "COMPATIBILITY", "LONG"],
        "invalidModes": [],
        "privilege": "ANY",
        "operandSizes": [16],
        "addressSizes": [16, 32, 64]
      },
      "flags": [
        { "flag": "x86.flag.cf", "effect": "MODIFIED",
          "condition": "set when the high half of the product is not the sign extension of the low half" },
        { "flag": "x86.flag.of", "effect": "MODIFIED", "condition": "same as CF" },
        { "flag": "x86.flag.sf", "effect": "UNDEFINED" },
        { "flag": "x86.flag.zf", "effect": "UNDEFINED" },
        { "flag": "x86.flag.af", "effect": "UNDEFINED" },
        { "flag": "x86.flag.pf", "effect": "UNDEFINED" }
      ],
      "operation": "DX:AX := AX * SignExtend(SRC)",
      "exceptions": [
        { "vector": "cpu.exception.0dh", "condition": "memory operand beyond the segment limit",
          "modes": ["PROTECTED_16", "PROTECTED_32"] }
      ],
      "availability": [
        { "backend": "emu8086", "status": "AVAILABLE" },
        { "backend": "gdb",     "status": "AVAILABLE" },
        { "backend": "external","status": "AVAILABLE" }
      ],
      "sources": ["src.irvine.5e-es#7.4.2:205", "src.intel-sdm.093#vol2:IMUL"]
    },
    {
      "id": "x86.instr.imul#form.r16-rm16-imm8",
      "syntax": [ { "dialect": "masm", "version": "6.11", "text": "IMUL r16, r/m16, imm8" } ],
      "operands": [
        { "position": 1, "kind": "REG",     "sizes": [16], "access": "WRITE",     "role": "EXPLICIT" },
        { "position": 2, "kind": "REG_MEM", "sizes": [16], "access": "READ",      "role": "EXPLICIT" },
        { "position": 3, "kind": "IMM",     "sizes": [8],  "access": "READ",      "role": "EXPLICIT",
          "extension": "SIGN" }
      ],
      "implicitOperands": [],
      "requirement": { "minGeneration": "I80186", "features": [],
        "validModes": ["REAL","PROTECTED_16","PROTECTED_32","COMPATIBILITY","LONG"],
        "invalidModes": [], "privilege": "ANY", "operandSizes": [16], "addressSizes": [16,32,64] },
      "flags": [
        { "flag": "x86.flag.cf", "effect": "MODIFIED",
          "condition": "set when the product does not fit in the destination (significant digits are lost)" },
        { "flag": "x86.flag.of", "effect": "MODIFIED", "condition": "same as CF" },
        { "flag": "x86.flag.sf", "effect": "UNDEFINED" },
        { "flag": "x86.flag.zf", "effect": "UNDEFINED" },
        { "flag": "x86.flag.af", "effect": "UNDEFINED" },
        { "flag": "x86.flag.pf", "effect": "UNDEFINED" }
      ],
      "operation": "DEST := TruncateToDestSize(SRC1 * SignExtend(SRC2))",
      "availability": [
        { "backend": "emu8086", "status": "UNAVAILABLE",
          "reason": "the emulator does not implement opcodes 69h/6Bh" },
        { "backend": "gdb", "status": "AVAILABLE" }
      ],
      "sources": ["src.irvine.5e-es#7.4.2:205-206", "src.intel-sdm.093#vol2:IMUL"]
    }
  ]
}
```

What this fragment solves and the current model cannot: a different requirement per form (8086 versus 80186),
flags with a condition, implicit operands with their role, per-backend availability with a reason, and one source
per claim.

### Register

```json
{
  "id": "x86.reg.ax", "name": "AX", "group": "G-1", "sizeBits": 16,
  "parent": "x86.reg.eax", "parentOffsetBits": 0,
  "views": [
    { "id": "x86.reg.ax#view.ah", "name": "AH", "sizeBits": 8, "offsetBits": 8 },
    { "id": "x86.reg.ax#view.al", "name": "AL", "sizeBits": 8, "offsetBits": 0 }
  ],
  "conventionalUse": "accumulator",
  "architecturalUse": [
    { "instruction": "x86.instr.mul",  "role": "multiplicand and low half of the product" },
    { "instruction": "x86.instr.div",  "role": "low part of the dividend and the quotient" },
    { "instruction": "x86.instr.xlat", "role": "index into the table and the result" },
    { "instruction": "x86.instr.in",   "role": "destination of the port read" }
  ],
  "writeSemantics": "writing AL or AH does not touch the other half, but does change the value of AX and EAX",
  "accessConstraints": [],
  "requirement": { "minGeneration": "I8086", "validModes": ["REAL","PROTECTED_16","PROTECTED_32","COMPATIBILITY","LONG"] },
  "exposedBy": ["emu8086", "gdb", "external"],
  "sources": ["src.irvine.5e-es#2.2.2:34"]
}
```

### Syntax item

```json
{
  "id": "syntax.common.colon.segoverride",
  "sign": ":", "class": "PUNCTUATION",
  "dialects": [
    { "dialect": "masm", "version": "6.11", "assemblable": true },
    { "dialect": "tasm", "version": "4.1",  "assemblable": true },
    { "dialect": "nasm", "version": "3.02", "assemblable": true }
  ],
  "contexts": ["OPERAND_MEMORY"],
  "notation": false,
  "contrastWith": ["syntax.common.colon.label", "syntax.common.colon.logicaladdress",
                   "syntax.common.colon.registerpair"],
  "sources": ["src.stallings.7e-es#11.2:416"]
}
```

### Service function

```json
{
  "id": "dos.int21.0ah", "environment": "DOS", "vector": "21h", "selector": { "register": "AH", "value": "0Ah" },
  "sinceVersion": "1.0",
  "inputs": [
    { "register": "AH", "value": "0Ah" },
    { "register": "DS:DX", "meaning": "address of the keyboard input structure" }
  ],
  "bufferFormat": [
    { "offset": 0, "size": 1, "direction": "IN",  "meaning": "maximum characters, the Enter key included" },
    { "offset": 1, "size": 1, "direction": "OUT", "meaning": "characters read, not counting Enter" },
    { "offset": 2, "size": "n", "direction": "OUT", "meaning": "the characters, terminated with 0Dh" }
  ],
  "outputs": [ { "target": "buffer", "meaning": "the structure is filled in" } ],
  "flagsAndRegisters": { "documented": "the contract promises neither to preserve nor to change the flags" },
  "errors": [],
  "availability": [
    { "backend": "emu8086", "status": "AVAILABLE",
      "caveat": "accepts one character more than the contract: checks length < maxInput instead of maxInput-1" },
    { "backend": "external", "status": "AVAILABLE" }
  ],
  "sources": ["src.irvine.5e-es#13.2.3:443"]
}
```

The `caveat` field is the plan's answer to the requirement "mark exactly what is implemented versus what another
environment requires": the divergence is documented, not hidden, and it also produces the emulator fix task.

---

## D.3 Compatibility resolution

### Context

```java
record CompatibilityContext(
        Isa isa,                       // X86
        CpuGeneration generation,      // I8086 … P6 … X86_64
        ProcessorMode mode,            // REAL, PROTECTED_16, PROTECTED_32, COMPATIBILITY, LONG
        Set<Feature> features,         // X87, MMX, SSE, SSE2, AVX…  (empty ≠ none: see below)
        Privilege privilege,           // CPL0, USER, UNKNOWN
        Dialect dialect, String dialectVersion,
        Platform platform, Abi abi,    // DOS / WINDOWS / LINUX ; DOS16, CDECL, STDCALL, WIN64, SYSV_AMD64
        String toolchainId,            // borland-tasm | microsoft-masm | nasm
        HostKind host,
        DebugBackend backend)          // EMU8086 | GDB | EXTERNAL | NONE
```

**It is built from the `TargetProfile`, not from a string** (fixes A-15):

| Context field | Origin |
|---|---|
| `isa` | `TargetProfile.architecture` |
| `generation` | `TargetProfile.cpuBaseline`, and if it is not recognized → `UNKNOWN`, **never 8086** |
| `mode` | `TargetProfile.processorMode` + `codeMode` (the field unused today: H-05) |
| `features` | declared in the project (a new, optional field) or `UNKNOWN` |
| `dialect`, `dialectVersion` | `ToolchainSelection.id` + the version resolved by `ToolRegistry` |
| `platform`, `abi` | `TargetProfile.platform` + a rule per profile |
| `host` | `NativeHost.kind()` |
| `backend` | `DebugConfiguration.backend` |

### Algorithm

```
resolve(requirement, context) -> Availability + reasons[] + confidence

1.  if context.generation == UNKNOWN or requirement.minGeneration == UNKNOWN  → UNKNOWN(gen-unknown)
2.  if context.generation < requirement.minGeneration                        → UNAVAILABLE(gen-insufficient)
3.  if requirement.invalidModes contains context.mode                        → UNAVAILABLE(mode-invalid)
4.  if requirement.validModes is not empty and does not contain context.mode → UNAVAILABLE(mode-not-valid)
5.  for each f in requirement.features:
        if context.features == UNKNOWN                                       → UNKNOWN(extension-unknown)
        if f is not in context.features                                      → UNAVAILABLE(extension-missing)
6.  if requirement.privilege == CPL0 and context.privilege == USER           → UNAVAILABLE(privilege)
7.  if requirement.operandSizes does not fit the mode                        → UNAVAILABLE(operand-size)
8.  if the form has no syntax for context.dialect+version                    → UNAVAILABLE(dialect)
9.  if availability[context.backend] == UNAVAILABLE                          → PARTIAL(backend) with a reason
10. in any other case                                                        → AVAILABLE
```

`confidence` is carried over from the source of the claim: `CERTAIN` if the source is the reference revision,
`LIKELY` if it is a secondary source, `UNVERIFIED` if the entry is at the outline level.

### The three rules that protect the editor

1. **`UNKNOWN` never turns into `AVAILABLE` or `UNAVAILABLE`.** An incomplete context produces silence, not a
   claim.
2. **The analyzer only emits a diagnostic with `UNAVAILABLE` + `CERTAIN`.** Everything else is information the
   sheet shows but that does not mark the code. This is what stops a growing corpus from inventing errors.
3. **`PARTIAL(backend)` is never a code error**: it is a note on the sheet ("this instruction exists on your CPU
   but the built-in emulator does not run it"), because the code is correct.

### How the cases of the assignment are resolved

| Case | Context | Result |
|---|---|---|
| `AAA` in a `win-pe64-console` project | `mode = LONG` | `UNAVAILABLE(mode-invalid)` → it does not appear in completion, the analyzer marks it, the sheet can still be consulted |
| `MOVAPS` in a `win-pe64-console` project with no declared extensions | `features = UNKNOWN` | `UNKNOWN` → **no diagnostic** (fixes A-18), the sheet warns that it requires SSE |
| `FSQRT` in `dos-exe-16` with the `emu8086` backend | `features` without `X87` declared | `UNKNOWN` for the requirement + `PARTIAL(emu8086)` with the reason of the missing opcode → acceptance case 11 |
| `IMUL ax, bx, 3` in `dos-exe-16`, `cpu = 8086` | three-operand form, `minGeneration = I80186` | `UNAVAILABLE(gen-insufficient)` + `CERTAIN` → a correct warning that does not exist today (A-17) |
| `syscall` in `win-pe64-console` | `platform = WINDOWS` | `UNAVAILABLE(platform)`: the instruction exists, the service does not |

---

## D.4 Semantics and prose kept apart

Layout of the resources inside the module:

```
idearm-language/src/main/resources/io/github/dinamo541/idearm/language/knowledge/
├── corpus.json                     index: corpus version, reference revision, list of files
├── semantic/
│   ├── instructions/f01-transfer.json … f18-simd.json
│   ├── registers/general.json, segment.json, flags.json, x87.json, system.json
│   ├── syntax/masm.json, tasm.json, nasm.json, common.json
│   ├── services/dos-int21.json, bios-int10.json, …, cpu-exceptions.json, vectors-index.json
│   ├── concepts/*.json
│   └── sources.json
└── text/
    ├── en/instructions.json, registers.json, syntax.json, services.json, concepts.json, examples.json
    └── es/…  (same keys)
```

A text file is a flat map `"<id>.<field>": "<text>"`:

```json
{
  "x86.instr.imul.summary": "Signed integer multiplication",
  "x86.instr.imul.description": "Multiplies … ",
  "x86.instr.imul#form.rm16.operationPlain": "Takes AX and multiplies it by the operand …",
  "x86.instr.imul#form.rm16.flags.cf.condition": "set when the high half …"
}
```

Rules **[proposal]**:

- No semantic table is duplicated per language. A flag condition exists once as a structure and once per language
  as a sentence.
- **Three different things, three mechanisms:** the **UI language** is the `ResourceBundle` of `idearm-app`
  (ADR-006); the **content language** is the file under `text/`; a **missing translation** is a key missing from
  `text/es`, and the sheet points it out with a visible mark ("not translated; the English original is shown")
  instead of silently mixing languages.
- The integrity test requires parity of the **mandatory keys** (summary and description) in both languages, and
  lets long fields (prose pseudocode, notes) be missing in Spanish with the mark on. That way the corpus can grow
  without being blocked by translation, which is what ADR-006 identifies as its main cost.

---

## D.5 Storage, loading and respecting ADR-007

### Why data resources and not Java code

| Criterion | Hand-written Java (today) | JSON in resources (proposal) |
|---|---|---|
| Technical ceiling | **9406 B of 65535 in `<clinit>` with 214 entries** (A-27) | none that matters |
| Change review | code diff; a comma mistake is caught at compile time | data diff; a syntax error is caught by the load test |
| Editing by non-programmers | impossible | possible |
| Validation | types only | a full schema in tests (fields, references, uniqueness, sources) |
| Startup cost | zero | measurable; mitigated by lazy loading off the JavaFX thread |
| New dependencies | none | **none**: our own reader |

### ADR-007 compliance

| Rule | How it is met |
|---|---|
| L3 uses only the JDK | the JSON reader is our own code over `String`/`char[]`; no Jackson, no SnakeYAML |
| No `java.nio.file.Files` in L3 | resources are read with `Class.getResourceAsStream`, which is not file-system I/O and which `ArchitectureTest` does not forbid **[code: the test names `java.nio.file.Files` and `java.lang.ProcessBuilder`]** |
| No `ProcessBuilder` in L3 | no process is started |
| JPMS | the resources live in the module's own package, so they are reachable without `opens` |
| Enforcer | no dependency is added, so there is nothing to forbid |

It is proposed to **add a new rule** to `ArchitectureTest` **[proposal]**: the `knowledge` package cannot depend on
`linter`, `parser` or `lexer` (the dependency only goes the other way). That way the corpus never "knows" how the
code is analyzed, which is the condition for the views to work.

### Loading

```java
// Lazy, thread-safe, never blocking on the JavaFX thread.
final class Corpus {
    private static final class Holder { static final Corpus INSTANCE = load(); }
    static Corpus get() { return Holder.INSTANCE; }
    static void warmUp() { Thread.ofVirtual().start(Corpus::get); }   // called from WorkbenchBootstrap
}
```

And the field that forces initialization when the class loads today is fixed: `AssemblyLexer.INSTRUCTIONS` goes
from a `static final Set<String>` initialized in its declaration **[code: `AssemblyLexer.java:43`]** to a query
of the lazy holder.

Measurable targets **[proposal]**, measured with tagged tests and **not** presented as facts until measured:
loading the whole corpus ≤ 150 ms; resolving a mnemonic ≤ 5 ms; a cross-cutting search ≤ 50 ms over the closed
corpus; hover ≤ 30 ms; resident corpus memory ≤ 40 MB; size added to the distributable package ≤ 8 MB.

---

## D.6 Integration per module

| Module | What changes | What is added | What does **not** change |
|---|---|---|---|
| `idearm-domain` (L3) | nothing in the project model | optional: a `features` field in `TargetSelection` to declare extensions (AA-P2-04) | `TargetProfile`, `TargetProfileCatalog`, ports |
| `idearm-language` (L3) | `catalog` becomes a facade; the lexer becomes parameterized by dialect; `CpuBaselineRule` and `UnknownInstructionRule` query `AnalysisView` | a `knowledge` package (model, reader, views, compatibility resolution) + the corpus resources + an operand parser (`OperandParser`) | `lexer`, `parser`, `index` keep their public API |
| `idearm-application` (L2) | `QueryHover` reverses its resolution order; `QueryCompletion` stops hard-coding registers and directives | `QueryExplain` (help by position), `QueryKnowledge` (cross-cutting search and navigation of the center) | `QueryDefinition`, `QueryReferences`, `QueryOutline`, `LintSource` (except how the rules are built) |
| `idearm-app` (L1) | `MnemonicsDictionaryDialog` evolves into `AcademicCenterView`; `MnemonicsDictionaryViewModel` becomes a generic `KnowledgeViewModel`; `HoverCardPopup` accepts collapsible sections; `CompletionPopup` shows the right language | sheet views per entity type, history, deep links, new i18n keys | the localization system, the theme, the non-modal window, the existing shortcuts |
| `idearm-emu8086` (L4) | nothing mandatory | a table of the implemented opcodes, exposed so a test can compare it with `availability.emu8086` | the emulator; extending it is a separate decision (D-AA-11) |
| `idearm-cli` | nothing mandatory | optional: a `kb validate` / `kb coverage` command for the coverage report | |
| `idearm-infrastructure`, toolchains | nothing | | |

**What the architecture forbids and the plan respects:** Presentation does not call the domain directly (the
academic center consumes `QueryKnowledge`, an L2 use case); user messages still travel as a code and arguments, not
as sentences (ADR-006 §6 and its addendum); no layer below Presentation formats UI text.

---

## D.7 Views over the corpus and the analyzer boundary

| View | Consumer | What it exposes | What it does **not** expose |
|---|---|---|---|
| `ReferenceView` | academic center | everything: complete sheets, sources, examples, paths | — |
| `HoverView` | editor hover | summary, what it does, the resolved form, the flags of that form, one link | long prose, pseudocode, exception lists |
| `CompletionView` | completion | candidates filtered by context, with text in the UI language | entries that are `UNAVAILABLE` in that context |
| `AnalysisView` | lexer, highlighter, analyzer rules | **only** entries whose `recognizedBy` includes the project's assembler, with a `CERTAIN` requirement | everything academic: outlines, mnemonics of other dialects, concepts |
| `DebugView` | debugger panel | register/flag ↔ live state mapping, and what each backend exposes | registers no backend exposes (it marks them as theoretical) |

**The boundary, in one test:** `adding an outline-level entry does not change
AnalysisView.recognizedMnemonics(dialect)`. And its converse: `every entry with a non-empty recognizedBy appears in
AnalysisView`. These two tests are the contract that keeps a growing corpus from breaking the editor, which is the
central concern of the assignment at this point.

Consumption map before and after:

```
BEFORE                                   AFTER
InstructionCatalog.knownMnemonics()      AnalysisView.recognizedMnemonics(dialect)
  ← AssemblyLexer.INSTRUCTIONS             ← AssemblyLexer (parameterized)
  ← UnknownInstructionRule                 ← UnknownInstructionRule
  ← AssemblySyntaxHighlighter (indirect)   ← AssemblySyntaxHighlighter (indirect)
InstructionCatalog.getForCpu(String)     CompletionView.candidates(prefix, context)
  ← QueryCompletion                        ← QueryCompletion
InstructionCatalog.find(String)          HoverView.resolve(token, context)  /  AnalysisView.form(...)
  ← QueryHover  ← CpuBaselineRule          ← QueryHover   ← CpuBaselineRule
InstructionCatalog.getAll()              ReferenceView.browse(topic, filters)
  ← MnemonicsDictionaryViewModel           ← KnowledgeViewModel
```

---

## D.8 Migration in four cuts

Each cut leaves the suite green and the product usable. None changes visible behavior except for the fixes it is
after.

| Cut | What is done | How it is shown not to break anything |
|---|---|---|
| **M1 — data without a model change** | dump the 214 entries and their 52 aliases to JSON; `InstructionCatalog` keeps its exact API and reads from the corpus | a frozen dump (`expected-catalog.txt`) generated with `Inventory` **before** the change, and a test that generates it again afterwards and compares byte by byte |
| **M2 — enriched model behind a facade** | add `InstructionForm`, `Operand`, `Requirement`, `FlagEffectSpec`, `Register`, `SyntaxItem`, `Service`; the facade keeps returning an `InstructionInfo` synthesized from the first form | the existing tests (22 for the catalog, those of the lexer, the analyzer, hover and completion) keep passing untouched |
| **M3 — consumers move to the views** | move the lexer, rules, hover and completion to the views; fix A-18, A-20, A-22 | new tests per fix + the two boundary tests of §D.7 |
| **M4 — retire the facade** | `catalog` marked `@Deprecated` and then removed; `InstructionInfo` ceases to exist | no `import` of `catalog` outside the package itself; an architecture test pins it |

Outward compatibility: the project format (`idearm.toml`) does not change in M1–M3. If the (optional) `features`
field is added, an old project still opens and the missing field means `UNKNOWN`, which by rule 1 of §D.3 produces
silence, not errors.

---

## D.9 Academic center: navigation

```
┌─ Academic Center — IDEARM ────────────────────────────────── [EN ▾] ─ ✕ ┐
│ ◀ ▶  ⌂   [ Search all the material…                         ] (Ctrl+K) │
│      Filters: [Profile: dos-exe-16 ▾] [Dialect: TASM 4.1 ▾]            │
│               [Mode: real ▾] [Level: all ▾]     ⟲ Reset                │
├──────────────────────┬─────────────────────────────────────────────────┤
│ 1 Fundamentals       │  IMUL                    [Arithmetic] [8086+]   │
│ 2 The machine        │  Signed integer multiplication                  │
│   2.1 Registers      │  ┌ In this project ──────────────────────────┐  │
│   2.2 Flags          │  │ One-operand form ......... available      │  │
│   2.3 Memory         │  │ Two/three-operand forms .. require 186    │  │
│   2.4 Stack          │  │ Built-in emulator ........ one operand    │  │
│ 3 The language       │  └──────────────────────────────────────────┘  │
│   3.2 Signs ◀────────│  In this sheet:                                 │
│ 4 Instructions       │   · What it does      · Forms (3)               │
│   F-01 Transfer      │   · Operands          · Flags per form          │
│   F-02 Arithmetic ◀──│   · Examples (4)      · Common mistakes         │
│     ADD SUB MUL IMUL │   · Related           · Sources                 │
│ 5 The environment    │                                                 │
│ 6 From code…         │  [ Back to the editor ]  [ Open example ]       │
│ 7 Paths              │                                                 │
└──────────────────────┴─────────────────────────────────────────────────┘
```

Kept from the current dialog **[code]**: a non-modal window, accent-insensitive search, chips that filter when
clicked, back/forward with `Alt+←`/`Alt+→`, copying an example with a temporary notice, switching language on the
fly. Added: a topic tree, context facets, a sheet index, history (`◀ ▶`), home (`⌂`), deep links
(`idearm://kb/x86.instr.imul#form.r16-rm16-imm8`) and a way back to the editor.

**Cross-cutting search.** A single field queries every entity and returns results **grouped and told apart by
type**, which is what case 9 requires:

```
Search: "colon"
  SIGNS (4)
    :  label definition              — "loop_top:" · assemblable syntax
    :  segment override              — "ES:[DI]" · assemblable syntax
    :  logical address               — "CS:IP" · notation, not code
    :  register pair                 — "DX:AX" · notation, not code
  CONCEPTS (2)
    Logical address segment:offset
    Labels and scope
```

The search index includes: names, aliases, summaries, descriptions, **the examples** (not indexed today: A-24), the
signs as text **and their names in both languages** ("dos puntos", "colon", "corchetes", "brackets", "acarreo",
"carry", "segmento extra", "extra segment"), and synonyms declared per entry.

---

## D.10 Sketch of the sheet and the hover

**Hover** (at most 12 visible lines; the rest collapsed):

```
┌────────────────────────────────────────────────┐
│ IMUL — Signed multiplication       [8086+] ⓘ  │
│ imul bx        DX:AX := AX * BX (signed)       │
│ Flags: CF OF from the result · SF ZF AF       │
│        PF are left undefined                  │
│ ▸ Implicit operands (AX, DX)                  │
│ ▸ Two more forms (186+)                       │
│ Open full sheet  ·  Example                   │
└────────────────────────────────────────────────┘
```

**Full sheet** (fixed order, with a side index): header with context chips → What it does → Forms (one tab or block
per form, with syntax per dialect) → Operands (a table with explicit and implicit ones, access and size) → Flags of
**that** form, with the condition written out → Additional effects (stack, flow, status word) → Requirements and
availability (generation, mode, extension, privilege, and one row per backend) → Operation (pseudocode and its
prose version) → Progressive examples with an initial and final state and a verification mark → Common mistakes
and counterexamples → Related → Sources.

**What the sheet does not do:** it does not show a chapter in the hover; it does not describe a simulation as a real
execution (an example with `verification = NOT_RUN` says so); it does not promise availability that has not been
verified.

---

## D.11 Contextual help: what is possible today and what must be extended

### Real state of the analysis **[code]**

| Capability | Today | Enough for |
|---|---|---|
| Classify a token (instruction, register, directive, number, string, identifier, `:`, `[`, `]`, `,`, `+`, `-`, `*`, `/`, `=`, `?`) | yes, `AssemblyLexer` | knowing **which kind** of help to show |
| Exact position (line, column, length) | yes, `Token` | anchoring the help |
| Line structure | partial, `AssemblyParser`: label, segment directive, `SEGMENT`/`ENDS`, `PROC`/`ENDP`, `MACRO`, `EQU`/`=`, data, instruction, unknown | knowing whether the token is in instruction or operand position |
| Operands | **no**: `extractOperands` returns **strings** | nothing: they have to be parsed |
| Project symbols | yes, `ProjectSymbolIndex` with five classes and a location | solving case 10 |
| Dialect | **no**: the lexer mixes dialects | telling NASM `mov ax, var` from MASM's |
| Preprocessor | **no**: `%` is dropped | NASM |
| Structures, `IF`/`ELSE`, `STRUC` | **no** | a later extension |

### What has to be added

1. **`OperandParser`** (new, in `idearm-language`): from an operand string to a tree
   `Operand → {SegmentOverride?, SizePrefix?, MemoryExpression | Register | Immediate | SymbolRef}` with
   `MemoryExpression → {base?, index?, scale?, displacement?}` and the position of each part within the line. It is
   the enabler of cases 1 and 2.
2. **`QueryExplain(filePath, line, column, context)`** (new, in L2): returns a tree of `Explanation` (token, the
   operand that contains it, the statement) so the UI shows the explanation at the level the student asked for.
3. **A lexer per dialect**: `AssemblyLexer(Dialect)` with the sets `AnalysisView` hands it.
4. **A new resolution order**: project symbol → register → instruction/alias → directive/operator of the dialect →
   numeric literal **in its context** (if the literal is the operand of `INT`, the help is the service, not the base
   conversion: fixes A-21) → punctuation sign in its context.
5. **Respect for the user's macros**: if the index has a definition, it wins, and the homonymous instruction is
   offered as a secondary card "there is also an instruction called X" (A-20).

### Case 1, solved step by step

Selecting `mov ax, [bx+si+4]` in a `dos-exe-16` project:

```
mov            instruction · transfer · 8086 · modifies no flags
ax             operand 1 · 16-bit register · destination · written in full
,              operand separator; in Intel syntax the destination comes first
[bx+si+4]      operand 2 · memory · read · 16-bit size (set by AX)
  [ ]          access to the memory contents
  bx           base  (one of only two valid bases with a 16-bit address)
  si           index (one of only two valid indexes)
  4            displacement, added at assembly time
  segment      DS by default (it would be SS if the base were BP)
  address      effective = BX + SI + 4 ; physical = DS×16 + effective
Why [bx+bp] is not valid: two bases. Why [ax] is not valid: AX cannot be a base with a
16-bit address. With a 32-bit address both would be valid.
```

---

## D.12 Non-functional requirements

| Requirement | Target **[proposal]** | How it is measured |
|---|---|---|
| Offline | all distributed content works without a network | a test that loads the corpus with the network disabled; no URL is resolved at run time (sources are text, not links that are followed) |
| Loading | ≤ 150 ms for the whole corpus | tagged performance test, average of 10 runs on the declared development machine |
| Search | ≤ 50 ms for a query over the closed corpus | same |
| Hover | ≤ 30 ms from request to content | same |
| Memory | ≤ 40 MB resident for the corpus | measured with `Runtime` in the load test |
| Package | ≤ 8 MB added to the distributable | comparing the artifact size before and after |
| JavaFX thread | no corpus operation on the UI thread | an extended `PresentationArchitectureTest` + a review of the warm-up |
| Cancellation | every search and every load can be cancelled | the pattern already exists in `LiveLintCoordinator` **[code]** |
| Keyboard | the center can be walked through entirely without a mouse | usability script |
| Accessibility | contrast verified in the light and dark themes; scalable text | review with the project's themes (ADR-012) |
| ES/EN | parity of the mandatory keys; a missing translation is visible | a parity test extended to the corpus |

---

## D.13 Generative AI, RAG and external services

"Academic assistant" does **not** imply a chatbot, and the baseline solution of this plan does not use one:
reference and teaching are solved with verifiable, versioned and citable content, which is exactly what a
generated answer does not offer.

It is recorded as a **separate option, not recommended for the baseline version** **[proposal]**, with its cost
made explicit:

| Aspect | Consequence |
|---|---|
| Demonstrated need | none of the twelve acceptance situations of the assignment requires it; all are solved with the corpus and analysis |
| Offline requirement | an external service breaks it; a local model multiplies the size of the distributable |
| Verifiability | a generated answer has no checkable source, and this plan's closing criterion requires a source per claim |
| Maintenance cost | keys, quotas, model versions, privacy of the student's data |
| Where it could help | rephrasing an explanation that is already written at the student's level, or generating exercise variants **from the corpus**, always marked as generated and never as a source |

If it is ever adopted, the condition is that the corpus remains the source and generation only rewrites what the
corpus already states, with a visible mark and the original one click away.
