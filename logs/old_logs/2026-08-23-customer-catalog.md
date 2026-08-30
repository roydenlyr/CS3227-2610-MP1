# Customer Catalog Vertical Slice Summary

## Scope

Implemented the approved customer-only flow:

`Welcome screen -> ENTER -> persisted movie catalog`

The catalog displays movie titles, display-only content ratings, and screening
dates and times. Administration, movie selection, seats, snacks, discounts, and
checkout remain out of scope.

## Owner decisions applied

- Structured UTF-8 plain-text persistence; no database
- Stable movie and screening IDs
- Screenings contain both date and time
- Ratings are display-only
- Fictional defaults are bundled in the JAR
- Missing runtime data initializes from the bundled defaults
- Valid empty data displays a friendly message
- Malformed data produces a clear error without fallback or partial display
- Customer mode only
- Local repository branch renamed from `main` to `master`

## Implementation decisions

- A single versioned TSV file keeps movie-screening references together and needs
  no production dependency.
- The three ratings named in the approved flow (`PG13`, `M18`, and `R21`) form the
  accepted version 1 rating set.
- File order is preserved for both movies and screenings. Movies may have no
  screenings, in which case the UI says none are available.
- The fixed seed dates are deterministic and are not shifted based on the clock.
- The runtime path is relative to the process working directory.
- The local `master` branch has no upstream because the remote still exposes
  `origin/main`; no remote branch was pushed, changed, or deleted.

## Implementation

- Added immutable movie, screening, and rating model types.
- Added strict, line-aware TSV parsing and validation.
- Added missing-file initialization and classpath seed loading.
- Added a customer UI and application coordinator with separate normal/error
  output.
- Replaced the silent bootstrap entry point with the approved customer workflow.
- Updated the README, user guide, developer guide, data policy, and reflections.

## Testing

- Added parser tests for valid, empty, duplicate, missing, orphaned, unsupported,
  and invalid date/time records, including a UTF-8 title.
- Added storage tests for seed initialization, preservation of existing valid,
  empty, and malformed files, idempotence, and a missing resource.
- Added CLI/application tests for output order, the empty message, graceful errors,
  and fresh initialization through the main wiring.

## Verification

- Baseline `clean verify`: succeeded with 1 test before implementation.
- Implementation `clean verify`: succeeded with 21 tests, 0 failures, 0 errors,
  and 0 skipped.
- Final JAR/resource and isolated fresh-run checks are recorded after final
  verification.

## Issues and limitations

- The sandbox could not use Maven's configured local repository, so Maven checks
  required the approved external execution path.
- The former smoke test blocked after the entry point became interactive. The run
  was stopped and the test was replaced with deterministic injected input/output.
- The hosted remote still uses `main`; changing the remote default branch was not
  part of the authorized local implementation.
- Runtime write/update support beyond first-run initialization is deferred with
  administration.
