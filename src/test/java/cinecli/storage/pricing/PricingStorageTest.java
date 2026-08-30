package cinecli.storage.pricing;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.model.Pricing;
import cinecli.model.PromoCode;
import cinecli.model.SnackMenuItem;
import cinecli.model.TicketType;
import cinecli.storage.exception.PricingStorageException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PricingStorageTest {
    @TempDir
    Path tempDirectory;

    @Test
    void load_missingFile_seedsCanonicalDefaultsAtomically() throws Exception {
        Path pricingPath = runtimePricingPath();

        Pricing pricing = new PricingStorage(pricingPath).load();

        assertAll(
                () -> assertEquals(Pricing.defaults(), pricing),
                () -> assertEquals(PricingStorage.serialize(Pricing.defaults()),
                        Files.readString(pricingPath, UTF_8)));
    }

    @Test
    void load_existingValidFile_returnsParsedStateWithoutChangingBytes() throws Exception {
        Path pricingPath = writeRuntimePricing(nonCanonicalValidPricing());
        byte[] original = Files.readAllBytes(pricingPath);

        Pricing pricing = new PricingStorage(pricingPath).load();

        assertAll(
                () -> assertEquals(1100, pricing.ticketPriceInCents(cinecli.model.TicketType.ADULT)),
                () -> assertArrayEquals(original, Files.readAllBytes(pricingPath)));
    }

    @Test
    void load_malformedExistingFile_preservesBytes() throws Exception {
        Path pricingPath = writeRuntimePricing("CINECLI-PRICING\t1\nTICKET_PRICE\tADULT\t11.00\n");
        byte[] original = Files.readAllBytes(pricingPath);

        assertThrows(PricingStorageException.class, () -> new PricingStorage(pricingPath).load());

        assertArrayEquals(original, Files.readAllBytes(pricingPath));
    }

    @Test
    void save_completeValidState_writesCanonicalDeterministicBytesAndRoundTrips() throws Exception {
        Path pricingPath = writeRuntimePricing(nonCanonicalValidPricing());
        PricingStorage storage = new PricingStorage(pricingPath);

        storage.save(pricingWithReversePromotions());

        assertAll(
                () -> assertEquals(PricingStorage.serialize(Pricing.defaults()),
                        Files.readString(pricingPath, UTF_8)),
                () -> assertEquals(Pricing.defaults(), storage.load()));
    }

    @Test
    void save_invalidState_preservesOriginalBytes() throws Exception {
        Path pricingPath = writeRuntimePricing(nonCanonicalValidPricing());
        byte[] original = Files.readAllBytes(pricingPath);
        PricingStorage storage = new PricingStorage(pricingPath);

        assertThrows(PricingStorageException.class, () -> storage.save(null));

        assertArrayEquals(original, Files.readAllBytes(pricingPath));
    }

    @Test
    void save_preReplacementFailures_preserveOriginalBytesAndCause() throws Exception {
        for (PricingStorageOperation failedOperation : List.of(
                PricingStorageOperation.CREATE_DIRECTORIES,
                PricingStorageOperation.CREATE_TEMPORARY,
                PricingStorageOperation.WRITE_TEMPORARY,
                PricingStorageOperation.FORCE_TEMPORARY)) {
            Path pricingPath = tempDirectory.resolve(failedOperation.name()).resolve("pricing.tsv");
            Files.createDirectories(pricingPath.getParent());
            Files.writeString(pricingPath, nonCanonicalValidPricing(), UTF_8);
            byte[] original = Files.readAllBytes(pricingPath);
            IOException cause = new IOException("simulated " + failedOperation);
            PricingStorage storage = new PricingStorage(
                    pricingPath,
                    (operation, path) -> {
                        if (operation == failedOperation) {
                            throw cause;
                        }
                    });

            PricingStorageException exception = assertThrows(
                    PricingStorageException.class, () -> storage.save(Pricing.defaults()));

            assertAll(
                    () -> assertEquals(cause, exception.getCause()),
                    () -> assertArrayEquals(original, Files.readAllBytes(pricingPath)));
        }
    }

    @Test
    void save_atomicReplacementFailureAndCleanupFailure_preservesOriginalBytes() throws Exception {
        Path pricingPath = writeRuntimePricing(nonCanonicalValidPricing());
        byte[] original = Files.readAllBytes(pricingPath);
        PricingStorage storage = new PricingStorage(
                pricingPath,
                (operation, path) -> {
                    if (operation == PricingStorageOperation.ATOMIC_REPLACE) {
                        throw new AtomicMoveNotSupportedException(path.toString(), path.toString(), "simulated");
                    }
                    if (operation == PricingStorageOperation.DELETE_TEMPORARY) {
                        throw new IOException("cleanup");
                    }
                });

        PricingStorageException exception = assertThrows(
                PricingStorageException.class, () -> storage.save(Pricing.defaults()));

        assertAll(
                () -> assertTrue(exception.getMessage().contains("atomic replacement")),
                () -> assertArrayEquals(original, Files.readAllBytes(pricingPath)));
    }

    @Test
    void load_seedFailure_leavesFileMissing() {
        Path pricingPath = runtimePricingPath();
        PricingStorage storage = new PricingStorage(
                pricingPath,
                (operation, path) -> {
                    if (operation == PricingStorageOperation.CREATE_DIRECTORIES) {
                        throw new IOException("simulated");
                    }
                });

        assertThrows(PricingStorageException.class, storage::load);

        assertFalse(Files.exists(pricingPath));
    }

    @Test
    void load_readFailure_preservesExistingBytesAndCause() throws Exception {
        Path pricingPath = writeRuntimePricing(nonCanonicalValidPricing());
        byte[] original = Files.readAllBytes(pricingPath);
        IOException cause = new IOException("simulated read failure");
        PricingStorage storage = new PricingStorage(
                pricingPath,
                (operation, path) -> {
                    if (operation == PricingStorageOperation.READ_TARGET) {
                        throw cause;
                    }
                });

        PricingStorageException exception = assertThrows(PricingStorageException.class, storage::load);

        assertAll(
                () -> assertEquals(cause, exception.getCause()),
                () -> assertArrayEquals(original, Files.readAllBytes(pricingPath)));
    }

    private Path runtimePricingPath() {
        return tempDirectory.resolve("data/runtime/pricing.tsv");
    }

    private Path writeRuntimePricing(String content) throws IOException {
        Path pricingPath = runtimePricingPath();
        Files.createDirectories(pricingPath.getParent());
        Files.writeString(pricingPath, content, UTF_8);
        return pricingPath;
    }

    private String nonCanonicalValidPricing() {
        return """
                CINECLI-PRICING\t1
                PROMOTION\tCS3227\t99
                SNACK_PRICE\tNACHOS\t6.00
                TICKET_PRICE\tSTUDENT\t7.00
                TICKET_PRICE\tADULT\t11.00
                SNACK_PRICE\tPOPCORN_COMBO\t7.00
                SNACK_PRICE\tSOFT_DRINK\t3.00
                TICKET_PRICE\tSENIOR\t4.50
                SNACK_PRICE\tNACHOS_COMBO\t8.00
                PROMOTION\tCS2103\t20
                SNACK_PRICE\tPOPCORN\t5.00
                """;
    }

    private Pricing pricingWithReversePromotions() {
        Map<TicketType, Integer> ticketPrices = Map.of(
                TicketType.ADULT, 1100,
                TicketType.SENIOR, 450,
                TicketType.STUDENT, 700);
        Map<SnackMenuItem, Integer> snackPrices = Map.of(
                SnackMenuItem.POPCORN, 500,
                SnackMenuItem.NACHOS, 600,
                SnackMenuItem.SOFT_DRINK, 300,
                SnackMenuItem.POPCORN_COMBO, 700,
                SnackMenuItem.NACHOS_COMBO, 800);
        return new Pricing(ticketPrices, snackPrices, List.of(
                new PromoCode("CS3227", 99),
                new PromoCode("CS2103", 20)));
    }
}
