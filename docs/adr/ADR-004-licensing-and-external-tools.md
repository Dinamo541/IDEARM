# ADR-004 — Licensing and external tool management

- **Status:** Accepted (user decision: public distribution on GitHub)
- **Date:** 2026-09-14
- **Reference:** `docs/technical-plan.md` §13, §17

## Context
TASM (Borland/Embarcadero) and MASM 6.11 (Microsoft) are proprietary and not redistributable. Turtoria downloads
TASM from an unofficial GitHub mirror, and Mastermind bundles MASM inside its executable. DOSBox-X and Staging are
GPL-2; NASM is BSD-2; JWasm/UASM use the OWPL; the DEBUG.COM shipped with Staging has an MIT-style license + public
domain.

## Decision
1. The repository, CI and installer **neither contain nor download** TASM or MASM.
2. The IDE **detects** existing installations or lets the user **register them manually** (version read from the
   binary without running it, companion files, SHA-256).
3. Managed downloads ("Desirable" phase) are limited to free tools, with a fixed official URL, pinned version,
   SHA-256 and a visible license.
4. If DOSBox-X is bundled with the installer: include the GPL license and a link to the source code.
5. Tests that need proprietary tools run locally only (tags `requires-tasm` / `requires-masm`); public CI only runs
   unit tests and fixtures.
6. The user's projects (Mastermind, Turtoria, Architecture_Project_2) are used as **local** tests only; the repo
   contains samples written from scratch.
7. Spike outputs that may contain proprietary tool output live in `spikes/out/`, which is git-ignored. Curated
   error-message fixtures (short text lines) may be committed.

## Consequences
- (+) Legal publication on GitHub.
- (−) The first run requires the user to supply their own TASM or MASM copy; the UX must guide them (Tools /
  Doctor page).
