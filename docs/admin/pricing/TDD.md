# Pricing Storage TDD

**Status:** Owner-approved implementation contract

**Approval date:** 29 August 2026

## Modules and boundaries

`Pricing` is the immutable complete state: an exact-cent price for every
`TicketType` and `SnackMenuItem`, plus immutable `PromoCode` values. It rejects
missing identities, invalid prices, and duplicate promotion codes. The enums are
identity/menu definitions only; they do not own global prices.

`PricingParser` owns version-1 grammar validation and returns no partial state.
`PricingStorage` owns missing-file seeding, reading, canonical serialization, and
atomic saves. It reports checked `PricingStorageException` values, a subtype of
the shared `StorageException`. `PricingStorageOperationHook` is a package-local
fault seam for storage-stage tests, matching existing storage conventions.

`CustomerApplication` owns the one-time session load and passes the immutable
state to the UI and selection creation. `CustomerUi` formats prices supplied by
the state or selection snapshots; it does not calculate prices. `Bill` uses only
captured selection prices and the optional promotion snapshot.

## Version-1 validation and serialization

- The first record is `CINECLI-PRICING<TAB>1`; a UTF-8 BOM before that header is
  accepted.
- Nonblank records are `TICKET_PRICE`, `SNACK_PRICE`, or `PROMOTION`, each with
  exactly three fields. Input record order is accepted when the complete state is
  valid.
- Ticket and snack identity names are their fixed enum names. Each must occur
  exactly once.
- Price syntax is `(0|[1-9][0-9]{0,4})\.[0-9]{2}` and its parsed cent value is
  1 through 999999. Promotion codes and percentages follow the PRD rules.
- Canonical output has a final newline, fixed ticket records in `TicketType`
  declaration order, fixed snack records in `SnackMenuItem` declaration order,
  and promotions sorted lexicographically by canonical code.

## Save protocol

`save(Pricing)` serializes and reparses the complete proposed state before
touching the target. It then creates the target directory if necessary, creates a
temporary file in that directory, writes the complete UTF-8 bytes, forces the
temporary file, and moves it with `ATOMIC_MOVE` and `REPLACE_EXISTING`. Any I/O
or unsupported-atomic-move failure becomes `PricingStorageException`; temporary
cleanup never replaces the primary failure. `load()` calls this protocol only
when the target is known to be missing (`Files.notExists`), using
`Pricing.defaults()`; it otherwise attempts the read and does not reseed an
existing or indeterminate target.

## Snapshot semantics

`TicketSelection` and `SnackSelection` records each contain a validated
`unitPriceInCents`. `PromoCode` contains a normalized canonical code and a
validated percentage. `Bill` takes defensive list copies and sums these snapshots
with checked long-cent arithmetic. It has no dependency on mutable global pricing.

## Intentional exclusions

No production generic atomic-writer abstraction is introduced: the storage code
uses the established catalogue/seat atomic-write sequence and its pricing-specific
typed fault seam. Admin editing and promotion management are deferred.
