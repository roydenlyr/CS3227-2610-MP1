package cinecli.storage.transaction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.model.ContentRating;
import cinecli.model.Movie;
import cinecli.model.Screening;
import cinecli.storage.catalog.CatalogStorage;
import cinecli.storage.catalog.CatalogTransactionAdapter;
import cinecli.storage.exception.ScreeningDeletionCommitException;
import cinecli.storage.exception.ScreeningDeletionPreparationException;
import cinecli.storage.exception.TransactionStorageException;
import cinecli.storage.seat.SeatStorage;
import cinecli.storage.seat.SeatTransactionAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScreeningDeletionTransactionTest {
    private static final String DEFAULT_RESOURCE = "/cinecli/default-catalog.tsv";

    @TempDir
    Path tempDirectory;

    @Test
    void resultModels_rejectInvalidValuesIndependently() {
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new ScreeningDeletionResult(null, "MOV-1", "First", null, 0)),
                () -> assertThrows(NullPointerException.class,
                        () -> new ScreeningDeletionResult("SCR-1", null, "First", null, 0)),
                () -> assertThrows(NullPointerException.class,
                        () -> new ScreeningDeletionResult("SCR-1", "MOV-1", null, null, 0)),
                () -> assertThrows(NullPointerException.class,
                        () -> new ScreeningDeletionResult("SCR-1", "MOV-1", "First", null, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ScreeningDeletionResult(
                                "SCR-1", "MOV-1", "First", java.time.LocalDateTime.now(), -1)));
    }

    @Test
    void preparedDeletion_rejectsInvalidPreviewValues() {
        Movie movie = new Movie("MOV-1", "First", ContentRating.PG13, List.of());
        Screening screening = new Screening("SCR-1", LocalDateTime.of(2027, 1, 1, 10, 0));
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> prepared(null, screening, 1, 0)),
                () -> assertThrows(NullPointerException.class, () -> prepared(movie, null, 1, 0)),
                () -> assertThrows(IllegalArgumentException.class, () -> prepared(movie, screening, 0, 0)),
                () -> assertThrows(IllegalArgumentException.class, () -> prepared(movie, screening, 1, -1)));
    }

    @Test
    void prepareAndCommit_occupiedScreeningRemovesOnlyTargetState() throws Exception {
        Paths paths = writeState(catalog(), """
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-1\tA1
                TAKEN_SEAT\tSCR-2\tB2
                """);
        MovieDeletionTransaction transaction = transaction(paths);

        PreparedScreeningDeletion deletion = transaction.prepareScreening("SCR-1");

        assertAll(
                () -> assertEquals("SCR-1", deletion.screeningId()),
                () -> assertEquals("MOV-1", deletion.parentMovieId()),
                () -> assertEquals("First", deletion.parentMovieTitle()),
                () -> assertEquals(1, deletion.displayPosition()),
                () -> assertEquals(1, deletion.occupiedSeatCount()));
        assertEquals(
                new ScreeningDeletionResult(
                        "SCR-1", "MOV-1", "First", deletion.startsAt(), 1),
                transaction.commitScreening(deletion));
        assertEquals("""
                CINECLI-CATALOG\t1
                MOVIE\tMOV-1\tFirst\tPG13
                MOVIE\tMOV-2\tSecond\tR21
                SCREENING\tSCR-2\tMOV-1\t2027-01-02\t12:00
                """, Files.readString(paths.catalog(), UTF_8));
        assertEquals("""
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-2\tB2
                """, Files.readString(paths.seats(), UTF_8));
        assertFalse(Files.exists(paths.journal()));
        assertThrows(IllegalStateException.class, () -> transaction.commitScreening(deletion));
        assertThrows(IllegalArgumentException.class, () -> transaction.commitScreening(null));
    }

    @Test
    void prepare_missingOrUnaffectedOccupancy_preservesMissingOrExactBytes() throws Exception {
        Paths missingPaths = writeState(catalog(), null);
        PreparedScreeningDeletion missing = transaction(missingPaths).prepareScreening("SCR-1");
        transaction(missingPaths).commitScreening(missing);
        assertFalse(Files.exists(missingPaths.seats()));

        String unrelated = "CINECLI-SEATS\t1\nTAKEN_SEAT\tSCR-2\tB2\n";
        Paths presentPaths = writeState(catalog(), unrelated);
        byte[] original = Files.readAllBytes(presentPaths.seats());
        PreparedScreeningDeletion unaffected = transaction(presentPaths).prepareScreening("SCR-1");
        transaction(presentPaths).commitScreening(unaffected);
        assertArrayEquals(original, Files.readAllBytes(presentPaths.seats()));
    }

    @Test
    void prepare_invalidAndMalformedStateDoesNotWrite() throws Exception {
        Paths paths = writeState(catalog(), "broken\n");
        byte[] catalogBefore = Files.readAllBytes(paths.catalog());
        byte[] seatsBefore = Files.readAllBytes(paths.seats());
        MovieDeletionTransaction transaction = transaction(paths);

        assertAll(
                () -> assertThrows(ScreeningDeletionPreparationException.class,
                        () -> transaction.prepareScreening("SCR-404")),
                () -> assertThrows(ScreeningDeletionPreparationException.class,
                        () -> transaction.prepareScreening("SCR-1")));
        assertArrayEquals(catalogBefore, Files.readAllBytes(paths.catalog()));
        assertArrayEquals(seatsBefore, Files.readAllBytes(paths.seats()));
        Files.writeString(paths.journal(), "pending\n", UTF_8);
        assertThrows(ScreeningDeletionPreparationException.class,
                () -> transaction.prepareScreening("SCR-1"));
    }

    @Test
    void commit_alwaysPublishesJournalAndRecoveryCompletesInterruptedScreeningDeletion()
            throws Exception {
        Paths paths = writeState(catalog(), "CINECLI-SEATS\t1\n");
        MovieDeletionTransaction interrupted = transaction(paths, (operation, path) -> {
            if (operation == TransactionOperation.VERIFY_CATALOG) {
                throw new IOException("simulated interruption");
            }
        });
        ScreeningDeletionCommitException exception = assertThrows(
                ScreeningDeletionCommitException.class,
                () -> interrupted.commitScreening(interrupted.prepareScreening("SCR-1")));
        assertEquals(ScreeningDeletionCommitException.Status.RECOVERY_PENDING, exception.status());
        String journal = Files.readString(paths.journal(), UTF_8);
        assertTrue(journal.contains("OPERATION\tDELETE_SCREENING\nSUBJECT_ID\tSCR-1\n"));

        assertEquals(RecoveryResult.RECOVERED, transaction(paths).recover());
        assertFalse(Files.exists(paths.journal()));
        assertFalse(Files.readString(paths.catalog(), UTF_8).contains("SCREENING\tSCR-1\t"));
    }

    @Test
    void commit_notAppliedAndMalformedScreeningJournalPreserveData() throws Exception {
        Paths paths = writeState(catalog(), "CINECLI-SEATS\t1\n");
        MovieDeletionTransaction stale = transaction(paths);
        PreparedScreeningDeletion deletion = stale.prepareScreening("SCR-1");
        Files.writeString(paths.catalog(), catalog() + "\n", UTF_8);
        ScreeningDeletionCommitException exception = assertThrows(
                ScreeningDeletionCommitException.class, () -> stale.commitScreening(deletion));
        assertEquals(ScreeningDeletionCommitException.Status.NOT_APPLIED, exception.status());
        assertFalse(Files.exists(paths.journal()));

        Files.writeString(paths.journal(), """
                CINECLI-CATALOG-TRANSACTION\t1
                OPERATION\tDELETE_SCREENING
                SUBJECT_ID\tSCR-404
                CATALOG_ORIGINAL\tbad\tbad
                CATALOG_INTENDED\tbad\tbad
                SEATS_ORIGINAL\tMISSING
                SEATS_INTENDED\tMISSING
                """, UTF_8);
        assertThrows(TransactionStorageException.class, () -> transaction(paths).recover());
        assertTrue(Files.exists(paths.journal()));

        Files.writeString(paths.journal(), """
                CINECLI-CATALOG-TRANSACTION\t1
                OPERATION\tDELETE_ANYTHING
                SUBJECT_ID\tSCR-1
                CATALOG_ORIGINAL\tbad\tbad
                CATALOG_INTENDED\tbad\tbad
                SEATS_ORIGINAL\tMISSING
                SEATS_INTENDED\tMISSING
                """, UTF_8);
        assertThrows(TransactionStorageException.class, () -> transaction(paths).recover());
    }

    private MovieDeletionTransaction transaction(Paths paths) {
        return transaction(paths, TransactionOperationHook.NONE);
    }

    private MovieDeletionTransaction transaction(Paths paths, TransactionOperationHook hook) {
        CatalogStorage catalogStorage = new CatalogStorage(paths.catalog(), DEFAULT_RESOURCE);
        SeatStorage seatStorage = new SeatStorage(paths.seats());
        return new MovieDeletionTransaction(
                new CatalogTransactionAdapter(catalogStorage, paths.catalog()),
                new SeatTransactionAdapter(seatStorage, paths.seats()),
                paths.journal(), hook);
    }

    private Paths writeState(String catalog, String seats) throws Exception {
        Path runtime = tempDirectory.resolve(UUID.randomUUID().toString());
        Files.createDirectories(runtime);
        Path catalogPath = runtime.resolve("catalog.tsv");
        Path seatsPath = runtime.resolve("seats.tsv");
        Files.writeString(catalogPath, catalog, UTF_8);
        if (seats != null) {
            Files.writeString(seatsPath, seats, UTF_8);
        }
        return new Paths(catalogPath, seatsPath, runtime.resolve("catalog-transaction.journal"));
    }

    private String catalog() {
        return """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-1\tFirst\tPG13
                MOVIE\tMOV-2\tSecond\tR21
                SCREENING\tSCR-1\tMOV-1\t2027-01-01\t10:00
                SCREENING\tSCR-2\tMOV-1\t2027-01-02\t12:00
                """;
    }

    private PreparedScreeningDeletion prepared(
            Movie movie, Screening screening, int displayPosition, int occupiedSeatCount) {
        TransactionFileSnapshot<Map<String, Set<cinecli.model.SeatCoordinate>>> missing =
                TransactionFileSnapshot.missing(Map.of());
        return new PreparedScreeningDeletion(
                movie,
                screening,
                displayPosition,
                occupiedSeatCount,
                List.of(),
                new byte[0],
                new byte[0],
                missing,
                missing);
    }

    private record Paths(Path catalog, Path seats, Path journal) {
    }
}
