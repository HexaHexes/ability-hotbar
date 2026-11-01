# Manual input-gating matrix (hotbar #21, REQ-HOT-18)

The gating invariant ("our controls never steal vanilla or chat input") cannot
be proven headless - it needs a real client. `InputGateTest` covers the pure
decision; this is the in-game matrix the issue requires. Run against a world
with `ability-core` + `ability-hotbar` installed, and record observed results.

Legend: **vanilla** = behaves exactly as if the mod were absent · **ours** = the
hotbar control fires once · **n/a** = not applicable.

| Context | plain wheel | Ctrl+wheel | plain 1-9 | Ctrl+1-9 | G | K | Right Ctrl |
|---|---|---|---|---|---|---|---|
| No screen (in-game) | vanilla | ours (one slot step, vanilla bar still) | vanilla | ours | ours | ours | ours |
| Chat open | vanilla | vanilla | vanilla | vanilla | vanilla | vanilla | vanilla |
| Inventory open | vanilla | vanilla | vanilla | vanilla | vanilla | vanilla | vanilla |
| Any mod screen open | vanilla | vanilla | vanilla | vanilla | vanilla | vanilla | vanilla |

## Checks

- [ ] Each of the four contexts was exercised (no screen, chat, inventory, one mod screen).
- [ ] **Ctrl+wheel with no screen** moves exactly one slot per notch (no double-scroll)
      and the vanilla hotbar selection does *not* also move.
- [ ] **Ctrl+wheel / Ctrl+1-9 / G / K / Right Ctrl with any screen open** do nothing:
      nothing is intercepted, nothing is sent, the screen keeps full input.
- [ ] **Plain wheel** hotbar selection is identical with the mod installed vs a
      clean instance (side-by-side).
- [ ] **Plain 1-9** still switch the vanilla hotbar in every context.
- [ ] No `InputEvent` is cancelled outside the Ctrl+wheel branch (code-review gate).
- [ ] With **Controlling** (or another keybind mod) installed: our keys appear under
      the Ability Hotbar category and are rebindable (one in-game check).

## Why the explicit gate

NeoForge already fires `MouseScrollingEvent` only outside a screen, and vanilla
normally suppresses in-game keybinds while a screen is up - but the invariant is
ours, not theirs. `InputGate` states it explicitly and centrally so a remap, a
pack keybind mod, or an upstream change cannot silently break it, and so the
spellbook bar (#13), mode (#17) and menu (#27) reuse the same rule instead of
re-deriving it.
