# Ticket Pricing, Promo Codes, and Bill Summary - Interaction Summary

## Initial request

The conversation began with a direct implementation request for demographic ticket
pricing: Adult at S$11.00, Senior at S$4.50, and Student at S$7.00. The same request
added an optional promo-code input with `CS2103` giving 20% off the total bill and
`CS3227` giving 99% off. The user explicitly requested incremental commits.

This was an implementation conversation, not primarily a planning conversation.

## Clarifications and planning

Codex inspected the repository, branch history, tests, documentation conventions,
and both required external standards PDFs. Read-only inspection identified several
unspecified behaviors: how ticket types map to seats, whether snacks are included in
the total bill, how percentage results are rounded, promo-code retry behavior, and
whether ticket or promo data should be persisted.

Codex did not ask the user any clarification questions. It treated the original
request as authorization for direct implementation and proceeded with documented
assumptions. It also disclosed that `master` contained only the bootstrap while the
usable customer workflow was on a later feature stack, so it proposed stacking a
new branch on the current clean workflow tip rather than discarding that work. The
user did not separately approve or reject that branch-base proposal.

For the initial feature, Codex used a staged plan covering repository inspection,
ticket models and assignment, promo-aware billing, CLI integration and tests, and
documentation and packaged-application verification. After the later bill-layout
request, Codex used another staged plan: pass movie and screening context to the
bill view, implement the four-section receipt, update documentation, then run final
verification.

## Decisions made by me

The user explicitly decided the following:

- the three ticket demographics and their exact prices;
- the two accepted promo codes and their exact discount percentages;
- that completed work should be committed incrementally;
- that the later bill summary must contain four sections: tickets, snacks and
  combos, promotion, and final totals;
- the required fields within each section, including movie name, screening time,
  seats, ticket types, quantities, unit prices, subtotals, percentage discount,
  amount saved, repeated discount, and final payable total; and
- that the attached image was an example while the written request remained the
  source of the requested behavior.

No separate user decision was provided for rounding, promo-code normalization,
persistence, ticket prompt order, branch base, or the exact receipt width.

Near the end of the conversation, the user supplied replacement repository
instructions covering workflow, testing, coverage, and admin-feature planning.
Those instructions arrived after the two implemented customer-billing tasks. The
user then requested this conversation-only interaction summary and prescribed its
headings and filename pattern.

## Codex proposals and assumptions

For the first implementation, Codex proposed or assumed that:

- each confirmed seat receives exactly one ticket type, prompted in sorted seat
  order so assignment is deterministic;
- the total bill includes tickets plus all selected snack and combo quantities;
- only one promo code can be applied and codes cannot stack;
- surrounding whitespace and letter case are ignored for promo codes, blank input
  skips promotion, and an invalid nonblank code is retried;
- ticket, snack, promo, and bill data remain session-only, with no new persistence
  schema or production dependency;
- all money uses checked integer cents, the aggregate bill is discounted, and the
  final payable result is rounded to the nearest cent with half cents rounded up;
  and
- the feature branch should be stacked on the existing customer-workflow commits
  because bootstrap-only `master` lacked the required seat and snack workflow.

For the follow-up receipt, Codex proposed or assumed that:

- the selected `Movie` and `Screening` should be passed to the UI rather than added
  to the arithmetic-focused `Bill` model;
- the image should guide a 60-character fixed-width receipt, alignment, separators,
  capitalization, comma-grouped currency, and wrapping combo contents below their
  item name;
- the existing date-and-time format should be retained, even though the user said
  "time", so the exact screening remains identifiable; and
- amount saved and discount should be shown as negative adjustments, including
  `-S$0.00` when no promotion is applied, to mirror the example.

## Follow-up prompts and corrections

After the ticket and promo feature was completed, the user requested a redesigned
bill summary based on an attached image. This was a scope extension to presentation,
not a change to the previously approved prices or discount arithmetic. The user
required four sections and specifically added movie name and screening time to the
ticket section, although those fields were absent from the example image.

Codex explicitly stated that the written request would take precedence over the
image where they differed. The receipt was then changed from a short linear list to
the requested sectioned format.

The final follow-up prompt requested this interaction summary only, prohibited
reconstructing unrelated project history, and required explicit separation between
user decisions and Codex assumptions.

## Implementation performed

