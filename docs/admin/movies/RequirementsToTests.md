# Movie Management Requirements-to-Tests Checklist

**Status:** Approved implementation checklist

**Draft date:** 28 August 2026

**Source PRD:** Owner-approved on 28 August 2026

**Source TDD:** Owner-approved on 28 August 2026

## Purpose

This checklist is the normative traceability record for Movie management. It
identifies the public seam and planned observable evidence for every PRD
requirement and use-case extension. During implementation, each `Planned` entry
must be changed to `Passing` only after the named test exists, fails for the
intended reason, passes with the minimum implementation, and has been inspected.

`Deferred - role routing` is an approved workstream handoff, not a completed
requirement. No other row may remain deferred when the Movie branch is offered
for integration.

## Test seam index

| Prefix | Public seam | Planned test class |
| --- | --- | --- |
| `MOV-WF` | `MovieManagementApplication.run()` | `MovieManagementApplicationTest` |
| `MOV-CAT` | `CatalogStorage.load/save` | `CatalogStorageTest` |
| `MOV-SEAT` | Read-only and administrative `SeatStorage` snapshot interface | `SeatStorageTest` |
| `MOV-TX` | `MovieDeletionTransaction.recover/prepare/commit` | `MovieDeletionTransactionTest` |
| `MOV-ROUTE` | Later shared application loop | `ApplicationRoutingTest` |

Tests at the first four seams use real files under JUnit `@TempDir`. The workflow
test uses a scripted terminal adapter and deterministic UUID adapter. Storage and
transaction tests use typed fault adapters only to arrange filesystem failures;
assertions remain at the public seam.

## Planned workflow tests

