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
  `ability_tensura` (battlewill provider). Optional providers gate behind
  `ModList.isLoaded`; a missing provider disables only its own features.
- **Resource-cost boundary** (ability-hotbar#117): this repo only fires
  activation intents and displays core's refusal outcomes — validation,
  charging, providers and consumers all live in `tensura-ability-core`'s
  pipeline. No resource imports or cost math here, ever.

**Bar textures** (issue #125): a registered bar may optionally brand its two
sprites via `HotbarRegistry.register(..., BarTextures)` or
`HotbarContentProviders.registerContentBar(..., BarTextures)` - background
and selection; null halves fall back to the vanilla `hud/hotbar` /
`hud/hotbar_selection` (built-ins never register, so they always resolve
vanilla). The ids must resolve in the GUI sprite atlas (`blitSprite`): ship
`assets/<namespace>/gui/sprites/<path>.png` plus a directory source for your
namespace in `assets/<namespace>/atlases/gui.json` - the data-driven
registration vanilla's own sprites use.

**Hotbar styles** (issue #127): `HotbarStyles.register(id, displayNameKey,
BarTextures)` names a texture set for reuse and presentation; content bars
select one at registration (`registerContentBar(barId, key, styleId)` -
register styles before the bars that use them, unknown ids fail loudly with
both ids). Assignable bars are player-owned: the menu's style cycler writes
the CLIENT `menu.barStyles` preference (`barId=styleId;...`), and
`HotbarStyles.effective` applies a player choice to **assignable bars only**
- a content bar keeps its mod's brand, and stale or malformed entries fall
back to the registration's textures (vanilla by default), never a crash.

**Menu styles** (issue #129): `MenuStyle` is the ability menu's palette and
backdrop parameters as data (background/raised/text/muted/accent/action +
world dim and panel opacity percentages, `alpha(rgb, percent)` baking the
percentage into the alpha byte). `MenuStyles.register(id, displayNameKey,
style)` adds a style at setup (loud duplicate/null/blank ownership, same
contract as bars/styles); the shipped default is pre-registered core-owned
as `ability_hotbar:trs_saodark` - **`MenuStyle.TRS_SAODARK` is a palette
sync from Tensura RPG System's default `InterfaceTheme.SAO_DARK`** (their
enum, MIT; recorded with provenance in the record's javadoc - re-syncing
their palette later is a deliberate edit, never an automatic coupling):
background `0x131D2C`, raised `0x22354A`, text `0xEAF4FF`, muted
`0x95ACC2`, accent `0x67DDED`, action `0xFFC679`, dim `15%`, opacity
`78%`. The screen resolves the CLIENT config `menu.style` (empty =
built-in; stale/malformed falls back, never crashes) through
`MenuStyles.active` and draws **every** fill and text through it - the
palette hex literals live in `MenuStyle` only, enforced by a grep fixture
(the inert-diagnostic red is the one exception). The menu-style cycler pill
sits at the hint bar's right end (it once sat by the title; the #27
rework moved it), visible once a second style is registered, and writes the
preference.

**Menu labels** (issue #139): the menu never shows a raw category id, tag
or provider namespace. `MenuLabels` checks a convention translation key
first - providers ship these in their own lang files:
`ability_hotbar.category.<namespace>.<path>` (path separators become dots),
`ability_hotbar.tag.<tag>` and `ability_hotbar.provider.<namespace>` -
and falls back to the prettified raw value (separators to spaces, first
letter capitalised: `tensura:magic` reads as "Magic"). A grep fixture keeps
the raw sites out of the screen.

**Menu filtering and order** (issue #142): rows ship in the deterministic
default order **provider (id namespace) -> category -> lowest tag -> id**;
the category cycle is hierarchical over the open **`type:` subcategory
convention** (any -> category -> its `type:` tags, pref `menu.lastSubCategory`)
- a provider adopts `type:`-prefixed tags to subdivide its categories
(Tensura's eight skill/magic types are the first consumers). A pool without
`type:` tags behaves exactly as before.

**Menu tabs** (issue #154): tabs are **registered, not derived** -
`MenuTabs.register(id, displayNameKey, [icon | ItemStack], membership)` at
setup (loud ownership, one icon kind per tab, strip order = registration
order), rendered as the owner's **Icon/Item Stack + Text** identifier; the
selected tab's membership narrows the query alongside every other filter
(pref `menu.lastTab`, unknown ids fall back to All). Providers that keep
their abilities exclusive to their own hotbars (ability-iss,
pseudocore-curios) simply register nothing: no strip, pool unfiltered.
ability-tensura registers Skills/Magic/Battlewills (companion issue).

**Unlock gating and reconciliation** (issues #135/#147): the menu shows
**only unlocked abilities** - proven-unlearned ids filter out and unknown
always shows (`UnlockRule.provenUnlearned`, the never-guess map over core's
`AbilityUnlocks`); the server refuses slotting a proven-unlearned id while
still echoing state. Losing a unlock **clears the slot server-side**:
`UnlockReconciler` sweeps every snapshot and accepted apply in
`BarStateSync` (saved + echoed, no tick loop, one log line per cleared id,
unknown never touched); no client write exists for any of it.

**Menu layout** (issue #27): the screen follows the Tensura RPG System
skill-menu geometry (the design comment on that issue): a header band with
the title top-left and the target-bar selector top-right (chip + used/total
+ slot meter; the chip cycles bars), one collapsed filter strip (borderless
search pill + category/tag cycle chips), the full-width ability list with
**inline** hover metadata directly under the row (no detail pane, no
sidebar), and a bottom stack of chamfered slot cells over the hint bar.
`MenuChrome` (client) owns the drawing primitives - backdrop, chamfered
glass capsules/cells, meters - all fed by the active `MenuStyle`; the row
hit-test, slot width and meter math are pure and fixture-pinned
(`MenuGeometryTest`). Each slot cell with an assigned ability draws that
ability's `iconId` (issue #156: the rows' resolution, the item cells' 2px
padding) before its number; empty, unresolvable or icon-less cells - and
`display.showIcons = false` - keep the number-only cell (icon needs 16px
of cell width, the number 12 more; also fixture-pinned).

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