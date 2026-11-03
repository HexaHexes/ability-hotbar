# Ability Hotbar

Ability Hotbar gives the Tensura RPG pack one bar for every combat
ability: Tensura skills and magic, Iron's Spells spells and battlewills, all
assigned, selected and triggered from a single configurable hotbar that sits
alongside the vanilla one, never on top of it.

## What it does

*(Design phase, no release yet. The open issues on this repository hold the
full plan, research tables and open design decisions.)*

- **Nine-slot ability bar** - vanilla-style slots with dynamic ability icons,
  cooldown and resource overlays, and both direct-slot and selected-slot
  activation on configurable keys.
- **Management menu** - search, filter, preview and reassign abilities; your
  layout persists per player.
- Tensura skills and magic, ISS spells and battlewills share the bar through
  one contract. If an ability or its provider goes missing, the bar handles it.
- **Optional spellbook hotbar** - a separate, independently toggleable bar for
  the spells of your equipped Iron's Spells spellbook (with ISS installed).
- The vanilla controls are untouched: the modifier keys only claim the wheel
  and the number keys while they are held.

## Controls

All keys are rebindable in vanilla's Controls screen under the **Ability Hotbar**
category. Defaults (from SPIKE-hot-3):

| Action | Default | Notes |
|---|---|---|
| Select slot | **Ctrl + wheel** | Moves the selected-slot cursor only; plain wheel still drives the vanilla hotbar, and the wheel is only claimed while Ctrl is held. |
| Activate slot 1..9 | **Ctrl + 1..9** | Activates that slot directly. Plain 1..9 still switches the vanilla hotbar. |
| Activate selected slot | **G** | Fires the ability in the selected slot. |
| Open ability menu | **K** | Menu flow lands with the management menu. |
| Toggle ability mode | **Right Ctrl** | Aligned with Tensura's native mode key. |

Selecting a slot never fires an ability. Activating uses the selected slot and
leaves the selection where it was. Empty, restricted, unaffordable or on-cooldown
slots fail with no resource consumed.

Our controls never steal vanilla or chat input: with any screen open (chat,
inventory, a mod GUI) nothing is intercepted, and without the modifier held the
plain wheel and 1-9 behave exactly as if the mod were absent. The in-game
verification matrix is in `docs/input-gating-matrix.md`.

## Configuration

The bar's look is a **CLIENT** config (`display.*`) - display-only, never
policy; it is never synced to the server and cannot widen server rules. It
opens in NeoForge's own configuration screen (config UI mods are bypassed for
this mod so their editor cannot reset values you did not edit). The
slot *count* is two-tier: the client's preference is clamped to the server's
ceiling (see Persistence).

