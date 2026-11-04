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

- [ ] Tabs appear for the built-in window plus every registered
      **assignable** bar; a registered non-assignable bar (e.g. a curios bar)
      is never listed; empty bars are never hidden (no skip-empty here).
- [ ] Selecting a tab shows that bar's slots with their current contents;
      selecting a slot marks it as the assignment target.
- [ ] The world does **not** pause while the menu is open.

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
- [ ] **Clear slot** empties the slot and nothing else (no data loss).
- [ ] A previously-assigned id whose provider is gone shows in **red with
      its raw id** (diagnostic), never hidden.
- [ ] A stale or tampered request is rejected server-side and the slots
      repaint from the echoed snapshot - the screen shows server truth.

## Regression

- [ ] Bar switching (Alt+wheel / Alt+1..0) and activation still work with
      the menu open and closed - see the input matrix.
