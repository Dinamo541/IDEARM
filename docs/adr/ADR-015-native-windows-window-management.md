# ADR-015: Native Windows window management

Date: 2026-10-02. Status: accepted. Amends ADR-009 for Windows.

## Context and evidence

Moving an undecorated stage by assigning its coordinates bypasses the Windows move/size loop. It cannot
provide Snap Assist, the Windows 11 top-edge layout picker or the maximize-button Snap Layout flyout.
The existing 800-pixel minimum width also excludes narrow snap zones.

The installed JavaFX 25.0.2 graphics module exposes `StageStyle.EXTENDED`, `HeaderBar`, `HeaderDragType`
and `HeaderButtonType`. The JavaFX 25 source and public documentation explicitly describe native dragging,
system menus and Windows Snap Layouts on custom maximize buttons. No additional library is necessary.

References:
- [JavaFX extended stage](https://openjfx.io/javadoc/25/javafx.graphics/javafx/stage/StageStyle.html)
- [JavaFX HeaderBar](https://openjfx.io/javadoc/25/javafx.graphics/javafx/scene/layout/HeaderBar.html)
- [Microsoft Snap Layout integration and minimum dimensions](https://learn.microsoft.com/windows/apps/desktop/modernize/apply-snap-layout-menu)

## Decision

Use an extended stage and a full-width `HeaderBar` on supported Windows runtimes. Preserve the themed
buttons and declare their native semantics. Explicit draggable regions exclude menus and toolbar actions.
Windows owns move/resize, snap, title double-click, system menus and native window keyboard shortcuts.
The previous handlers remain exclusively for the unsupported-platform/preview-disabled fallback.

Enable the JavaFX preview API in development, test and packaged launchers. Keep all preview API usage
inside `WindowChrome`, pin JavaFX 25.0.2 and revalidate the integration on JavaFX upgrades. The runtime
property is separate from Java language preview features; no `--enable-preview` compiler flag is needed.

Keep button activation in one event path using the documented release-filter override, retaining keyboard
and accessibility activation, close cancellation and the existing full-screen exit action. Set the default
system button height to zero to avoid duplicate controls.

Allow 500 x 360 logical pixels. Collapse the menus into a single menu at narrow widths, preserving all
commands and accelerators. Temporarily collapse the sidebar below 720 pixels and the bottom panel below
480 pixels high; restore automatically hidden panels when space returns, while respecting manual actions.
Keep caret/encoding status visible in compact windows. Window placement uses the same minimum dimensions
when restoring a session. Match the scene fill to the styled header so JavaFX chooses the matching native
frame color scheme.

## Limits

Snap choices and flyouts belong to Windows and depend on its version, display size, scaling and the user's
multitasking settings. They are not simulated by the IDE. Physical multi-monitor/DPI transitions need a
multi-monitor verification environment. Other platforms retain the existing window implementation.

Verification results are recorded in [window management review](../window-management-review.md).
