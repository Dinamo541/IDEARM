# IDEARM Visual Debugger Review & Architecture Improvements

**Date:** 2026-10-14  
**Author:** Antigravity (Collaborative Pairing with Codex)  
**Status:** Implemented in `idearm-app` presentation layer; unit layout/interaction suite passed via isolated runner; ready for Codex integration and desktop verification.

---

## 1. Executive Summary

This review documents the focused visual and structural modernization of the IDEARM visual debugger (`DebuggerPanelView`), its scoped stylesheets in `workbench.css`, and its automated verification suite in `DebuggerPanelViewTest.java`. In accordance with the assembly IDE parity plan (`docs/assembly-ide-parity-plan.md`) and collaborative task instructions, the debugger panel was reorganized from an inflexible 3-column configuration into a **two-panel resizable layout**:
- **Left Panel (Sidebar):** CPU registers table, single active debug control strip, condition code flags (CF, ZF, SF, OF, PF, AF, IF, DF), and instructions executed counter. Sized with a minimum width of 300px.
- **Right Panel (Inspection Area):** An expandable, full-width `TabPane` providing tabbed access to all five debugger views: **Disassembly**, **Memory Dump**, **Call Stack**, **Breakpoints**, and **Watches**. Sized with a minimum width of 320px.

The redesign eliminates the rigid outer `ScrollPane` that previously imposed tool-wide horizontal scrollbars whenever panel width dipped below 880px. Data columns and cell factories now support full 16-character hexadecimal values and 20-character unsigned decimal values (with bold styling on mutation and value tooltips on data cells for accessibility). All session capabilities, capability gating, external notice handling, register documentation hovers, clipboard copy actions, memory address inputs, watches, and breakpoint management are preserved, along with the restoration of informative domain comments.

---

## 2. Observations & Root Cause Analysis

### 2.1 The Baseline Geometry at 1280x800
On a standard 1280x800 workbench display:
1. **Available Workbench Width:** The workbench allocates ~48px for the activity rail and ~260px for the project explorer, leaving approximately 960px for the editor area and bottom panel.
2. **The 3-Column Layout Overload:** The previous layout partitioned this ~960px into three horizontal columns in a `SplitPane` with divider positions `(0.44, 0.74)`:
   - Registers Column: ~422px nominal (0.44)
   - Center TabPane (Memory, Stack, Disassembly): ~288px nominal (0.30)
   - Right TabPane (Breakpoints, Watches): ~250px nominal (0.26)
3. **Outer ScrollPane Fallback:** To cope with narrow widths, the earlier implementation gave the entire `SplitPane` a `minWidth(880)` and wrapped it in an outer `ScrollPane`. Whenever the bottom panel dipped below 880px (e.g. when expanding the sidebar or resizing the window), a persistent horizontal scrollbar was injected across the entire debugger tool.

### 2.2 Register Header Truncation
- In `createRegistersBox()`, the `TableView` had `-fx-font-family: 'Consolas'` applied globally to the table.
- Column headers inherited this monospaced font, combined with default cell padding.
- With `nameCol.setPrefWidth(75)`, the text "Register" (English) or "Registro" (Spanish) required 8 characters in Consolas (~63px) plus 20px padding = 83px, causing column headers to truncate with an ellipsis (`Regis...`).

### 2.3 Disassembly Tab Accessibility
- In the earlier center TabPane, Disassembly was placed **third** (`memoryTab`, `stackTab`, `disasmTab`).
- In Spanish, tab titles required substantial width:
  - `VOLCADO DE MEMORIA` (18 chars)
  - `PILA DE LLAMADAS` (16 chars)
  - `DESENSAMBLADO` (13 chars)
- In the allocated ~288px column, the third tab (`DESENSAMBLADO`) overflowed into an overflow dropdown menu button. A student stepping through assembly instructions could not immediately view the disassembly line without clicking through an overflow menu.

