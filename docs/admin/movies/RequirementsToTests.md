# Movie Management Requirements-to-Tests Checklist

**Status:** Draft verification revision - open nondeferred gaps

**Original checklist approval date:** 28 August 2026

**Revision date:** 28 August 2026

**Source PRD:** Owner-approved on 28 August 2026

**Source TDD:** Original approved on 28 August 2026; current revision awaiting approval

## Verification summary

The Movie workstream has implementation evidence through four approved public
seams. The recorded final `mvn verify` run executed 184 tests with no failures and
met the repository's 100% aggregate line and branch coverage checks. Tests use
real temporary files; test doubles are confined to the terminal, UUID, and typed
file-operation seams.

Several planned scenarios were consolidated into parameterized or matrix-style
tests. Backticked evidence names below are implemented test methods; plain text
identifies grouped evidence or an explicit missing scenario. Review of the
assertions found open behaviour and traceability gaps, so a
passing test suite and coverage gate do not yet justify the former claim that all
nondeferred checks pass.

## Test seam index

| Prefix | Public seam | Evidence class |
| --- | --- | --- |
| `MOV-WF` | `MovieManagementApplication.run()` | `MovieManagementApplicationTest` and workflow failure/recovery tests |
| `MOV-CAT` | `CatalogStorage.load/save` | `CatalogStorageTest` |
| `MOV-SEAT` | Read-only and administrative `SeatStorage` snapshot interface | `SeatStorageTest` |
| `MOV-TX` | `MovieDeletionTransaction.recover/prepare/commit` | `MovieDeletionTransactionTest` |
| `MOV-ROUTE` | Later shared application loop | Deferred role-routing tests |

IDs shown without a prefix in an evidence table inherit the prefix in that
table's heading.

`Passing` in an evidence table means the named test currently passes. Requirement
completion is evaluated separately in the traceability tables and may remain
partial when the test omits a required partition or assertion.

## Open verification gaps

| Gap | Affected requirements | Necessary evidence before completion |
| --- | --- | --- |
| MOV-GAP-001 | MOV-FR-014, MOV-FR-016; UC-MOV-02/03/04 `*` extensions | Exercise each typed global command, EOF, and input failure at every distinct prompt. Current global-command prefixes omit Edit title, Edit rating, and Edit confirmation; EOF/input-failure evidence is entry-only. |
| MOV-GAP-002 | MOV-FR-003, MOV-FR-004, MOV-FR-007, MOV-FR-008 | Add independent boundary/partition evidence for movie target `count + 1` and overflow; non-tab ISO controls; duplicate and ordinary slash-prefixed titles; and invalid Edit fields while another valid draft change is retained. |
| MOV-GAP-003 | MOV-FR-001 through MOV-FR-016, MOV-NFR-003 | Replace substring-only checks with independent exact expected output blocks or transcripts for every specified screen, prompt, preview, validation, cancellation, failure, and success path. |
| MOV-GAP-004 | MOV-FR-012, MOV-FR-016, MOV-NFR-002 | At every injected output failure, assert catalogue, occupancy, journal presence/bytes, and the pre-intent or post-intent durability outcome. The current output-failure matrix asserts only the returned outcome and error text. |
| MOV-GAP-005 | MOV-FR-015, MOV-NFR-002, TDD journal protocol | Add deterministic journal-directory failure and atomic-publication postcondition cases: absent journal means `NOT_APPLIED`, exact candidate means `RECOVERY_PENDING`, and a divergent existing journal is preserved and blocks later access. |
| MOV-GAP-006 | MOV-FR-010, MOV-FR-015, MOV-NFR-001 | Add missing-occupancy preview evidence at the workflow seam, prove malformed occupancy does not block List, Add, or Edit, add unreadable-occupancy workflow evidence, and inspect exact present-occupancy journal bytes independently of production formatting. |
| MOV-GAP-007 | MOV-FR-015, MOV-NFR-001, MOV-NFR-002 | Reject a semantically valid-looking childless `DELETE_MOVIE` journal and retain explicit evidence that intended catalogue bytes must be canonical. |
| MOV-GAP-008 | MOV-NFR-005 | Record a completed design review against the approved PRD, `AdminInterfacePlan.md`, both standards, and the confirmed deep-module interfaces. This NFR cannot be established by coverage alone. |

## Owner decision dependency

| Decision | Affected requirements | Choice and resulting evidence |
| --- | --- | --- |
| MOV-DEC-001 | MOV-FR-001, MOV-FR-004, MOV-NFR-001, MOV-NFR-003 | Choose how a legacy version-1 title containing a non-TSV ISO control character is displayed. The TDD recommends retaining the exact persisted value but displaying controls as visible uppercase four-hex escapes. After approval, add exact load, list/preview, Edit, and round-trip evidence for the chosen behaviour. |

