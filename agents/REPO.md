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
  `tensura_battlewills` (battlewill provider). Optional providers gate behind
  `ModList.isLoaded`; a missing provider disables only its own features.

**Test:** `./gradlew runGameTestServer` - `AbilityHotbarGameTests` (1 test). `expectedTests = 1`
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