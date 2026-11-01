# Manual input checklist (hotbar #20)

The keybinds and their routing cannot be automated (they need a real client and
input device). This is the honest manual pass to run in-game before merge. Tick
each item against a world with `ability_core` installed.

## Setup

- [ ] Install `ability-core` + `ability-hotbar` in a client instance.
- [ ] Assign at least one ability to a bar slot (so activation has a target).
- [ ] Open **Options → Controls** and confirm an **Ability Hotbar** group exists.

## Defaults and rebinding (REQ-HOT-10)

- [ ] All five defaults are present in the Ability Hotbar group: Ctrl+1..9,
      G, K, Right Ctrl.
- [ ] Each is rebindable via the Controls screen (no collisions with the
      verified landscape; note any conflict found).
- [ ] Vanilla **G** and **K** are unbound or confirmed non-conflicting, and the
      vanilla hotbar's plain **1..9** still switches the vanilla hotbar.

## Selection (REQ-HOT-09/10)

- [ ] **Ctrl + wheel up/down** moves the selected-slot cursor on the ability bar.
- [ ] Ctrl+wheel does **not** move the vanilla hotbar selection.
- [ ] Plain wheel (Ctrl released) behaves 100% vanilla - hotbar scrolls as usual.
- [ ] Holding Ctrl and scrolling produces **no double-scroll** (one step per notch).

## Activation (REQ-HOT-11/20)

- [ ] **Ctrl + N** activates slot N directly, without switching the vanilla hotbar.
- [ ] **G** activates the currently selected slot.
- [ ] One tap = one activation (holding a key does not auto-repeat).
- [ ] The selected slot is **retained** after activation (unchanged unless you
      explicitly re-select).

## Fail-safe (REQ-HOT-19)

- [ ] Activating an **empty** slot does nothing and consumes nothing.
- [ ] Activating a **restricted / unaffordable / on-cooldown** slot fails with
      zero resource consumed (server-side refusal).

## Menu / mode seams (REQ-HOT-12/16)

- [ ] **K** is bound and shows the "Open ability menu" action (menu flow itself
      lands with hotbar#27 - expected no-op for now).
- [ ] **Right Ctrl** is bound and shows the "Toggle ability mode" action (mode
      flow lands with hotbar#17 - expected no-op for now).
