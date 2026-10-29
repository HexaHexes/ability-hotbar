# Roadmap - tensura-ability-hotbar

Generated from and kept in sync with this repository's issues. The authoritative
status is always the issue list. The cross-repository requirements inventory
(REQ IDs) lives in `tensura-ability-core/docs/requirements.md` - this roadmap
references those IDs rather than duplicating their status.

## Phase 0 - Organisation and repository foundation

- [x] Repository created from `neoforge-mod-template` (bootstrap PR #1 merged)
- [x] Issue forms (feature / bug / investigation), labels, branch rulesets applied

## Phase 1 - Requirements and feasibility (spikes)

- [ ] #2 - ISS spell representation, icons and availability data (SPIKE-hot-1)
- [ ] #3 - Tensura skill/magic activation, icons, ability-mode state (SPIKE-hot-2)
- [ ] #4 - Keybind landscape and modifier gating (SPIKE-hot-3)
- [ ] #5 - Assignment persistence and migration (SPIKE-hot-4)
- [ ] #6 - ISS equipped-spellbook model and hotbar architecture, DEC-02 (SPIKE-sb-1)

Each spike closes only with verified findings **and** its implementation issues
created (investigation form contract).

## Phase 3 - Initial nine-slot hotbar

One implementation issue per requirement, created as spikes close:
REQ-HOT-01..11, REQ-HOT-13..15, REQ-HOT-17..21 (plus REQ-HOT-02/07/08).

## Phase 5 - Menu, mode integration, spellbook hotbar, stretch bars

- REQ-HOT-12 (management menu), REQ-HOT-16 (native ability-mode integration)
- REQ-SB-01..12 (equipped-spellbook hotbar - six issue groups per SPIKE-sb-1)
- REQ-HOT-22/23 (multiple cyclable hotbars - stretch, kept separate from SB)

## Phase 6 - Compatibility and release readiness

- REQ-TEST-02/03/05/07 slices for this repo; multiplayer and dedicated-server
  validation; configuration migration tests.

## Dependency notes

- Requires `tensura-ability-core` published artifact (PACKAGE_READ_TOKEN,
  step-scoped Gradle credentials).
- ISS features (spell provider, spellbook hotbar) are optional and gate off
  without ISS; the general hotbar must stay functional with only Tensura and/or
  battlewills present.
