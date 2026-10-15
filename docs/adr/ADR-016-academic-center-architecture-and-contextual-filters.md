# ADR-016: Academic Center Unified Filters, Architecture Bitness, and Contextual Navigation

Date: 2026-10-04. Status: accepted. Extends ADR-013 and ADR-011.

## Context

Prior to this decision, the Academic Assistant ("Centro Académico" / `AcademicCenterView` / `KnowledgeViewModel`) exhibited several usability and architectural disconnects:
1. **Mnemonic-Centric Filtering:** While instructions had family filters, search filters did not uniformly adapt to other knowledge entities. Registers lacked architecture bitness filtering (16-bit `AX`, 32-bit `EAX`, 64-bit `RAX`) and register-group filtering (General Purpose, Segment, Index/Pointer, Flags, Extended, Instruction Pointer). Syntax items lacked class filters (Directives, Operators, Symbols, Segments).
2. **Missing Architecture Bitness Dimension:** Assembly students working on 16-bit DOS real-mode programs (8086) frequently encountered 32-bit or 64-bit registers and instructions without clear indication or the ability to filter the assistant strictly to 16-bit concepts. Conversely, students exploring modern NASM 64-bit targets had no way to isolate 64-bit architectural entities.
3. **Disconnected Transversal Search:** The transversal search in `QueryKnowledge` only searched unconstrained instruction names and descriptions, omitting unified bitness filtering across registers, directives, and interrupt services.
4. **Lack of Contextual IDE Integration:**
   - The editor (`RichTextFxEditorComponent`) lacked a standard right-click context menu and dedicated hotkey to inspect symbols in the Academic Center.
   - The Problems panel (`BottomPanelView`) listed diagnostic errors and CPU-baseline warnings, but students had no direct mechanism to jump from a diagnostic warning into the Academic Center documentation.
   - Opening the Academic Center from an active project did not automatically synchronize with the active project's target architecture bitness (e.g. 16-bit for `dos-exe-16`, 64-bit for `win-pe64-console`).
5. **Linter Baseline Gaps:** While `CpuBaselineRule` warned against 32-bit registers (`EAX`, `FS`, etc.) in 8086 projects, it did not detect 64-bit registers (`RAX`..`R15`, `R8D`..`R15D`, `R8W`..`R15W`, `R8B`..`R15B`, `RIP`) when the project CPU target was below `x86-64`.

## Decision

### 1. Domain Model: Architecture Bitness (`idearm-language`)
- Created `ArchitectureBitness` enum (`ALL`, `BITS_16`, `BITS_32`, `BITS_64`) with helper `fromBits(int bits)`.
- Associated instructions, registers, and syntax items with explicit bitness metadata:
  - Instructions: Determined by instruction forms and operand constraints (e.g. forms with 64-bit operands require 64-bit; real-mode-only instructions require 16-bit).
  - Registers: Mapped via register size in bits (`16` -> `BITS_16`, `32` -> `BITS_32`, `64` -> `BITS_64`, `8` -> `BITS_16`/baseline).
  - Syntax items: Categorized by applicable target modes and dialect constraints.
- Extended `CpuBaselineRule` to detect 64-bit registers (`REGS_64BIT`) and emit warning diagnostic `lint.cpu-baseline-register64` when targeted CPU baseline is below `CPU_X86_64` (level 7).

### 2. Application Layer: Transversal Filtering (`idearm-application`)
- Extended `QueryKnowledge.search()` to accept `ArchitectureBitness` and `Dialect` parameters.
- Unified search scans across instructions, registers, syntax items, and interrupt services, enforcing bitness and dialect compatibility before aggregating results into pedagogical search matches.

### 3. Presentation ViewModels & Reactive State (`idearm-app`)
- Enhanced `KnowledgeViewModel` with reactive JavaFX properties:
  - `selectedBitnessProperty()` (`ArchitectureBitness`)
  - `selectedRegisterGroupProperty()` (`RegisterGroup` / `ALL`)
  - `selectedSyntaxClassProperty()` (`SyntaxClass` / `ALL`)
- Integrated multi-predicate filtering into `filteredInstructions`, `filteredRegisters`, and `filteredSyntaxItems` lists so that text search, family/class filter, and bitness filter compose reactively and atomically.
- Added `navigateTo(String idOrUri)` allowing deep-linking across any catalog entity.
- Extended `WorkbenchViewModel` with `getActiveProjectBitness()` to extract active target bitness from `currentProject.get().target().profile()` via `TargetProfileCatalog`.

### 4. Smart Filter Retention in Academic Center View (`idearm-app`)
- Added `setTargetBitness(ArchitectureBitness)` to `AcademicCenterView`, binding the bitness filter combo box.
- Implemented smart filter retention: `selectEntity`, `selectMnemonic`, `selectRegister`, and `selectSyntaxItem` inspect whether the requested entity is present in the current filtered list. If already visible under the active filters, existing filters are preserved. If the target entity is filtered out, filters are cleanly reset to ensure the item is visible and selected.

### 5. Contextual IDE Navigation & Deep-Linking
- **Editor Context Menu & Shortcuts:**
  - Extended `EditorComponent` with `setOnAcademicCenterRequested(Consumer<String>)`.
  - Added right-click context menu in `RichTextFxEditorComponent` with "Show in Academic Center" (`Shift+F1`), "Go to Definition" (`F12`), "Find References" (`Shift+F12`), and standard Cut/Copy/Paste actions.
  - Bound `Shift+F1` keyboard shortcut to inspect the word at caret and open the Academic Center.
- **Problems Panel Context Menu:**
  - Extended `BottomPanelView` with row-level context menu in the problems table ("Show in Academic Center").
  - Extracted mnemonic or register tokens from `Diagnostic.arguments()` to seamlessly navigate to the relevant entity.
- **Workbench Integration:**
  - `WorkbenchView` inspects the active word at caret when F1 or Shift+F1 is pressed.
  - Automatically queries `workbenchViewModel.getActiveProjectBitness()` and applies the project bitness filter when launching the Academic Center dialog.

### 6. Internationalization (i18n)
- Added localized messages to `messages_en.properties` and `messages_es.properties`:
  - `diagnostic.lint.cpu-baseline-register64`
  - `academic.bitness.all`, `academic.bitness.16`, `academic.bitness.32`, `academic.bitness.64`
  - `editor.context.showInAcademicCenter`, `editor.context.goToDefinition`, `editor.context.findReferences`
  - `problems.context.showInAcademicCenter`
- Verified 100% key parity and format correctness via `MessageBundlesTest`.

## Consequences

- Students can isolate their learning experience to 16-bit real mode, 32-bit protected mode, or 64-bit long mode with a single dropdown, eliminating confusion caused by modern or legacy instructions.
- All knowledge tabs (Instructions, Registers, Directives/Syntax, Search) respond consistently to bitness and classification filters.
- Students encountering a compilation or linter error can right-click the problem row or press `Shift+F1` on any code symbol in the editor to immediately view detailed pedagogical cards, register layouts, flag effects, and caveats.
- Full offline test suite (all 10 Maven reactor modules, 932+ tests) passes green with zero regressions.