## Public-seam evidence

### Workflow (`MOV-WF`)

| IDs | Implemented test evidence | Status |
| --- | --- | --- |
| 001 | `run_nonemptyCatalog_listsCompleteMoviesInPersistedOrder` | Passing |
| 002 | `run_emptyCatalog_keepsActionsAndRejectsEmptyEditAndDelete` | Passing |
| 003, 005, 006 | `run_numericTitleRatingAndConfirmationPartitions_repromptExactly`; `run_addConfirmed_retriesValidationAndCollisionThenAppends` | Partial - MOV-GAP-002 and MOV-GAP-003 |
| 004, 013, 019 | `run_editAndDeleteCancellationAlternatives_preserveData`; `run_editFieldCancelRatingOnlyPreviewAndEmptyMessageFailure_coverAlternatives` | Partial - MOV-GAP-002 and MOV-GAP-003 |
| 007 | `run_addConfirmed_retriesValidationAndCollisionThenAppends` | Partial - MOV-GAP-003 |
| 008 | `run_addCancelledAtRating_preservesExactCatalog`; `run_publicConstructorsAndLocalTitleCancel_coverProductionAdapters` | Partial - MOV-GAP-003 |
| 009, 014 | `run_addOrEditSaveFailure_reportsNotSavedAndPreservesCatalog` | Passing |
| 010, 011 | `run_editBothConfirmed_preservesIdentityPositionAndScreenings`; `run_editFieldCancelRatingOnlyPreviewAndEmptyMessageFailure_coverAlternatives` | Partial - MOV-GAP-003 |
| 012 | `run_editNoOpThenCancel_reportsWithoutWriting` | Passing |
| 015 | `run_childlessDelete_showsNoCascadeAndLeavesMalformedSeatsUntouched` | Partial - MOV-GAP-003 |
| 016, 020 | `run_cascadeDelete_previewsCountsAndPersistsThenRedraws` | Partial - MOV-GAP-003 |
| 017 | `commit_missingSeats_preservesAbsenceAndUsesJournaledCascade`; missing workflow scenario | Partial - MOV-GAP-006 |
| 018 | `run_accessAndPreparationFailures_reportExactPrefixesAndReturnBack`; `run_childlessDelete_showsNoCascadeAndLeavesMalformedSeatsUntouched` | Partial - MOV-GAP-006 |
| 021 | `run_globalCommandsAtNestedPrompts_returnTypedOutcomesWithoutWrites` | Partial - MOV-GAP-001 |
| 022 | `run_typedGlobalEofInputAndOutputFailures_areFailClosed` | Partial - MOV-GAP-001 |
| 023, 024 | `run_outputFailureAtEveryRenderedBlock_terminatesAndNeverRevertsDurableData`; `run_storageFailureMessageOutputFailure_terminates` | Partial - MOV-GAP-003 and MOV-GAP-004 |
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
| Movie targets at 0, 1, count, count + 1, malformed, overflow, and `/cancel` | cancellation alternatives and output matrix | Partial - MOV-GAP-002 |
| Raw controls, Unicode outer whitespace, blank, Unicode/internal spaces, duplicates, slash titles, and title `0` | add validation/collision and numeric-title tests | Partial - MOV-GAP-002 |
| Ratings 1-3, invalid names/numbers/overflow, retry, and `/cancel` | numeric/rating test and cancellation alternatives | Passing |
| Confirmation Y/N, case/whitespace, `/cancel`, invalid retry, typed transition | add, edit, delete, global-command, and output matrices | Passing |
| Catalogue sizes zero, one, and multiple; stable append/edit/delete order | list, Add, Edit, childless and cascade Delete tests | Passing |
| Zero, one, and multiple nonchronological child screenings | transaction preparation and workflow preview tests | Passing |
| Missing, empty, unrelated, affected, malformed, unreadable, and divergent occupancy | occupancy, workflow, and transaction suites | Partial - MOV-GAP-006 |
| No journal; O/O, O/I, I/O, I/I; divergence; malformed; interrupted retry | recovery matrix and semantic journal suites | Partial - MOV-GAP-005 and MOV-GAP-007 |

## Requirement traceability

