# ADR-005 — Code editor component

- **Status:** Accepted (spike S5)
- **Date:** 2026-09-14
- **Reference:** `docs/technical-plan.md` §15, §18 · `spikes/REPORT.md` (S5)

## Context
The editor must highlight MASM/TASM syntax and, later, offer completion, hover, folding, outline and diagnostics.
JavaFX has no stable code editor control. The options evaluated were RichTextFX, the JavaFX incubator `CodeArea`
(module `jfx.incubator.richtext`, available since JavaFX 24) and Monaco inside a `WebView`.

## Evidence (S5, `loadmap.asm`: 104 KB, 4839 lines)

| Measure | RichTextFX 0.11.7 | JavaFX incubator `CodeArea` 25.0.2 |
|---|---|---|
| Load | 90 ms | 16 ms |
| Typing in one paragraph (p50 / p95 / max) | **6 / 11 / 34 ms** | 26 / 33 / 58 ms |
| Re-highlighting the whole file per keystroke | 50 / 59 / 79 ms | — |
| Stability | Third-party; **0.11.5 does not start on JavaFX 25** (fixed in 0.11.6/0.11.7) | First-party but incubating: API may change, prints an incubator warning, no public scroll-to-caret method found |

Both meet NFR-03 (< 50 ms) when highlighting is recomputed per paragraph. Monaco was not measured: both native
options already meet the target without a web stack.

## Decision
1. **RichTextFX 0.11.7 (or newer)** is the editor for the MVP, wrapped by an `EditorComponent` interface in the
   Presentation layer so it can be replaced.
2. **Highlighting is recomputed per paragraph** (Assembly comments and strings never span lines); re-highlighting
   the whole file on every keystroke is not allowed.
3. **JavaFX and RichTextFX are upgraded together**, guarded by an editor smoke test (open a large file, type,
   highlight). The 0.11.5 / JavaFX 25 break shows how tightly they are coupled.
4. **Fallback:** the JavaFX incubator `CodeArea`. It removes the third-party dependency and loads faster, but typing
   is about three times slower and its API is still incubating. Re-evaluate when it leaves incubation.
5. **Monaco in a `WebView`** is no longer plan B; it is a last resort only.

## Consequences
- (+) Typing latency far below the target on the largest real file available.
- (+) The language model stays in `idearm-language` (Domain), independent of the widget.
- (−) A JavaFX upgrade can break the editor until RichTextFX catches up; the smoke test and the fallback mitigate it.

## Verification
- [x] Keystroke latency < 50 ms with highlighting (p95 11 ms with RichTextFX 0.11.7).
- [x] Whole-file load and highlight well under a second.
- [x] Fallback measured (incubator `CodeArea`: p95 33 ms).
- [ ] Prototype of a completion popup anchored at the caret (F3).
- [ ] Folding and diagnostic squiggles (F3/F6).
- [ ] Editor smoke test that runs on every JavaFX or RichTextFX upgrade (F3).
