# IDEARM visual identity

Designed and integrated by Codex on 2026-09-17.

The original mark combines an **A** for Assembly with code brackets inside a hexagon. The frame suggests a
hardware component. Its cyan-to-blue gradient and mint monogram work on both workbench themes.

## Deliverables

Assets: `idearm-app/src/main/resources/io/github/dinamo541/idearm/app/branding/`.

- `idearm.svg`: scalable source, 64 × 64 viewBox.
- `idearm.png`: transparent 512 × 512 PNG.
- `idearm.ico`: 256 × 256 Windows launcher icon, configured in `scripts/package-native.ps1`.

The header and JavaFX window/taskbar icons are drawn by `app.ui.BrandLogo`. Keep its four paths and colors
in sync with the SVG. Preserve the outer padding around the hexagon.

| Color | Role |
|---|---|
| `#52D6EF` → `#3976F6` | Outer frame gradient |
| `#101B2D` | Dark inner field |
| `#79EFD2` | Assembly monogram |
| `#52C6FF` | Code brackets |

## README screenshots

`docs/images/workbench-dark.png` and `docs/images/workbench-light-es.png` are unedited copies of the real
workbench captures from the 2026-09-17 visual smoke run. They show the repository's `hello` example in English
and Spanish respectively. The README reuses the original SVG logo directly from the app's branding folder.

When refreshing these screenshots, use the visual smoke workflow described in
[workbench-shortcuts.md](workbench-shortcuts.md#maintenance-and-verification), inspect both images, and copy
only the workbench captures into `docs/images/`. Keep recent-history captures and local machine paths out of
the public README images.

## Regenerate raster assets

Run with `IDEARM_VISUAL_SMOKE` naming an output directory and `IDEARM_USER_DATA_DIR` naming an isolated
history directory. The walkthrough writes `idearm-logo.png` and `idearm-icon-256.png`. Then:

```powershell
python scripts/export-branding.py scratch/workbench-review
```

The exporter validates the dimensions and embeds the PNG in ICO without resampling. No imaging libraries,
network requests or font dependencies are needed.
