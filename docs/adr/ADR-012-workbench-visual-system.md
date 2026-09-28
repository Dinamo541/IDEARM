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