| Requirement | Evidence IDs | Trace status |
| --- | --- | --- |
| MOV-FR-001 | MOV-WF-001, MOV-WF-002, MOV-WF-025, MOV-WF-026 | Partial - MOV-GAP-003 |
| MOV-FR-002 | MOV-WF-001 through MOV-WF-003 | Partial - MOV-GAP-003 |
| MOV-FR-003 | MOV-WF-003, MOV-WF-004, MOV-WF-006 | Partial - MOV-GAP-002 |
| MOV-FR-004 | MOV-WF-005, MOV-WF-007, MOV-WF-010, MOV-CAT-003, MOV-CAT-004 | Partial - MOV-GAP-002 and MOV-GAP-003 |
| MOV-FR-005 | MOV-WF-006, MOV-WF-007, MOV-WF-011 | Partial - MOV-GAP-003 |
| MOV-FR-006 | MOV-WF-007 through MOV-WF-009, MOV-CAT-001, MOV-CAT-005 | Partial - MOV-GAP-003 |
| MOV-FR-007 | MOV-WF-004, MOV-WF-013, MOV-WF-019 | Partial - MOV-GAP-002 |
| MOV-FR-008 | MOV-WF-010 through MOV-WF-013 | Partial - MOV-GAP-002 and MOV-GAP-003 |
| MOV-FR-009 | MOV-WF-010 through MOV-WF-014 | Partial - MOV-GAP-003 |
| MOV-FR-010 | MOV-WF-016 through MOV-WF-018, MOV-TX-001, MOV-TX-002 | Partial - MOV-GAP-003 and MOV-GAP-006 |
| MOV-FR-011 | MOV-WF-015, MOV-WF-017, MOV-WF-020, MOV-TX-003 through MOV-TX-009 | Partial - MOV-GAP-003 and MOV-GAP-005 |
| MOV-FR-012 | MOV-WF-008, MOV-WF-013, MOV-WF-019, MOV-WF-023 | Partial - MOV-GAP-003 and MOV-GAP-004 |
| MOV-FR-013 | MOV-WF-001, MOV-WF-007, MOV-WF-010, MOV-WF-015, MOV-WF-016, MOV-CAT-001 | Covered by current evidence |
| MOV-FR-014 | MOV-WF-008, MOV-WF-013, MOV-WF-019, MOV-WF-021, MOV-ROUTE-001 | Partial - MOV-GAP-001; raw routing deferred |
| MOV-FR-015 | MOV-WF-009, MOV-WF-014, MOV-WF-018, MOV-WF-025, MOV-WF-026, MOV-TX-005 through MOV-TX-020 | Partial - MOV-GAP-005 through MOV-GAP-007 |
| MOV-FR-016 | MOV-WF-022 through MOV-WF-024, MOV-TX-006, MOV-TX-018 | Partial - MOV-GAP-001 and MOV-GAP-004 |
| MOV-NFR-001 | MOV-CAT-001 through MOV-CAT-004, MOV-SEAT-002 through MOV-SEAT-005, MOV-TX-017 | Partial - MOV-GAP-006 and MOV-GAP-007 |
| MOV-NFR-002 | MOV-CAT-005 through MOV-CAT-007, MOV-SEAT-006 through MOV-SEAT-008, MOV-TX-005 through MOV-TX-020 | Partial - MOV-GAP-004, MOV-GAP-005, and MOV-GAP-007 |
| MOV-NFR-003 | MOV-WF-001 through MOV-WF-020 | Partial - MOV-GAP-003 |
| MOV-NFR-004 | All public-seam evidence and deterministic adapters | Covered by the confirmed seams; open fault cases remain under MOV-GAP-005 |
| MOV-NFR-005 | TDD module design and review gate | Pending design review - MOV-GAP-008 |

## Use-case extension traceability

