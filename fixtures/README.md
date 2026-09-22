# Fixtures

Real output captured from the assemblers and linkers IDEARM integrates, used as test data for the diagnostic,
listing and map parsers (Infrastructure layer, F1 onwards).

| Folder | Content |
|---|---|
| `diagnostics/<tool>-<version>/<CASE>.txt` | Raw console output of one tool invocation for one sample source |
| `diagnostics/index.csv` | Tool, version, case, exit code and file of every diagnostic fixture |
| `listings/<assembler>-<version>/` | Listing files (`.LST`): line number, offset and bytes per source line |
| `maps/<linker>-<version>/` | Map files (`.MAP`): segment layout, publics and entry point |

- **Sources:** the sample programs live in [`spikes/asm/`](../spikes/asm/); each file's first comment states the
  expected problem and its line.
- **Tools and versions:** TASM 4.1 / TLINK 7.1 (captured in DOSBox-X), TASM 3.2 / TLINK 3.01 (captured in DOSBox
  0.74-3), ML 6.11 (native on Windows) and LINK 5.31 (captured in DOSBox 0.74-3).
- **Placeholder paths:** host paths of the spike workspace are replaced by `C:\IDEARM-FIXTURE`. DOS paths such as
  `S:\HELLO.ASM` are kept as produced (drive `S:` is the mounted source folder).
- **Regenerating:** run `spikes/scripts/s2-tasm-dosbox.ps1` (labels `tasm41-dosboxx` and `tasm32-dosbox074`) and
  `spikes/scripts/s3-masm611.ps1`, then `spikes/scripts/export-fixtures.ps1`.
- **Licensing:** these are short text excerpts of tool output kept for interoperability testing. No proprietary
  binaries are stored in this repository (ADR-004).
