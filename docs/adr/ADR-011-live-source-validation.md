# ADR-011: Validating the source while it is typed, and one source of truth for a mnemonic

Date: 2026-09-23. Status: accepted. Implements **P2-06** of [`docs/action-plan.md`](../action-plan.md) and extends
it with an unknown-instruction rule.

## Context

Until now the IDE only told a student that a line was wrong after a build. Typing `MUV AX, 1` instead of
`MOV AX, 1` produced no feedback at all while editing: the word simply stayed uncoloured, which is easy to miss in
a file of a few hundred lines.

The pipeline could not have reported it even if something had asked. `AssemblyLexer` typed a word as
`TokenType.INSTRUCTION` only when it appeared in a private hardcoded set, so `MUV` became an `IDENTIFIER`;
`AssemblyParser.parseLine` built an `InstructionNode` only when the first token was already `INSTRUCTION`, so the
whole line was **dropped without a trace**; and every `LintRule` iterates `ast.instructions()`, which the line
never reached. `LintSource`, the use case written for exactly this, had no caller at all.

There were also three separate answers to "what is a valid mnemonic": the lexer's set (150 names), the editor
highlighter's regex (about 85) and `InstructionCatalog` (148 primaries plus aliases derived from the syntax
variants). Validating against one while colouring from another would have produced visible contradictions — a word
the editor left in plain text and the linter called valid, or the reverse.

`Diagnostic`'s `Location` carried a line and an optional column but no width, so nothing could say how much text a
problem covered.

## Evidence

Checked against `richtextfx-0.11.7.jar` before the design depended on it (rule 6), because the assumption that
RichTextFX cannot draw a wavy underline would have forced a custom node:

- `TextExt` declares the styleable properties `-rtfx-underline-color`, `-rtfx-underline-width`,
  `-rtfx-underline-offset`, `-rtfx-underline-cap`, `-rtfx-underline-dash-array`, `-rtfx-underline-double-gap` and
  **`-rtfx-underline-wave-radius`**.
- `ParagraphText` carries `waveRadius` in its `UnderlineAttributes` and builds an `UnderlinePath` out of
  `MoveTo`/`LineTo` segments. The familiar squiggle therefore renders natively, from a CSS class alone.
- `StyleSpans.overlay(other, merger)` exists as an instance method, which is what allows an underline layer to be
  combined with the syntax layer in the one `setStyleSpans` call RichTextFX gives us.

Measured on the development machine, on a generated 104 KB / 7 600-line DOS program:

- one `LintSource.execute` pass: 25 ms at best, 37 ms on average, off the JavaFX thread (P2-06 asks for under
  100 ms);
- one paragraph of highlighting: 0.014 ms, even though the instruction alternation grew from 85 names to 173
  (ADR-005 budgets typing at p95 11 ms).

## Decision

- **`InstructionCatalog` is the single source of truth.** `InstructionCatalog.knownMnemonics()` returns the
  primary names plus the variant aliases, and both `AssemblyLexer` and `AssemblySyntaxHighlighter` now key on it
  instead of keeping their own list. A mnemonic can no longer be coloured one way and judged another.
  `AssemblyLexerTest` guards it. The lexer's `SETCC` entry was dropped: it is a family placeholder, not an
  instruction. The mnemonics the catalog genuinely lacked were added to it — `RETF`, the generic `MOVS`, `CMPS`,
  `LODS`, `STOS`, `SCAS` forms and the `REP`/`REPE`/`REPZ`/`REPNE`/`REPNZ` prefix family — while `SAL` and `RETN`
  became variants of `SHL` and `RET`, which is what they are.

  The **x87**, **SETcc** and **CMOVcc** families followed, in a new `InstructionCategory.FLOATING_POINT` for the
  first of them. Each condition is its own entry with its own wording, the way the `Jcc` family was already
  written: the catalog derives an alias from the first token of every syntax variant, so folding `SETA` in with
  `SETZ` would have made the hover card explain the wrong test to a student reading it. Only genuine synonyms of
  one condition share an entry — `SETNBE` under `SETA`, `CMOVE` under `CMOVZ` — because those are two spellings of
  a single instruction. The x87 entries all report `FlagSummary.none()`: that unit answers through the condition
  bits of its own status word, which is why `FSTSW AX` followed by `SAHF` is how a floating-point comparison
  reaches a conditional jump at all.

- **The parser records what it does not recognise** instead of dropping the line: `UnknownStatementNode` for a
  word in instruction position that no branch matched, and `MacroNode` for a `name MACRO` definition. The parser
  stays neutral — it records "unrecognised", it does not decide "wrong" — because the word may be a macro
  invocation or a symbol from another file.

- **`UnknownInstructionRule` decides, and errs on the side of silence.** It reports a word only after four guards
  clear: the catalog does not know it, its shape is plain letters and digits of 2 to 10 characters, it is not a
  macro, label, procedure, constant or data name of this file, and `ProjectSymbolIndex` has no definition for it.

  The severity is `ERROR`. Once those guards have cleared, the word is not a macro, a label or a symbol of the
  project, so no assembler will accept it and the file will not build — and `ERROR` is what paints the mark red,
  which is the colour every IDE uses for a misspelling and the one the user asked for. The educational rules keep
  `WARNING` and their amber underline, which gives the two kinds of mark a meaning worth telling apart: amber for
  a questionable practice in a program that assembles, red for a word that cannot assemble at all. The cost of
  that choice is that a false positive looks fatal, so the guards, not the severity, are what keeps the rule
  honest; the one case that still slips through, a macro from a file missing from `[sources] modules`, is written
  up in `docs/troubleshooting.md`.

  There is no longer a family the rule skips wholesale. It briefly carried a guard that ignored every word
  starting with `F` and every `SET`/`CMOV` followed by a condition code, because the catalog described neither the
  **x87 floating-point unit** nor the **SETcc / CMOVcc conditionals** and their members could not be told apart
  from a typo. That guard silenced genuine typos too — `FADDD` was as invisible as `FADD` — so the families were
  written into the catalog instead and the guard was removed. The one exception that remains is not a family: a
  macro from a file missing from `[sources] modules`, described above.

- **A diagnostic can carry a span.** `Location` gained a fourth component, `length`, with a three-argument
  secondary constructor so the six toolchain diagnostic parsers stay untouched — no assembler reports a column,
  let alone a width, so theirs stays null and the editor falls back to the word at that column.

- **Validation is debounced and runs off the JavaFX thread.** `LiveLintCoordinator` schedules a pass 400 ms after
  typing stops, cancelling the one before it, and only the reading of the text happens on the JavaFX thread —
  once per pause rather than once per keystroke. Which rules run depends on the target: the DOS rules on a DOS
  project, and on any other target only the unknown-instruction rule, since warning a Linux program that it never
  returns to DOS would be wrong.

- **Build problems and live problems are separate.** `BottomPanelViewModel` keeps two lists and recomposes the
  visible one, so a validation pass every few hundred milliseconds cannot erase what the assembler reported.

- **The underline is a style class, and the hover explains it.** `.idearm-diagnostic-error` and
  `.idearm-diagnostic-warning` in `editor.css` use theme colours so both themes stay readable, and
  `EditorComponent.setDiagnostics(...)` merges the marks into the same span pass as the syntax colours, because
  `setStyleSpans` overwrites a paragraph wholesale and a separate layer would be wiped by the next keystroke.
  Hovering a marked word shows the message and the closest real mnemonic, from
  `InstructionCatalog.suggest(...)`, in the hover card the instruction and symbol hovers already use.
