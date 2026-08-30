# Role Routing and Deferred Confirmation Requirements-to-Tests Checklist

**Status:** Integrated implementation record; clean isolated verification completed
on 30 August 2026.

## Traceability

| Requirement | Evidence |
| --- | --- |
| `Main` remains a thin UTF-8 bootstrap | `MainTest`; source review of `Main` delegation to `ApplicationRouter` |
| Raw global commands are trimmed and case-insensitive | `Utf8TerminalTest.readLine_globalCommands_areTrimmedAndCaseInsensitive` |
| Noncommands retain their submitted text and input/output failures become typed results | `Utf8TerminalTest.readLine_nonCommandsAndEndOfInput_preservesLineOrReturnsTypedResult`; `readLine_readerFailure_returnsInputFailure`; `write_destinations_flushSuccessfullyOrReportFailure` |
| Customer and administrator transitions are owned by one router | `ApplicationRouterTest.run_routesBetweenCustomerAndAdministratorModes`; `run_adminCommandFromAdministratorMode_restartsHomepage`; `run_endOfInputInEachRole_terminates` |
| Recovery runs before role dispatch, completes a valid pending deletion, and blocks unsafe access | `ApplicationRouterTest.run_unrecoverableJournal_reportsErrorWithoutDispatchingRole`; `MovieManagementRecoveryTest.router_recoversPendingMovieDeletionBeforeCustomerAndAdministratorAccess`; `router_recoveryFailureDoesNotDispatchWhenErrorOutputFails`; `ScreeningManagementRecoveryTest.router_recoversPendingScreeningDeletionBeforeCustomerAndAdministratorAccess`; `router_recoveryFailureDoesNotDispatchWhenErrorOutputFails` |
| Homepage exposes and delegates its three approved sections | `AdministratorApplicationTest.run_homepageChoices_delegateAndReturnToHomepage`; `run_zero_returnsCustomer` |
| Homepage handles typed global commands and terminal failure | `AdministratorApplicationTest.run_globalCommandsAndTerminalInputFailures_returnTypedOutcomes`; `run_invalidChoiceWhenErrorCannotBeWritten_terminates` |
| Global commands at customer deferred prompts discard tentative state | `CustomerApplicationTest.run_globalCommandsAtEveryDeferredPrompt_discardTentativeSeats` |
| Local cancellation at customer deferred prompts discards tentative state | `CustomerApplicationTest.run_localCancellationAtEveryDeferredPrompt_discardsTentativeSeats` |
| Bill text is suppressed on final conflict and same-screening retry uses refreshed occupancy | `CustomerApplicationTest.run_finalConflictSuppressesFirstBillAndReturnsToFreshSeatSelection` |
| Output failures before final confirmation leave seats unmodified | `CustomerApplicationTest.run_outputFailuresAtDeferredStages_preserveUnfinalizedState` |
| Output failure after successful final confirmation does not roll back seats | `CustomerApplicationTest.run_billOutputFailureRetainsDurablyConfirmedSeats` |
| Input failure and initial output failure terminate without persistence | `CustomerApplicationTest.run_inputAndInitialOutputFailures_terminateSafely` |
| Missing-file browsing does not create `seats.tsv` | `SeatStorageTest.loadTakenSeats_missingFile_returnsEmptyWithoutCreatingFile`; `CustomerApplicationTest` cancellation and global-command matrices |
| Failed final persistence for a missing target does not create that file | `SeatStorageTest.confirmSeats_atomicReplacementFailure_leavesMissingTargetMissing`; `confirmSeats_directoryCreationFailure_leavesMissingTargetMissing`; `CustomerApplicationTest.run_finalSeatWriteFailure_reportsErrorAndSuppressesLaterWorkflow` |

## Deliberate exclusions and inherited gaps

Booking records/payment, cross-process locking, screening moves/manual ordering,
auditorium rules, and a new transaction subsystem are not introduced. The Movie
checklist's `MOV-GAP-002` through `MOV-GAP-008` remain inherited open gaps; this
integration record does not mark them complete. The integration-specific forced
subprocess-termination proof from the broader plan remains an audit item until a
dedicated subprocess regression test is added and verified.

## Final verification

`clean verify` completed using a temporary source copy and Maven repository so
the locked workspace build directories were not used. All tests passed and
JaCoCo reported zero missed instructions, branches, and lines. The packaged JAR
was then smoke-tested from a separate empty temporary directory with `/admin`,
`1`, `0`, and `/exit`; it displayed the administrator and Movie Management menus
and exited successfully.