| ID | Planned test name and observable evidence | Status |
| --- | --- | --- |
| MOV-WF-001 | `run_nonemptyCatalog_listsCompleteMoviesInPersistedOrder` asserts the exact multi-line entries and fixed menu. | Planned |
| MOV-WF-002 | `run_emptyCatalog_keepsAllActionsAndRejectsEmptyEditAndDelete` asserts the empty message, both no-target messages, redraw, and Back. | Planned |
| MOV-WF-003 | `run_actionInputPartitions_repromptWithExactFixedMessage` independently covers blank, negative, sign, decimal, exponent, internal space, leading zero, unavailable values, non-ASCII digits, overflow, and `0`/`1`/`2`/`3`. | Planned |
| MOV-WF-004 | `run_targetInputBoundaries_repromptWithoutLosingOperation` covers `0`, `1`, count, count + 1, malformed, overflow, and `/cancel` for both Edit and Delete. | Planned |
| MOV-WF-005 | `run_titlePartitions_normalizeOrRejectExactly` covers raw tab/control rejection, blank Unicode whitespace, outer `isWhitespace`/`isSpaceChar`, Unicode text, preserved internal spaces, duplicates, ordinary slash titles, and title `0`. | Planned |
| MOV-WF-006 | `run_ratingPartitions_mapNumbersAndRejectOtherValues` covers each rating number, literal names, `0`, `4`, malformed input, retry, and `/cancel`. | Planned |
| MOV-WF-007 | `run_addConfirmed_generatesUniqueLowercaseUuidAndAppends` asserts collision retry, stable preview ID, final bytes, display position, success summary, and persisted redraw. | Planned |
| MOV-WF-008 | `run_addCancelledAtEachPrompt_preservesCatalog` parameterizes title, rating, invalid-confirmation retry, `N`, and `/cancel`, asserting exact cancellation and original bytes. | Planned |
| MOV-WF-009 | `run_addSaveFailure_reportsNotSavedAndPreservesCatalog` injects each catalogue atomic-write failure and asserts `BACK`, cause/message, and original bytes. | Planned |
| MOV-WF-010 | `run_editTitleConfirmed_preservesIdentityPositionRatingAndScreenings` asserts preview delta, atomic bytes, success, and redraw. | Planned |
| MOV-WF-011 | `run_editRatingOrBothConfirmed_persistsOneAtomicFinalState` covers rating-only and two-field drafts with complete old/new preview lines. | Planned |
| MOV-WF-012 | `run_editDraftReplacementAndReversion_tracksOnlyRealChanges` covers repeated field editing, reversion to original, unchanged messages, and Review with no changes. | Planned |
| MOV-WF-013 | `run_editCancelledAtSelectionMenuFieldOrConfirmation_discardsWholeDraft` covers `0`, `/cancel`, and `N` with exact message and original bytes. | Planned |
| MOV-WF-014 | `run_editSaveFailure_reportsNotSavedAndPreservesCatalog` asserts both proposed fields fail atomically and the original file remains exact. | Planned |
| MOV-WF-015 | `run_childlessDeleteConfirmed_usesCatalogOnlyAndClosesOrderGap` uses a failing seat-read adapter to prove no occupancy access, covers last-movie deletion, and asserts no journal. | Planned |
| MOV-WF-016 | `run_cascadePreview_ordersScreeningsAndShowsOnlyCounts` asserts English date-times, child order, per-screening and total counts, warning, and absence of coordinates. | Planned |
| MOV-WF-017 | `run_cascadeWithMissingSeats_previewsZeroAndPreservesAbsence` asserts successful journaled deletion without creating `seats.tsv`. | Planned |
| MOV-WF-018 | `run_malformedSeats_blocksOnlyCascadePreparation` asserts the preparation error and homepage return while listing, Add, Edit, and childless Delete remain usable. | Planned |
| MOV-WF-019 | `run_deleteCancelledAtSelectionOrConfirmation_preservesAllData` covers `0`, `/cancel`, `N`, invalid-confirmation retry, exact message, and absent journal. | Planned |
| MOV-WF-020 | `run_cascadeConfirmed_reportsExactRemovalAndRedrawsPersistedState` asserts both intended files, journal removal, counts, relative ordering, and success text. | Planned |
| MOV-WF-021 | `run_typedGlobalCommandAtEveryPrompt_discardsUnconfirmedState` parameterizes `ADMIN`, `CUSTOMER`, and `EXIT` at all Movie prompts and asserts typed outcomes and unchanged durable data. | Planned |
| MOV-WF-022 | `run_eofOrInputFailureAtEveryPrompt_terminatesWithoutPrecommitWrites` parameterizes every prompt, distinguishes error output, and handles a post-journal failure as pending recovery. | Planned |
| MOV-WF-023 | `run_outputFailureBeforeConfirmation_terminatesWithoutMutation` fails each complete screen/preview write and asserts the best-effort error and exact original state. | Planned |
| MOV-WF-024 | `run_outputFailureAfterPersistence_doesNotRollBackDurableChange` covers Add, Edit, completed Delete, and pending-journal failure. | Planned |
| MOV-WF-025 | `run_validPendingRecovery_reportsCompletionBeforeListing` asserts recovery-before-load, exact completion message, intended files, journal removal, and normal Movie display. | Planned |
| MOV-WF-026 | `run_unrecoverableJournal_blocksMovieManagementWithoutPartialList` asserts exact access-error prefix, `BACK`, unchanged targets, and retained journal. | Planned |

## Planned catalogue-storage tests

