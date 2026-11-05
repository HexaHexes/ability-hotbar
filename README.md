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
| Switch bar prev/next | **Alt + wheel** | Cycles hotbars, skipping bars with no assigned abilities by default (`display.skipEmptyBars`). Claimed only while Alt is held; Ctrl+wheel wins when both are held. The scroll event is cancelled at `HIGHEST` priority, before other mods' bridges see it — Alt+wheel never also triggers Tensura Reincarnated's ability-preset scrolling (hotbar#106). |
| Activate slot 01..09 | **Ctrl + 1..9** | Activates that slot directly. Plain 1..9 still switches the vanilla hotbar. Also moves the selected-slot cursor to that slot, like the vanilla number keys (hotbar#108). |
| Switch to bar 1..10 | **Alt + 1..9, Alt + 0** | Selects a bar directly (never skips). Plain digits still drive the vanilla hotbar; a digit beyond the cycle set does nothing. |
| Activate selected slot | **G** | Fires the ability in the selected slot. |
| Open ability menu | **Grave** (left of 1) | Opens the ability management menu (rebindable; see below). Does nothing when no mod has registered any ability - there would be nothing to manage (hotbar#107). |
| Toggle ability mode | **Right Ctrl** | Aligned with Tensura's native mode key. |

Selecting a slot never fires an ability. Activating uses the selected slot and
leaves the selection where it was. Empty, restricted, unaffordable or on-cooldown
slots fail with no resource consumed. Costs themselves are core-side: the hotbar
only sends the activation intent, and Tensura Ability Core's pipeline validates
and charges resources — the resource providers and consumers live there, never
here.

Abilities can carry a target: when an ability's definition is tagged
**`targeted`** and you activate it with an entity under the crosshair, that
entity rides along as the activation's target hint and the server resolves it
before the ability runs. Untagged abilities - or activating with nothing aimed
at - send no hint at all, exactly as before; which entity you were pointing at
is never trusted from the client.

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
| `display.slotsPerBar` | `9` | Preferred slots (clamped to the server's `hotbar.maxSlots`, hard cap 18). Bars longer than nine slots render as nine-slot segments, each wearing the full vanilla hotbar texture. |
| `display.hotbarCount` | `3` | Built-in hotbars in the cycle set (clamped to the server's `hotbar.maxBars`). Registered hotbars are always included and never clipped by this window. |
| `display.skipEmptyBars` | `true` | Alt+wheel cycling skips bars where every slot is unassigned (inert ids count as assigned). Direct selection (Alt+digit) always lands where pressed. |
| `display.switchToast` | `true` | Show the newly active bar's name briefly above the bar after a switch. |
| `display.horizontal` | `true` | Bar orientation: `true` = left-to-right, `false` = top-to-bottom. |
| `display.anchor` | `ABOVE_HOTBAR` | Named position: `aboveHotbar`, `aboveXp`, `aboveStatus`, `leftEdge` or `rightEdge`. Each is a full 2D placement rule, so every value works with either orientation. |
| `display.statusAnchorMode` | `DYNAMIC` | Height used by `aboveStatus`: `DYNAMIC` tracks the player's hearts, armor and oxygen; `FIXED` uses the vanilla baseline and never moves. Read only for `aboveStatus`. |
| `display.opacity` | `1.0` | Bar opacity (0.0–1.0). |
| `display.showCooldowns` | `true` | Draw the cooldown overlay on slots. |
| `display.showCosts` | `true` | Draw the resource-cost badge on slots. |
| `display.offsetX` / `display.offsetY` | `0` | Pixel offset from the bar's anchor. |
| `display.scale` | `1.0` | Uniform render scale (0.5–2.0). |
| `display.showIcons` | `true` | Draw ability icons (falls back to a placeholder when none resolves). |

## Multi-hotbar (stretch)

Beyond the first bar you can cycle additional hotbars, each with its own
assignments and its own selected slot (REQ-HOT-22):

- **Switching** - Alt+wheel cycles prev/next, Alt+1..9 and Alt+0 jump
  directly (see Controls). Only the active bar renders, and switching never
  casts an ability, never moves the selection and never resets the ability
  mode.
- **Cycle set** - the built-in window (`display.hotbarCount`, clamped by the
  server's `hotbar.maxBars`), then every mod-registered bar in registration
  order. Registrations are never clipped by the window, and a digit beyond
  the set does nothing (fail-closed).
- **Skip-empty** - with `display.skipEmptyBars` (default on) the wheel steps
  over bars whose slots are all unassigned; direct selection never skips, and
  when every bar is empty the wheel still steps normally.
- **Registered hotbars** - other mods declare their own bars through the
  registration API (ADR-012 in `ability-core`). Assignable registrations get
  their own state; non-assignable bars (their content belongs to the
  registering mod) are still switchable, and declare what they show through
  the **content-provider API**: a provider supplies its cells each frame
  (rendered as vanilla items, so tint and foil come from the item itself),
  its empty-cell texture, the framed cell, and the emptiness answer the
  wheel skips by. A bar without a provider counts as empty for skipping,
  and a provider that throws makes the bar draw nothing for that frame
  (logged once) rather than crashing. The owner can also **seed its verbs
  into the bar's cells** server-side - the cell then activates that verb
  with the cell as its context, while the cell's content stays the
  provider's item (hotbar#89).
- **Notification** - after a switch the new bar's name appears briefly above
  the bar, governed by `display.switchToast`.
- **Scoped abilities** - a mod may declare that an ability belongs to
  exactly one registered bar (a release verb that only means something on
  its own bar, for example). It is then rejected for assignment on every
  other bar and refused activation outside it; abilities without a
  declaration stay freely assignable everywhere.

## Ability menu

Press the **grave accent** key - the one left of `1`, below Esc (rebindable)
- to open the ability management menu: pick a bar
from its tabs (every assignable hotbar - the built-in window plus
registered ones - and never an empty one hidden), pick a slot, then click
an ability to assign or replace it; **Clear slot** removes it. Search,
category and tag filters narrow the list and are remembered as client
preferences (`menu.*`), while the bar's contents themselves stay server
data. Every change is a server-validated request naming the target bar,
so a stale click is rejected and the screen repaints from the server's
answer. Registered non-assignable bars are never listed - their content
belongs to the owner (ADR-012). The on-screen pass lives in
`docs/menu-checklist.md`.

Screenshots: _placeholder_.

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
- **Content-owned bars store mod-seeded assignments too**: the mod that owns
  a non-assignable bar writes its verbs into its own cells server-side (they
  persist and sync like every other cell), while players can never write
  them - the menu excludes the bar and the assignment path rejects it
  outright (ADR-012 §3 amendment, hotbar#89).

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