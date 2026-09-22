# Contributing to IDEARM

Help make Assembly easier to learn and inspect. Useful contributions include clear bug reports, documentation
fixes, small example programs, accessibility improvements and focused code changes.

## Before you start

- Search [existing issues](https://github.com/Dinamo541/IDEARM/issues) for related work. For a substantial
  feature or architectural change, open an issue explaining the problem and proposed approach first.
- Read [PLAN.md](PLAN.md) for project rules and verified facts, and the
  [architecture decisions](docs/adr/) for the reasoning behind the design.
- Keep code, comments, documentation and commit messages in **English**. The application interface supports
  both **English and Spanish**; UI changes must update both message bundles.
- Keep proprietary tools and their binaries out of commits, attachments, CI and distributable packages.

## Development setup

Use JDK 25 and Maven 3.9 or newer. Clone your fork, then run from the repository root:

```sh
mvn install
mvn -f idearm-app/pom.xml javafx:run
```

See the [installation guide](README.md#installation) for platform requirements and external Assembly tools.
The default test suite does not require TASM, MASM, NASM or DOSBox. Register tools through
**Help → Tool Doctor...** when working on integrations that use them.

## Design conventions

The dependency direction is `Presentation → Application → Domain ← Infrastructure`.

- Keep JavaFX in the presentation layer and file/process I/O out of the domain.
- Add assembler-specific behavior through provider and adapter ports. Keep concrete infrastructure wiring in
  composition roots; desktop views and view models receive services through `WorkbenchServices`.
- Represent UI messages with localization keys. Update `messages_en.properties` and `messages_es.properties`
  together, including accessible names and failure messages.
- Preserve source encodings, project portability and the separation between project configuration and local
  tool paths. Projects must not depend on a contributor's machine-specific directories.
- Keep changes focused and follow the style of the surrounding code. Add tests for meaningful behavior changes
  and regression cases; documentation-only edits need link, command and rendering checks.

## Validation

Run the standard checks before submitting code changes:

```sh
mvn verify
```

For tests only, use `mvn test`. By default Maven excludes these tags:
`requires-tasm`, `requires-masm`, `requires-nasm` and `requires-dosbox`.

With TASM, DOSBox, NASM, GNU ld and GDB available, include local integration tests:

```sh
mvn test -Plocal-tools
```

This profile still excludes `requires-masm`. To include MASM tests as well, first register MASM 6.11 and its
companion tools, then clear the exclusions explicitly:

```sh
mvn test -DexcludedGroups=
```

For the Linux CI selection, which uses freely available tools and excludes TASM/MASM:

```sh
xvfb-run -a mvn -B verify -DexcludedGroups=requires-tasm,requires-masm
```

That command requires NASM, binutils, GDB, DOSBox, Xvfb and xauth. See the
[CI workflow](.github/workflows/ci.yml) for its setup. UI changes should also be checked in both themes and
languages; the [workbench verification guide](docs/workbench-shortcuts.md#maintenance-and-verification)
documents the desktop smoke test and isolated history configuration.

## Reporting bugs

Use the [bug report form](https://github.com/Dinamo541/IDEARM/issues/new?template=bug_report.yml). Include:

- IDEARM version or commit, operating system and installation method.
- A minimal reproduction and the expected and actual behavior.
- Target profile, assembler/linker versions and relevant Tool Doctor output.
- Relevant Build Log output and a minimal `idearm.toml` or source example when applicable.

Remove credentials, personal paths and unrelated private content from logs. Share only source and materials
you have permission to publish; do not attach proprietary tool binaries.

## Submitting a pull request

1. Create a branch in your fork and make a focused change.
2. Update documentation or examples if the user-facing behavior changes.
3. Run the checks relevant to your change and inspect `git diff --check`.
4. Open a pull request describing the problem, resulting behavior and validation. Mention any checks you could
   not run and why. For UI changes, include screenshots in the relevant themes and languages.

Avoid generated build output, local tool installations and unrelated formatting changes. Small, reviewable
pull requests make it easier to verify both the student experience and the toolchain behavior.