| Key | Default | Meaning |
|---|---|---|
| `display.slotsPerBar` | `9` | Preferred slots (clamped to the server's `hotbar.maxSlots`, hard cap 10). |
| `display.horizontal` | `true` | Bar orientation: `true` = left-to-right, `false` = top-to-bottom. |
| `display.anchor` | `ABOVE_HOTBAR` | Named position: `aboveHotbar`, `aboveXp`, `aboveStatus`, `leftEdge` or `rightEdge`. Each is a full 2D placement rule, so every value works with either orientation. |
| `display.statusAnchorMode` | `DYNAMIC` | Height used by `aboveStatus`: `DYNAMIC` tracks the player's hearts, armor and oxygen; `FIXED` uses the vanilla baseline and never moves. Read only for `aboveStatus`. |
| `display.opacity` | `1.0` | Bar opacity (0.0–1.0). |
| `display.showCooldowns` | `true` | Draw the cooldown overlay on slots. |
| `display.showCosts` | `true` | Draw the resource-cost badge on slots. |
| `display.offsetX` / `display.offsetY` | `0` | Pixel offset from the bar's anchor. |
| `display.scale` | `1.0` | Uniform render scale (0.5–2.0). |
| `display.showIcons` | `true` | Draw ability icons (falls back to a placeholder when none resolves). |

## Persistence

Your assignments are per-player data, stored in a server-authoritative attachment
and **kept on death**. The format is versioned so a pack update never destroys a
bar. Each hotbar is stored as its own bar with its own selected slot. Stored
NBT keys:

| Key | Type | Meaning |
|---|---|---|
| `schema_version` | int | Format version (currently `3`). A *newer* version than the mod supports loads as a valid empty bar instead of crashing. |
| `active_bar` | string | The **id** of the active bar (e.g. `ability_hotbar:bar_0` or a registered id), server-owned. An id that names nothing known re-anchors to the first stored bar on load (logged). |
| `bar_count` | int | Number of bars. Written explicitly, so a lowered `hotbar.maxBars` ceiling never truncates stored bars. |
| `bar_N` | compound | Bar `N`'s own keys: `bar_id`, `slot_count`, `selected_slot` and `slot_M` (below). |

Inside each `bar_N`:

| Key | Type | Meaning |
|---|---|---|
| `bar_id` | string | This bar's stable identity: `ability_hotbar:bar_N` for built-ins, the registration id for mod-added bars. |
| `slot_count` | int | Number of slots in this bar. Written explicitly, so lowering the config later never truncates stored slots. |
| `selected_slot` | int | This bar's selected-slot index (clamped into range on load). |
| `slot_M` | string | The ability id in slot `M` of this bar. Absent = empty slot. |

Upgrade and preservation rules:

- A save written before the current format (flat keys at the top level, or the
  nested format with an integer `active_bar`) upgrades forward with its values
  retained **as built-in bar 0** and the active bar set to its id; the slot
  count is inferred from the highest `slot_M` key present.
- **Identity is inert**: a stored `bar_id` whose registration is gone is kept,
  never remapped onto a later registrant, and re-adopted if the same mod
  registers again. A `bar_id` that is missing or corrupt keeps its data under
  an `unidentified` id - preserved, never wrongly claimed (ADR-012).
- On every load the save is reconciled with the live registry: a newly
  registered assignable bar is appended with fresh state, and an active id that
  is neither stored nor registered re-anchors to the first stored bar (both
  logged).
- Bars are never dropped to satisfy a lowered ceiling: a state saved with more
  bars than the current `hotbar.maxBars` keeps them all (ADR-011).
- A stored id whose provider is missing is **preserved raw**, so the slot renders
  as diagnosed-inert rather than being dropped (REQ-HOT-21).
- An id that cannot even be parsed is logged - naming its bar and slot - and
  left empty, never crashing the load (REQ-HOT-15).

Migration ladders (which saved versions upgrade on load):

| From | To | Ladder |
|---|---|---|
| Pre-versioned (no `schema_version` - read as `0`) | `3` | Yes - values retained as built-in bar 0, upgrade logged |
| `1` (flat single-bar format) | `3` | Yes - values retained as built-in bar 0, upgrade logged |
| `2` (nested bars, integer `active_bar`, no `bar_id`) | `3` | Yes - built-in ids assigned by position, active integer converted to its id, upgrade logged |
| `3` (current) | - | Current version, no ladder needed |

Each future schema version adds its row here; upgrades are logged with their
from/to versions so a pack update is visible in `latest.log`.

## Dependencies
| | Mod | Why |
|---|---|---|
| Required | Minecraft / NeoForge | 1.21.1 / 21.1.259 |
| Required | Tensura Ability Core | The ability contracts the bar reads and activates |
| Optional | Tensura: Reincarnated | Skills and magic appear in the bar when installed |
| Optional | Iron's Spells 'n Spellbooks | Spells and the equipped-spellbook hotbar appear when installed |
| Optional | Tensura Battlewills | Battlewills appear in the bar when installed |

## Credits
- Built on [NeoForge](https://neoforged.net/).

## License

GNU Lesser General Public License v2.1 - see [`LICENSE`](LICENSE).
Copyright (c) 2026 Bitwise Madness.

## AI disclosure

Parts of this project were written with the assistance of AI coding tools.
AI-assisted code, data and documentation ship only after human review by the
maintainers.