| Use-case extension | Evidence IDs | Trace status |
| --- | --- | --- |
| UC-MOV-01 2a: recovery cannot safely complete | MOV-WF-026, MOV-TX-016, MOV-TX-017 | Covered by current evidence |
| UC-MOV-01 3a: empty catalogue | MOV-WF-002, MOV-CAT-002 | Covered by current evidence |
| UC-MOV-01 3b: malformed or unreadable catalogue | MOV-WF-026 and catalogue-load tests | Covered by current evidence |
| UC-MOV-01 4a: output fails | MOV-WF-023 | Partial - MOV-GAP-003 and MOV-GAP-004 |
| UC-MOV-02 2a: invalid field | MOV-WF-005, MOV-WF-006 | Partial - MOV-GAP-002 |
| UC-MOV-02 2b: local cancellation | MOV-WF-008 | Covered by current evidence |
| UC-MOV-02 3a: ID collision | MOV-WF-007 | Covered by current evidence |
| UC-MOV-02 4a: invalid confirmation | MOV-WF-008 | Partial - MOV-GAP-003 |
| UC-MOV-02 4b: N or `/cancel` | MOV-WF-008 | Covered by current evidence |
| UC-MOV-02 5a: persistence failure | MOV-WF-009, MOV-CAT-005, MOV-CAT-006 | Covered by current evidence |
| UC-MOV-02 `*a`: global command or precommit I/O failure | MOV-WF-021 through MOV-WF-023, MOV-ROUTE-001 | Partial - MOV-GAP-001 and MOV-GAP-004; raw routing deferred |
| UC-MOV-03 1a: empty catalogue | MOV-WF-002 | Covered by current evidence |
| UC-MOV-03 1b: invalid selection | MOV-WF-004 | Partial - MOV-GAP-002 |
| UC-MOV-03 2a: invalid field | MOV-WF-005, MOV-WF-006 | Partial - MOV-GAP-002 |
| UC-MOV-03 2b: proposed field equals persisted value | MOV-WF-012 | Covered by current evidence |
| UC-MOV-03 3a: no real change | MOV-WF-012 | Covered by current evidence |
| UC-MOV-03 4a: N or `/cancel` | MOV-WF-013 | Covered by current evidence |
| UC-MOV-03 5a: persistence failure | MOV-WF-014, MOV-CAT-005, MOV-CAT-006 | Covered by current evidence |
| UC-MOV-03 `*a`: `0` cancellation | MOV-WF-004, MOV-WF-013 | Covered by current evidence |
| UC-MOV-03 `*b`: global command or precommit I/O failure | MOV-WF-021 through MOV-WF-023, MOV-ROUTE-001 | Partial - MOV-GAP-001 and MOV-GAP-004; raw routing deferred |
| UC-MOV-04 1a: empty catalogue | MOV-WF-002 | Covered by current evidence |
| UC-MOV-04 1b: invalid selection | MOV-WF-004 | Partial - MOV-GAP-002 |
| UC-MOV-04 2a: childless movie | MOV-WF-015, MOV-TX-001, MOV-TX-008 | Covered by current evidence |
| UC-MOV-04 2b: missing occupancy | MOV-WF-017, MOV-SEAT-001, MOV-TX-003 | Partial - MOV-GAP-006 |
| UC-MOV-04 2c: malformed or unreadable occupancy | MOV-WF-018, MOV-SEAT-004 | Partial - MOV-GAP-006 |
| UC-MOV-04 3a: N or `/cancel` | MOV-WF-019 | Covered by current evidence |
| UC-MOV-04 4a: failure before durable intent | MOV-TX-005, MOV-WF-015 | Partial - MOV-GAP-005 |
| UC-MOV-04 4b: failure after durable intent | MOV-TX-006, MOV-TX-018 | Partial - MOV-GAP-005 |
| UC-MOV-04 5a: deleted movie was last | MOV-WF-015, MOV-CAT-002 | Covered by current evidence |
| UC-MOV-04 `*a`: global command or precommit I/O failure | MOV-WF-021 through MOV-WF-023, MOV-ROUTE-001 | Partial - MOV-GAP-001 and MOV-GAP-004; raw routing deferred |

## Deferred role-routing obligations

| ID | Required later evidence | Status |
| --- | --- | --- |
| MOV-ROUTE-001 | Production raw parsing of trimmed mixed-case `/admin`, `/customer`, and `/exit` into Movie's typed transitions at every prompt. | Deferred - role routing |
| MOV-ROUTE-002 | Route both customer and administrator access through the shared `CatalogRecoveryGate` before affected catalogue or occupancy data is used. | Gate implemented; shared routing deferred |

Movie management has evidence for each typed handoff value and for recovery
behaviour at its public seam, but MOV-GAP-001 still requires those handoffs at
every distinct prompt. The production parser and cross-role use of the recovery
gate remain mandatory acceptance items for the later role-routing workstream and
are not claimed as implemented here.

## Completion gate

The checklist may return to `Implementation complete - all nondeferred checks
passing` only when:

- MOV-GAP-001 through MOV-GAP-008 have passing evidence or an explicit owner-
  approved deferral;
- every requirement and use-case-extension row is `Covered` or carries that
  explicit deferral;
- exact output and byte assertions use independent literal expectations rather
  than production formatters or serializers;
- the revised TDD and its remaining compatibility decision are owner-approved;
- `mvn verify` again passes with 100% aggregate line and branch coverage; and
- the JaCoCo report, assertions, canonical bytes, exception causes/statuses, and
  final implementation diff have been inspected.
