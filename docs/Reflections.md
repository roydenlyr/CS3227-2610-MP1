# Reflections

This document records engineering lessons and considered trade-offs. Factual task
results belong in the corresponding task summary under `logs/`.

## 2026-08-21 - Project bootstrap

- The foundation deliberately contains only an entry point and one smoke test so
  that unapproved cinema behavior and speculative architecture are not embedded in
  the initial design.
- Tool and dependency versions are pinned to make local and future automated builds
  repeatable.
- Persistence structure is reserved without selecting a data format prematurely.

## 2026-08-23 - First customer vertical slice

- A small versioned TSV format is sufficient for the approved read-only catalog
  and keeps parsing possible with the Java standard library alone.
- Treating missing data differently from malformed data prevents convenient seed
  initialization from hiding corruption or silently discarding user state.
- Injecting readers, writers, and runtime paths keeps an interactive CLI testable
  without replacing global system streams or touching real runtime data.

## 2026-08-23 - Terminal seat selection

- Persisting occupied coordinates keeps domain state independent from the
  terminal's derived `O` and `X` representation. For a fixed 140-seat layout,
  readability and separation of concerns matter more than storage size.
- Tentative and persisted seats can share the required `X` marker while remaining
  different states internally; persistence occurs only after explicit
  confirmation.
- Isolating transitional occupancy storage behind `SeatStorage` makes the future
  move to booking-owned seat allocations a localized replacement instead of a UI
  rewrite.
- Exact output tests are useful for spatial CLI requirements, such as keeping row
  `G` next to `SCREEN` and the numeric axis below row `A`.