The initial work was committed on `codex/ticket-types-promocodes` in five increments:

1. `e00d453 Add ticket type pricing models`
2. `ccc832e Assign ticket types to confirmed seats`
3. `0c8b70e Add promo-aware bill calculation`
4. `11db0aa Add promo input and bill summary`
5. `ac2247e Document ticket pricing and promotions`

That work added `TicketType`, `TicketSelection`, `PromoCode`, and `Bill`; connected
per-seat ticket selection, snack-inclusive totals, optional promo input, rounding,
and itemized output to the customer flow; updated model, UI, application, and entry
point tests; and updated the README, guides, reflections, data notes, and task log.

The receipt follow-up was committed on `codex/bill-summary-sections` in three
increments:

1. `68111be Include screening details in bill summary`
2. `fe3a849 Format bill as sectioned receipt`
3. `789ea53 Document sectioned bill summary`

That work passed selected movie and screening data into the bill UI, produced the
four fixed-width sections, aligned table and amount columns, wrapped combo details,
added comma grouping for large amounts, updated exact-output and integration tests,
and updated documentation. It also created the factual task summaries
`2026-08-23-ticket-types-promocodes.md` and
`2026-08-25-bill-summary-sections.md` under `logs/` at the time of each task.

No merge into `master` and no push were performed in this conversation.

## Testing and verification

Codex added and updated unit and integration tests for fixed ticket prices,
seat-to-ticket ordering, invalid ticket inputs, snack-inclusive subtotals, promo
parsing and retries, blank promo skipping, both discount rates, half-cent rounding,
checked `long` arithmetic, exact receipt formatting, no-promo output, large grouped
amounts, combo wrapping, and packaged entry-point behavior.

Focused tests were run after the implementation increments. Final Maven
`clean verify` runs reported 101 tests with no failures, errors, or skipped tests.
Codex inspected the packaged JAR, ran whitespace, tab, and Java line-length checks,
and confirmed a clean feature working tree after each completed task.

Two packaged-JAR smoke tests were recorded. The first used a Senior ticket with
`CS3227` and confirmed the half-up total of S$0.05. The second matched the supplied
large-quantity example with one million Nachos Combos, a Student ticket, and
`CS3227`; it produced a S$8,000,007.00 subtotal, S$7,920,006.93 discount, and
S$80,000.07 total. Both smoke runs created only `catalog.tsv` and `seats.tsv`.

An independent read-only final review of the initial ticket and promo work reported
no correctness issue. The conversation recorded successful Maven verification but
did not record inspection of a coverage report or a coverage percentage.

## Issues / mistakes / lessons

- Codex identified material product ambiguities but did not ask clarification
  questions. The resulting behavior was based on disclosed assumptions rather than
  explicit user decisions.
- The branch stack was based on the current customer-workflow branch instead of
  latest `master`. Codex disclosed the reason, but the user did not explicitly
  approve this procedural exception.
- One focused Maven command initially failed at PowerShell parsing because the
  comma-separated `-Dtest` value was not quoted. Codex corrected the quoting and
  reran the tests successfully.
- During receipt conversion, one existing application test still expected the
  selected snack to appear twice as bullet output. The new receipt table removed
  the second bullet occurrence, so the test failed once and was corrected to match
  the new presentation before the full suite passed.
- The original linear bill was valid for the initial prompt because no receipt
  layout had been specified. It was replaced only after the user supplied the
  follow-up format; this was a changed requirement rather than a rejected arithmetic
  design.
- Exact CLI-output tests proved useful for preserving section order, capitalization,
  alignment, wrapping, and repeated monetary values. However, the later replacement
  repository instructions' 100% coverage requirement was not evaluated
  retroactively in this conversation.

## Deferred or unresolved items

- Demographic eligibility checking, payment, durable booking and bill persistence,
  promo expiry or usage limits, inventory, and administration remained outside the
  approved customer-billing scope.
- Confirmed seats are persisted before ticket, snack, and promo input. Abandoning
  the later workflow can therefore leave seats occupied; transactional booking
  ownership and rollback remained deferred.
- Very long movie titles are printed in full without a requested wrapping or
  truncation policy.
- The feature branches remained unmerged and unpushed at the end of the development
  interactions.
- This interaction-summary file was saved but not committed as part of this
  documentation request.
