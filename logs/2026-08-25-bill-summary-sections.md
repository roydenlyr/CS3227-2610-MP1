# Four-Section Bill Summary

## Approved scope

Reformat the final customer bill into four sections based on the supplied example:

1. tickets with movie, screening time, seat, ticket type, unit price, and subtotal;
2. distinct snacks, drinks, and combos with quantity, unit price, and subtotal;
3. promotion code, discount percentage, and amount saved; and
4. pre-discount subtotal, repeated discount amount, and final payable total.

Commit completed work incrementally.

## Implementation assumptions

- The written request is authoritative where it extends the example image. Movie
  title and screening time are therefore shown above the ticket table even though
  the example does not contain those two fields.
- The image guides fixed-width presentation, capitalization, section rules,
  right-aligned values, negative discount adjustments, and combo-description
  wrapping.
- The screening date is retained with its time using the existing
  `dd MMM uuuu, HH:mm` display format so the bill identifies the exact screening.
- `Amount Saved` and the repeated final discount use a leading minus sign to show
  that they are subtracted from the subtotal. A skipped promotion shows `None`,
  `0% OFF`, and `-S$0.00`.
- Existing pricing, promotion arithmetic, rounding, selection order, and
  session-only persistence boundaries are unchanged.
- The branch was stacked from clean ticket-and-promo commit `ac2247e` because the
  feature remains ahead of the bootstrap-only `master` branch.

## Changes

- Passed the selected movie and screening into the final bill UI call without
  expanding the pricing-focused `Bill` model.
- Replaced the linear bill output with a 60-character-wide receipt containing the
  four requested sections.
- Added ticket and snack tables with aligned seat or quantity, item/type, and unit
  price columns.
- Wrapped parenthesized combo contents beneath the combo name and added comma
  grouping for large currency values.
- Updated exact UI, application, and entry-point tests for the new headings,
  fields, promotion presentation, repeated totals, skipped-promotion output, and
  large-number formatting.
- Updated the README, user guide, developer guide, and reflections.

## Incremental commits

1. `68111be Include screening details in bill summary`
2. `fe3a849 Format bill as sectioned receipt`

## Verification

- Re-read the required external standards before implementation and inspected the
  complete supplied example image.
- Ran focused UI, application, and entry-point tests after each implementation
  increment; all passed.
- Ran the Maven Wrapper `clean verify` with the existing Java 25 and pinned Maven
  toolchain; all 101 tests passed with no failures, errors, or skipped tests, and
  the executable JAR was rebuilt.
- Ran `git diff --check` and scanned changed Java files for tabs and lines longer
  than 120 characters; no issues were found.
- Smoke-tested the packaged JAR from an empty working directory and confirmed the
  four sections, selected movie and time, comma-grouped values, promotion details,
  repeated discount, and payable total. The run created only `catalog.tsv` and
  `seats.tsv` runtime files.

## Limitations and deferred work

- The receipt remains terminal output only; it is not persisted, exported, or
  associated with a durable booking.
- Long movie titles are printed in full rather than truncated or wrapped because
  no title-width policy was requested.
- Payment, booking persistence, demographic eligibility, and administration remain
  outside this task.