| ID | Planned test name and observable evidence | Status |
| --- | --- | --- |
| MOV-CAT-001 | `save_validCompleteCatalog_writesCanonicalExactBytes` asserts UTF-8, LF, no BOM, final newline, movie order, grouped screening order, and date/time grammar. | Planned |
| MOV-CAT-002 | `save_emptyCatalog_writesHeaderOnlyAndLoadsEmpty` asserts the exact valid empty file. | Planned |
| MOV-CAT-003 | `save_legacyIdsAndAcceptedLegacyTitle_roundTripsWithoutIdentityChange` proves version-1 compatibility and UI-only control restrictions. | Planned |
| MOV-CAT-004 | `save_invalidCompleteState_rejectsEachDefectBeforeFileAccess` independently covers nulls, invalid/duplicate IDs, invalid titles, null ratings/times, and graph-order defects. | Planned |
| MOV-CAT-005 | `save_atomicStageFailure_preservesOriginalBytes` parameterizes directory, temporary creation, write, force, and atomic replacement failures and inspects exception causes. | Planned |
| MOV-CAT-006 | `save_atomicMoveUnsupported_hasNoFallbackAndPreservesOriginal` asserts the explicit no-fallback message and cleanup. | Planned |
| MOV-CAT-007 | `save_cleanupFailure_doesNotMisreportCommittedReplacement` distinguishes best-effort temporary cleanup from target update failure. | Planned |
| MOV-CAT-008 | `save_thenLoad_returnsEqualOrderedModel` verifies the external storage round trip without calling an internal parser seam. | Planned |

## Planned occupancy-storage tests

| ID | Planned test name and observable evidence | Status |
| --- | --- | --- |
| MOV-SEAT-001 | `loadSnapshot_missingFile_returnsAbsentEmptyWithoutInitialization` asserts the parent and file remain missing. | Planned |
| MOV-SEAT-002 | `loadSnapshot_headerOnly_returnsPresentEmpty` distinguishes an existing empty file from absence. | Planned |
| MOV-SEAT-003 | `loadSnapshot_validRecords_returnsCompleteImmutableSortedState` validates every record before returning the public snapshot. | Planned |
| MOV-SEAT-004 | `loadSnapshot_malformedOrUnknownScreening_preservesAndRejectsWholeFile` covers malformed partitions independently. | Planned |
| MOV-SEAT-005 | `replaceSnapshot_removedRecords_writesCanonicalExactBytes` asserts unrelated occupancy and file presence are preserved. | Planned |
| MOV-SEAT-006 | `replaceSnapshot_unchangedLogicalState_preservesExactOriginalBytes` uses a valid noncanonical source and proves no replacement. | Planned |
| MOV-SEAT-007 | `replaceSnapshot_atomicStageFailure_preservesOriginalBytes` parameterizes temporary creation, write, force, unsupported atomic move, and replacement failure. | Planned |
| MOV-SEAT-008 | `replaceSnapshot_missingIntent_performsNoCreationOrDeletion` proves presence cannot change through administrative replacement. | Planned |

## Planned deletion-transaction tests