### 2.4 Hex Dump and Inspection Cramping
- A standard 16-byte memory hex dump line (`076C:0100  48 65 6C 6C 6F 2C 20 57  6F 72 6C 64 21 0D 0A 24  Hello, World!..$`) spans 75 monospaced characters, requiring approximately 540px of width in Consolas 12px.
- Placing the memory dump into a ~288px center column forced horizontal wrapping or per-line scrolling on every memory line.
- Breakpoints and Watches simultaneously consumed an entire ~250px column permanently, even when idle.

---

## 3. Implementation Decisions & Architecture

### 3.1 Two-Panel Resizable Layout
The debugger panel is structured into two resizable panes within a horizontal `SplitPane`:
```
+--------------------------------+-------------------------------------------------------------+
| REGISTERS (Sidebar ~384px)     | INSPECTION TABS (Expandable Area ~576px)                    |
| - Controls Strip (7 action btns) [Disassembly] [Memory Dump] [Call Stack] [Breakpoints] [Wat]|
| - Register Table               |                                                             |
|   (Reg, Hex, Dec, Bin)         | (Active tab receives full inspection width;                 |
| - Condition Flags (8 badges)   |  Hex dump fits without wrapping;                            |
| - Instructions Executed        |  Disassembly lines fully visible;                           |
|                                |  Breakpoints & Watches have ample space)                    |
+--------------------------------+-------------------------------------------------------------+
                                 <--- Resizable Divider (0.40) --->
```

1. **Divider Proportions & Constraints:**
   - Default divider position set to `0.40`, allocating ~384px to registers and ~576px to inspection tabs at 960px panel width.
   - Left Registers Box minimum width: `300px`.
   - Right Inspection TabPane minimum width: `320px`.
   - `SplitPane.setResizableWithParent(..., true)` applied to both panes.
2. **Direct StackPane Hosting:** Removed the outer `ScrollPane` and the hardcoded `880px` min-width. The `SplitPane` fits directly inside `DebuggerPanelView`, adapting to varying workbench widths without injecting a tool-wide horizontal scrollbar.
3. **External Debugger Notice:** Reactive visibility binding retained: when an external launch-only session is active (e.g., Turbo Debugger or CodeView in DOSBox), the split pane is hidden and replaced by a centered, localized explanatory notice.

### 3.2 Register Header & Table Proportions
1. **Header Typography:** Scoped `.debug-register-table` in `workbench.css` ensures column headers use the workbench sans-serif typography (`Segoe UI` 11px) with compact padding (`4 6`).
2. **Column Width Metrics:**
   - `nameCol`: `minWidth(72)`, `prefWidth(82)`. In Segoe UI at 11px, "Register" and "Registro" occupy ~44px + 12px padding = 56px, fitting comfortably in both English and Spanish.
   - `hexCol`: In 16-bit DOS mode: `minWidth(54)`, `prefWidth(64)`. In 64-bit native mode: `minWidth(130)`, `prefWidth(150)`. 16 hex digits (`FFFFFFFFFFFFFFFF`) span ~115px + 12px padding = ~127px, fitting within the 130px minimum width and 150px preferred width.
   - `decCol`: In 16-bit DOS mode: `minWidth(56)`, `prefWidth(68)`. In 64-bit native mode: `minWidth(150)`, `prefWidth(170)`. 20 unsigned decimal digits (`18446744073709551615`) span ~144px + 12px padding = ~156px, fitting within the 150-170px width.
   - `binCol`: `minWidth(135)`, `prefWidth(150)`. Visible in 16-bit DOS mode, automatically hidden in 64-bit native mode (`viewModel.nativeSessionProperty().not()`).
3. **Table Resize Policy:** `TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN` distributes remaining available space across visible columns.
4. **Data Sizing & Value Tooltips:** Monospaced Consolas 11.5px is retained for data cells. Changed register values highlight in bold with `-color-warning-fg`. To guarantee that full 16-digit hex and 20-digit decimal values remain accessible even if columns are resized narrowly by the user, `regCellFactory` installs a `Tooltip` containing the exact full value on every data cell.
5. **Right-Click Context Menu & Hovers:** Right-click context menus (`Copy Hex`, `Copy Dec`, `Copy Bin`) copy string values to clipboard. Hovering on register names triggers register documentation tooltips via `viewModel.registerHelp(...)`.

