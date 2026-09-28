# Window management and branding verification

Verified locally on Windows x64, JDK 25 / JavaFX 25.0.2, on 2026-09-23.

## Behavior

- Full screen has a title-bar button, View menu entry and command-palette action. Ctrl+Alt+F11 always toggles
  it, Esc exits, and F11 keeps its existing debug-session behavior. Button icons and accessible names follow
  the actual stage state. Restoring from full screen preserves the previous maximized state.
- The title supports drag, double-click maximize/restore, drag out of maximization and drag-to-top maximize.
  Small pointer movements and interactive header controls do not initiate a drag. All eight resize edges
  respect minimum/maximum dimensions. Window menu actions are disabled when inapplicable.
- Closing through the button, Alt+F4 or File → Exit respects unsaved-file cancellation.
- Normal size/position and maximization persist separately. Invalid or disconnected-monitor coordinates are
  fitted to a connected screen's usable bounds on startup. Full screen and minimization are not persisted.
- Menu labels, the logo and window buttons remain visible at 800 × 500; the toolbar handles overflow.
- Every application stage and JavaFX alert receives the shared logo. Both native launchers embed all seven
  ICO sizes, from 16 to 256 px. Development launches still use the Java executable's process identity.

## Checks performed

| Check | Result |
| --- | --- |
| `mvn -B verify` | 617 tests reported: 615 passed, 2 skipped, no failures/errors |
| Window-specific tests | 21 passed, including eight resize directions, full-screen transitions, close cancellation, icon pixels, persistence and monitor geometry |
| `IDEARM_VISUAL_SMOKE` | PASS: both themes, EN/ES, editor keys, recents, minimize/maximize/restore, delayed tooltip, dirty-close cancellation |
| `IDEARM_DRAG_SMOKE` | PASS: native drag, release stops movement, drag-to-top maximize |
| Native executable visual inspection | PASS: shortcut/menu full screen, Esc, maximize, drag-to-restore, minimum size and restart at saved size |
| PE icon inspection | All seven ICO image payloads present in both `IDEARM.exe` and `idearm-cli.exe` |
| Portable packaging | Windows ZIP and SHA-256 generated with `scripts/package-native.ps1 -Type app-image -SkipBuild` |

Verification uses isolated `IDEARM_USER_DATA_DIR` folders under ignored `scratch/window-review*` directories.
The visual smoke explicitly requests focus after undoing minimization before testing pointer hover.

## Limits

The native window-state tests are Windows-only: an Xvfb display without a window manager cannot validate
maximize/minimize behavior. Geometry and settings tests remain platform-independent. Linux/macOS desktop
behavior, physical multi-monitor/DPI transitions and an installed MSI were not exercised in this review.
The existing custom title bar does not implement the Windows Snap Layout hover flyout; see ADR-009.

See [window shortcuts](workbench-shortcuts.md) and [branding](branding.md) for usage and asset regeneration.
