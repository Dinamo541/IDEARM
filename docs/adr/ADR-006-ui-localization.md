# ADR-006 — UI localization and project language

- **Status:** Accepted (user requirement)
- **Date:** 2026-09-14
- **Reference:** `docs/technical-plan.md` FR-63, NFR-11, §15, §22 (F3)

## Context
The user requires every project artifact to be in English, while the IDE itself must let users switch its
interface between English and Spanish. The IDE is aimed at students of a Spanish-speaking course, so Spanish must
be a first-class language, not an afterthought.

## Decision
1. **Project language:** code, identifiers, comments, documentation, ADRs, scripts and their messages, sample
   sources, file names and commit messages are written in English.
2. **UI languages:** English (default) and Spanish. The user changes the language at runtime from the UI; visible
   text updates immediately without a restart, and the choice is persisted in the global settings (F3).
3. **Mechanism:** Java `ResourceBundle` properties files (UTF-8) in `idearm-app`, one per language
   (`messages_en.properties`, `messages_es.properties`), accessed through a `Localization` service that exposes the
   current locale as an observable property and returns bound strings. Each language name is shown in its own
   language ("English", "Español").
4. **Explicit per-language bundles:** there is no base `messages.properties`. Requesting English must never fall back
   to the JVM default locale (which is Spanish on the development machine), and `ResourceBundle.Control` cannot be
   used from a named module.
5. **Consistency:** unit tests check that both bundles define the same keys, that every key the code asks for
   exists (a missing one is a `MissingResourceException` the moment that screen opens) and that no text is left
   defined but unused. No UI text is hard-coded.
6. **View models publish keys, not sentences:** status and caret text travel as a `Message` (key plus arguments)
   that the view resolves through `Localization`, so switching the language also retranslates text that is already
   on screen, such as the result of a build that has already finished.
7. **What is not translated:** raw output of external tools (TASM, TLINK, ML, DOSBox) is shown as produced; the IDE's
   own explanations around it are localized.
8. **Content beyond UI strings:** the instruction knowledge base carries English and Spanish descriptions, and new
   project templates use comments in the UI language selected when the project is created.

## Consequences
- (+) Spanish-speaking students get a native experience; contributors work in a single language.
- (−) Every UI string needs two translations; the key parity test prevents one language from falling behind.
- (−) Educational content effort doubles; the knowledge base format must hold both languages per entry.

## Addendum (2026-09-17): problems reported by the IDE

- Tool output (TASM, MASM, NASM, ld) is shown in the tool's own words, so students can search for it.
- Problems the IDE itself reports carry a stable code and the values their message mentions
  (`DomainException.arguments()`, `Diagnostic.arguments()`). The bundles translate each one as
  `diagnostic.<code>`, formatted with those values when the problem is displayed; the English sentence is the
  fallback and remains visible as a tooltip. `MessageBundlesTest` requires a translation for every code the lower
  layers use, and no translation without a code.
