# ADR-012: One visual system for the desktop workbench

- **Status:** Accepted
- **Date:** 2026-09-24

## Context

The editor already resembled a code workbench, but dialogs, popups and tool panels used different palettes,
spacing and control sizes. The dictionary did not follow a theme change while open. The debugger's labeled
controls and three columns compressed into unreadable fragments on narrow windows.

## Decision

Use a VS Code-inspired neutral palette and compact geometry while retaining IDEARM's original icons and
branding. Keep the implementation in the presentation module, using existing JavaFX and AtlantaFX controls.

- `workbench.css` defines dark and light surface, text, semantic and syntax colors, plus shared control,
  selection, hover and keyboard-focus states. Color is reinforced with icons, labels and active borders.
- `WorkbenchTheme` applies the same stylesheet to owned stages and popups. Visible secondary windows follow
  their owner's theme; listeners are detached when they hide. `DialogWindow` provides consistent form headings
  and sizing based on content. Native operating-system file choosers retain their system appearance.
- The welcome screen offers real project, history, command and reference actions with visible shortcuts.
  Empty tool panels explain the next relevant action without creating sample output.
- The debugger uses icon controls with accessible names and tooltips, wrapping flags, a useful minimum panel
  height and horizontal scrolling when its inspection columns cannot fit. The welcome screen also scrolls
  when the available height is small.
- Both localization bundles contain the new copy. Existing project, build, editor, debugger and persistence
  operations remain behind their existing view models.

## Verification

`mvn verify` runs the standard suite, including form-layout and window-control regressions. Set
`IDEARM_DESIGN_SMOKE` to an output folder and `IDEARM_USER_DATA_DIR` to an isolated directory to capture the
editor, welcome screen, consoles, debugger, forms, history, palette, dictionary and editor popups. This mode
requires an open project with a source file; `IDEARM_SMOKE_PROJECT` can select the example project explicitly.
It captures dark English and light Spanish surfaces, checks live reference-window theming, and renders an
800 × 600 workbench. It does not build or execute the project.

The existing `IDEARM_VISUAL_SMOKE` workflow still checks editor shortcuts, undo, recent navigation and native
window controls. Inspect captured images in addition to checking the logs: a successful render alone cannot
prove that a layout is readable. Desktop scaling and native window behavior on Linux still need platform
verification.

## Addendum: visual polish and academic iconography — 2026-09-26

- Keep all line icons in `WorkbenchIcons`, on its existing 16 px grid with the shared 1.25 px stroke.
  Eleven instruction symbols distinguish transfer, arithmetic, logic, flow, strings, flags, stack,
  interrupts, I/O, bit manipulation and floating point. Six register symbols distinguish general storage,
  pointers, segments, execution, vectors and system registers. Academic lists, category filters and metadata
  badges reuse these symbols. Operand classes reuse the existing reference, arithmetic, configuration,
  include and segment symbols. The presentation view owns these mappings; the corpus and view model stay unchanged.
- Explorer actions, folders and generic files now use the same enum and fixed icon canvas. The new
  `-wb-icon-folder` token preserves the existing warm folder hue in dark mode and uses a darker counterpart
  in light mode; other file colors use existing tokens. Brand geometry and colors remain unchanged.
- Titles sit above wrapping metadata and muted summaries. List descriptions elide with full tooltips;
  the three narrow academic tabs remain text-only to preserve room for their bilingual labels.
- Forms and pickers share 24 px horizontal padding and 12 px vertical spacing. Content cards have quiet
  token-based borders; keyboard focus and hover use the shared accent and hover tokens, including family links.
- The design smoke now captures all four dark/light × English/Spanish combinations, academic categories,
  registers, operands, search and minimum-width cards, plus the real About dialog. Continue inspecting the
  PNGs; a PASS line alone does not establish visual quality.

## Addendum: downloaded PNG icon assets — 2026-09-26

The user's subsequent request replaces all custom code-drawn icons with downloaded bitmap assets while
explicitly retaining IDEARM's existing brand PNG. This supersedes the earlier hand-drawn SVG icon convention.

- `WorkbenchIcons` retains its semantic API and fixed canvas, mapping 64 identifiers to 61 Lucide 1.48.0
  PNG resources. Eleven instruction categories and six register groups keep their presentation mappings.
  Editor breakpoint/execution markers and both console input prompts also use these assets.
- PNGs are transparent 96 px exports of pinned upstream SVGs. The application ships only the raster assets,
  their license and a provenance manifest. The maintenance script requires an existing Sharp installation;
  no new Maven or runtime dependency is introduced.
- `RasterIcon` recolors each PNG's alpha mask using the styleable `-wb-icon-color` property. Theme and state
  selectors reuse existing color tokens, and tinted images are cached. There is no runtime icon geometry.
- `BrandLogo` displays the unchanged 512 px `idearm.png` through `ImageView`, including About/header marks
  and window icons. The original SVG remains the design master, separate from Java rendering.
- Regression tests cover all packaged assets, transparency, live theme/selection colors and brand reuse.
  The design smoke adds four 16/28 px icon galleries and four gutter-state captures to its existing
  theme/language and academic captures. Offline tests pass (832 tests, 2 skipped); design smoke passes with
  86 captures. The galleries and major surfaces were inspected for icon clarity, theme contrast, compact
  layout and unchanged brand appearance.

## Addendum: "Hexcore" visual refresh — 2026-10-15

The user asked for the most modern, attractive, intuitive and ergonomic interface the workbench can have. This
supersedes the VS Code-neutral palette above; the structure, tokens-first approach and icon assets remain.

