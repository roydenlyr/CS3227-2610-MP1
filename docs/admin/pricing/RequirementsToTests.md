# Pricing Storage Requirements-to-Tests Checklist

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

## Verification note

The integrated worktree's reported full `mvn verify` result is 210 passing tests
with the repository's aggregate JaCoCo line and branch coverage gate satisfied.
Coverage is not treated as evidence in place of the behavioral and byte-level
assertions listed above.

## Deferred acceptance items

Administrator price-editing and promotion CRUD tests, cross-role routing tests,
and booking-persistence tests for snapshot storage are outside this workstream.
