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
- [ ] Every category tab's icon and text sit **4px** in from the tab's
      left edge - the same margin as the subcategory pill icons below
      (they were 1px proud of them); text-only tabs (no resolvable
      icon) indent the same way, and labels still truncate inside the
      tab instead of running under the style pill.
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
      tags/cost line, Stats and Modes sections are gone. Sections are
      separated by a **blank line** (the first follows the name tight).
- [ ] Each Mastery Bonuses line carries the **mastery-ability slice in
      parentheses** - `Power +5% (+2%)` - the part owed to the share-fed
      masteries; no shares (or a slice that rounds to zero) shows the
      plain line exactly as before.
- [ ] A mastery bonus that rounds to **0%** is omitted from the pane
      entirely; when every declared axis reads 0%, no Mastery Bonuses
      section shows at all (the live Status fields stand in).
- [ ] The row's mastery bar shows XP clamped to **two decimals**
      (`45.57`, never `45.567891`); whole pools stay whole (`45`), a
      short fraction keeps its shape (`45.5`), and the number never
      carries locale punctuation.
- [ ] The row's **Lv badge** counts Tensura's states on top of core's
      level - **Learned +1, Mastered +2**, Known/unlearned +0 - and ids
      with no declaration show the plain core level.

## Search and filters (AC1)

- [ ] Search narrows the list live, case-insensitively, against resolved
      display names; clearing the box restores the full list.
- [ ] Category and tag readouts cycle through the distinct values present
      (plus "any"), and the chosen pair **persists across reopen and
      restart** (CLIENT config `menu.lastCategory` / `menu.lastTag`).
- [ ] A scoped ability (e.g. `press`) never appears on any listed bar.

## Row costs (owner's all-costs brief, #222)

- [ ] A row whose ability declares several costs shows **every** non-zero
      one on the value line under the `Cost:` label, in declaration order,
      comma-joined (Technomagy's duo spells read e.g. `Cost: 1 Water,
      2 Light`) - never only the first entry.
- [ ] A declared **zero** amount never renders, and an ability whose costs
      are empty or all zero shows **no** cost line at all (label and value
      both absent; the meter keeps its full width).
- [ ] The value stays right-aligned with the meter stopping 6px before it;
      a long multi-cost value squeezes the meter without overlapping it and
      is never truncated.
- [ ] With an ISS repricing projection in force, every entry quotes the
      live price - not only the first.

## Assign / replace / remove (AC2/AC3/AC6)

- [ ] Clicking an ability assigns it to the selected slot of the selected
      bar; the slot updates after the server's echo (one round trip) and
      **survives relog**.
- [ ] Clicking another ability over an occupied slot replaces it.
- [ ] A **locked** ability, a **NONE** (passive mastery) or an **UNKNOWN**
      ability never reaches a hotbar slot - neither click nor drag (the
      owner's gate); on an informational tab every other type stays inert
      to click, drag, toggle and cycle there.
- [ ] **Clear slot** empties the slot and nothing else (no data loss).
- [ ] A previously-assigned id whose provider is gone shows in **red with
      its raw id** (diagnostic), never hidden.
- [ ] A stale or tampered request is rejected server-side and the slots
      repaint from the echoed snapshot - the screen shows server truth.
- [ ] A **locked** ability and an ability whose activation is `NONE` or
      `UNKNOWN` never reaches a hotbar slot: the drag press is consumed
      and the click assigns nothing. Ordinary rows still click- and
      drag-assign exactly as before.

## Regression

- [ ] Bar switching (Alt+wheel / Alt+1..0) and activation still work with
      the menu open and closed - see the input matrix.
