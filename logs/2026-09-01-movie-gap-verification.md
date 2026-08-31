# Movie Management Verification-Gap Review

**Date:** 1 September 2026

**Scope:** Resolve only nondeferred Movie-management verification gaps on the
current administrator integration baseline. Role-routing contracts were inspected
as integration constraints and were not changed.

## Inputs reviewed

- `AGENTS.md`;
- both authoritative standards PDFs in `../Resources/`;
- `docs/AdminInterfacePlan.md`;
- the owner-approved Movie PRD and the revised Movie TDD;
- the Movie workflow, catalogue, seat, transaction, router, and integration
  tests; and
- the final implementation diff and JaCoCo report.

## Design review outcome

The implemented boundaries remain appropriately separated: the Movie workflow
coordinates terminal interaction and drafts, input rules interpret submitted
values, Movie text renders output, catalogue and seat storage own single-file
format and atomic replacement, and `MovieDeletionTransaction` owns only
multi-file recovery. `ApplicationRouter` remains the owner of cross-role raw
command routing and exactly-once `CatalogRecoveryGate` invocation. No production
dependency, persistence schema, database, framework, or unrelated refactor was
introduced.

The verification change adds only a post-publication journal classification seam:
an absent journal is `NOT_APPLIED`, an exact published candidate is
`RECOVERY_PENDING`, and any divergent journal is preserved and blocks later
affected access. Recovery additionally rejects a childless `DELETE_MOVIE`
journal, because a childless deletion never creates a journal. These rules match
the revised TDD's data-safety protocol.

## Remaining owner dependencies

This review does not approve the revised TDD or decide MOV-DEC-001 (the display
policy for legacy non-TSV ISO control characters). Those are explicit owner
approval dependencies. MOV-GAP-001 remains the separately recorded historical
direct-workflow EOF/input-failure evidence deferral; the production role-routing
contracts themselves are already implemented and were preserved.

## Verification

`mvnw clean verify` passed with 306 tests, 0 failures, 100% aggregate line
coverage (3381/3381), and 100% aggregate branch coverage (1431/1431). The final
diff passed `git diff --check` and the JaCoCo report was inspected after the
clean verification.
