# Screening Management - 29 August 2026

## Approved scope and decisions

Implemented Screening Management list, add, edit/reschedule, and delete flows.
Screening IDs are immutable `SCR-<UUID>` values; valid legacy IDs remain valid.
The parent movie is selected only during Add and is immutable. Edit changes the
date, time, or both. Rescheduling preserves occupancy because the screening ID
does not change; deleting a screening removes its occupancy.

## Implementation

- Added the `ScreeningManagementApplication` workflow, screening input rules,
  terminal rendering, deterministic UUID seam, and workflow outcomes.
- Preserved `catalog.tsv` version 1 and `seats.tsv` version 1. Screenings display
  in movie-major persisted order and append to the selected parent movie.
- Extended the existing Movie deletion foundation, rather than creating a second
  recovery mechanism. The shared catalogue transaction journal now validates and
  recovers both `DELETE_MOVIE` and `DELETE_SCREENING` operations while retaining
  operation-specific semantic preparation and result models.
- Preserved existing Movie Management behaviour and regression tests.

## Verification

- Focused Screening workflow, storage-failure, deletion-transaction, and recovery
  tests cover success, validation, cancellation, occupancy preservation/clearing,
  durable recovery, ordering, and terminal failures.
- Integrated `mvn verify` passed with 234 tests and satisfied the aggregate JaCoCo
  line and branch coverage gate.

## Documentation

Updated the Developer Guide, data README, this concise requirements-to-tests
checklist, and the Admin Interface Plan status. Full UserGuide administrator
instructions remain deferred because `Main` does not yet provide role routing.

## Deferred work

- Administrator role routing and end-user administrator entry points.
- Moving screenings between movies, manual reordering, auditorium/clash rules,
  and duplicate-time policy.
- Pricing/promotion administration and booking persistence.
