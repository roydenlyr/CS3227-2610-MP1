# Pricing and Promotions Management - 29 August 2026

## Implemented scope

- Added guided administrator workflows for fixed ticket prices and fixed
  snack/combo prices. All identities remain fixed; valid prices are exact cents
  from S$0.01 through S$9,999.99.
- Added promotion list, add, independent code/percentage edit, rename collision
  rejection, and destructive delete confirmation. Codes normalize through
  `PromoCode`; valid whole discounts are 1 through 100, including 100.
- Every mutation loads complete pricing, creates a new immutable replacement,
  previews it, and saves only after confirmation using existing `PricingStorage`.
  No pricing contract, file format, or alternate path was introduced.

## QA and regression proof

- Focused workflow tests cover validation, confirmation, cancellation, code
  normalization/collision, and promotion order loaded from a deliberately
  noncanonical valid file.
- Storage-failure coverage proves the administrator reports that a ticket change
  was not saved and retains the original pricing bytes.
- Snapshot regression coverage proves a later complete pricing save does not
  alter existing ticket, snack, promo-code, or bill snapshots.
- `.\mvnw.cmd '-Djunit.jupiter.tempdir.cleanup.mode.default=NEVER' clean verify`
  passed with 272 tests and the aggregate JaCoCo line and branch coverage gate
  satisfied. The desktop runner requires this cleanup setting because Windows can
  retain a test-file handle after assertions have completed.

## Documentation and deferred work

Updated the Developer Guide, data README, Admin Interface Plan, and pricing
requirements-to-tests checklist. Full UserGuide administrator instructions and
role routing remain deferred because `Main` still starts only the customer flow.
