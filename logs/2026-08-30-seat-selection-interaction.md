# Seat Selection - Interaction Summary

## Initial request

- The user requested a seat-selection display containing `SCREEN`, rows `A-G`
  with `G` closest to the screen, and columns `1-20`. Available seats were to use
  `O` and taken seats `X`.
- Before implementation, the user asked Codex to compare persisting only taken
  coordinates with persisting the entire rendered arrangement, recommend the
  better approach or alternatives, and propose a seat-data schema.

## Clarifications and planning

- Codex inspected the existing CLI, persistence conventions, and authoritative
  standards before changing code. It identified that the word "GUI" was
  ambiguous because the application was a terminal program.
- Codex surfaced clarification points about terminal versus windowed display,
  whether layouts varied by screening, what `X` meant during selection, how a
  screening was chosen, whether multiple processes were expected, and whether
  bookings or a standalone seat file would ultimately own occupancy.
- Codex planned the interaction as catalogue display, screening-code entry, seat
  map, tentative selection, and confirmation. An inline five-step implementation
  plan covered the feature branch and contract, seat model/storage, terminal
  workflow and tests, wiring/documentation, and final verification. No separate
  PRD or technical-design document was created in this conversation.

## Decisions made by me

- The display would be a terminal map, not a windowed GUI.
- Every screening would use the same fixed 7-by-20 layout.
- `X` would visually cover both already confirmed occupancy and seats tentatively
  selected in the current interaction. Tentative seats would become persistent
  only after confirmation.
- Only one CineCLI process was expected.
- The user would reach seat selection after the movie catalogue and identify a
  screening with a movie number followed by its timing letter, for example `3B`
  for the second timing of the third movie.
- Bookings should eventually own seat allocations as the source of truth. A
  temporary seat-occupancy implementation was approved until bookings existed.
- The number axis had to be below row `A`, furthest from `SCREEN`. After adding
  this correction, the user explicitly instructed Codex to proceed.
- Later, after learning that no commits had been created, the user explicitly
  authorized Codex to commit the completed changes according to `AGENTS.md`.

## Codex proposals and assumptions

- Codex recommended storing screening-scoped taken coordinates rather than the
  rendered `O`/`X` grid. The reasons were separation of domain state from terminal
  presentation, readability, and easier replacement by booking-owned allocations;
  retrieval speed and file size were considered insignificant for 140 seats.
- A full grid and compact bitset/hex representation were considered alternatives,
  but the full grid duplicated available-seat state and the compact form was less
  transparent than needed.
- Codex proposed a separate, versioned UTF-8 TSV file rather than modifying the
  static catalogue file. The final approved representation was:

  ```text
  CINECLI-SEATS<TAB>1
  TAKEN_SEAT<TAB>screeningId<TAB>seatCoordinate
  ```

- Codex assumed strict whole-file validation, deterministic record ordering,
  missing-file initialization, and atomic replacement for confirmed updates.
  Multi-process locking was excluded under the user's single-process decision.
- The proposed selection workflow accepted multiple coordinates, displayed them
  as tentative `X` values, used `Y` to confirm and `N` to clear them, and allowed
  `CANCEL` at the coordinate prompt.

## Follow-up prompts and corrections

- The user corrected the map orientation by requiring the horizontal number axis
  at the bottom, below the row furthest from the screen.
- After implementation, the user asked whether Codex had committed along the way.
  Codex answered that it had not because commit authorization had not previously
  been explicit. The user then authorized local commits.
- No push, merge into `master`, or remote operation was requested or performed.

## Implementation performed

- Codex created `codex/seat-selection` and implemented case-insensitive screening
  codes such as `3B`, lettered catalogue timings, validated coordinates `A1-G20`,
  and the fixed terminal map in the order `SCREEN`, `G` through `A`, then `1-20`.
- The interaction accepted multiple seats, rejected malformed, duplicate, and
  occupied coordinates, rendered tentative seats as `X`, handled `Y`/`N` and
  cancellation, and persisted only confirmed selections.
- Temporary seat parsing and storage were added in an isolated module so booking
  persistence could replace them later. Existing malformed data was rejected
  without being overwritten, and confirmed writes required atomic replacement.
- Tests and the README, user guide, developer guide, data documentation,
  reflections, and `logs/2026-08-23-seat-selection.md` were updated or created.
- After authorization, the seat/catalogue vertical slice was committed as
  `bc6e9dc` (`Implement catalog-driven seat selection`). Because the catalogue
  slice was already uncommitted and structurally intertwined with this feature,
  that commit was not seat-only. Two other already-completed, non-seat working-tree
  units were committed separately as `957755b` and `198a523`; their development
  histories are intentionally not reconstructed in this conversation summary.

## Testing and verification

- The pre-feature baseline reported 22 passing tests. The initial focused seat
  model/storage work reported 28 passing tests.
- Integration expanded the full suite to 60 tests. A final invalid-input test then
  produced one failure out of 61 because its assertion expected the phrase
  "must match" while the implemented validation correctly said "must use". The
  assertion was corrected.
- Final `.\mvnw.cmd clean verify` runs passed 61 tests with no failures, errors,
  or skips.
- Codex inspected the packaged JAR for the bundled catalogue and relevant classes,
  then smoke-tested `3A -> A1 -> Y` from an empty working directory. The map showed
  tentative `A1` as `X`, and the seat file stored
  `TAKEN_SEAT<TAB>SCR-005<TAB>A1`.
- Diff whitespace checks, Java line-length and tab checks, and an independent
  requirements/standards review were completed. Some reviewer-side commands hit
  sandbox Maven-repository or temporary-directory limitations, but the root
  verification completed successfully.
- No separate code-coverage report was generated in this conversation.

## Issues / mistakes / lessons

- The implementation temporarily used the record tag `TAKEN` even though the
  pre-implementation proposal used `TAKEN_SEAT`. Review caught the schema drift,
  and the parser, writer, tests, and documentation were realigned.
- The first writer implementation fell back to a non-atomic replacement when an
  atomic move was unsupported. Review identified the data-safety risk. The
  fallback was removed so the update now fails while retaining existing data.
- One test asserted different wording from the actual validation message; the test
  was corrected rather than changing correct user-facing behavior.
- The task log briefly contained the mistyped command `\.\mvnw.cmd`; it was fixed
  to `.\mvnw.cmd`.
- Commits were created only after implementation because Codex correctly waited
  for explicit authorization. Consequently, the main feature commit bundled the
  previously uncommitted catalogue slice with seat selection instead of recording
  incremental seat-feature commits.

## Deferred or unresolved items

- Booking persistence remains the intended long-term source of truth. The
  temporary seat file must be removed or replaced when booking-owned allocations
  are introduced.
- Confirmed-booking cancellation and the wider checkout/payment lifecycle were
  not implemented in this conversation.
- Multi-process locking was deliberately deferred under the approved one-process
  assumption.
- On a file system without atomic replacement, confirmation deliberately fails
  instead of risking persisted seat data.
- The local feature commits were left unpushed and unmerged at the end of the
  conversation.
