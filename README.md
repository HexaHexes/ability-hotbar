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