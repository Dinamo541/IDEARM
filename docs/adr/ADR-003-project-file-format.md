# ADR-003 — Project file format

- **Status:** Proposed
- **Date:** 2026-09-14
- **Reference:** `docs/technical-plan.md` §7, §16

## Context
Each project must store its logical configuration (what is built, with what, and where it runs) without physical
machine paths. Mastermind shows the opposite problem: `bin/dosbox.conf` and `Build.bat` hold absolute paths and
became obsolete.

## Proposed decision
- **`idearm.toml`** at the project root (versioned), with a `schema` field and migrations.
- **`.idearm/local.toml`** and **`.idearm/workspace.json`** for local data (not versioned).
- Global tool registry in **`%APPDATA%\IDEARM\tools.toml`** and IDE preferences (UI language, theme) in
  **`%APPDATA%\IDEARM\settings.toml`**.
- Read and written with Jackson `jackson-dataformat-toml` behind a `ProjectStore` interface, with atomic writes
  (temporary file + rename).

## Naming convention (2026-09-16)
Every file the IDE writes into a project keeps the name the user chose and uses a lower-case extension
(`FileNames` in the domain): a new project starts with `src/main.asm`, a file typed as `Utils.ASM` is stored as
`Utils.asm`, and build outputs are `obj/<name>.obj`, `lst/<name>.lst`, `bin/<name>.exe` and `map/<name>.map`.
Existing upper-case sources still build; only what the IDE generates follows the rule.

New projects describe their sources with a pattern, so files added to `src/` join the build:

```toml
[sources]
entry   = "src/main.asm"
modules = ["src/*.asm"]   # the entry is excluded automatically
include = []
exclude = []              # patterns too
```

Renaming the entry, an explicit module or an include folder from the explorer updates these paths.

## Consequences
- (+) Readable, supports comments, a familiar format (Cargo, pyproject).
- (−) Saving from the properties dialog rewrites the file in canonical form: hand-written comments are not
  preserved (accepted limitation).
- Everything physical (paths, command lines, DOSBox confs, 8.3 names) is **derived**; it is never stored.

## Pending verification
- [ ] Round trip TOML → model → TOML without data loss.
- [ ] Behavior with an unknown or future `schema`.
