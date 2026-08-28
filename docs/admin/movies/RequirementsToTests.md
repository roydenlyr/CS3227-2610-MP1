# Movie Management Requirements-to-Tests Checklist

**Status:** Implementation complete - all nondeferred checks passing

**Approval date:** 28 August 2026

**Source PRD:** Owner-approved on 28 August 2026

**Source TDD:** Owner-approved on 28 August 2026

## Verification summary

The Movie workstream is implemented through the four approved public seams. The
final `mvn verify` run executed 184 tests with no failures and met the repository's
100% aggregate line and branch coverage checks. Tests use real temporary files;
test doubles are confined to the terminal, UUID, and typed file-operation
boundaries.

Several planned scenarios were consolidated into parameterized or matrix-style
tests. Every evidence name below is an implemented test method, not a prospective
name.

## Public-seam evidence

### Workflow (`MOV-WF`)

| IDs | Implemented test evidence | Status |
| --- | --- | --- |
| 001 | `run_nonemptyCatalog_listsCompleteMoviesInPersistedOrder` | Passing |
| 002 | `run_emptyCatalog_keepsActionsAndRejectsEmptyEditAndDelete` | Passing |
| 003, 005, 006 | `run_numericTitleRatingAndConfirmationPartitions_repromptExactly`; `run_addConfirmed_retriesValidationAndCollisionThenAppends` | Passing |
| 004, 013, 019 | `run_editAndDeleteCancellationAlternatives_preserveData`; `run_editFieldCancelRatingOnlyPreviewAndEmptyMessageFailure_coverAlternatives` | Passing |
| 007 | `run_addConfirmed_retriesValidationAndCollisionThenAppends` | Passing |
| 008 | `run_addCancelledAtRating_preservesExactCatalog`; `run_publicConstructorsAndLocalTitleCancel_coverProductionAdapters` | Passing |
| 009, 014 | `run_addOrEditSaveFailure_reportsNotSavedAndPreservesCatalog` | Passing |
| 010, 011 | `run_editBothConfirmed_preservesIdentityPositionAndScreenings`; `run_editFieldCancelRatingOnlyPreviewAndEmptyMessageFailure_coverAlternatives` | Passing |
| 012 | `run_editNoOpThenCancel_reportsWithoutWriting` | Passing |
| 015 | `run_childlessDelete_showsNoCascadeAndLeavesMalformedSeatsUntouched` | Passing |
| 016, 020 | `run_cascadeDelete_previewsCountsAndPersistsThenRedraws` | Passing |
| 017 | `commit_missingSeats_preservesAbsenceAndUsesJournaledCascade`; workflow output matrix | Passing |
| 018 | `run_accessAndPreparationFailures_reportExactPrefixesAndReturnBack`; `run_childlessDelete_showsNoCascadeAndLeavesMalformedSeatsUntouched` | Passing |
| 021 | `run_globalCommandsAtNestedPrompts_returnTypedOutcomesWithoutWrites` | Passing |
| 022 | `run_typedGlobalEofInputAndOutputFailures_areFailClosed` | Passing |
| 023, 024 | `run_outputFailureAtEveryRenderedBlock_terminatesAndNeverRevertsDurableData`; `run_storageFailureMessageOutputFailure_terminates` | Passing |
| 025 | `run_validPendingRecovery_reportsBeforeListingOrTerminatesOnOutputFailure` | Passing |
| 026 | `run_accessAndPreparationFailures_reportExactPrefixesAndReturnBack`; transaction malformed/divergence tests | Passing |

### Catalogue storage (`MOV-CAT`)

| IDs | Implemented test evidence | Status |
| --- | --- | --- |
| 001, 008 | `save_completeValidState_writesCanonicalBytesAndRoundTrips` | Passing |
| 002 | `load_existingEmptyValidCatalog_returnsEmptyWithoutSeeding`; last-movie workflow tests | Passing |
| 003 | Existing legacy load/UTF-8 tests plus canonical round-trip test | Passing |
| 004 | `save_invalidCompleteState_preservesOriginalBytes`; `save_nullStateOrMovie_rejectsBeforeChangingTarget` | Passing |
| 005 | `save_atomicStageFailures_preserveOriginalAndPrimaryCause` | Passing |
| 006, 007 | `save_atomicMoveUnsupportedAndCleanupFailure_preservePrimaryResult` | Passing |

### Occupancy storage (`MOV-SEAT`)

| IDs | Implemented test evidence | Status |
| --- | --- | --- |
| 001 | `loadSnapshot_missingFile_returnsAbsentEmptyWithoutInitialization` | Passing |
| 002 | `loadSnapshot_headerOnly_returnsPresentEmpty` | Passing |
| 003, 004 | Existing complete-file parser tests; `transactionSnapshot_targetDisappearsAfterValidatedRead_wrapsCause` | Passing |
| 005 | `replaceSnapshot_removedRecords_writesCanonicalExactBytes` | Passing |
| 006 | `replaceSnapshot_unchangedStatePreservesExactBytesAndTransactionPresenceRules` | Passing |
| 007 | Existing atomic replacement, temporary failure, and cleanup tests, including force-stage coverage | Passing |
| 008 | `replaceSnapshot_missingIntent_performsNoCreationOrDeletion`; `replaceSnapshot_presenceMismatchOrUnknownId_rejectsWithoutMutation` | Passing |