### 3.3 Full-Width Inspection TabPane
1. **Tab Structure & Order:**
   - **Tab 1: Disassembly** (`debugger.disasm.title`, `WorkbenchIcons.ASSEMBLY`, 14px icon + tooltip)
   - **Tab 2: Memory Dump** (`debugger.memory.title`, `WorkbenchIcons.CHIP`, 14px icon + tooltip)
   - **Tab 3: Call Stack** (`debugger.stack.title`, `WorkbenchIcons.STACK_PROCEDURES`, 14px icon + tooltip)
   - **Tab 4: Breakpoints** (`debugger.breakpoints.title`, `WorkbenchIcons.BREAKPOINT`, 14px icon + tooltip)
   - **Tab 5: Watches** (`debugger.watches.title`, `WorkbenchIcons.SEARCH`, 14px icon + tooltip)
2. **Disassembly First:** Moving Disassembly to the primary tab ensures that upon pausing or stepping, the user immediately sees the disassembled instructions at `CS:IP` and the highlighted execution line (`.disasm-current`).
3. **Reclaimed Vertical Real Estate:** Redundant inner labels repeating the tab names were eliminated from inside the tab views, recovering ~20px of vertical space for extra disassembly or memory lines.
4. **Memory Address Bar:** Reorganized into a single horizontal row (`Address Label`, `Segment`, `:`, `Offset`, `Read Button`). In 64-bit native mode, the segment field and colon are hidden automatically.
5. **Memory Dump Width:** In the ~576px inspection pane, the 16-byte memory hex dump line (~540px) fits without horizontal wrapping.

### 3.4 Scoped CSS Styles (`workbench.css`)
Style additions and refinements are strictly scoped to debugger selectors:
- `.debugger-split-pane` divider hover state uses `-color-accent-emphasis`.
- `.debugger-tab-pane` header background matches `-wb-sidebar`, active tab indicator uses a 2px blue underline (`-color-accent-emphasis`), and tab labels use 11px font.
- `.debug-controls` button spacing tuned to `2px` gaps and `26px` height.
- `.debug-flag` styled with subtle background, active flag highlighting in `-color-accent-emphasis`, and changed flag borders in `-color-danger-emphasis`.
- `.debug-register-table .table-cell.reg-changed` and `.workbench .table-cell.reg-changed` apply bold warning typography to changed registers.
- `.workbench .list-cell.disasm-current` and `.debug-section .list-cell.disasm-current` apply warning background and bold font to the current instruction line.

### 3.5 Restored Explanatory Comments
All helpful domain explanatory comments removed during earlier refactoring were restored in `DebuggerPanelView.java`:
- Explanations of capability gating vs launch-only DOSBox sessions in the class header.
- Font monospacing and 64-bit value alignment rationale in `createRegistersBox()`.
- Rationale for omitting 64-bit binary display in native mode.
- Context menu base-copy rationale and flag badge beginner explanations.
- Instruction decoding explanation in `createDisassemblyBox()` (`shl ax, 3` expanding to 3 shifts on 8086).
- Segment:offset vs flat addressing rationale in `createMemoryBox()`.
- Source line navigation double-click explanation in `createCallStackBox()`.
- Unevaluated watch error explanation in `createWatchesBox()`.
- Breakpoint checkbox persistence and intent listener explanation in `createBreakpointsBox()`.
- Method docstrings on callback setters (`setOnCallFrameSelected`, `setOnRestartRequested`).

---

## 4. Verification & Testing

### 4.1 Automated UI Regression Suite (`DebuggerPanelViewTest.java`)
The superficial test cases that merely asserted constants or called setters were replaced with comprehensive JavaFX layout, scene graph, and interaction tests:

