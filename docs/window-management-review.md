# Window management and branding verification

## Native Windows integration — 2026-10-02

Windows x64, JDK 25.0.3 / JavaFX 25.0.2. Implementation: [ADR-015](adr/ADR-015-native-windows-window-management.md).

- The supported Windows path uses `StageStyle.EXTENDED` and `HeaderBar`. Native hit testing identifies title
  regions as `HTCAPTION`, Maximize/Restore as `HTMAXBUTTON`, and normal header actions as `HTCLIENT`.
  Native frame flags enable the system menu, maximizing and resizing. Windows owns drag/snap and its flyouts.
- The native integration replaces manual title dragging and resize handling on Windows. The previous code
  remains a fallback on other platforms and when the JavaFX preview option is disabled.
- All eight resize directions are verified against the real Win32 frame. Button release fires once;
  releasing outside cancels. Minimize, maximize, full screen and saved normal bounds round-trip correctly.
  Both the custom close action and native `WM_CLOSE` honor cancellation.
- The 500 × 360 logical-pixel minimum supports smaller snap zones. Menus collapse into one menu; command
  search becomes an icon. Sidebar and bottom-panel space adapts and restores as the window grows. Toolbar
  overflow, compact activity buttons and abbreviated status content keep essential controls reachable.
- Both languages and themes are rendered at four window sizes. No desktop keyboard or pointer input is
  injected by the new native protocol tests or the window-layout smoke.

| Check | Result |
| --- | --- |
| `mvn -o -B verify` | 932 tests: 930 passed, 2 skipped, no failures/errors |
| Native protocol / fallback chrome / placement | 27 passed |
| Final targeted run including EN/ES bundles | 34 passed |
| `IDEARM_WINDOW_SMOKE` | PASS: four sizes in dark EN and light ES; native controls, compact menu structure, Save accelerator, visible status and automatic panel restoration |
| Portable build | `scripts/package-native.ps1 -Type app-image -SkipBuild`; JavaFX preview enabled in the packaged desktop launcher |
| Packaged executable | `IDEARM_SMOKE=1`: PASS; packaged application JAR hash matches the final build; preview option verified in `IDEARM.cfg` |

Artifacts and logs are under ignored `scratch/native-window-review/`, with isolated settings directories.
The portable executable is `target/release/image/IDEARM/IDEARM.exe`; the ZIP and checksum are in `dist/release/`.

### Remaining manual checks

The user's VS Code window was observed as a reference. Computer Use was stopped with the physical Escape
key before the drag/hover comparison completed; no further Computer Use input was issued. The new tests
prove native hit-test and state integration, but do not claim visual verification of the Windows shell's
top-edge picker, hover flyout, Snap Assist groups, Win-key shortcuts or physical cross-monitor/DPI dragging.
Those checks require an interactive Windows desktop with snapping enabled. Linux/macOS were not exercised.

## Previous verification — 2026-09-23

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
That review used the original custom title bar without native Windows Snap Layouts (ADR-009), now superseded
on Windows by ADR-015 above.

See [window shortcuts](workbench-shortcuts.md) and [branding](branding.md) for usage and asset regeneration.
