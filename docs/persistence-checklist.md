# Manual persistence checklist (hotbar #24)

The NBT upgrade ladder and corrupt-id handling are covered by JUnit fixtures
(`BarStatePersistenceTest`, `BarStateTest`). The checks below need a real server
lifecycle (save/load, death/respawn, dimension change) and so are manual. Run
against a world with `ability_core` + `ability-hotbar` installed.

## Assign -> relog (REQ-HOT-14)

- [ ] Assign abilities to several slots and set a selected slot.
- [ ] Leave the world and rejoin.
- [ ] Assignments and the selected slot are identical to before leaving.

## Death + respawn (REQ-HOT-14, `copyOnDeath`)

- [ ] Assign abilities, then die and respawn.
- [ ] Assignments are **kept** (the attachment is `copyOnDeath`).

## Corrupt / unknown ids (REQ-HOT-21, REQ-HOT-15)

- [ ] Assign an ability from a mod, then remove that mod (or edit the saved NBT
      to an id nothing resolves). Relog.
- [ ] The slot renders as diagnosed-**inert** with the raw id preserved - not
      dropped, not substituted, and no load crash.

## Schema forward-migration (REQ-HOT-15, ADR-004, ADR-011, ADR-012)

- [ ] Edit the saved player NBT: replace the `bar_state` compound with the flat
      pre-v2 shape - no `active_bar`/`bar_count`/`bar_0`, just `schema_version`
      `1` (or absent) with `slot_count`, `selected_slot` and `slot_N` keys at
      the top level. Relog.
- [ ] Values are retained as **built-in bar 0** with active bar
      `ability_hotbar:bar_0`, the save rewrites at the current schema version
      (`bar_0` nested with its `bar_id`, `active_bar` as an id string), and
      `latest.log` shows the upgrade line with its from/to versions.
- [ ] Edit a current save down to the **v2 shape**: set `schema_version` to `2`,
      change `active_bar` from the id string to an integer index, and delete the
      `bar_id` keys inside every `bar_N`. Relog.
- [ ] Positions become built-in ids (`ability_hotbar:bar_N`), the integer
      active bar converts to its id, values are retained, and the upgrade line
      appears in `latest.log`.
- [ ] Register a test bar (or remove a registering mod), relog, and confirm the
      save reconciles: a new assignable bar is appended with fresh state, a
      deregistered bar's data is kept but inert, and the log names what changed.

## Snapshot flows (REQ-HOT-14, with core#18)

- [ ] On **login**, the bar state arrives and matches the server's.
- [ ] On **respawn** and **dimension change**, a fresh snapshot arrives and the
      client cache matches the server.