### Deletion transaction (`MOV-TX`)

| IDs | Implemented test evidence | Status |
| --- | --- | --- |
| 001 | `prepare_childlessAndCommit_neverAccessesMalformedSeats` | Passing |
| 002 | `prepare_cascade_returnsOrderedImpactsAndCommitRemovesExactState` | Passing |
| 003 | `commit_missingSeats_preservesAbsenceAndUsesJournaledCascade`; `recover_missingOccupancyJournal_validatesBothMissingLines` | Passing |
| 004 | `prepare_cascade_returnsOrderedImpactsAndCommitRemovesExactState`; semantic journal tests inspect digest/Base64 snapshots | Passing |
| 005, 006 | `commit_faultsBeforeAndAfterPublication_reportTruthfulStatus`; `commit_verificationDetectsDivergentTargetsAfterDurableIntent` | Passing |
| 007, 008 | `commit_stalePreparedDeletion_returnsNotAppliedWithoutJournal`; `prepareAndCommit_programmingOrStaleStatesFailBeforeIntent` | Passing |
| 009 | `prepare_cascade_returnsOrderedImpactsAndCommitRemovesExactState` verifies single use | Passing |
| 010 | `recover_noJournalOrMalformedJournal_isSafe` | Passing |
| 011-014 | `recover_everyOriginalIntendedCombination_completesIdempotently` | Passing |
| 015 | `recover_noAffectedSeatBytesMustRemainExactAndDivergentPresenceBlocks` | Passing |
| 016 | `recover_divergentTargetsAndMalformedJournal_preserveJournalAndTargets`; presence-divergence test | Passing |
| 017 | `recover_semanticallyMalformedJournalPartitions_blockBeforeTargetMutation`; malformed grammar tests | Passing |
| 018, 020 | `recover_readOrCleanupFault_retainsValidJournalForRetry`; recovery-state matrix | Passing |
| 019 | `recover_noJournalOrMalformedJournal_isSafe` and uniquely named temporary-file behavior | Passing |

## Equivalence partitions and boundaries

| Input or state | Evidence | Status |
| --- | --- | --- |
| Action number syntax, overflow, range, and all choices | `run_numericTitleRatingAndConfirmationPartitions_repromptExactly`; workflow matrix | Passing |
| Movie targets at 0, 1, count, count + 1, malformed, overflow, and `/cancel` | cancellation alternatives and output matrix | Passing |
| Raw controls, Unicode outer whitespace, blank, Unicode/internal spaces, duplicates, slash titles, and title `0` | add validation/collision and numeric-title tests | Passing |
| Ratings 1-3, invalid names/numbers/overflow, retry, and `/cancel` | numeric/rating test and cancellation alternatives | Passing |
| Confirmation Y/N, case/whitespace, `/cancel`, invalid retry, typed transition | add, edit, delete, global-command, and output matrices | Passing |
| Catalogue sizes zero, one, and multiple; stable append/edit/delete order | list, Add, Edit, childless and cascade Delete tests | Passing |
| Zero, one, and multiple nonchronological child screenings | transaction preparation and workflow preview tests | Passing |
| Missing, empty, unrelated, affected, malformed, and divergent occupancy | occupancy, workflow, and transaction suites | Passing |
| No journal; O/O, O/I, I/O, I/I; divergence; malformed; interrupted retry | recovery matrix and semantic journal suites | Passing |

## Requirement and use-case traceability

All `MOV-FR-001` through `MOV-FR-016`, `MOV-NFR-001` through `MOV-NFR-005`, and
all extensions of `UC-MOV-01` through `UC-MOV-04` are covered by the passing
public-seam evidence above. Exact transcripts, exact persisted bytes, typed
outcomes, exception status/cause, file presence, and failure preservation are
asserted at the applicable seam.

Invalid inputs are tested independently before combined-invalid cases. Boundary
values immediately below, at, and above every finite numbered range are covered.

## Deferred role-routing obligations

| ID | Required later evidence | Status |
| --- | --- | --- |
| MOV-ROUTE-001 | Production raw parsing of trimmed mixed-case `/admin`, `/customer`, and `/exit` into Movie's typed transitions at every prompt. | Deferred - role routing |
| MOV-ROUTE-002 | Route both customer and administrator access through the shared `CatalogRecoveryGate` before affected catalogue or occupancy data is used. | Gate implemented; shared routing deferred |

Movie management already tests every typed handoff and recovery behavior it owns.
The production parser and cross-role use of the recovery gate remain mandatory
acceptance items for the later role-routing workstream and are not claimed as
implemented here.
