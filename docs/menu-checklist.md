# Manual menu checklist (hotbar #27)

The screen's appearance and interaction cannot be proven headless (org rule:
client UI cannot be proven headless - AGENTS). This is the honest pass in a
dev client with `ability_core` + `ability-hotbar` installed. The query layer
itself is fixture-tested (`MenuQueryTest`); this list covers the pixels and
the input.

## Open / close (AC5)

- [ ] The **grave accent** key (left of 1) opens the menu in-game; the same
      key closes it again when the search box is *not* focused; typing that
      character into the search box inserts it instead of closing.
- [ ] **Esc** closes it.
- [ ] Chat, inventory and other screens are unaffected (cross-check
      `docs/input-gating-matrix.md`).

## Bars and slots (AC7/AC8/AC9)

- [ ] The header strip shows the permanent All tab plus every registered
      tab **whose own list has at least one entry**; a tab that would list
      nothing is never drawn and never stays selected (it falls back to
      All). No registrations, no strip. The bar cycle keeps listing every
      assignable bar - empty ones included (no skip-empty there).
- [ ] Selecting a tab shows that tab's list (and its provider bar's slot
      strip when the tab declares one); selecting a slot marks it as the
      assignment target.
- [ ] Subcategory pills mirror the live list: a pill whose rows the current
      scope/search would empty is not drawn, and an emptied active
      subcategory clears itself to All.
- [ ] Slot pills are the **same size on every bar** - the assignable nine, a
      shorter bar and a provider strip (e.g. the ISS Spells tab) all share
      one cell width; the strip just ends earlier on a shorter bar.
- [ ] The slot strip shows `min(bar slots, display.slotsPerBar)` cells -
      the same count the HUD draws (the client setting is respected in the
      menu too); the header meter still reports the bar's true used/total,
      and the mode preview still finds an ability in any slot.
- [ ] A provider pill whose provider ships a **name** (the ISS spellbook
      strip) shows it beside the icon, wrapped to two lines max; a provider
      without the hook stays icon-only exactly as before.
- [ ] Hovering a provider pill's **ability icon** shows that provider's
      tooltip (for ISS: the matching scroll's spellbook tooltip), drawn
      above the detail pane and the drag ghost; a provider without the
      hook shows nothing, and the tooltip never appears mid-drag.
- [ ] The world does **not** pause while the menu is open.
- [ ] A **greyed passive mastery** row (activation `NONE`, provably
      unlearned - e.g. a locked ISS school mastery) never lists at all;
      unknown unlock state and every other row keep their grey.

## Detail pane

- [ ] The pane reads **name -> description -> status -> activation**:
      the Mastery Bonuses section **replaces** Status for an ability that
      can earn core XP, a passive never shows Activation, and the
      tags/cost line, Stats and Modes sections are gone.

## Search and filters (AC1)

- [ ] Search narrows the list live, case-insensitively, against resolved
      display names; clearing the box restores the full list.
- [ ] Category and tag readouts cycle through the distinct values present
      (plus "any"), and the chosen pair **persists across reopen and
      restart** (CLIENT config `menu.lastCategory` / `menu.lastTag`).
- [ ] A scoped ability (e.g. `press`) never appears on any listed bar.

## Assign / replace / remove (AC2/AC3/AC6)

- [ ] Clicking an ability assigns it to the selected slot of the selected
      bar; the slot updates after the server's echo (one round trip) and
      **survives relog**.
- [ ] Clicking another ability over an occupied slot replaces it.
- [ ] A **NONE** (passive mastery) or **UNKNOWN** ability slots even on an
      informational tab (the owner's settable brief); every other type
      stays inert to click, drag, toggle and cycle there.
- [ ] **Clear slot** empties the slot and nothing else (no data loss).
- [ ] A previously-assigned id whose provider is gone shows in **red with
      its raw id** (diagnostic), never hidden.
- [ ] A stale or tampered request is rejected server-side and the slots
      repaint from the echoed snapshot - the screen shows server truth.
- [ ] A **locked** ability and an ability whose activation is `NONE` or
      `UNKNOWN` never start a **drag** to the hotbar (the press past the
      threshold is consumed); a plain click still assigns, and ordinary
      rows drag exactly as before.

## Regression

- [ ] Bar switching (Alt+wheel / Alt+1..0) and activation still work with
      the menu open and closed - see the input matrix.