- **Palette from the brand.** Surfaces are tinted towards the mark's navy field, in four layers: rail (title,
  activity and status bars), sidebar, editor and raised (cards, menus, popups). The accent is the brand blue
  `#3976F6`; registers use the mint of the monogram; the welcome title and the dictionary mnemonic use the
  frame's cyan-to-blue gradient. The light theme repeats the hues on cool white. `docs/branding.md` lists the
  main tokens.
- **Bundled typefaces.** Inter for the interface and JetBrains Mono for code, both under the SIL OFL 1.1 and
  loaded by `WorkbenchFonts` at startup. Linux has neither Segoe UI nor Consolas, and the editor had fallen back
  to a proportional font that misaligned every column. Inline `'Consolas'` and `'Segoe UI'` styles now name
  the bundled families.
- **Shape and depth.** Controls use 7 px radii, cards 10–12 px; menus, popups, the palette and the hover card
  carry soft drop shadows. Primary buttons have a subtle vertical gradient. Editor tabs open into the canvas
  with a brand stripe; panel tabs are segmented pills; the activity bar marks the active view with a tinted
  pill instead of a thin edge.
- **Focus only when it helps.** Keyboard focus draws an accent ring through `:focus-visible`, so clicking a
  toolbar or card button no longer leaves an outline. Text fields keep a focus glow while typing.
- **Welcome cards.** Each entry is a card with an icon tile, a one-line description (new `welcome.*.description`
  texts in both bundles) and its shortcut as a keycap. The two groups sit side by side and stack below 640 px.
- **Fixes found on the way.** `editor.css` pinned dark colour tokens on every node through a `*` rule, which
  painted identifiers light grey on the light theme's white canvas; the rule is gone and the editor inherits the
  workbench tokens. Selected explorer rows no longer use white text on the light selection. The command centre
  takes only the width the actions leave, so the theme and language buttons no longer fall into the toolbar's
  overflow menu with the longer Spanish menu names. The hover card and completion list use style classes
  instead of inline styles; the completion badge no longer appends a style string on every cell reuse.
- **Status bar.** Quiet, on the rail colour, with hover pills; while debugging it turns into a warm gradient so
  the session state is visible at a glance.

Verification: the app module's tests pass under Xvfb (203 tests, 15 skipped), including the icon-tint test with
the new palette values. The design smoke passes with 90 captures; the four theme/language combinations,
welcome, debugger, consoles, dialogs, palette, hover and completion popups, Academic Center and the 800 × 600
compact layout were inspected in both themes.

## Addendum: VS Code 2026 palette and the editing view — 2026-10-15

The user then asked for the editing view to look impressive yet minimal and comfortable, keeping its indicators,
in Visual Studio Code's current palette with deep blacks. This supersedes the "Hexcore" palette above; the
bundled typefaces, shapes and focus behaviour remain.

- **Palette.** The tokens now follow VS Code's default themes, Dark 2026 and Light 2026 (microsoft/vscode,
  `extensions/theme-defaults/themes/2026-dark.json` and `2026-light.json`). The editor is the deepest surface
  (`#121314`) inside a `#191A1B` frame with `#2A2B2C` hairlines; floating widgets use `#202122`; the accent is
  `#3994BC` with `#297AA0` buttons. Selections in lists, the activity bar and the panel tabs are neutral, as in
  VS Code. Syntax colours take the 2026 hues: instructions coral, registers blue, directives orange, labels and
  procedures lavender, numbers the inherited Dark+ green, strings light blue, comments grey. `docs/branding.md`
  lists them for both themes.
- **Tabs.** A flat strip with hairline separators. The active file opens into the canvas under a 2 px accent line
  (black in the light theme, as in Light 2026). The close button shows on the active tab and under the pointer;
  a dot replaces it while the file has unsaved changes, so the title no longer carries " *". The tooltip names
  the full path and, when modified, says "Unsaved changes" (`editor.tab.unsaved`).
- **Breadcrumbs.** `EditorBreadcrumbs` shows folder › file (with its icon) › segment › procedure. The symbols
  follow the caret and refresh 450 ms after typing stops; clicking one moves the caret to its declaration.
  `OutlineItem` gained `endLine` (ENDP for a procedure, ENDS for a full segment; a simplified segment runs until
  the next one), filled in by `QueryOutline`.
- **Find.** The find/replace widget floats over the top-right corner of the code, hanging from the tab strip as
  in VS Code, instead of pushing every line down.
- **Code canvas.** The line highlight sits behind the code only; the caret's line number brightens while the
  others stay quiet. The selection is VS Code's teal, the caret 2 px, and the squiggles use VS Code's error and
  warning colours. The glyph margin draws vector markers: a red dot for a breakpoint, a grey dot while it is
  switched off, a yellow arrow on the paused line (with the dot inside when it stopped on a breakpoint), and a
  faint dot where a click would add one. The paused line keeps a translucent tint and a 2 px stripe. These two
  markers replace the PNG ring and play icons in the gutter; the rest of the workbench keeps its Lucide assets.
- **Fixed on the way.** Line numbers had always been painted as bright as the code: the plain-text rule matched
  the "text" node a Label draws its digits with. Code rules now name RichTextFX's `.paragraph-text` parent. The
  explorer tree had taken the editor's background through rule order; it is back on the sidebar colour.

Verification: the application and app modules' tests pass (182 and 206, 15 skipped), including
`EditorBreadcrumbsTest` for the symbol path and an `endLine` assertion in `QueryEditorUseCasesTest`. The design
smoke now also saves `editing-<theme>-<language>.png`: a second tab, an unsaved edit, a warning and an error, the
find widget, a breakpoint and the paused line, with the caret inside the procedure. It passes with 94 captures,
inspected in both themes and languages and at 800 × 600.
