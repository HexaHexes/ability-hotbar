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
- [ ] An assigned ability with a declared `IconProvider` shows the provider's
      texture instead of the definition's static icon; no provider, or a
      provider answering null, changes nothing (core#62 chain, hotbar#217).
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

## Active bar (REQ-HOT-22, #28)

- [ ] Only the **active** bar's slots are drawn - its assignments and *its own*
      selected slot; no second bar's contents ever appear beside or under it.
- [ ] After a switch (Alt+wheel / Alt+N, #28 controls - needs the switch slice)
      the bar redraws with the newly active bar's assignments and *its* selection
      highlight, with no flash of the previous bar's slots.
- [ ] With a non-assignable bar active (no stored state, e.g. a curios bar from
      `pseudocore-curios`) the ability bar draws **nothing** - never another
      bar's slots standing in for it.

## Switch notification (REQ-HOT-22, #28 AC 4)

- [ ] After a switch the new bar's **name** appears briefly above the bar -
      built-ins read "Hotbar 1".."Hotbar 10", a registered bar reads its
      registered display name, and an unknown id shows the raw id (diagnosed,
      never an invented name).
- [ ] The name clears itself after ~2.5 s; switching again replaces it.
- [ ] `display.switchToast = false` suppresses it live (no restart) while
      switching keeps working.
- [ ] The text stays clear of the bar at `display.scale` `0.5` and `2.0`, in
      both orientations, at every `display.anchor`.
- [ ] The toast follows the bar's fail-closed decision: it draws above a
      provider bar, and not while the bar itself draws nothing (a
      provider-less or failing non-assignable bar).

## Provider bars (hotbar#83, ADR-012 §3)

Manual visual pass for content-owned bars (needs a registering mod - the
pseudocore-curios strip bar is the first consumer). Headless fixtures cover
the contracts; these are the on-screen half.

- [ ] A provider bar draws **exactly its own cells** (six for curios) at the
      built-in cell size on the shared anchor - **no nine-slot background**
      and no ghost frames beyond the last cell.
- [ ] Filled cells render as **vanilla items**: tier tint, enchant foil and
      the mob shimmer all come through (the probe: pseudocore's tinted,
      enchanted cores).
- [ ] Empty cells use the provider's `emptyCellTexture()` when supplied
      (curios' empty-slot icon) and the hotbar's own cell chrome otherwise -
      still clipped to the bar's cells.
- [ ] `activeSlot()` is framed with the bar's selection frame; a provider
      returning `-1` (or an out-of-range index) draws no frame, and the
      violation is logged **once**.
- [ ] A provider that throws logs one line naming the bar and the bar draws
      **nothing** for that frame - no crash, no log spam at frame rate.
- [ ] Wheel skipping: a provider bar with content is landed on, one without
      is skipped, and a registered bar with no provider is skipped too
      (fail-closed); Alt+N still lands where pressed regardless.
- [ ] The switch toast still appears above a provider bar when it is the
      active one.

## Segmented bars (hotbar#86)

Segments follow the **grow-from-anchor** rule: cell 0 sits nearest the anchor
and numbering grows outward.

- [ ] A bar of **10-18 slots** draws **two** complete vanilla hotbar
      textures - each a full, unstretched `hud/hotbar` sprite, never
      squashed, stretched or cropped.
- [ ] **Left edge anchor**: the strip grows rightward (`0,1`).
- [ ] **Right edge anchor**: the strip is mirrored - cell 0 at the right
      edge, segments reading `1,0` left to right.
- [ ] **Bottom anchors** (`aboveHotbar`/`aboveXp`/`aboveStatus`, horizontal
      bar): segments stack as **rows** - segment 0 on the bottom row,
      segment 1 above it (`1 above 0`), an 18-slot bar becoming a 9-wide,
      2-row block that still centres and rests on its anchor.
- [ ] **Vertical orientation**: side anchors keep the downward column; a
      bottom anchor grows the column **upward** (cell 0 at the bottom).
- [ ] A **partial** second segment (10-17 slots) shows its unused cells as
      ordinary empty cells of a second, mostly empty hotbar.
- [ ] Changing **anchor / offsetX / offsetY / scale / orientation** moves
      both segments together as one unit - the seam never drifts.
- [ ] Exactly **one** selection frame at any moment, drawn on the selected
      slot's own segment - never two frames.
- [ ] **Nine or fewer slots** render exactly as before (regression - the
      single-segment path must be unchanged under every anchor).
- [ ] Slots 10+ show icons, overlays and selection like any other cell, and
      their unbound hotkeys (slice A) activate them.

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
