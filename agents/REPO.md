# Repo rules - ability-hotbar

Repo-specific rules and notes. The org-wide `AGENTS.md` beside this file is
maintained in [`neoforge-mod-template`](https://github.com/HexaHexes/neoforge-mod-template)
and pulled in verbatim - **never hand-edit it.** Everything in this file is this
repo's own and is edited freely; keep it aligned with the org rules rather than
restating them.

**Repo:** `HexaHexes/ability-hotbar` - **Mod ID:** `ability_hotbar` - **Branch:** `main`
**Platform:** Minecraft 1.21.1 / NeoForge 21.1.259 / JDK 21 - **Jar:** `build/libs/ability_hotbar-<version>.jar`

A configurable nine-slot ability hotbar for the Tensura RPG pack: vanilla-style
slots and textures with dynamic icons, cooldown and resource overlays, direct
and selected-slot activation, an ability management menu with search and
filters, per-player assignment persistence, provider support for Tensura
skills/magic, ISS spells and battlewills, plus an optional independently
toggleable equipped-spellbook hotbar (ISS only).

**Dependencies**
- Required: Minecraft, NeoForge, and `tensura-ability-core` (consumed from
  GitHub Packages with the org secret `PACKAGE_READ_TOKEN`, scoped to the
  Gradle step only - see AGENTS.md).
- Optional: `tensura` (skill/magic provider), `irons_spellbooks` (spell provider
  and the spellbook hotbar - that feature must gate off entirely without ISS),
  `ability_battlewills` (battlewill provider). Optional providers gate behind
  `ModList.isLoaded`; a missing provider disables only its own features.
- **Resource-cost boundary** (ability-hotbar#117): this repo only fires
  activation intents and displays core's refusal outcomes — validation,
  charging, providers and consumers all live in `tensura-ability-core`'s
  pipeline. No resource imports or cost math here, ever.

**Release plan:** [docs/release-plan.md](https://github.com/HexaHexes/ability-core/blob/main/docs/release-plan.md) - the release train, per-repo checklist, core-before-consumers sequencing and ALPHA_RELEASES state (hub: ability-core).

**Test:** `./gradlew runGameTestServer` - `AbilityHotbarGameTests` (3 tests). `expectedTests = 3`
in `build.gradle` must match the `@GameTest` count.

**Gotchas**
- **Vanilla input is sacred.** Modifier-gated controls (Ctrl+wheel, Ctrl+number)
  must never intercept the plain wheel, hotbar keys, chat or other GUI contexts.
  Regression-test every input path in-game - GameTests cannot prove this.
- Client HUD work cannot be proven headless: the honest gate is build green plus
  the maintainer's in-game check (see AGENTS.md).
- A saved assignment referencing a missing ability must be preserved, not
  silently replaced or dropped.
- The equipped-spellbook hotbar is independent of the general hotbar and of the
  stretch goal for multiple cyclable hotbars - do not entangle the three.