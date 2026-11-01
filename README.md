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

The bar's look is a **CLIENT** config (`display.*`) — display-only, never
policy; it is never synced to the server and cannot widen server rules. The
slot *count* is two-tier: the client's preference is clamped to the server's
ceiling (see Persistence).

| Key | Default | Meaning |
|---|---|---|
| `display.slotsPerBar` | `9` | Preferred slots (clamped to the server's `hotbar.maxSlots`, hard cap 10). |
| `display.horizontal` | `true` | Bar orientation: `true` = left-to-right, `false` = top-to-bottom. |
| `display.opacity` | `1.0` | Bar opacity (0.0–1.0). |
| `display.showCooldowns` | `true` | Draw the cooldown overlay on slots. |
| `display.showCosts` | `true` | Draw the resource-cost badge on slots. |
| `display.offsetX` / `display.offsetY` | `0` | Pixel offset from the bar's anchor. |
| `display.scale` | `1.0` | Uniform render scale (0.5–2.0). |
| `display.showIcons` | `true` | Draw ability icons (falls back to a placeholder when none resolves). |

## Persistence

Your assignments are per-player data, stored in a server-authoritative attachment
and **kept on death**. The format is versioned so a pack update never destroys a
bar. Stored NBT keys:

| Key | Type | Meaning |
|---|---|---|
| `schema_version` | int | Format version (currently `1`). A *newer* version than the mod supports loads as a valid empty bar instead of crashing. |
| `slot_count` | int | Number of slots. Written explicitly, so lowering the config later never truncates stored slots. |
| `selected_slot` | int | The selected-slot index (clamped into range on load). |
| `slot_N` | string | The ability id in slot `N`. Absent = empty slot. |

Upgrade and preservation rules:

- A compound written before `schema_version` / `slot_count` existed upgrades
  forward with its values retained; the slot count is inferred from the highest
  `slot_N` key present.
- A stored id whose provider is missing is **preserved raw**, so the slot renders
  as diagnosed-inert rather than being dropped (REQ-HOT-21).
- An id that cannot even be parsed is logged and left empty, never crashing the
  load (REQ-HOT-15).

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