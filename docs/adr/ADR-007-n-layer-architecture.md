# ADR-007 — N-layer architecture with dependency inversion

- **Status:** Accepted (user requirement)
- **Date:** 2026-09-14
- **Reference:** `docs/technical-plan.md` §9, §10, §19, §22 (F1)

## Context
The user asked for an N-layer architecture. IDEARM combines a desktop UI, orchestration of external processes
(assemblers, linkers, DOSBox, debuggers), Assembly project rules and I/O (files, TOML, processes). The reference
scripts show what happens without layers: Mastermind's `build.py` mixes tool detection, DOS batch generation,
console prompts, cleanup and packaging in one file, and `debug.py` reaches into its internals.

The earlier plan already grouped tool families as vertical modules and used SPI interfaces, but it did not define
explicit layers or dependency rules between them.

## Options considered
1. **Classic strict N-layer** (Presentation → Business → Data access): familiar, but the business layer would depend
   directly on DOSBox, TOML and `ProcessBuilder`, so rules could not be tested without real tools and every new
   assembler would change the business layer.
2. **N-layer with dependency inversion:** same layers and top-down call flow, but the Domain owns the interfaces
   (ports) and Infrastructure implements them.
3. **Feature modules only** (previous plan): good for tool families, but no guarantee that UI, orchestration and
   rules stay apart.

## Decision
Option 2, with four layers:

| Layer | Modules | Responsibility | Depends on |
|---|---|---|---|
| L1 Presentation | `idearm-app` (JavaFX, MVVM), `idearm-cli` | Views, view models, commands, localization EN/ES, composition roots | L2 (reads L3 records) |
| L2 Application | `idearm-application` | Use cases, async tasks, cancellation, application events | L3 |
| L3 Domain | `idearm-domain`, `idearm-language` | Model, rules, ports (SPI), Assembly language model | JDK only |
| L4 Infrastructure | `idearm-infrastructure`, `idearm-toolchain-dos`, `idearm-emu8086` | Implement ports: TOML, files, processes, DOSBox, TASM/MASM, parsers, emulator | L3 (and L2 ports) |

Rules:
1. Calls go from a layer to the layer directly below. Presentation may read immutable domain records returned by use
   cases, but never calls domain services or ports.
2. The Domain uses only the JDK: no JavaFX, no `java.nio.file.Files`, no `ProcessBuilder`, no networking, no TOML.
3. Application depends only on the Domain.
4. Infrastructure implements ports and never references Presentation or Application use cases.
5. Only the composition root of each front end knows concrete infrastructure classes; tool families are discovered
   with `ServiceLoader` (JPMS `uses`/`provides`).
6. Lower layers return message codes and arguments, never UI text; Presentation localizes them (ADR-006).
7. Tool families are vertical slices inside Infrastructure (one module per family), so adding MASM means adding
   Infrastructure code only.
8. A module is created when its first class exists (F0 has only `idearm-domain` and `idearm-app`).

## Consequences
- (+) Domain and Application are testable with fakes, without DOSBox, TASM or JavaFX.
- (+) The JavaFX UI and the CLI share the same use cases.
- (+) New toolchains, environments and debug backends plug in without touching upper layers (NFR-08).
- (−) More modules and some boilerplate (use case classes, ports). Mitigated by records instead of DTO/mapper
  frameworks, by letting Presentation read domain records, and by creating modules lazily.

## Enforcement
- [x] Maven Enforcer bans `org.openjfx` in the Domain module.
- [x] The ban covers every module below Presentation (`idearm-application`, `idearm-infrastructure`,
      `idearm-toolchain-dos`).
- [x] `ArchitectureTest` (in `idearm-cli`) checks the layer rules for Domain, Application and Infrastructure.
- [x] `PresentationArchitectureTest` (in `idearm-app`) checks the desktop application: only
      `io.github.dinamo541.idearm.app.bootstrap` may name an adapter, view models never depend on views, and view
      models never format user text. Everything else receives ports through `WorkbenchServices`.
- [x] JPMS: each module exports only its API packages, and every port implementation is registered both as a
      `provides` clause and under `META-INF/services`, because the CLI and its tests run on the class path where
      module declarations are ignored.

### The composition root in practice
`WorkbenchBootstrap.system()` (desktop) and `Main` (CLI) are the only places that name `TomlProjectRepository`,
`FileBuildWorkspace`, `ProcessService`, `DefaultToolRegistry` or `HybridToolRunner`. A screen that needs machine
state asks a port for it: the Tool Doctor reads `ToolRegistry.all()` from the registry the build uses, instead of
detecting tools a second time and reporting something the build would never pick.