| ID | Planned test name and observable evidence | Status |
| --- | --- | --- |
| MOV-TX-001 | `prepare_childlessMovie_returnsZeroImpactWithoutSeatAccess` asserts the opaque preview and read-free seat path. | Planned |
| MOV-TX-002 | `prepare_cascade_returnsOrderedImpactsAndIntendedCounts` asserts immutable preview values from real catalogue and occupancy files. | Planned |
| MOV-TX-003 | `commit_missingSeats_publishesExactVersionOneJournalBeforeTargets` inspects the synchronized publication point and exact missing-form grammar. | Planned |
| MOV-TX-004 | `commit_presentSeats_publishesExactVersionOneJournalBeforeTargets` asserts standard padded Base64, lowercase SHA-256, field order, and exact snapshots. | Planned |
| MOV-TX-005 | `commit_prepublicationFault_returnsNotAppliedAndPreservesTargets` parameterizes journal directory, temporary creation, write, force, and atomic-publication failures. | Planned |
| MOV-TX-006 | `commit_postpublicationFault_returnsRecoveryPendingAndRetainsJournal` parameterizes catalogue write/force/replace, occupancy write/force/replace, verification, and journal-delete failures. | Planned |
| MOV-TX-007 | `commit_stalePreparedDeletion_returnsNotAppliedWithoutJournal` changes each target after preparation and asserts divergence is detected before durable intent. | Planned |
| MOV-TX-008 | `commit_childlessFailure_isAlwaysNotAppliedAndNeverCreatesJournal` covers catalogue-only failure stages. | Planned |
| MOV-TX-009 | `commit_samePreparedDeletionTwice_rejectsSecondWithoutStorageAccess` verifies the single-use interface invariant. | Planned |
| MOV-TX-010 | `recover_noJournal_returnsNoJournalWithoutTargetAccess` asserts the no-op result. | Planned |
| MOV-TX-011 | `recover_originalOriginal_appliesBothAndDeletesJournal` covers the first recovery-state row. | Planned |
| MOV-TX-012 | `recover_originalIntended_appliesOnlyCatalogAndDeletesJournal` covers the second recovery-state row. | Planned |
| MOV-TX-013 | `recover_intendedOriginal_appliesOnlySeatsAndDeletesJournal` covers the third recovery-state row. | Planned |
| MOV-TX-014 | `recover_intendedIntended_verifiesAndDeletesJournal` covers cleanup-only recovery. | Planned |
| MOV-TX-015 | `recover_unchangedSeatBytes_skipsSeatReplacementForEveryCatalogState` proves identical original/intended occupancy is both classifications. | Planned |
| MOV-TX-016 | `recover_divergentCatalogOrSeats_blocksAndPreservesEverything` independently covers neither-match bytes and unexpected presence changes. | Planned |
| MOV-TX-017 | `recover_malformedJournalPartition_blocksAndPreservesEverything` independently covers BOM, header/version, order, counts, names, blanks, extras, operation, ID, Base64, digest syntax/mismatch, embedded formats, presence mismatch, and semantic-transition defects. | Planned |
| MOV-TX-018 | `recover_interruptedAtEachStage_retainsJournalAndRetryCompletes` parameterizes every recovery replacement, force, verification, and cleanup stage. | Planned |
| MOV-TX-019 | `recover_leftoverUnpublishedTemporary_ignoresItWithoutCreatingIntent` proves temporary files are not journals. | Planned |
| MOV-TX-020 | `recover_validJournalRepeatedly_isIdempotent` interrupts, retries, completes, then returns `NO_JOURNAL` on the next call. | Planned |

## Deferred role-routing tests

| ID | Planned test name and observable evidence | Status |
| --- | --- | --- |
| MOV-ROUTE-001 | `run_rawGlobalCommandAtEveryMoviePrompt_emitsTypedTransition` covers trimmed mixed-case `/admin`, `/customer`, and `/exit` through the production Reader adapter. | Deferred - role routing |
| MOV-ROUTE-002 | `run_customerOrAdminAccessWithPendingJournal_recoversBeforeAffectedRead` proves the shared loop gates both roles before catalogue or occupancy access. | Deferred - role routing |

## Equivalence partitions and boundaries

| Input/state | Required partitions and boundary representatives | Covered by |
| --- | --- | --- |
| Action menu | blank, `-1`, `0`, `1`, `2`, `3`, `4`, `01`, `1.0`, `1e0`, internal space, non-ASCII digit, `2147483647`, `2147483648`, text | MOV-WF-003 |
| Movie target for count `n` | `0`, `1`, `n`, `n + 1`, malformed, overflow, `/cancel`; empty catalogue bypasses prompt | MOV-WF-002, MOV-WF-004 |
| Title | empty, only Unicode spaces, raw tab/control at start/middle/end, outer Java whitespace, outer Unicode space characters, Unicode content, duplicate content, internal repeated spaces, reserved typed commands, `/cancel`, other slash prefix, `0` | MOV-WF-005, MOV-WF-021 |
| Rating | `1`, `2`, `3`, `0`, `4`, literal rating, malformed, overflow, `/cancel` | MOV-WF-006 |
| Confirmation | `Y`, `y`, `N`, `n`, surrounding space, `/cancel`, blank, partial word, unavailable number, typed global command | MOV-WF-008, MOV-WF-019, MOV-WF-021 |
| Catalogue size | zero, one, multiple; Add at `n + 1`; Delete first/middle/last/only | MOV-WF-001, MOV-WF-002, MOV-WF-007, MOV-WF-015, MOV-WF-020 |
| Child screenings | zero, one, multiple in nonchronological persisted order | MOV-WF-015, MOV-WF-016, MOV-TX-001, MOV-TX-002 |
| Occupancy | missing, present header-only, unrelated only, one affected, multiple affected across children, malformed | MOV-WF-016 through MOV-WF-020, MOV-SEAT-001 through MOV-SEAT-008 |
| Journal target state | no journal; original/original; original/intended; intended/original; intended/intended; divergent catalogue; divergent occupancy; presence divergence | MOV-TX-010 through MOV-TX-020 |

