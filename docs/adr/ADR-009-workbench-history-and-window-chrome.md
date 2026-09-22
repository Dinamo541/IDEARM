# ADR-009: Local recent history and client-rendered window chrome

Date: 2026-09-17. Status: accepted.

## Context

The user requested a VS Code-like Open Recent picker, delayed button explanations, an integrated title bar
with window controls, and an original logo. The JavaFX app preserves its existing N-layer architecture.

## Decision

- Domain `RecentItem` values describe files and project folders. Application `ManageRecentItems` owns MRU
  ordering, deduplication and the limit of twenty entries per category.
- Inject the `RecentItemsStore` port through `WorkbenchServices`. Infrastructure `FileRecentItemsStore`
  writes versioned JSON to `%APPDATA%/IDEARM/recent.json`, falling back to `~/.idearm/recent.json`.
  `IDEARM_USER_DATA_DIR` overrides the directory for portable use and isolated tests.
- Serialize background writes and flush on disposal. Read the small local history before initial navigation.
  Use atomic replacement where supported. Preserve unreadable or newer formats until explicit Clear history;
  report read/write failures as localized messages.
- Record successful opens centrally, including existing tabs, and Save As after its write succeeds. Validate
  recent targets before changing workbench state. Removing history never deletes source files.
- Use an undecorated JavaFX stage and `WindowChrome` for title dragging, double-click maximize/restore,
  edge/corner resizing, Alt+Space and Alt+F4. Every exit dispatches the close-request event to honor
  Save All / Don't Save / Cancel. Do not manually hide after dispatch; JavaFX owns the default handler.
- Use original vector icons and shared `HoverHelp` (450 ms delay, translated text and accessible names).
- Use an original hexagonal Assembly A/code-bracket mark. Keep SVG and `BrandLogo` geometry synchronized;
  render PNG with JavaFX and embed its 256 px version in ICO for jpackage. No dependencies were added.

## Consequences and limits

History does not restore sessions or dirty buffers. Separate simultaneous IDE instances currently use
last-writer-wins persistence. Externally moved locations can be removed and reopened; their new path is not
automatically discovered. The Windows Snap Layout hover flyout is not emulated.

Unit tests cover MRU, persistence, corruption, write ordering, stale targets and existing tabs. The opt-in
JavaFX smoke walkthrough covers recent search/Enter, window controls, real pointer hover and close cancellation.
