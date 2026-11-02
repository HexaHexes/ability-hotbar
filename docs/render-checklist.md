# Manual render checklist (hotbar #26)

The bar's on-screen appearance cannot be proven headless — the layout and
per-slot render decisions are unit-tested (`BarLayoutTest`, `SlotRenderTest`),
but the actual GPU draw needs a real client. This is the honest visual pass to
run before merge. Tick each item in a dev client with `ability_core` +
`ability-hotbar` (fixture providers optional but helpful for icons).

## Boot / isolation

- [ ] Dedicated server boots; no client-class load errors in the log (the
      render layer is `Dist.CLIENT`-gated — verified by GameTest boot).
- [ ] Client joins a world and the bar renders above the vanilla hotbar.

## Slots and icons (REQ-HOT-01/02)

- [ ] Up to nine slots render with the vanilla hotbar texture language.
- [ ] An assigned ability shows its icon; an assigned id with **no** icon
      resolves to the neutral placeholder (no blank, no crash).
- [ ] An **empty** slot shows the neutral background.
- [ ] An **inert** slot (provider removed / unknown id) renders the red
      diagnostic tint, no icon — never a wrong value.

## Layout config applied live (REQ-HOT-07)

- [ ] `display.horizontal` flips the bar between left-to-right and
      top-to-bottom.
- [ ] In top-to-bottom mode the background shows the hotbar strip **turned** -
      cells stacked vertically, frame intact, sitting on the slot grid - and
      **not** stretched into vertical stripes; the selection frame turns with
      it (#64).
- [ ] `display.offsetX` / `display.offsetY` shift the bar.
- [ ] `display.scale` grows/shrinks the whole bar (0.5–2.0).
- [ ] `display.opacity` changes transparency.
- [ ] `display.showIcons` toggles icon drawing.
- [ ] `display.showCooldowns` / `display.showCosts` toggle their overlays.
- [ ] All values apply **live** (no restart) and the bar stays on-screen at
      both orientations.

## Anchor points (REQ-HOT-07, #60)

- [ ] `display.anchor = aboveHotbar` reproduces the previous placement exactly
      (bar resting on the vanilla hotbar, horizontally centred).
- [ ] `aboveXp` rests the bar on the vanilla experience bar.
- [ ] `aboveStatus` clears the armor, health, hunger and oxygen rows with
      `display.statusAnchorMode = DYNAMIC` - check with armor equipped, with
      more than one row of hearts, and while diving (oxygen row visible).
- [ ] `aboveStatus` with `FIXED` holds a constant position.
- [ ] `leftEdge` / `rightEdge` dock the bar against that border, vertically
      centred, with the selection frame fully visible - repeat at `display.scale`
      `0.5`, `1.0` and `2.0`.
- [ ] Every anchor is reachable at **both** `display.horizontal` values, stays
      on-screen, and still honours `display.offsetX` / `display.offsetY`.

## Selection (REQ-HOT-09/20, #20 state machine)

- [ ] The gold selection border tracks Ctrl+wheel / the selected slot.
- [ ] Selection survives activation (activating does not move the highlight).
- [ ] The cursor can sit on an empty slot (highlight shows, no icon).

## Availability overlay (REQ-HOT-08, #19 contract)

- [ ] A **known** cooldown darkens the slot proportionally (0 = covered,
      1 = clear).
- [ ] An **unknown** cooldown draws **no** overlay (never a wrong value).
- [ ] An **unavailable** verdict tints the slot red; **available** and
      **unknown** do not.
- [ ] A **known** cost shows the bottom badge; **unknown** shows none.

## Refusal feedback (REQ-HOT-19)

- [ ] A refusal (empty/restricted slot) surfaces feedback with **no** slot or
      resource state change (server-side; needs a fixture provider).

## Regression

- [ ] Vanilla hotbar, chat and screens unaffected (see
      `docs/input-gating-matrix.md`).
- [ ] No upstream mod types leak outside provider adapters (grep gate).