Invalid inputs are tested one at a time before combined-invalid scenarios. Every
valid enum value and every valid menu choice appears in at least one positive
test.

## Use-case extension traceability

| Use-case extension | Planned tests | Status |
| --- | --- | --- |
| UC-MOV-01 2a: recovery cannot safely complete | MOV-WF-026, MOV-TX-016, MOV-TX-017 | Planned |
| UC-MOV-01 3a: empty catalogue | MOV-WF-002, MOV-CAT-002 | Planned |
| UC-MOV-01 3b: malformed or unreadable catalogue | MOV-WF-026, existing catalogue-load tests | Planned |
| UC-MOV-01 4a: output fails | MOV-WF-023 | Planned |
| UC-MOV-02 2a: invalid field | MOV-WF-005, MOV-WF-006 | Planned |
| UC-MOV-02 2b: local cancellation | MOV-WF-008 | Planned |
| UC-MOV-02 3a: ID collision | MOV-WF-007 | Planned |
| UC-MOV-02 4a: invalid confirmation | MOV-WF-008 | Planned |
| UC-MOV-02 4b: N or `/cancel` | MOV-WF-008 | Planned |
| UC-MOV-02 5a: persistence failure | MOV-WF-009, MOV-CAT-005, MOV-CAT-006 | Planned |
| UC-MOV-02 *a: global command or precommit I/O failure | MOV-WF-021 through MOV-WF-023, MOV-ROUTE-001 | Planned / deferred split |
| UC-MOV-03 1a: empty catalogue | MOV-WF-002 | Planned |
| UC-MOV-03 1b: invalid selection | MOV-WF-004 | Planned |
| UC-MOV-03 2a: invalid field | MOV-WF-005, MOV-WF-006 | Planned |
| UC-MOV-03 2b: proposed field equals persisted value | MOV-WF-012 | Planned |
| UC-MOV-03 3a: no real change | MOV-WF-012 | Planned |
| UC-MOV-03 4a: N or `/cancel` | MOV-WF-013 | Planned |
| UC-MOV-03 5a: persistence failure | MOV-WF-014, MOV-CAT-005, MOV-CAT-006 | Planned |
| UC-MOV-03 *a: `0` cancellation | MOV-WF-004, MOV-WF-013 | Planned |
| UC-MOV-03 *b: global command or precommit I/O failure | MOV-WF-021 through MOV-WF-023, MOV-ROUTE-001 | Planned / deferred split |
| UC-MOV-04 1a: empty catalogue | MOV-WF-002 | Planned |
| UC-MOV-04 1b: invalid selection | MOV-WF-004 | Planned |
| UC-MOV-04 2a: childless movie | MOV-WF-015, MOV-TX-001, MOV-TX-008 | Planned |
| UC-MOV-04 2b: missing occupancy | MOV-WF-017, MOV-SEAT-001, MOV-TX-003 | Planned |
| UC-MOV-04 2c: malformed or unreadable occupancy | MOV-WF-018, MOV-SEAT-004 | Planned |
| UC-MOV-04 3a: N or `/cancel` | MOV-WF-019 | Planned |
| UC-MOV-04 4a: failure before durable intent | MOV-TX-005, MOV-WF-015 | Planned |
| UC-MOV-04 4b: failure after durable intent | MOV-TX-006, MOV-TX-018 | Planned |
| UC-MOV-04 5a: deleted movie was last | MOV-WF-015, MOV-CAT-002 | Planned |
| UC-MOV-04 *a: global command or precommit I/O failure | MOV-WF-021 through MOV-WF-023, MOV-ROUTE-001 | Planned / deferred split |

