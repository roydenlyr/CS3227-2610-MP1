# Terminal Seat Selection Summary

## Scope

Implemented the approved customer flow:

`Movie catalog -> screening code -> terminal seat map -> tentative seats -> confirmation`

Every screening uses the same fixed 7-by-20 layout. The map places `SCREEN`
above row `G`, renders rows `G` through `A`, and places numbers `1` through `20`
below row `A`. Available seats use `O`; confirmed and current tentative seats use
`X`.

## Owner decisions applied

- The seat display remains inside the terminal rather than using a windowed GUI.
- A screening is selected with its movie number and timing letter, such as `3B`.
- Every screening uses the same fixed layout.
- The numeric axis is at the bottom, furthest from the screen.
- One CineCLI process is expected, so inter-process locking is not required.
- Bookings will eventually own seat allocations as the source of truth.
- Until booking persistence exists, a temporary coordinate-based seat store is
  approved and must remain replaceable.

## Persistence decision

Persisted coordinates were chosen instead of the rendered `O`/`X` grid because
coordinates represent occupancy without coupling storage to terminal formatting.
The approved separate UTF-8 TSV schema is:

```text
CINECLI-SEATS<TAB>1
TAKEN_SEAT<TAB>screeningId<TAB>seatCoordinate
```

The entire file is validated before use. Missing data initializes to a header-only
file, malformed data is preserved and rejected, confirmed updates use a
same-directory temporary file and require atomic replacement, and records are
written in a deterministic order.

## Implementation

- Added case-insensitive screening code parsing and lettered catalog timings.
- Added validated seat coordinates for rows `A-G` and numbers `1-20`.
- Added the fixed terminal map and tentative-selection display.
- Added multiple-seat entry, duplicate and occupied-seat rejection, confirmation,
  reselection, and cancellation.
- Added temporary seat parsing and storage isolated from the catalog and UI.
- Wired `data/runtime/seats.tsv` through the application entry point.
- Updated the README, user guide, developer guide, data policy, and reflections.

## Testing and verification

- Added model, parser, storage, UI, application, and entry-point tests covering
  valid and invalid screening codes, layout orientation, tentative rendering,
  confirmation, cancellation, occupied seats, corruption, initialization,
  deterministic writes, and failure-safe updates.
- `.\mvnw.cmd clean verify`: succeeded with 61 tests, 0 failures, 0 errors, and
  0 skipped.
- Confirmed the packaged JAR contains the bundled default catalog and the seat
  storage implementation.
- Smoke-tested the packaged JAR from an empty working directory with
  `3A -> A1 -> Y`; it initialized both runtime files and persisted
  `TAKEN_SEAT<TAB>SCR-005<TAB>A1`.
- `git diff --check` and Java line-length/tab checks reported no source-formatting
  errors.

## Limitations and deferred work

- The temporary seat file records confirmed occupancy but is not a complete
  booking record. It must be replaced by booking-owned allocations when booking
  persistence is implemented.
- Inter-process locking and multi-kiosk conflict handling are deliberately
  deferred under the approved single-process assumption.
- Administration, checkout, payment, cancellation of confirmed bookings, and
  other booking lifecycle behavior remain outside this task.
