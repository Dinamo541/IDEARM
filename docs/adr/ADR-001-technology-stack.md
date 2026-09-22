# ADR-001 — Technology stack

- **Status:** Proposed (mostly verified by spike S1; installer check pending)
- **Date:** 2026-09-14
- **Reference:** `docs/technical-plan.md` §18

## Context
The IDE needs a code editor, a VS Code-like UI, robust management of external processes (assemblers, linkers,
DOSBox), lower layers that are testable without a UI (ADR-007) and, later, an 8086 emulator of its own. A Maven +
JavaFX skeleton already existed (NetBeans template), and the host has Temurin 25.0.3 and Maven 3.9.16.

## Options considered
Java 25 + JavaFX · C#/.NET + Avalonia · C++ + Qt 6 · Electron + TypeScript · Tauri 2.

## Proposed decision
**Java 25 LTS + JavaFX 25**, multi-module Maven with JPMS. JavaFX is only allowed in the Presentation layer.

## Consequences
- (+) Excellent process handling (`ProcessHandle`, virtual threads, FFM for Job Objects), testable lower layers,
  continuity with the existing skeleton and the team's workflow.
- (−) Editor and terminal are weaker than Monaco/xterm.js → mitigated by ADR-005.
- The original `pom.xml` used `release 11`, but JavaFX 25 requires JDK 23+ → raised to `release 25`.

## Verification (S1)
- [x] The multi-module build succeeds with `release 25` and JavaFX 25.0.2 (`mvn install` ≈ 10 s, run with the former
      `idearm-core` + `idearm-app` layout).
- [x] Smoke run (`IDEARM_SMOKE=1 mvn -f idearm-app/pom.xml javafx:run`) opens and closes the window on Java 25.0.3 +
      JavaFX 25.0.2.
- [x] The Maven Enforcer rule bans `org.openjfx:*` in the Domain module, and `requireJavaVersion [25,)` is enforced.
- [x] `javafx:jlink` produces a runnable image (≈ 107 MB) whose launcher passes the smoke test.
- [x] AtlantaFX 2.1.0 (`atlantafx.base` module) works with JavaFX 25 (Primer dark/light themes).
- [x] The JavaFX native-access warning on JDK 25 is removed with `--enable-native-access=javafx.graphics`.
- [x] Rebuild after renaming `idearm-core` to `idearm-domain` (ADR-007) and adding EN/ES localization (ADR-006):
      `mvn clean install` passes, including the bundle key parity test; the smoke run switches English → Spanish.
- [ ] javac warns that the module name component `dinamo541` ends in digits; harmless, but decide before publishing
      whether module names keep the GitHub handle.
- [ ] jpackage installer (MSI/EXE): WiX Toolset is not installed on the machine; to be checked before F4.
- [ ] Confirm the enforcer rule fails the build when a JavaFX dependency is added below Presentation.
