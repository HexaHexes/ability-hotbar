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

## Schema forward-migration (REQ-HOT-15, ADR-004, ADR-011)

- [ ] Edit the saved player NBT: replace the `bar_state` compound with the flat
      pre-v2 shape - no `active_bar`/`bar_count`/`bar_0`, just `schema_version`
      `1` (or absent) with `slot_count`, `selected_slot` and `slot_N` keys at
      the top level. Relog.
- [ ] Values are retained as **bar 0** with `active_bar = 0`, the save rewrites
      at the current schema version (`bar_0` nested), and `latest.log` shows the
      upgrade line with its from/to versions.

## Snapshot flows (REQ-HOT-14, with core#18)

- [ ] On **login**, the bar state arrives and matches the server's.
- [ ] On **respawn** and **dimension change**, a fresh snapshot arrives and the
      client cache matches the server.
