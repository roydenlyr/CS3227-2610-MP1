# Ticket Types and Promo Codes Summary

## Approved scope

Extend the customer flow so ticket prices differ by demographic:

- Adult: S$11.00;
- Senior: S$4.50;
- Student: S$7.00.

Add optional promo-code input with `CS2103` applying 20% off the total bill and
`CS3227` applying 99% off the total bill. Commit completed work incrementally.

## Implementation assumptions

- Each confirmed seat receives exactly one ticket type. Seats are prompted in
  coordinate order so input order cannot change which demographic is assigned to
  a seat.
- The total bill includes all ticket prices plus every selected snack or combo
  quantity.
- One optional promo code applies to the aggregate subtotal; codes do not stack.
- A blank promo input skips the promotion. Nonblank codes ignore surrounding
  whitespace and letter case, while unsupported codes are rejected and retried.
- Money is represented in exact Singapore cents using checked `long` arithmetic.
  The final payable percentage result is rounded to the nearest cent, with half
  cents rounded up. The displayed discount is subtotal minus that rounded total.
- Ticket, snack, promotion, and bill data remain session-only. No persistence
  schema or production dependency was added.
- The feature branch was stacked from clean customer-workflow commit `7d2968c`
  because `master` still contains only the bootstrap and does not contain the
  catalog, seat, or snack flow required by this ticket.

## Changes

- Added fixed `TicketType` pricing and immutable seat-to-type `TicketSelection`
  data, with one retrying selection prompt per confirmed seat.
- Changed seat selection to return confirmed coordinates so the application can
  preserve the exact seat-to-demographic relationship.
- Added `PromoCode` and `Bill` domain models for ticket subtotals, quantity-aware
  snack subtotals, aggregate discounts, half-up payable rounding, and final totals.
- Added optional promo input, invalid-code retries, and an itemized bill showing
  all selections, subtotals, promotion details, discount, and payable total.
- Expanded model, UI, application, and entry-point tests for every fixed price and
  promotion, deterministic seat order, invalid inputs, skipping, long arithmetic,
  aggregate snack inclusion, and the half-cent rounding boundary.
- Updated the README, user guide, developer guide, data policy, and reflections.

## Incremental commits

1. `e00d453 Add ticket type pricing models`
2. `ccc832e Assign ticket types to confirmed seats`
3. `0c8b70e Add promo-aware bill calculation`
4. `11db0aa Add promo input and bill summary`

## Verification

- Read both required external standards PDFs before design and implementation.
- Ran focused model, UI, application, and entry-point JUnit tests after each
  implementation increment; all passed.
- Ran the Maven Wrapper `clean verify` using Java 25.0.4 and Maven 3.9.16: 101
  tests passed with no failures, errors, or skipped tests, and the JAR was built.
- Ran `git diff --check`; no whitespace errors were reported.
- Scanned Java sources for tabs and lines longer than 120 characters; none were
  found.
- Inspected the packaged JAR and confirmed it contains the ticket, promotion,
  billing, application, UI, and snack classes plus the bundled default catalog.
- Smoke-tested the packaged JAR from an empty temporary working directory. The
  run assigned a Senior ticket to `A1`, skipped snacks, applied lowercase
  `cs3227`, displayed a S$0.05 total, and created only `catalog.tsv` and
  `seats.tsv`.

## Limitations and deferred work

- CineCLI does not verify demographic eligibility or persist ticket types, snack
  selections, promo codes, or bills.
- Confirmed seat occupancy is written before ticket, snack, and promo input. An
  abandoned session after confirmation therefore leaves those seats occupied;
  transactional booking ownership and rollback remain deferred.
- Promo expiry, usage limits, inventory, payment, complete booking records, and
  administration remain outside this task.
