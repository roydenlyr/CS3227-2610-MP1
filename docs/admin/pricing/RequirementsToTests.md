# Pricing and Promotions Management Requirements-to-Tests Checklist

**Status:** Implementation verification record

**Approved scope date:** 29 August 2026

## Traceability

| Requirement | Evidence |
| --- | --- |
| Missing pricing seeds the complete former customer values canonically | `PricingStorageTest.load_missingFile_seedsCanonicalDefaultsAtomically`; `PricingTest.defaults_existingCustomerValues_exposesCompleteImmutableState` |
| Existing valid data loads without rewriting bytes | `PricingStorageTest.load_existingValidFile_returnsParsedStateWithoutChangingBytes` |
| Existing malformed data is rejected and preserved | `PricingStorageTest.load_malformedExistingFile_preservesBytes`; parser malformed-file matrix |
| Complete-state grammar, fixed identities, uniqueness, and boundaries are enforced | `PricingParserTest.parse_headerOrRecordShapeFailures_rejectsFile`; `parse_missingOrDuplicateFixedIdentity_rejectsFile`; `parse_priceAtBoundsAndNonCanonicalOrOutOfRangePrice_handlesBoundaries`; `parse_promotionValidation_rejectsInvalidPercentagesCodesAndDuplicates` |
| Saves are deterministic and round-trip | `PricingStorageTest.save_completeValidState_writesCanonicalDeterministicBytesAndRoundTrips` |
| Invalid and pre-replacement failures preserve original bytes | `PricingStorageTest.save_invalidState_preservesOriginalBytes`; `save_preReplacementFailures_preserveOriginalBytesAndCause`; `save_atomicReplacementFailureAndCleanupFailure_preservesOriginalBytes` |
| Seed/read failures retain safe state and causal error information | `PricingStorageTest.load_seedFailure_leavesFileMissing`; `load_readFailure_preservesExistingBytesAndCause`; parser read-failure test |
| Ticket, snack, and promotion values are immutable snapshots | `TicketSelectionTest.constructor_validSeatTypeAndPrice_createsPriceSnapshot`; `SnackSelectionTest.constructor_positiveQuantityAndPrice_createsPriceSnapshot`; `PromoCodeTest.constructor_validCodeAndPercentage_canonicalizesCode`; `BillTest.amounts_capturedPrices_calculatesWithoutConsultingIdentityPrices` |
| Customer uses persisted prices and promotions in menus, selections, and bills | `CustomerApplicationTest.run_customPricing_capturesAndRendersLoadedPrices`; customer-UI rendering tests |
| Pricing failure ends customer startup before seat access or mutation | `CustomerApplicationTest.run_malformedPricing_reportsFailureBeforeSeatStorageIsAccessed` |
| Main wires the sibling runtime pricing path | `MainTest.run_missingRuntimeCatalog_initializesAndDisplaysSeed` |
| Ticket prices list in menu order and edit valid/boundary exact-cent values only | `TicketPriceManagementApplicationTest` workflow and input-boundary cases |
| Snack/combo prices list in menu order and edit valid/boundary exact-cent values only | `SnackComboPriceManagementApplicationTest` workflow and input-boundary cases |
| Price mutation needs preview/confirmation and cancellation preserves pricing bytes | `TicketPriceManagementApplicationTest`; `SnackComboPriceManagementApplicationTest` |
| Promotions list in loaded persisted order, add, edit, rename, and delete | `PromotionManagementApplicationTest.run_listsPromotionsInPersistedOrder`; add/edit/delete cases |
| Promotion input normalizes codes, rejects collisions, and accepts 1% and 100% only | `PromotionManagementApplicationTest.run_addNormalizesCodeAndAcceptsPercentageBoundariesAfterInvalidValues`; `run_editRejectsCaseInsensitiveRenameCollisionAndCancellationPreservesBytes` |
| Promotion deletion has a destructive preview and confirmation | `PromotionManagementApplicationTest.run_deleteShowsDestructivePreviewAndPersistsConfirmedRemoval` |
| An administrator save failure is reported truthfully and retains pricing bytes | `PricingManagementStorageFailureTest.run_ticketPriceSaveFailure_reportsNotSavedAndPreservesOriginalBytes` |
| A later complete pricing save cannot alter customer snapshots already captured | `PricingCustomerSnapshotRegressionTest.save_replacedPricing_preservesExistingCustomerPriceAndPromotionSnapshots`; `BillTest.amounts_pricingChangesAfterSelection_leaveExistingBillUnchanged` |

## Verification note

Final integrated verification ran
`.\mvnw.cmd '-Djunit.jupiter.tempdir.cleanup.mode.default=NEVER' clean verify`
and passed 272 tests. The aggregate JaCoCo line and branch coverage gate also
passed. Coverage is not treated as evidence in place of these behavioral and
byte-level assertions.

## Deferred acceptance items

Cross-role routing, end-user administrator instructions, and booking persistence
of completed snapshots remain outside this workstream.
