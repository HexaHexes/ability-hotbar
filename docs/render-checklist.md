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
- [ ] `display.offsetX` / `display.offsetY` shift the bar.
- [ ] `display.scale` grows/shrinks the whole bar (0.5–2.0).
- [ ] `display.opacity` changes transparency.
- [ ] `display.showIcons` toggles icon drawing.
- [ ] `display.showCooldowns` / `display.showCosts` toggle their overlays.
- [ ] All values apply **live** (no restart) and the bar stays on-screen at
      both orientations.

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
