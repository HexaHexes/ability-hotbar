# Manual input checklist (hotbar #20)

The keybinds and their routing cannot be automated (they need a real client and
input device). This is the honest manual pass to run in-game before merge. Tick
each item against a world with `ability_core` installed.

## Setup

- [ ] Install `ability-core` + `ability-hotbar` in a client instance.
- [ ] Assign at least one ability to a bar slot (so activation has a target).
- [ ] Open **Options → Controls** and confirm an **Ability Hotbar** group exists.

## Defaults and rebinding (REQ-HOT-10, REQ-HOT-22)

- [ ] All fifteen defaults are present in the Ability Hotbar group: Ctrl+1..9,
      Alt+1..9 and Alt+0, G, K, Right Ctrl.
- [ ] Each is rebindable via the Controls screen (no collisions with the
      verified landscape; note any conflict found).
- [ ] Vanilla **G** and the **grave accent** key are unbound or confirmed
      non-conflicting, and the
      vanilla hotbar's plain **1..9** still switches the vanilla hotbar.

## Selection (REQ-HOT-09/10)

- [ ] **Ctrl + wheel up/down** moves the selected-slot cursor on the ability bar.
- [ ] Ctrl+wheel does **not** move the vanilla hotbar selection.
- [ ] Plain wheel (Ctrl released) behaves 100% vanilla - hotbar scrolls as usual.
- [ ] Holding Ctrl and scrolling produces **no double-scroll** (one step per notch).

## Bar switching (REQ-HOT-22)

- [ ] **Alt + wheel up/down** cycles bars prev/next (one bar per notch), the
      vanilla hotbar selection does *not* also move, and holding Ctrl+Alt scrolls
      slots (Ctrl wins).
- [ ] With **`display.skipEmptyBars` on** (default) the wheel skips bars whose
      slots are all unassigned and lands on one with content; with it off every
      bar is visited. With *all* bars empty the wheel still steps normally.
- [ ] **Alt + 1..9 and Alt + 0** select bars 1..10 directly; a digit beyond
      `display.hotbarCount` (plus registered bars) does nothing at all.
- [ ] Switching **never casts** an ability, never moves the selected-slot
      cursor, and never resets the ability mode (server-side: see the GameTest;
      confirm in-game here).
- [ ] After a switch the bar shows the newly active bar's assignments and its
      own selection (cross-check the render checklist's Active bar section).

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

- [ ] The **grave accent** key (left of 1) is bound and shows the "Open
      ability menu" action, and opens the menu (hotbar#27 - see
      docs/menu-checklist.md).
- [ ] **Right Ctrl** is bound and shows the "Toggle ability mode" action (mode
      flow is tensura-iss-integration#51 - expected no-op here).
