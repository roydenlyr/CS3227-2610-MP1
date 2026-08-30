# Pricing Storage - 29 August 2026

## Approved scope

Added runtime `pricing.tsv` persistence, immutable customer price/promotion
snapshots, and fail-fast customer pricing loading. The workstream does not add an
administrator pricing UI or promotion CRUD.

## Implemented behaviour

- `Pricing` provides complete immutable ticket, snack/combo, and promotion state
  using the former customer-facing values as missing-file defaults.
- `PricingParser` and `PricingStorage` validate version-1 pricing data, seed only
  a target known to be missing, preserve malformed existing files, serialize
  deterministically, and atomically save complete valid state with a
  same-directory forced temporary file.
- `TicketSelection`, `SnackSelection`, `PromoCode`, and `Bill` use immutable
  snapshots instead of enum-owned prices.
- `CustomerApplication` loads pricing after welcome and before catalog or seat
  access; a pricing failure reports an error and ends the session without
  initializing or changing seat data.
- `Main` wires `data/runtime/pricing.tsv` alongside catalog and seat storage.

## Verification

- Focused model, pricing-storage, customer-flow, UI, and main wiring tests were
  added or updated.
- Reported integrated verification: `.\mvnw.cmd verify` passed with 210 tests and
  the aggregate JaCoCo line and branch coverage gate satisfied.

## Documentation

Updated the user and developer guides and `data/README.md`. Recorded concise
pricing PRD, TDD, and requirements-to-tests artifacts under `docs/admin/pricing/`.

## Deferred work

- Administrator ticket/snack/combo price editing.
- Administrator promotion listing and CRUD.
- Shared customer/admin role routing.
- Booking persistence of completed price and promotion snapshots.
