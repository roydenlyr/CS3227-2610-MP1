# Snack and Combo Selection Summary

## Scope

Extended the customer flow after successful seat confirmation:

`Movie catalog -> screening -> seats -> confirmation -> optional snack or combo`

The fixed menu is:

- Popcorn: S$5.00
- Nachos: S$6.00
- Soft Drink: S$3.00
- Popcorn Combo (Popcorn + Soft Drink): S$7.00
- Nachos Combo (Nachos + Soft Drink): S$8.00

## Implementation assumptions

- Prices are fictional Singapore-dollar amounts stored as exact integer cents.
- A customer can choose one item by number or enter `0` to skip.
- The menu appears only after confirmed seats are persisted successfully.
- Snack and combo choices are session-only. Quantities, totals, inventory,
  booking integration, checkout, payment, and snack persistence remain deferred.

## Implementation

- Added a fixed `SnackMenuItem` enumeration with stable menu numbers, categories,
  names, and exact prices.
- Added a grouped terminal menu, exact two-decimal price formatting, selection
  acknowledgment, skip handling, and retryable validation.
- Added an explicit seat-selection result so the application coordinator starts
  snack selection only after seat confirmation succeeds.
- Updated the README, User Guide, and Developer Guide with the complete menu,
  interaction steps, and session-only limitation.

## Testing and verification

- Added model, UI, application, and entry-point tests for every menu item, exact
  prices, menu grouping, post-seat ordering, valid selection, skipping, malformed
  and out-of-range retries, and cancellation before confirmation.
- `.\mvnw.cmd clean verify`: succeeded with 69 tests, 0 failures, 0 errors, and
  0 skipped.
- Confirmed the packaged JAR contains the snack model, UI, application, and
  bundled default catalog.
- Smoke-tested the packaged JAR from an empty temporary working directory with
  `3A -> A1 -> Y -> 5`; it exited successfully, displayed and selected the S$8.00
  Nachos Combo, initialized only the catalog and seat files, and persisted seat
  `A1` for screening `SCR-005`.
- `git diff --check` and Java tab and line-length scans reported no formatting
  errors.

## Limitations and deferred work

- Only one optional menu choice is supported per run.
- The choice is acknowledged but not stored or included in a booking or total.
- Pricing administration, quantities, inventory, discounts, checkout, and
  payment require separate approved requirements.
