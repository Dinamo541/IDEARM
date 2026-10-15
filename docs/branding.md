# IDEARM visual identity

Designed and integrated by Codex on 2026-09-17.

The original mark combines an **A** for Assembly with code brackets inside a hexagon. The frame suggests a
hardware component. Its cyan-to-blue gradient and mint monogram work on both workbench themes.

## Deliverables

Assets: `idearm-app/src/main/resources/io/github/dinamo541/idearm/app/branding/`.

- `idearm.svg`: scalable source, 64 × 64 viewBox.
- `idearm.png`: transparent 512 × 512 PNG.
- `idearm.ico`: Windows launcher icon with 16, 24, 32, 48, 64, 128 and 256 px entries, configured for both
  `IDEARM.exe` and `idearm-cli.exe` in `scripts/package-native.ps1`.

The header and dialogs use `app.ui.BrandLogo` to display the existing `idearm.png` in an `ImageView`.
Java contains no brand paths or gradient definitions. The SVG remains the editable design master; when
changing the brand, export a matching transparent PNG and preserve its outer padding around the hexagon.
Native JavaFX window/taskbar icons load the same bundled PNG at multiple sizes.
`BrandLogo.apply` brands explicit stages before showing them; `BrandLogo.install` also covers JavaFX alerts
and other secondary stages. About dialogs show the mark alongside the product name.

Start the packaged `IDEARM.exe` for the IDEARM process name and embedded executable icon in Windows.
Development launches through Maven or `java.exe` still use the Java executable's process identity; setting
a JavaFX window icon cannot replace the icon embedded in that separate executable. No JDK files are modified.

| Color | Role |
|---|---|
| `#52D6EF` → `#3976F6` | Outer frame gradient |
| `#101B2D` | Dark inner field |
| `#79EFD2` | Assembly monogram |
| `#52C6FF` | Code brackets |

## Workbench palette and typography

The workbench colours follow Visual Studio Code's current default themes, **Dark 2026** and **Light 2026**
(`extensions/theme-defaults/themes/2026-dark.json` and `2026-light.json` in microsoft/vscode), with VS Code's deep
blacks in the dark theme. The brand keeps its own colours in the logo and in the welcome title's gradient. All
values are tokens in `idearm-app/src/main/resources/io/github/dinamo541/idearm/app/view/workbench.css`.

| Token | Dark | Light | VS Code colour |
|---|---|---|---|
| `-wb-editor` | `#121314` | `#FFFFFF` | `editor.background` — the deepest surface |
| `-wb-rail`, `-wb-sidebar` | `#191A1B` | `#FAFAFD` | `titleBar`, `activityBar`, `sideBar`, `panel`, `statusBar` |
| `-wb-raised` | `#202122` | `#FAFAFD` | `editorWidget`, `menu`, `quickInput` |
| `-color-border-default` | `#2A2B2C` | `#E4E5E6` | `widget.border`, `tab.border` |
| `-color-accent-emphasis` | `#3994BC` | `#0069CC` | `focusBorder`, `tab.activeBorderTop` (black in Light 2026) |
| `-wb-button` | `#297AA0` | `#0069CC` | `button.background`, `quickInputList.focusBackground` |
| `-wb-current-line` | `#1D1E20` | `#F6F6F8` | `editor.lineHighlightBackground`, slightly deeper |
| `-wb-editor-selection` | `#276782DD` | `#0069CC40` | `editor.selectionBackground` |

Syntax colours take the 2026 themes' hues and map them onto Assembly:

| Assembly token | Dark | Light | Theme role |
|---|---|---|---|
| Instruction | `#FF7B72` | `#CF222E` | keyword |
| Register | `#79C0FF` | `#0550AE` | constant, `variable.language` |
| Directive | `#FFA657` | `#953800` | `entity.name` |
| Label, procedure | `#D2A8FF` | `#8250DF` | `entity.name.function` |
| Number | `#B5CEA8` | `#098658` | `constant.numeric` (inherited from Dark+ / Light+) |
| String | `#A5D6FF` | `#0A3069` | string |
| Comment | `#8B949E` | `#6E7781` | comment |

