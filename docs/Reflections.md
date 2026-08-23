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

## 2026-08-23 - Snack and combo selection

- Storing fixed prices as integer cents avoids floating-point rounding concerns
  while leaving customer-facing currency formatting in the UI.
- Returning an explicit seat-selection result keeps the next workflow stage at
  the application coordinator level and ensures the menu appears only after seat
  persistence succeeds.
- Keeping one optional snack or combo choice in memory satisfies the current
  interaction requirement without inventing quantities, totals, checkout rules,
  or a persistence schema before those requirements are approved.

## 2026-08-23 - Multiple snack and combo quantities

- The later quantity requirement intentionally supersedes the earlier single-choice
  boundary while retaining the same fixed menu, prices, and session-only scope.
- Pairing each item with a validated immutable quantity keeps malformed input out of
  the selection state and gives the UI one clear value to format.
- Keying selections by menu item lets customers correct a quantity by selecting the
  item again, while insertion order keeps the final summary aligned with the order
  in which different items were first chosen.
- Separating the item prompt from the quantity prompt allows invalid quantities to
  be retried without forcing customers to re-enter a valid item number.
