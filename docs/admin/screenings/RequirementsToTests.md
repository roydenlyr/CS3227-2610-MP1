# Screening Management Requirements-to-Tests Checklist

**Status:** Implemented verification record

**Approved decisions:** A screening's ID and parent movie are immutable. Add
selects the parent; Edit changes the date, time, or both. Rescheduling preserves
occupancy, while deletion removes the selected screening's occupancy.

## Public seams

| Seam | Main evidence |
| --- | --- |
| `ScreeningManagementApplication.run()` | `ScreeningManagementApplicationTest`; `ScreeningManagementStorageFailureTest`; `ScreeningManagementRecoveryTest` |
| `MovieDeletionTransaction.prepareScreening/commitScreening/recover` | `ScreeningDeletionTransactionTest`; `ScreeningManagementRecoveryTest` |
| Shared Movie deletion/recovery foundation | `MovieDeletionTransactionTest`; `MovieManagementRecoveryTest` |

## Requirement traceability

| Requirement | Evidence |
| --- | --- |
| List screenings with parent context in movie-major persisted order | `run_listsFlattenedScreeningsInPersistedMovieMajorOrder` |
| Add to a selected parent with a generated `SCR-<UUID>` ID and collision retry | `run_addRetriesValidationAndCollisionThenAppendsToSelectedParent` |
| Reject invalid parent/date/time input; preserve state on unavailable Add or cancellation | `run_addRetriesValidationAndCollisionThenAppendsToSelectedParent`; `run_addUnavailableAndCancellationsPreserveCatalog` |
| Edit date, time, or both without changing screening ID, parent, or position | `run_editDateAndTimePreservesIdParentPositionAndOccupancy`; `run_editCancellationAndSingleFieldPreviewsCoverAlternatives` |
| Preserve occupancy when rescheduling | `run_editDateAndTimePreservesIdParentPositionAndOccupancy` |
| Detect no-op edits and preserve exact catalogue bytes on cancellation | `run_editValidatesNoOpAndCancelPreservesExactCatalog` |
| Delete only the selected screening and its occupied seats after preview/confirmation | `run_deletePreviewsOnlyCountThenClearsTargetOccupancyAndRedraws`; `prepareAndCommit_occupiedScreeningRemovesOnlyTargetState` |
| Preserve missing or unaffected occupancy state | `run_publicConstructorAndMissingOrUnaffectedSeatsSupportDelete`; `prepare_missingOrUnaffectedOccupancy_preservesMissingOrExactBytes` |
| Fail closed on malformed occupancy and avoid writes on invalid deletion state | `run_deleteCancellationAndMalformedOccupancyFailClosed`; `prepare_invalidAndMalformedStateDoesNotWrite` |
| Report catalogue save failures without changing the catalogue | `run_addOrEditSaveFailureReportsNotSavedAndPreservesCatalog` |
| Handle typed global outcomes, terminal input/output failures, and empty/access states | `run_globalCommandsAndTerminalFailuresLeaveDataUnchanged`; `run_outputFailureAtEveryRenderedBlockTerminatesWithoutRollingBackDurableChanges`; `run_emptyActionsAndAccessFailuresReportAndReturnBack` |
| Publish one `DELETE_SCREENING` journal and recover an interrupted deletion | `commit_alwaysPublishesJournalAndRecoveryCompletesInterruptedScreeningDeletion`; `run_recoversPendingScreeningDeletionBeforeRendering` |
| Retain data for not-applied or malformed-journal cases and report durable commit status | `commit_notAppliedAndMalformedScreeningJournalPreserveData`; `run_commitFailuresReportBothDurableStatuses` |
| Keep Movie deletion and recovery behaviour covered after shared-foundation extension | `MovieDeletionTransactionTest`; `MovieManagementRecoveryTest` |

## Deferred acceptance work

- Production role routing must parse raw global commands and make the administrator
  modules reachable through `Main`.
- Full end-user administrator instructions remain deferred until that route exists.
- Moving a screening between movies, manual ordering, auditorium/clash rules, and
  booking persistence are outside this workstream.
