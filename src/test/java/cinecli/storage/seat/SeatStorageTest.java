package cinecli.storage.seat;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.model.SeatCoordinate;
import cinecli.storage.exception.SeatStorageException;
import cinecli.storage.exception.MovieDeletionCommitException;
import cinecli.storage.exception.TransactionStorageException;
import cinecli.storage.catalog.CatalogStorage;
import cinecli.storage.catalog.CatalogTransactionAdapter;
import cinecli.storage.transaction.TransactionFileSnapshot;
import cinecli.storage.transaction.MovieDeletionTransaction;
import cinecli.storage.transaction.RecoveryResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeatStorageTest {
    private static final Set<String> KNOWN_SCREENING_IDS = Set.of("SCR-001", "SCR-002");

    @TempDir
    Path tempDirectory;

    @Test
    void loadSnapshot_missingFile_returnsAbsentEmptyWithoutInitialization() throws Exception {
        Path runtimeSeats = runtimeSeatsPath();

        SeatOccupancySnapshot snapshot = new SeatStorage(runtimeSeats)
                .loadSnapshot(KNOWN_SCREENING_IDS);

        assertAll(
                () -> assertFalse(snapshot.isPresent()),
                () -> assertTrue(snapshot.occupiedSeatsByScreening().isEmpty()),
                () -> assertFalse(Files.exists(runtimeSeats)),
                () -> assertFalse(Files.exists(runtimeSeats.getParent())));
    }

    @Test
    void loadSnapshot_headerOnly_returnsPresentEmpty() throws Exception {
        Path runtimeSeats = writeRuntimeSeats("CINECLI-SEATS\t1\n");

        SeatOccupancySnapshot snapshot = new SeatStorage(runtimeSeats)
                .loadSnapshot(KNOWN_SCREENING_IDS);

        assertAll(
                () -> assertTrue(snapshot.isPresent()),
                () -> assertTrue(snapshot.occupiedSeatsByScreening().isEmpty()));
    }

    @Test
    void replaceSnapshot_removedRecords_writesCanonicalExactBytes() throws Exception {
        Path runtimeSeats = writeRuntimeSeats("""
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-002\tB2
                TAKEN_SEAT\tSCR-001\tA1
                """);
        SeatStorage seatStorage = new SeatStorage(runtimeSeats);

        seatStorage.replaceSnapshot(
                SeatOccupancySnapshot.present(Map.of(
                        "SCR-002", Set.of(SeatCoordinate.parse("B2")))),
                KNOWN_SCREENING_IDS);

        assertEquals("""
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-002\tB2
                """, Files.readString(runtimeSeats, UTF_8));
    }

    @Test
    void replaceSnapshot_missingIntent_performsNoCreationOrDeletion() throws Exception {
        Path missingPath = runtimeSeatsPath();
        SeatStorage missingStorage = new SeatStorage(missingPath);
        missingStorage.replaceSnapshot(SeatOccupancySnapshot.missing(), KNOWN_SCREENING_IDS);

        Path presentPath = tempDirectory.resolve("present/seats.tsv");
        Files.createDirectories(presentPath.getParent());
        Files.writeString(presentPath, "CINECLI-SEATS\t1\n", UTF_8);
        byte[] original = Files.readAllBytes(presentPath);

        assertAll(
                () -> assertFalse(Files.exists(missingPath)),
                () -> assertThrows(SeatStorageException.class,
                        () -> new SeatStorage(presentPath).replaceSnapshot(
                                SeatOccupancySnapshot.missing(), KNOWN_SCREENING_IDS)),
                () -> assertArrayEquals(original, Files.readAllBytes(presentPath)));
    }

    @Test
    void replaceSnapshot_presenceMismatchOrUnknownId_rejectsWithoutMutation() throws Exception {
        Path missing = tempDirectory.resolve("missing/seats.tsv");
        SeatStorage missingStorage = new SeatStorage(missing);
        Path present = tempDirectory.resolve("known/seats.tsv");
        Files.createDirectories(present.getParent());
        Files.writeString(present, "CINECLI-SEATS\t1\n", UTF_8);
        byte[] original = Files.readAllBytes(present);

        assertAll(
                () -> assertThrows(SeatStorageException.class,
                        () -> missingStorage.replaceSnapshot(
                                SeatOccupancySnapshot.present(Map.of()), KNOWN_SCREENING_IDS)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SeatStorage(present).replaceSnapshot(
                                SeatOccupancySnapshot.present(Map.of(
                                        "SCR-404", Set.of(SeatCoordinate.parse("A1")))),
                                KNOWN_SCREENING_IDS)),
                () -> assertFalse(Files.exists(missing)),
                () -> assertArrayEquals(original, Files.readAllBytes(present)));
    }

    @Test
    void transactionSnapshot_targetDisappearsAfterValidatedRead_wrapsCause() throws Exception {
        Path runtimeSeats = writeRuntimeSeats("CINECLI-SEATS\t1\n");
        SeatStorage storage = new SeatStorage(
                runtimeSeats,
                (operation, path) -> {
                    if (operation == SeatStorageOperation.READ_TARGET) {
                        Files.delete(path);
                    }
                });
        SeatTransactionAdapter adapter = new SeatTransactionAdapter(storage, runtimeSeats);

        SeatStorageException exception = assertThrows(
                SeatStorageException.class, () -> adapter.loadSnapshot(KNOWN_SCREENING_IDS));

        assertTrue(exception.getMessage().contains("snapshot seat occupancy"));
    }

    @Test
    void replaceSnapshot_unchangedStatePreservesExactBytesAndTransactionPresenceRules()
            throws Exception {
        Path present = writeRuntimeSeats("""
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-002\tB2
                TAKEN_SEAT\tSCR-001\tA1
                """);
        byte[] noncanonical = Files.readAllBytes(present);
        SeatStorage storage = new SeatStorage(present);
        SeatOccupancySnapshot sameState = SeatOccupancySnapshot.present(Map.of(
                "SCR-001", Set.of(SeatCoordinate.parse("A1")),
                "SCR-002", Set.of(SeatCoordinate.parse("B2"))));
        storage.replaceSnapshot(sameState, KNOWN_SCREENING_IDS);
        assertArrayEquals(noncanonical, Files.readAllBytes(present));

        Path missing = tempDirectory.resolve("transaction-missing/seats.tsv");
        SeatTransactionAdapter missingAdapter = new SeatTransactionAdapter(
                new SeatStorage(missing), missing);
        missingAdapter.replace(TransactionFileSnapshot.missing(Map.of()), KNOWN_SCREENING_IDS);
        assertFalse(Files.exists(missing));

        assertAll(
                () -> assertThrows(SeatStorageException.class,
                        () -> new SeatTransactionAdapter(storage, present).replace(
                                TransactionFileSnapshot.missing(Map.of()), KNOWN_SCREENING_IDS)),
                () -> assertThrows(SeatStorageException.class,
                        () -> missingAdapter.replace(
                                TransactionFileSnapshot.present(
                                        Map.of(), "CINECLI-SEATS\t1\n".getBytes(UTF_8)),
                                KNOWN_SCREENING_IDS)));
    }

    @Test
    void loadTakenSeats_missingFile_initializesHeaderOnlyFile() throws Exception {
        Path runtimeSeats = tempDirectory.resolve("data/runtime/seats.tsv");
        SeatStorage seatStorage = new SeatStorage(runtimeSeats);

        Set<SeatCoordinate> takenSeats = seatStorage.loadTakenSeats(
                "SCR-001", KNOWN_SCREENING_IDS);

        assertAll(
                () -> assertTrue(Files.exists(runtimeSeats)),
                () -> assertEquals("CINECLI-SEATS\t1\n", Files.readString(runtimeSeats, UTF_8)),
                () -> assertTrue(takenSeats.isEmpty()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> takenSeats.add(SeatCoordinate.parse("A1"))));
    }

    @Test
    void confirmSeats_validSeats_roundTripsAndPreservesOtherScreening() throws Exception {
        SeatStorage seatStorage = new SeatStorage(runtimeSeatsPath());
        seatStorage.confirmSeats(
                "SCR-001",
                Set.of(SeatCoordinate.parse("G4"), SeatCoordinate.parse("A20")),
                KNOWN_SCREENING_IDS);
        seatStorage.confirmSeats(
                "SCR-002", Set.of(SeatCoordinate.parse("B3")), KNOWN_SCREENING_IDS);

        Set<SeatCoordinate> firstScreeningSeats = seatStorage.loadTakenSeats(
                "SCR-001", KNOWN_SCREENING_IDS);
        Set<SeatCoordinate> secondScreeningSeats = seatStorage.loadTakenSeats(
                "SCR-002", KNOWN_SCREENING_IDS);

        assertAll(
                () -> assertEquals(Set.of(
                        SeatCoordinate.parse("A20"),
                        SeatCoordinate.parse("G4")), firstScreeningSeats),
                () -> assertEquals(Set.of(SeatCoordinate.parse("B3")), secondScreeningSeats));
    }

    @Test
    void confirmSeats_unsortedState_writesCanonicalDeterministicOrder() throws Exception {
        SeatStorage seatStorage = new SeatStorage(runtimeSeatsPath());
        seatStorage.confirmSeats(
                "SCR-002",
                Set.of(
                        SeatCoordinate.parse("G20"),
                        SeatCoordinate.parse("A10"),
                        SeatCoordinate.parse("A2")),
                KNOWN_SCREENING_IDS);
        seatStorage.confirmSeats(
                "SCR-001",
                Set.of(
                        SeatCoordinate.parse("G1"),
                        SeatCoordinate.parse("B20"),
                        SeatCoordinate.parse("A1")),
                KNOWN_SCREENING_IDS);

        String expected = """
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-001\tA1
                TAKEN_SEAT\tSCR-001\tB20
                TAKEN_SEAT\tSCR-001\tG1
                TAKEN_SEAT\tSCR-002\tA2
                TAKEN_SEAT\tSCR-002\tA10
                TAKEN_SEAT\tSCR-002\tG20
                """;

        assertEquals(expected, Files.readString(runtimeSeatsPath(), UTF_8));
    }

    @Test
    void confirmSeats_alreadyTakenSeat_rejectsEntireUpdateWithoutChangingFile() throws Exception {
        Path runtimeSeats = writeRuntimeSeats("""
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-001\tA1
                """);
        byte[] originalBytes = Files.readAllBytes(runtimeSeats);
        SeatStorage seatStorage = new SeatStorage(runtimeSeats);

        SeatStorageException exception = assertThrows(
                SeatStorageException.class,
                () -> seatStorage.confirmSeats(
                        "SCR-001",
                        Set.of(SeatCoordinate.parse("A1"), SeatCoordinate.parse("G20")),
                        KNOWN_SCREENING_IDS));

        assertAll(
                () -> assertTrue(exception.getMessage().contains("already taken")),
                () -> assertArrayEquals(originalBytes, Files.readAllBytes(runtimeSeats)),
                () -> assertFalse(Files.readString(runtimeSeats, UTF_8).contains("G20")));
    }

    @Test
    void confirmSeats_malformedExistingFile_failsWithoutOverwritingIt() throws Exception {
        Path runtimeSeats = writeRuntimeSeats("not seat occupancy\n");
        byte[] originalBytes = Files.readAllBytes(runtimeSeats);
        SeatStorage seatStorage = new SeatStorage(runtimeSeats);

        SeatStorageException exception = assertThrows(
                SeatStorageException.class,
                () -> seatStorage.confirmSeats(
                        "SCR-001", Set.of(SeatCoordinate.parse("A1")), KNOWN_SCREENING_IDS));

        assertAll(
                () -> assertTrue(exception.getMessage().contains("Malformed seat occupancy")),
                () -> assertArrayEquals(originalBytes, Files.readAllBytes(runtimeSeats)));
    }

    @Test
    void loadTakenSeats_malformedUnrelatedRecord_validatesWholeFileWithoutOverwritingIt()
            throws Exception {
        Path runtimeSeats = writeRuntimeSeats("""
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-001\tA1
                TAKEN_SEAT\tSCR-404\tB2
                """);
        byte[] originalBytes = Files.readAllBytes(runtimeSeats);
        SeatStorage seatStorage = new SeatStorage(runtimeSeats);

        SeatStorageException exception = assertThrows(
                SeatStorageException.class,
                () -> seatStorage.loadTakenSeats("SCR-001", KNOWN_SCREENING_IDS));

        assertAll(
                () -> assertTrue(exception.getMessage().contains("unknown screening ID 'SCR-404'")),
                () -> assertArrayEquals(originalBytes, Files.readAllBytes(runtimeSeats)));
    }

    @Test
    void loadAndConfirm_invalidArguments_rejectedBeforeFileInitialization() {
        Path runtimeSeats = runtimeSeatsPath();
        SeatStorage seatStorage = new SeatStorage(runtimeSeats);
        Set<SeatCoordinate> seatsWithNull = new HashSet<>();
        seatsWithNull.add(null);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> seatStorage.loadTakenSeats("", KNOWN_SCREENING_IDS)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> seatStorage.loadTakenSeats("SCR-404", KNOWN_SCREENING_IDS)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> seatStorage.loadTakenSeats("SCR-001", null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> seatStorage.loadTakenSeats("SCR-001", Set.of("_BAD"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> seatStorage.confirmSeats("SCR-001", null, KNOWN_SCREENING_IDS)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> seatStorage.confirmSeats("SCR-001", Set.of(), KNOWN_SCREENING_IDS)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> seatStorage.confirmSeats(
                                "SCR-001", seatsWithNull, KNOWN_SCREENING_IDS)),
                () -> assertFalse(Files.exists(runtimeSeats)));
    }

    @Test
    void load_initializationFailure_wrapsCauseAndLeavesFileMissing() {
        Path runtimeSeats = runtimeSeatsPath();
        IOException cause = new IOException("simulated initialization failure");
        SeatStorage seatStorage = new SeatStorage(
                runtimeSeats,
                (operation, path) -> {
                    if (operation == SeatStorageOperation.CREATE_DIRECTORIES) {
                        throw cause;
                    }
                });

        SeatStorageException exception = assertThrows(
                SeatStorageException.class,
                () -> seatStorage.loadTakenSeats("SCR-001", KNOWN_SCREENING_IDS));

        assertAll(
                () -> assertEquals(cause, exception.getCause()),
                () -> assertFalse(Files.exists(runtimeSeats)));
    }

    @Test
    void load_concurrentInitialization_acceptsHeaderCreatedByOtherCaller() throws Exception {
        Path runtimeSeats = runtimeSeatsPath();
        SeatStorage seatStorage = new SeatStorage(
                runtimeSeats,
                (operation, path) -> {
                    if (operation == SeatStorageOperation.INITIALIZE_TARGET) {
                        Files.writeString(path, "CINECLI-SEATS\t1\n", UTF_8);
                    }
                });

        Set<SeatCoordinate> seats = seatStorage.loadTakenSeats(
                "SCR-001", KNOWN_SCREENING_IDS);

        assertTrue(seats.isEmpty());
    }

    @Test
    void load_readFailure_wrapsCauseWithoutChangingFile() throws Exception {
        Path runtimeSeats = writeRuntimeSeats("CINECLI-SEATS\t1\n");
        byte[] originalBytes = Files.readAllBytes(runtimeSeats);
        IOException cause = new IOException("simulated read failure");
        SeatStorage seatStorage = new SeatStorage(
                runtimeSeats,
                (operation, path) -> {
                    if (operation == SeatStorageOperation.READ_TARGET) {
                        throw cause;
                    }
                });

        SeatStorageException exception = assertThrows(
                SeatStorageException.class,
                () -> seatStorage.loadTakenSeats("SCR-001", KNOWN_SCREENING_IDS));

        assertAll(
                () -> assertEquals(cause, exception.getCause()),
                () -> assertArrayEquals(originalBytes, Files.readAllBytes(runtimeSeats)));
    }

    @Test
    void confirmSeats_atomicReplacementUnsupported_preservesOriginalFile() throws Exception {
        Path runtimeSeats = writeRuntimeSeats("CINECLI-SEATS\t1\n");
        byte[] originalBytes = Files.readAllBytes(runtimeSeats);
        SeatStorage seatStorage = new SeatStorage(
                runtimeSeats,
                (operation, path) -> {
                    if (operation == SeatStorageOperation.ATOMIC_REPLACE) {
                        throw new java.nio.file.AtomicMoveNotSupportedException(
                                path.toString(), path.toString(), "simulated");
                    }
                });

        SeatStorageException exception = assertThrows(
                SeatStorageException.class,
                () -> seatStorage.confirmSeats(
                        "SCR-001", Set.of(SeatCoordinate.parse("A1")), KNOWN_SCREENING_IDS));

        assertAll(
                () -> assertTrue(exception.getMessage().contains("atomic replacement")),
                () -> assertArrayEquals(originalBytes, Files.readAllBytes(runtimeSeats)));
    }

    @Test
    void confirmSeats_temporaryCreationFailure_preservesOriginalFile() throws Exception {
        Path runtimeSeats = writeRuntimeSeats("CINECLI-SEATS\t1\n");
        byte[] originalBytes = Files.readAllBytes(runtimeSeats);
        IOException cause = new IOException("simulated temporary-file failure");
        SeatStorage seatStorage = new SeatStorage(
                runtimeSeats,
                (operation, path) -> {
                    if (operation == SeatStorageOperation.CREATE_TEMPORARY) {
                        throw cause;
                    }
                });

        SeatStorageException exception = assertThrows(
                SeatStorageException.class,
                () -> seatStorage.confirmSeats(
                        "SCR-001", Set.of(SeatCoordinate.parse("A1")), KNOWN_SCREENING_IDS));

        assertAll(
                () -> assertEquals(cause, exception.getCause()),
                () -> assertArrayEquals(originalBytes, Files.readAllBytes(runtimeSeats)));
    }

    @Test
    void confirmSeats_forceFailure_preservesOriginalFile() throws Exception {
        Path runtimeSeats = writeRuntimeSeats("CINECLI-SEATS\t1\n");
        byte[] originalBytes = Files.readAllBytes(runtimeSeats);
        IOException cause = new IOException("simulated force failure");
        SeatStorage seatStorage = new SeatStorage(
                runtimeSeats,
                (operation, path) -> {
                    if (operation == SeatStorageOperation.FORCE_TEMPORARY) {
                        throw cause;
                    }
                });

        SeatStorageException exception = assertThrows(
                SeatStorageException.class,
                () -> seatStorage.confirmSeats(
                        "SCR-001", Set.of(SeatCoordinate.parse("A1")), KNOWN_SCREENING_IDS));

        assertAll(
                () -> assertEquals(cause, exception.getCause()),
                () -> assertArrayEquals(originalBytes, Files.readAllBytes(runtimeSeats)));
    }

    @Test
    void cascade_seatAtomicStageFailure_isRecoveryPendingWithOriginalSeats() throws Exception {
        for (SeatStorageOperation failedOperation : new SeatStorageOperation[] {
                SeatStorageOperation.CREATE_TEMPORARY,
                SeatStorageOperation.WRITE_TEMPORARY,
                SeatStorageOperation.FORCE_TEMPORARY,
                SeatStorageOperation.ATOMIC_REPLACE
        }) {
            Path runtime = tempDirectory.resolve("cascade-" + failedOperation);
            Files.createDirectories(runtime);
            Path catalogPath = runtime.resolve("catalog.tsv");
            Path seatsPath = runtime.resolve("seats.tsv");
            Path journalPath = runtime.resolve("catalog-transaction.journal");
            Files.writeString(catalogPath, """
                    CINECLI-CATALOG\t1
                    MOVIE\tMOV-1\tFirst\tPG13
                    SCREENING\tSCR-1\tMOV-1\t2027-01-02\t21:30
                    """, UTF_8);
            String originalSeats = """
                    CINECLI-SEATS\t1
                    TAKEN_SEAT\tSCR-1\tA1
                    """;
            Files.writeString(seatsPath, originalSeats, UTF_8);
            SeatStorage seatStorage = new SeatStorage(
                    seatsPath,
                    (operation, path) -> {
                        if (operation == failedOperation) {
                            throw new IOException("simulated " + failedOperation);
                        }
                    });
            CatalogStorage catalogStorage = new CatalogStorage(
                    catalogPath, "/cinecli/default-catalog.tsv");
            MovieDeletionTransaction transaction = new MovieDeletionTransaction(
                    new CatalogTransactionAdapter(catalogStorage, catalogPath),
                    new SeatTransactionAdapter(seatStorage, seatsPath),
                    journalPath);

            MovieDeletionCommitException exception = assertThrows(
                    MovieDeletionCommitException.class,
                    () -> transaction.commit(transaction.prepare("MOV-1")));

            assertEquals(MovieDeletionCommitException.Status.RECOVERY_PENDING, exception.status());
            assertTrue(Files.exists(journalPath));
            assertEquals(originalSeats, Files.readString(seatsPath, UTF_8));
            assertEquals("CINECLI-CATALOG\t1\n", Files.readString(catalogPath, UTF_8));
            assertThrows(TransactionStorageException.class, transaction::recover);
            MovieDeletionTransaction retry = new MovieDeletionTransaction(
                    new CatalogTransactionAdapter(
                            new CatalogStorage(catalogPath, "/cinecli/default-catalog.tsv"),
                            catalogPath),
                    new SeatTransactionAdapter(new SeatStorage(seatsPath), seatsPath),
                    journalPath);
            assertEquals(RecoveryResult.RECOVERED, retry.recover());
            assertFalse(Files.exists(journalPath));
        }
    }

    @Test
    void confirmSeats_cleanupFailure_doesNotHidePrimaryWriteFailure() throws Exception {
        Path runtimeSeats = writeRuntimeSeats("CINECLI-SEATS\t1\n");
        IOException writeCause = new IOException("simulated write failure");
        SeatStorage seatStorage = new SeatStorage(
                runtimeSeats,
                (operation, path) -> {
                    if (operation == SeatStorageOperation.WRITE_TEMPORARY) {
                        throw writeCause;
                    }
                    if (operation == SeatStorageOperation.DELETE_TEMPORARY) {
                        throw new IOException("simulated cleanup failure");
                    }
                });

        SeatStorageException exception = assertThrows(
                SeatStorageException.class,
                () -> seatStorage.confirmSeats(
                        "SCR-001", Set.of(SeatCoordinate.parse("A1")), KNOWN_SCREENING_IDS));

        assertEquals(writeCause, exception.getCause());
    }

    private Path runtimeSeatsPath() {
        return tempDirectory.resolve("data/runtime/seats.tsv");
    }

    private Path writeRuntimeSeats(String content) throws IOException {
        Path runtimeSeats = runtimeSeatsPath();
        Files.createDirectories(runtimeSeats.getParent());
        Files.writeString(runtimeSeats, content, UTF_8);
        return runtimeSeats;
    }
}