1. `panelHostsTwoResizableSectionsWithoutOuterHorizontalScrollPane`:
   - Asserts absence of any outer `ScrollPane` in `DebuggerPanelView`.
   - Asserts presence of `SplitPane` with exactly 2 items: left `VBox` (registers) and right `TabPane` (inspector).
   - Asserts minimum width constraints (`>= 300px` for registers, `>= 320px` for inspector).
   - Asserts presence, icons, and tooltips across all 5 inspection tabs.
2. `renderedHeadersAndValuesAt960pxInEnglishAndSpanish`:
   - Mounts the view in a `Scene` on a `Stage` sized at 960x450 and applies `workbench.css`.
   - Asserts English column header text ("Register", "Hex", "Dec", "Bin").
   - Switches `localization.localeProperty()` dynamically to Spanish and asserts reactively rendered headers ("Registro", "Hex", "Dec", "Bin") and section title ("REGISTROS").
   - Asserts `nameCol` minimum width (`>= 70px`) fits both English and Spanish without truncation.
   - Asserts execution counter label reflects updated instructions executed count.
3. `sixteenHexDigitsAndTwentyDecimalDigitsIn64BitNativeMode`:
   - Updates registers with 64-bit extended state containing maximum values (`0xFFFFFFFFFFFFFFFFL` = `18446744073709551615`, 20 unsigned decimal digits; `0x123456789ABCDEF0L`, 16 hex digits).
   - Asserts `nativeSessionProperty()` transitions to true and `binCol` hides.
   - Asserts `hexCol` width (`minWidth >= 130px`, `prefWidth >= 145px`) and `decCol` width (`minWidth >= 150px`, `prefWidth >= 165px`).
   - Asserts full 16 hex digits and 20 unsigned decimal digits in data items.
   - Queries rendered `TableCell` nodes in the scene graph to assert that value tooltips are attached with the exact full values.
   - Mutates register value and verifies the cell acquires `.reg-changed` styling.
4. `narrowLayoutAndInspectionTabsAccess`:
   - Mounts in a narrow scene (680x380) without an outer ScrollPane.
   - Cycles through all 5 tabs programmatically, layouts each, and asserts tab content visibility.
   - Populates `disassembly` lines and asserts `.disasm-current` style class on the active instruction list cell.
   - Populates and asserts memory dump text in `dumpArea`.
5. `capabilityGatingAndExternalNotice`:
   - Asserts idle state: panels visible, notice hidden.
   - Asserts launch-only external session (`active=true`, `integrated=false`): panels hide, centered notice appears with localized text in English and Spanish.
   - Asserts integrated session (`active=true`, `integrated=true`): panels visible, notice hidden.
   - Asserts step buttons disabled when running or when `canStep=false`, enabled when paused and `canStep=true`.
   - Asserts pause button disabled when paused or when `canPause=false`, enabled when running and `canPause=true`.
   - Asserts stop button enabled when active.
   - Asserts memory read button disabled when `canReadMemory=false`, enabled when `canReadMemory=true`.
6. `callbackWiringAndInteractions`:
   - Asserts restart button click invokes `onRestartRequested`.
   - Asserts double-clicking a breakpoint row invokes `onBreakpointSelected` with the selected item.
   - Asserts double-clicking a navigable call frame invokes `onCallFrameSelected` with the frame item.
   - Asserts typing into expression field and firing Add button adds watch to `viewModel.getWatches()`.
   - Asserts selecting a watch and firing Remove button removes watch from `viewModel.getWatches()`.
   - Asserts register table context menu contains 3 copy items.

### 4.2 Standalone Execution Results
To respect the concurrent Maven rule (Codex owns reactor Maven runs), Antigravity verified compilation and execution using an isolated scratch JUnit runner (`javac` & `java` with JavaFX and JUnit 5.11.4 dependencies from the local `.m2` repository):
- **Compilation:** `javac` completed with exit code 0 on both `DebuggerPanelView.java` and `DebuggerPanelViewTest.java`.
- **Execution:** All 6 automated tests succeeded with 0 failures (`Tests found: 6, Tests succeeded: 6, Tests failed: 0`).

