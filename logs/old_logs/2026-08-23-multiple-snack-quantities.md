# Multiple Snack and Combo Quantities Summary

## Approved scope

Extend the post-seat snack and combo menu so a customer can enter a quantity for
an a la carte item or combo and choose more than one option.

The implementation keeps the existing fixed items and unit prices. It accepts
positive whole-number quantities, repeats the item prompt until `0` is entered,
and displays the completed selections. Entering `0` with no selections retains the
existing skip behavior.

## Implementation assumptions

- A customer can select any number of distinct menu options in one run.
- Selecting the same item again replaces its earlier quantity so it can be
  corrected without creating duplicate lines.
- Different items remain in the order in which they were first selected.
- Quantities use the Java integer range from 1 through 2,147,483,647. Leading
  zeroes and an optional leading plus sign are accepted by integer parsing.
- Prices shown beside completed selections remain unit prices. Subtotals, order
  totals, checkout, payment, booking integration, inventory, and snack persistence
  remain outside this task.

## Changes

- Added immutable `SnackSelection` data with quantity parsing and positive-range
  validation.
- Extended `CustomerApplication` with an in-memory, insertion-ordered collection
  of selected items and quantities.
- Added distinct item and quantity retry loops, add/update acknowledgements, finish
  handling, and a final selection summary.
- Updated the menu and prompts to explain quantity input and `0` completion.
- Expanded model, UI, application, and real-wiring tests for multiple items,
  repeated-item replacement, malformed and non-positive quantities, exact unit
  prices, session isolation, and no snack data file.
- Updated the README, user guide, developer guide, and reflections for the new
  workflow and boundaries.

## Verification

- Established a clean baseline of 69 passing tests before implementation.
- Ran focused quantity, UI, application, and wiring tests successfully after
  implementation and review fixes.
- Ran `.\mvnw.cmd clean verify`: 79 tests passed with no failures, errors, or
  skipped tests; the packaged JAR was built successfully.
- Ran `git diff --check`; no whitespace errors were reported.
- Scanned Java sources for tabs and lines longer than 120 characters; none were
  found.
- Inspected the packaged JAR and confirmed it contains `Main`,
  `CustomerApplication`, `CustomerUi`, `SnackMenuItem`, `SnackSelection`, and the
  bundled default catalog.
- Smoke-tested the packaged JAR from an empty temporary working directory. The run
  confirmed a seat, added Popcorn and a Popcorn Combo with different quantities,
  replaced the Popcorn quantity, completed the selection, displayed both exact
  unit prices, and created only the expected catalog and seat runtime files.

## Limitations and deferred work

Snack and combo selections remain session-only and disappear when the process
ends. The application does not calculate monetary totals, persist the selections,
reserve inventory, attach them to a booking, or proceed to checkout or payment.