The interface is set in **Inter** and code in **JetBrains Mono**. Both are bundled under the SIL Open Font
License 1.1 in `idearm-app/src/main/resources/io/github/dinamo541/idearm/app/fonts/`, with their license files,
and loaded by `app.ui.WorkbenchFonts` before the first scene. Linux has neither Segoe UI nor Consolas, so without
bundled faces the editor fell back to a proportional font. JavaFX picks only a regular or a bold face within a
family; style sheets name "Inter Medium" and "Inter SemiBold" as families for the weights in between. The Inter
files are the 4.1.1 `inter-ui` web faces unpacked to TrueType; JetBrains Mono 2.305 ships as TrueType upstream.

## README screenshots

`docs/images/workbench-dark.png`, `docs/images/editing-dark.png`, `docs/images/workbench-light-es.png` and
`docs/images/welcome-dark.png` are unedited copies of the real captures from the 2026-10-15 design smoke run
(`IDEARM_DESIGN_SMOKE`). They show the repository's `hello` example in English and Spanish, the editing view with
an unsaved change, find, a breakpoint, the paused line and two diagnostics, and the welcome screen. The README
reuses the original SVG logo directly from the app's branding folder.

When refreshing these screenshots, run the design smoke described in ADR-012 against a copy of `examples/hello`
in a folder named `hello`, inspect the images, and copy only the workbench, editing and welcome captures into
`docs/images/`. Keep recent-history captures and local machine paths out of the public README images.

## Workbench icons

All custom workbench icons load local, transparent 96 × 96 PNGs from
`idearm-app/src/main/resources/io/github/dinamo541/idearm/app/icons/lucide/`.
The 64 semantic identifiers in `WorkbenchIcons` reuse 61 downloaded [Lucide 1.48.0](https://github.com/lucide-icons/lucide/releases/tag/1.48.0)
drawings, including the eleven instruction categories, six register groups, explorer, commands, debug controls,
gutter markers and console prompts. Their line style remains readable on the existing 16 px canvas; higher
resolution preserves detail in larger controls and on scaled displays. PNG retains transparency and sharp
edges without JPEG compression artifacts. IDEARM's original brand PNG is unchanged.

`RasterIcon` uses the downloaded PNG alpha mask and `-wb-icon-color` CSS to preserve the existing theme,
hover, selection and action colors. It caches tinted bitmaps; it never generates path geometry. The app
and Maven builds need neither network access nor an icon library.

The resource folder contains the upstream ISC/Feather MIT `LICENSE` and `manifest.json` with source URLs
and SVG/PNG SHA-256 hashes. To extend the set, update `scripts/workbench-icons.json` and the enum mapping,
then use an existing Node.js/Sharp maintenance environment:

```powershell
$env:NODE_PATH = '<directory containing the installed sharp module>'
node scripts/export-workbench-icons.cjs --download
```

Downloads are pinned to the manifest version. Omit `--download` to export the cached SVG sources in
`scratch/raster-icons/<version>/` again. Sharp is an export tool only, not an IDEARM dependency.
Run `RasterIconsTest` and the design smoke after changing assets; inspect the `png-icons-*.png` galleries
at 16/28 px and the actual controls in both themes.

## Regenerate launcher assets

Run with `IDEARM_VISUAL_SMOKE` naming an output directory and `IDEARM_USER_DATA_DIR` naming an isolated
history directory. The walkthrough writes `idearm-logo.png` and `idearm-icon-<size>.png` for all seven sizes. Then:

```powershell
python scripts/export-branding.py scratch/workbench-review
```

`BrandLogo` samples the existing brand PNG for these snapshots. The exporter validates their dimensions,
embeds the small PNGs in ICO without further resampling, and copies the 512 px snapshot as `idearm.png`.
This packages the current raster mark; it does not export a modified SVG design master. No imaging libraries,
network requests or font dependencies are needed.