## Requirement traceability

| Requirement | Planned tests | Status |
| --- | --- | --- |
| MOV-FR-001 | MOV-WF-001, MOV-WF-002, MOV-WF-025, MOV-WF-026 | Planned |
| MOV-FR-002 | MOV-WF-001 through MOV-WF-003 | Planned |
| MOV-FR-003 | MOV-WF-003, MOV-WF-004, MOV-WF-006 | Planned |
| MOV-FR-004 | MOV-WF-005, MOV-WF-007, MOV-WF-010, MOV-CAT-003, MOV-CAT-004 | Planned |
| MOV-FR-005 | MOV-WF-006, MOV-WF-007, MOV-WF-011 | Planned |
| MOV-FR-006 | MOV-WF-007 through MOV-WF-009, MOV-CAT-001, MOV-CAT-005 | Planned |
| MOV-FR-007 | MOV-WF-004, MOV-WF-013, MOV-WF-019 | Planned |
| MOV-FR-008 | MOV-WF-010 through MOV-WF-013 | Planned |
| MOV-FR-009 | MOV-WF-010 through MOV-WF-014 | Planned |
| MOV-FR-010 | MOV-WF-016 through MOV-WF-018, MOV-TX-001, MOV-TX-002 | Planned |
| MOV-FR-011 | MOV-WF-015, MOV-WF-017, MOV-WF-020, MOV-TX-003 through MOV-TX-009 | Planned |
| MOV-FR-012 | MOV-WF-008, MOV-WF-013, MOV-WF-019, MOV-WF-023 | Planned |
| MOV-FR-013 | MOV-WF-001, MOV-WF-007, MOV-WF-010, MOV-WF-015, MOV-WF-016, MOV-CAT-001 | Planned |
| MOV-FR-014 | MOV-WF-008, MOV-WF-013, MOV-WF-019, MOV-WF-021, MOV-ROUTE-001 | Planned / deferred split |
| MOV-FR-015 | MOV-WF-009, MOV-WF-014, MOV-WF-018, MOV-WF-025, MOV-WF-026, MOV-TX-005 through MOV-TX-020 | Planned |
| MOV-FR-016 | MOV-WF-022 through MOV-WF-024, MOV-TX-006, MOV-TX-018 | Planned |
| MOV-NFR-001 | MOV-CAT-001 through MOV-CAT-004, MOV-SEAT-002 through MOV-SEAT-005 | Planned |
| MOV-NFR-002 | MOV-CAT-005 through MOV-CAT-007, MOV-SEAT-006 through MOV-SEAT-008, MOV-TX-005 through MOV-TX-020 | Planned |
| MOV-NFR-003 | MOV-WF-001 through MOV-WF-020 exact transcript assertions | Planned |
| MOV-NFR-004 | All listed public-seam tests and deterministic adapters | Planned |
| MOV-NFR-005 | Design review plus tests confined to the four confirmed seams | Planned |

## Implementation completion gate

Before the Movie branch is offered for integration:

- every nondeferred row is `Passing`;
- every named test has been inspected for behaviour-level assertions and an
  independent expected value;
- `mvn verify` passes with 100% aggregate line and branch coverage;
- the JaCoCo HTML report has been inspected;
- exact catalogue, occupancy, and journal bytes have been reviewed;
- no tests mock an internal CineCLI module or assert private call order;
- no uncovered requirement, partition, boundary, exception, transaction state,
  or use-case extension remains; and
- the two deferred rows are copied into and fulfilled by the later role-routing
  checklist.