---

## 5. Codex Desktop Verification Requirements & Unresolved Issues

Because headless unit execution validates JavaFX scene graph properties, bindings, and layout calculations but cannot observe physical OS display rendering or peripheral devices, the following items require desktop verification by Codex:

1. **High-DPI Display Scaling on Windows:**
   - Test workbench bottom panel at 1280x800 resolution with Windows display scaling set to 125% and 150%.
   - Verify that font rasterization in monospaced data cells does not cause subtle sub-pixel clipping of the 16th hex digit or 20th decimal digit.
2. **Theme Contrast Verification (Dark vs Light):**
   - Verify visual contrast of `.reg-changed` (`-color-warning-fg`) and `.disasm-current` (`-color-warning-subtle`) against `-wb-editor` background in both Dark Modern and Light themes.
   - Verify condition flag badge readability (active vs inactive vs changed border) across themes.
3. **Manual SplitPane Divider Dragging:**
   - Manually drag the divider to minimum bounds (~300px left and ~320px right) on desktop to confirm that JavaFX SplitPane divider handles stay smooth without jitter or overflow.
4. **End-to-End Debugger Session Integration:**
   - Execute a real assembly project debugging run with `idearm-emu8086`: step through instructions, trigger breakpoints, view live memory dumps, and verify that the instruction executed counter increments.
   - Execute an external DOSBox session (Turbo Debugger) to verify that the external debugger notice presents cleanly in the desktop window.
5. **Full Reactor Maven Validation:**
   - Run `mvn verify` in the repository root to validate that all modules build and pass reactor checks.

---

## 6. Recommendations for Editor & Monaco Research

Following up on Section 6 of `docs/assembly-ide-parity-plan.md` (and Task C of the parallel work plan):
1. **JavaFX WebView Host vs RichTextFX:**
   - Monaco requires a Chromium or WebKit execution host. In JavaFX, `javafx.scene.web.WebView` uses WebKit. Windows IME, high-DPI font sharpness, and clipboard bridge synchronization require dedicated prototype evaluation.
2. **Performance Decision Gates:**
   - Benchmark keystroke latency on a 5,000-line assembly file: p95 typing latency should remain strictly under 50ms.
   - Initial document load time should remain under 1 second.
   - Diagnostics from `idearm-language` should be synchronized asynchronously via Monaco markers without blocking the JavaFX UI thread.
3. **Editor Component Abstraction:**
   - Maintain the `EditorComponent` interface contract so that RichTextFX remains a reliable, zero-browser fallback if WebView encounters packaging or performance constraints.

---

## 7. Changed Files Summary

| File | Change Summary |
|---|---|
| `idearm-app/src/main/java/io/github/dinamo541/idearm/app/view/DebuggerPanelView.java` | Restructured to 2-panel resizable layout (`SplitPane` divider 0.40, min 300px registers, min 320px inspector); unified inspection `TabPane`; tuned column widths for 16 hex and 20 decimal digits; added value tooltips on data cells; restored explanatory domain comments. |
| `idearm-app/src/main/resources/io/github/dinamo541/idearm/app/view/workbench.css` | Scoped debugger styles for `.debugger-split-pane`, `.debugger-tab-pane`, `.debug-register-table`, `.table-cell.reg-changed`, `.list-cell.disasm-current`, and flag badges. No cross-boundary modifications. |
| `idearm-app/src/test/java/io/github/dinamo541/idearm/app/view/DebuggerPanelViewTest.java` | 6 comprehensive JavaFX layout, 64-bit value, capability gating, and interaction tests replacing superficial tests. |
| `docs/idearm-debugger-visual-review.md` | Updated honest review removing unsupported claims, documenting layout and column metrics, recording standalone test results, and detailing Codex desktop verification requirements. |
