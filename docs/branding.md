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

## Regenerate raster assets

Run with `IDEARM_VISUAL_SMOKE` naming an output directory and `IDEARM_USER_DATA_DIR` naming an isolated
history directory. The walkthrough writes `idearm-logo.png` and `idearm-icon-256.png`. Then:

```powershell
python scripts/export-branding.py scratch/workbench-review
```

The exporter validates the dimensions and embeds the PNG in ICO without resampling. No imaging libraries,
network requests or font dependencies are needed.
