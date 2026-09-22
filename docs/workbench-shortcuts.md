# Workbench appearance and keyboard shortcuts

Updated 2026-09-17 by Codex. Build and debug services retain their existing behavior.

## Appearance

The workbench now uses a compact command header, a 48 px activity bar, a resizable explorer/outline sidebar,
flat document tabs with file icons, breadcrumbs, a bottom panel, and a blue status bar. The dark and light
palettes include Assembly token colors. Theme and language controls are in the upper right.
The command palette opens near the top center of the window.

The title bar is rendered inside the app: original logo, menu, command center, vector actions and
minimize/maximize/restore/close controls. Drag empty header space to move the window; double-click it to
maximize/restore. Resize from edges and corners. Closing asks about unsaved buffers, including Alt+F4 and
File → Exit. The Windows Snap Layout hover flyout is not implemented.

Button hover help appears after 450 ms, with explanations and shortcuts. Shared vector icons, hover/pressed/
focus states and translated accessible names cover the workbench, explorer, debugger, terminal, find widget
and recent picker. See [branding](branding.md) for the SVG, PNG and Windows icon.

Source control, extensions, multi-cursor editing, folding, and the complete VS Code command set are not
implemented by this update.

## Open Recent

Use **File → Open Recent**, **Ctrl+R**, the history icon, or the command palette. Search names or full paths;
filter All / Projects & folders / Files; open with arrows and Enter or a double-click. Escape closes the picker.
Each category retains twenty entries, newest first. An already open file activates its existing tab.
The row's × or Delete removes an entry. Clear history forgets all entries. These actions never delete files.

History survives restarting in `%APPDATA%/IDEARM/recent.json` (fallback `~/.idearm/recent.json`). Set
`IDEARM_USER_DATA_DIR` for an alternative directory. Missing or inaccessible locations show an explanation
without replacing the active project or creating empty buffers. Unreadable history is preserved until
explicitly cleared; write failures leave the entries available for the current session.

## Editor shortcuts

These commands apply when the code editor has focus. They are also listed in Edit and in the command palette.
Find and replace are literal, case-insensitive searches in the active document, with wraparound navigation.

| Shortcut | Action |
|---|---|
| Ctrl+X / Ctrl+C | Cut / copy selection; without a selection, cut / copy the current line |
| Ctrl+V | Paste |
| Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z | Undo / redo |
| Alt+Up / Alt+Down | Move the current line or selected block |
| Shift+Alt+Up / Shift+Alt+Down | Duplicate the current line or selected block |
| Ctrl+Shift+K | Delete the current line or selected block |
| Ctrl+Enter / Ctrl+Shift+Enter | Insert a line below / above, retaining indentation |
| Ctrl+L / Ctrl+A | Select line / select all |
| Ctrl+/ | Toggle Assembly line comments using semicolons |
| Tab / Shift+Tab | Insert four spaces or indent a selected block / outdent |
| Ctrl+] / Ctrl+[ | Indent / outdent lines |
| Ctrl+F / Ctrl+H | Open find / replace |
| F3 / Shift+F3 | Next / previous match |
| Ctrl+G | Go to a line |
| Alt+Z | Toggle word wrap |
| Ctrl+Space | Existing completion popup |
| F12 / Shift+F12 | Existing definition / references commands |

Line moves and copies preserve selection direction. Each block operation is one undo step.
Replace All also uses one undo step, including replacements that change text length.
Cut/copy/paste shortcuts in text fields and the terminal retain those controls' own behavior.

## Files and layout

| Shortcut | Action |
|---|---|
| Ctrl+O | Open a file |
| Ctrl+R | Open recent projects, folders and files |
| Alt+Space | Window controls menu |
| Alt+F4 | Close window, with unsaved-file confirmation |
| Ctrl+S / Ctrl+Shift+S | Save / Save As |
| Ctrl+K, then S | Save all modified documents |
| Ctrl+K, then Ctrl+O | Open a project folder |
| Ctrl+W / Ctrl+F4 | Close the current editor, with Save / Don't Save / Cancel for modified files |
| Ctrl+Tab / Ctrl+Shift+Tab | Next / previous editor tab |
| Ctrl+1 | Focus the active editor |
| Ctrl+B / Ctrl+J | Toggle sidebar / bottom panel |
| Ctrl+Shift+E | Show explorer |
| Ctrl+Shift+M / Ctrl+Shift+U / Ctrl+Shift+D | Show Problems / Output / Debugger |
| Ctrl+backtick | Show terminal |
| Ctrl+Shift+P / F1 | Command palette |
| Ctrl+Shift+B / F7 | Build project |
| F11 | Full screen outside a debug session; Step Into during debugging |

Existing F5, Ctrl+F5, Shift+F5, F9, F10, and Shift+F11 debug/run bindings remain.
IDEARM retains project-specific Ctrl+N (create file in explorer), Ctrl+Shift+N (new project), and Shift+F7
(clean and build). Keyboard chords expire after two seconds.

Reference: [VS Code's Windows shortcut sheet](https://code.visualstudio.com/shortcuts/keyboard-shortcuts-windows.pdf).

## Maintenance and verification

- Styling: presentation resource `view/workbench.css`; theme colors are shared through JavaFX CSS lookup values.
- Editing: `EditorCommand` defines the common keyboard/menu/palette metadata; `LineEditing` computes pure
  text transformations; `EditorActions` applies them to RichTextFX and owns the find widget.
- Keep editing shortcuts local to the editor. Global clipboard menu accelerators can steal input from the
  terminal and explorer rename fields.
- Labels exist in both EN/ES bundles. Update both whenever commands change.
- Recents: `RecentItem` / `RecentItemsStore` (domain), `ManageRecentItems` (application),
  `FileRecentItemsStore` (infrastructure), `RecentItemsViewModel` / `OpenRecentDialog` (presentation).
- Shared UI helpers: `app.ui.WindowChrome`, `WorkbenchIcons`, `HoverHelp`, `BrandLogo`.
- Decisions and limitations: [ADR-009](adr/ADR-009-workbench-history-and-window-chrome.md).
- Test totals for the whole repository are kept in `PLAN.md`.
- `IDEARM_VISUAL_SMOKE=<output folder>` starts the existing desktop entry point in verification mode.
  Set `IDEARM_USER_DATA_DIR` to an isolated scratch directory. It verifies editor keys, Replace All + Undo,
  recent search/Enter, stale locations, minimize/maximize/restore, actual delayed pointer hover, and cancelling
  close with unsaved changes. It captures both themes, exports the logo and creates fixtures under its output
  directory. Original buffers and retrievable clipboard formats are restored; no example source edits are saved.
  Check for both `VISUAL SMOKE: ... PASS` and `EXPERIENCE SMOKE: ... PASS`, and no `FAILED` log lines.
- Local verification artifacts are in the ignored `scratch/workbench-review/` folder.

Next useful refinements: configurable keybindings, a project-wide Ctrl+P picker, regex search, and multi-cursor
support. Do not expose commands for these until their behavior exists.
