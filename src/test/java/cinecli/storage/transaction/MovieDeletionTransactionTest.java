package cinecli.storage.transaction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.storage.catalog.CatalogStorage;
import cinecli.storage.catalog.CatalogTransactionAdapter;
import cinecli.storage.exception.MovieDeletionCommitException;
import cinecli.storage.exception.MovieDeletionPreparationException;
import cinecli.storage.exception.TransactionStorageException;
import cinecli.storage.seat.SeatStorage;
import cinecli.storage.seat.SeatTransactionAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MovieDeletionTransactionTest {
    private static final String DEFAULT_RESOURCE = "/cinecli/default-catalog.tsv";

    @TempDir
    Path tempDirectory;

    @Test
    void resultModels_invalidCounts_rejectIndependently() {
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new MovieDeletionResult(null, "Title", 0, 0)),
                () -> assertThrows(NullPointerException.class,
                        () -> new MovieDeletionResult("MOV-1", null, 0, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new MovieDeletionResult("MOV-1", "Title", -1, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new MovieDeletionResult("MOV-1", "Title", 0, -1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ScreeningDeletionImpact(
                                "SCR-1", LocalDateTime.of(2027, 1, 1, 10, 0), -1)),
                () -> assertThrows(NullPointerException.class,
                        () -> new ScreeningDeletionImpact(
                                null, LocalDateTime.of(2027, 1, 1, 10, 0), 0)),
                () -> assertThrows(NullPointerException.class,
                        () -> new ScreeningDeletionImpact("SCR-1", null, 0)));
    }

    @Test
    void prepare_cascade_returnsOrderedImpactsAndCommitRemovesExactState() throws Exception {
        Paths paths = writeState(cascadeCatalog(), """
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-2\tB2
                TAKEN_SEAT\tSCR-1\tA1
                TAKEN_SEAT\tSCR-3\tC3
                """);
        MovieDeletionTransaction transaction = transaction(paths);

        PreparedMovieDeletion deletion = transaction.prepare("MOV-1");

        assertAll(
                () -> assertEquals("MOV-1", deletion.movieId()),
                () -> assertEquals(1, deletion.displayPosition()),
                () -> assertEquals(2, deletion.screeningCount()),
                () -> assertEquals(2, deletion.occupiedSeatCount()),
                () -> assertEquals("SCR-2", deletion.screeningImpacts().get(0).screeningId()),
                () -> assertEquals("SCR-1", deletion.screeningImpacts().get(1).screeningId()));

        MovieDeletionResult result = transaction.commit(deletion);

        assertAll(
                () -> assertEquals(new MovieDeletionResult("MOV-1", "First", 2, 2), result),
                () -> assertEquals("""
                        CINECLI-CATALOG\t1
                        MOVIE\tMOV-2\tSecond\tR21
                        SCREENING\tSCR-3\tMOV-2\t2027-03-04\t12:00
                        """, Files.readString(paths.catalog(), UTF_8)),
                () -> assertEquals("""
                        CINECLI-SEATS\t1
                        TAKEN_SEAT\tSCR-3\tC3
                        """, Files.readString(paths.seats(), UTF_8)),
                () -> assertFalse(Files.exists(paths.journal())),
                () -> assertThrows(IllegalStateException.class, () -> transaction.commit(deletion)));
    }

    @Test
    void commit_missingSeats_preservesAbsenceAndUsesJournaledCascade() throws Exception {
        Paths paths = writeState(cascadeCatalog(), null);
        MovieDeletionTransaction transaction = transaction(paths);

        PreparedMovieDeletion deletion = transaction.prepare("MOV-1");
        MovieDeletionResult result = transaction.commit(deletion);

        assertAll(
                () -> assertEquals(0, result.occupiedSeatCount()),
                () -> assertFalse(Files.exists(paths.seats())),
                () -> assertFalse(Files.exists(paths.journal())));
    }

    @Test
    void commit_presentOccupancyJournal_hasIndependentCanonicalBytes() throws Exception {
        String catalog = cascadeCatalog();
        String seats = """
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-2\tB2
                TAKEN_SEAT\tSCR-1\tA1
                TAKEN_SEAT\tSCR-3\tC3
                """;
        Paths paths = writeState(catalog, seats);
        MovieDeletionTransaction transaction = transaction(
                paths, failing(TransactionOperation.VERIFY_CATALOG));

        assertThrows(MovieDeletionCommitException.class,
                () -> transaction.commit(transaction.prepare("MOV-1")));

        String intendedCatalog = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-2\tSecond\tR21
                SCREENING\tSCR-3\tMOV-2\t2027-03-04\t12:00
                """;
        String intendedSeats = """
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-3\tC3
                """;
        String expectedJournal = "CINECLI-CATALOG-TRANSACTION\t1\n"
                + "OPERATION\tDELETE_MOVIE\n"
                + "SUBJECT_ID\tMOV-1\n"
                + "CATALOG_ORIGINAL\t" + digest(catalog.getBytes(UTF_8)) + "\t"
                + Base64.getEncoder().encodeToString(catalog.getBytes(UTF_8)) + "\n"
                + "CATALOG_INTENDED\t" + digest(intendedCatalog.getBytes(UTF_8)) + "\t"
                + Base64.getEncoder().encodeToString(intendedCatalog.getBytes(UTF_8)) + "\n"
                + "SEATS_ORIGINAL\tPRESENT\t" + digest(seats.getBytes(UTF_8)) + "\t"
                + Base64.getEncoder().encodeToString(seats.getBytes(UTF_8)) + "\n"
                + "SEATS_INTENDED\tPRESENT\t" + digest(intendedSeats.getBytes(UTF_8)) + "\t"
                + Base64.getEncoder().encodeToString(intendedSeats.getBytes(UTF_8)) + "\n";

        assertEquals(expectedJournal, Files.readString(paths.journal(), UTF_8));
    }

    @Test
    void prepare_childlessAndCommit_neverAccessesMalformedSeats() throws Exception {
        String catalog = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-1\tFirst\tPG13
                MOVIE\tMOV-2\tSecond\tR21
                SCREENING\tSCR-1\tMOV-1\t2027-01-02\t21:30
                """;
        Paths paths = writeState(catalog, "malformed\n");
        MovieDeletionTransaction transaction = transaction(paths);
        byte[] seatsBefore = Files.readAllBytes(paths.seats());

        PreparedMovieDeletion deletion = transaction.prepare("MOV-2");
        MovieDeletionResult result = transaction.commit(deletion);

        assertAll(
                () -> assertEquals(0, deletion.screeningCount()),
                () -> assertEquals("MOV-2", result.movieId()),
                () -> assertArrayEquals(seatsBefore, Files.readAllBytes(paths.seats())));
    }

    @Test
    void prepare_unknownOrMalformedOccupancy_rejectsWithoutWriting() throws Exception {
        Paths paths = writeState(cascadeCatalog(), "malformed\n");
        byte[] catalogBefore = Files.readAllBytes(paths.catalog());
        byte[] seatsBefore = Files.readAllBytes(paths.seats());
        MovieDeletionTransaction transaction = transaction(paths);

        assertAll(
                () -> assertThrows(MovieDeletionPreparationException.class,
                        () -> transaction.prepare("MOV-404")),
                () -> assertThrows(MovieDeletionPreparationException.class,
                        () -> transaction.prepare("MOV-1")),
                () -> assertArrayEquals(catalogBefore, Files.readAllBytes(paths.catalog())),
                () -> assertArrayEquals(seatsBefore, Files.readAllBytes(paths.seats())),
                () -> assertFalse(Files.exists(paths.journal())));
    }

    @Test
    void commit_stalePreparedDeletion_returnsNotAppliedWithoutJournal() throws Exception {
        Paths paths = writeState(cascadeCatalog(), "CINECLI-SEATS\t1\n");
        MovieDeletionTransaction transaction = transaction(paths);
        PreparedMovieDeletion deletion = transaction.prepare("MOV-1");
        Files.writeString(paths.catalog(), cascadeCatalog() + "\n", UTF_8);

        MovieDeletionCommitException exception = assertThrows(
                MovieDeletionCommitException.class, () -> transaction.commit(deletion));

        assertAll(
                () -> assertEquals(MovieDeletionCommitException.Status.NOT_APPLIED, exception.status()),
                () -> assertFalse(Files.exists(paths.journal())));
    }

    @Test
    void recover_noJournalOrMalformedJournal_isSafe() throws Exception {
        Paths paths = writeState(cascadeCatalog(), null);
        MovieDeletionTransaction transaction = transaction(paths);
        assertEquals(RecoveryResult.NO_JOURNAL, transaction.recover());
        Files.writeString(paths.journal(), "not a journal\n", UTF_8);
        byte[] catalogBefore = Files.readAllBytes(paths.catalog());

        assertThrows(TransactionStorageException.class, transaction::recover);

        assertAll(
                () -> assertArrayEquals(catalogBefore, Files.readAllBytes(paths.catalog())),
                () -> assertTrue(Files.exists(paths.journal())));
    }

    @Test
    void commit_faultsBeforeAndAfterPublication_reportTruthfulStatus() throws Exception {
        for (TransactionOperation operation : List.of(
                TransactionOperation.CREATE_JOURNAL_TEMPORARY,
                TransactionOperation.WRITE_JOURNAL_TEMPORARY,
                TransactionOperation.FORCE_JOURNAL_TEMPORARY,
                TransactionOperation.PUBLISH_JOURNAL)) {
            Paths paths = writeState(cascadeCatalog(), "CINECLI-SEATS\t1\n");
            byte[] originalCatalog = Files.readAllBytes(paths.catalog());
            MovieDeletionTransaction transaction = transaction(paths, failing(operation));
            MovieDeletionCommitException exception = assertThrows(
                    MovieDeletionCommitException.class,
                    () -> transaction.commit(transaction.prepare("MOV-1")));
            assertEquals(MovieDeletionCommitException.Status.NOT_APPLIED, exception.status());
            assertArrayEquals(originalCatalog, Files.readAllBytes(paths.catalog()));
            assertFalse(Files.exists(paths.journal()));
        }

        for (TransactionOperation operation : List.of(
                TransactionOperation.VERIFY_CATALOG,
                TransactionOperation.VERIFY_SEATS,
                TransactionOperation.DELETE_JOURNAL)) {
            Paths paths = writeState(cascadeCatalog(), "CINECLI-SEATS\t1\n");
            MovieDeletionTransaction transaction = transaction(paths, failing(operation));
            MovieDeletionCommitException exception = assertThrows(
                    MovieDeletionCommitException.class,
                    () -> transaction.commit(transaction.prepare("MOV-1")));
            assertEquals(MovieDeletionCommitException.Status.RECOVERY_PENDING, exception.status());
            assertTrue(Files.exists(paths.journal()));
        }
    }

    @Test
    void recover_everyOriginalIntendedCombination_completesIdempotently() throws Exception {
        String originalCatalogText = cascadeCatalog();
        String originalSeatsText = """
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-1\tA1
                TAKEN_SEAT\tSCR-3\tC3
                """;
        Paths paths = writeState(originalCatalogText, originalSeatsText);
        MovieDeletionTransaction interrupted = transaction(
                paths, failing(TransactionOperation.VERIFY_CATALOG));
        assertThrows(MovieDeletionCommitException.class,
                () -> interrupted.commit(interrupted.prepare("MOV-1")));
        byte[] journal = Files.readAllBytes(paths.journal());
        byte[] intendedCatalog = Files.readAllBytes(paths.catalog());
        byte[] intendedSeats = Files.readAllBytes(paths.seats());
        byte[] originalCatalog = originalCatalogText.getBytes(UTF_8);
        byte[] originalSeats = originalSeatsText.getBytes(UTF_8);

        List<byte[][]> states = List.of(
                new byte[][] {originalCatalog, originalSeats},
                new byte[][] {originalCatalog, intendedSeats},
                new byte[][] {intendedCatalog, originalSeats},
                new byte[][] {intendedCatalog, intendedSeats});
        for (byte[][] state : states) {
            Files.write(paths.catalog(), state[0]);
            Files.write(paths.seats(), state[1]);
            Files.write(paths.journal(), journal);
            MovieDeletionTransaction recovery = transaction(paths);

            assertEquals(RecoveryResult.RECOVERED, recovery.recover());
            assertArrayEquals(intendedCatalog, Files.readAllBytes(paths.catalog()));
            assertArrayEquals(intendedSeats, Files.readAllBytes(paths.seats()));
            assertFalse(Files.exists(paths.journal()));
            assertEquals(RecoveryResult.NO_JOURNAL, recovery.recover());
        }
    }

    @Test
    void recover_divergentTargetsAndMalformedJournal_preserveJournalAndTargets() throws Exception {
        Paths paths = writeState(cascadeCatalog(), "CINECLI-SEATS\t1\n");
        MovieDeletionTransaction interrupted = transaction(
                paths, failing(TransactionOperation.VERIFY_CATALOG));
        assertThrows(MovieDeletionCommitException.class,
                () -> interrupted.commit(interrupted.prepare("MOV-1")));
        byte[] validJournal = Files.readAllBytes(paths.journal());
        Files.writeString(paths.catalog(), "divergent\n", UTF_8);
        byte[] divergent = Files.readAllBytes(paths.catalog());

        assertThrows(TransactionStorageException.class, () -> transaction(paths).recover());
        assertArrayEquals(divergent, Files.readAllBytes(paths.catalog()));
        assertArrayEquals(validJournal, Files.readAllBytes(paths.journal()));

        List<String> malformed = new ArrayList<>();
        String valid = new String(validJournal, UTF_8);
        malformed.add("\uFEFF" + valid);
        malformed.add(valid.replace("\n", "\r\n"));
        malformed.add(valid.substring(0, valid.length() - 1));
        malformed.add(valid.replace("CINECLI-CATALOG-TRANSACTION\t1", "BAD\t1"));
        malformed.add(valid.replace("DELETE_MOVIE", "DELETE_SCREENING"));
        malformed.add(valid.replace("SUBJECT_ID\tMOV-1", "SUBJECT_ID\t_bad"));
        malformed.add(valid.replaceFirst("[0-9a-f]{64}", "BAD"));
        malformed.add(valid.replaceFirst("\t[A-Za-z0-9+/]+=*\n", "\t%%%\n"));
        for (String content : malformed) {
            Files.writeString(paths.journal(), content, UTF_8);
            assertThrows(TransactionStorageException.class, () -> transaction(paths).recover());
            assertEquals(content, Files.readString(paths.journal(), UTF_8));
        }
    }

    @Test
    void prepareAndCommit_programmingOrStaleStatesFailBeforeIntent() throws Exception {
        Paths childlessPaths = writeState("""
                CINECLI-CATALOG\t1
                MOVIE\tMOV-1\tFirst\tPG13
                """, null);
        MovieDeletionTransaction childless = transaction(childlessPaths);
        PreparedMovieDeletion stale = childless.prepare("MOV-1");
        Files.writeString(childlessPaths.catalog(), "CINECLI-CATALOG\t1\n", UTF_8);
        MovieDeletionCommitException childlessFailure = assertThrows(
                MovieDeletionCommitException.class, () -> childless.commit(stale));
        assertEquals(MovieDeletionCommitException.Status.NOT_APPLIED, childlessFailure.status());
        assertThrows(IllegalArgumentException.class, () -> childless.commit(null));

        Paths cascadePaths = writeState(cascadeCatalog(), "CINECLI-SEATS\t1\n");
        MovieDeletionTransaction cascade = transaction(cascadePaths);
        PreparedMovieDeletion prepared = cascade.prepare("MOV-1");
        Files.writeString(cascadePaths.journal(), "occupied\n", UTF_8);
        MovieDeletionCommitException existingJournal = assertThrows(
                MovieDeletionCommitException.class, () -> cascade.commit(prepared));
        assertEquals(MovieDeletionCommitException.Status.NOT_APPLIED, existingJournal.status());
        assertThrows(MovieDeletionPreparationException.class,
                () -> cascade.prepare("MOV-1"));
    }

    @Test
    void commit_staleSeatsAndCleanupFaults_remainNotApplied() throws Exception {
        Paths stalePaths = writeState(cascadeCatalog(), "CINECLI-SEATS\t1\n");
        MovieDeletionTransaction staleTransaction = transaction(stalePaths);
        PreparedMovieDeletion stale = staleTransaction.prepare("MOV-1");
        Files.writeString(stalePaths.seats(), """
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-1\tA1
                """, UTF_8);
        MovieDeletionCommitException staleFailure = assertThrows(
                MovieDeletionCommitException.class, () -> staleTransaction.commit(stale));
        assertEquals(MovieDeletionCommitException.Status.NOT_APPLIED, staleFailure.status());

        Paths cleanupPaths = writeState(cascadeCatalog(), "CINECLI-SEATS\t1\n");
        TransactionOperationHook hook = (operation, path) -> {
            if (operation == TransactionOperation.WRITE_JOURNAL_TEMPORARY
                    || operation == TransactionOperation.DELETE_JOURNAL_TEMPORARY) {
                throw new IOException("simulated " + operation);
            }
        };
        MovieDeletionTransaction cleanup = transaction(cleanupPaths, hook);
        MovieDeletionCommitException cleanupFailure = assertThrows(
                MovieDeletionCommitException.class,
                () -> cleanup.commit(cleanup.prepare("MOV-1")));
        assertEquals(MovieDeletionCommitException.Status.NOT_APPLIED, cleanupFailure.status());
    }

    @Test
    void commit_journalDirectoryFailure_isNotAppliedAndPreservesTargets() throws Exception {
        Paths paths = writeState(cascadeCatalog(), "CINECLI-SEATS\t1\n");
        Path blockingFile = paths.catalog().resolveSibling("blocking-file");
        Files.writeString(blockingFile, "not a directory", UTF_8);
        Paths blockedJournalPaths = new Paths(
                paths.catalog(), paths.seats(), blockingFile.resolve("journal"));
        MovieDeletionTransaction transaction = transaction(blockedJournalPaths);
        byte[] originalCatalog = Files.readAllBytes(paths.catalog());

        MovieDeletionCommitException exception = assertThrows(
                MovieDeletionCommitException.class,
                () -> transaction.commit(transaction.prepare("MOV-1")));

        assertEquals(MovieDeletionCommitException.Status.NOT_APPLIED, exception.status());
        assertArrayEquals(originalCatalog, Files.readAllBytes(paths.catalog()));
        assertFalse(Files.exists(blockedJournalPaths.journal()));
    }

    @Test
    void commit_publicationFailure_classifiesJournalPostconditionTruthfully() throws Exception {
        Paths exactPaths = writeState(cascadeCatalog(), "CINECLI-SEATS\t1\n");
        MovieDeletionTransaction exactTransaction = transaction(exactPaths, (operation, path) -> {
            if (operation == TransactionOperation.VERIFY_JOURNAL_PUBLICATION) {
                throw new IOException("reported after publication");
            }
        });
        MovieDeletionCommitException exactFailure = assertThrows(
                MovieDeletionCommitException.class,
                () -> exactTransaction.commit(exactTransaction.prepare("MOV-1")));

        assertAll(
                () -> assertEquals(
                        MovieDeletionCommitException.Status.RECOVERY_PENDING, exactFailure.status()),
                () -> assertTrue(Files.exists(exactPaths.journal())),
                () -> assertEquals(RecoveryResult.RECOVERED, transaction(exactPaths).recover()));

        Paths absentPaths = writeState(cascadeCatalog(), "CINECLI-SEATS\t1\n");
        byte[] absentCatalog = Files.readAllBytes(absentPaths.catalog());
        MovieDeletionTransaction absentTransaction = transaction(absentPaths, (operation, path) -> {
            if (operation == TransactionOperation.VERIFY_JOURNAL_PUBLICATION) {
                Files.delete(path);
                throw new IOException("reported after absent publication");
            }
        });
        MovieDeletionCommitException absentFailure = assertThrows(
                MovieDeletionCommitException.class,
                () -> absentTransaction.commit(absentTransaction.prepare("MOV-1")));

        assertAll(
                () -> assertEquals(MovieDeletionCommitException.Status.NOT_APPLIED, absentFailure.status()),
                () -> assertArrayEquals(absentCatalog, Files.readAllBytes(absentPaths.catalog())),
                () -> assertFalse(Files.exists(absentPaths.journal())));

        Paths divergentPaths = writeState(cascadeCatalog(), "CINECLI-SEATS\t1\n");
        byte[] divergentJournal = "divergent journal\\n".getBytes(UTF_8);
        MovieDeletionTransaction divergentTransaction = transaction(divergentPaths, (operation, path) -> {
            if (operation == TransactionOperation.VERIFY_JOURNAL_PUBLICATION) {
                Files.write(path, divergentJournal);
                throw new IOException("reported after divergent publication");
            }
        });
        MovieDeletionCommitException divergentFailure = assertThrows(
                MovieDeletionCommitException.class,
                () -> divergentTransaction.commit(divergentTransaction.prepare("MOV-1")));

        assertAll(
                () -> assertEquals(
                        MovieDeletionCommitException.Status.NOT_APPLIED, divergentFailure.status()),
                () -> assertArrayEquals(divergentJournal, Files.readAllBytes(divergentPaths.journal())),
                () -> assertThrows(
                        TransactionStorageException.class, () -> transaction(divergentPaths).recover()),
                () -> assertArrayEquals(divergentJournal, Files.readAllBytes(divergentPaths.journal())));

        Paths unreadablePaths = writeState(cascadeCatalog(), "CINECLI-SEATS\t1\n");
        MovieDeletionTransaction unreadableTransaction = transaction(unreadablePaths, (operation, path) -> {
            if (operation == TransactionOperation.VERIFY_JOURNAL_PUBLICATION) {
                Files.delete(path);
                Files.createDirectory(path);
                throw new IOException("reported with unreadable publication state");
            }
        });
        MovieDeletionCommitException unreadableFailure = assertThrows(
                MovieDeletionCommitException.class,
                () -> unreadableTransaction.commit(unreadableTransaction.prepare("MOV-1")));

        assertAll(
                () -> assertEquals(
                        MovieDeletionCommitException.Status.NOT_APPLIED, unreadableFailure.status()),
                () -> assertTrue(Files.isDirectory(unreadablePaths.journal())),
                () -> assertThrows(
                        TransactionStorageException.class, () -> transaction(unreadablePaths).recover()));
    }

    @Test
    void recover_readOrCleanupFault_retainsValidJournalForRetry() throws Exception {
        Paths paths = writeState(cascadeCatalog(), "CINECLI-SEATS\t1\n");
        MovieDeletionTransaction interrupted = transaction(
                paths, failing(TransactionOperation.VERIFY_CATALOG));
        assertThrows(MovieDeletionCommitException.class,
                () -> interrupted.commit(interrupted.prepare("MOV-1")));

        for (TransactionOperation operation : List.of(
                TransactionOperation.READ_JOURNAL,
                TransactionOperation.VERIFY_CATALOG,
                TransactionOperation.VERIFY_SEATS,
                TransactionOperation.DELETE_JOURNAL)) {
            assertThrows(TransactionStorageException.class,
                    () -> transaction(paths, failing(operation)).recover());
            assertTrue(Files.exists(paths.journal()));
        }
        assertEquals(RecoveryResult.RECOVERED, transaction(paths).recover());
    }

    @Test
    void recover_semanticallyMalformedJournalPartitions_blockBeforeTargetMutation() throws Exception {
        String seats = """
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-1\tA1
                TAKEN_SEAT\tSCR-3\tC3
                """;
        Paths paths = writeState(cascadeCatalog(), seats);
        byte[] validJournalBytes = createPendingJournal(paths);
        String valid = new String(validJournalBytes, UTF_8);
        byte[] originalCatalog = snapshotBytes(valid, "CATALOG_ORIGINAL");
        byte[] intendedCatalog = snapshotBytes(valid, "CATALOG_INTENDED");
        byte[] originalSeats = seatSnapshotBytes(valid, "SEATS_ORIGINAL");
        byte[] intendedSeats = seatSnapshotBytes(valid, "SEATS_INTENDED");
        byte[] wrongLogicalSeats = "CINECLI-SEATS\t1\n".getBytes(UTF_8);
        byte[] noncanonicalIntendedSeats = new String(intendedSeats, UTF_8)
                .replace("\n", "\r\n").getBytes(UTF_8);
        String invalidBase64 = replaceEncodedValue(valid, "CATALOG_ORIGINAL", "%%%");
        String unpaddedBase64 = replaceBytesLine(
                valid,
                "CATALOG_ORIGINAL",
                "CINECLI-CATALOG\t1\nMOVIE\tX\tAA\tPG13\n".getBytes(UTF_8));
        unpaddedBase64 = unpaddedBase64.replaceFirst(
                "(CATALOG_ORIGINAL\t[0-9a-f]{64}\t[^\n=]+)=+", "$1");
        List<String> malformed = List.of(
                valid + "EXTRA\n",
                valid.replace("SUBJECT_ID\tMOV-1", "SUBJECT_ID\tMOV-404"),
                replaceBytesLine(valid, "CATALOG_INTENDED", originalCatalog),
                replaceBytesLine(
                        valid,
                        "CATALOG_INTENDED",
                        new String(intendedCatalog, UTF_8).replace("\n", "\r\n").getBytes(UTF_8)),
                valid.replaceFirst("SEATS_INTENDED\tPRESENT[^\n]+", "SEATS_INTENDED\tMISSING"),
                replaceSeatBytesLine(valid, "SEATS_INTENDED", originalSeats),
                replaceSeatBytesLine(valid, "SEATS_INTENDED", wrongLogicalSeats),
                replaceSeatBytesLine(valid, "SEATS_INTENDED", noncanonicalIntendedSeats),
                replaceBytesLine(valid, "CATALOG_ORIGINAL", new byte[] {(byte) 0xC3}),
                replaceSeatBytesLine(valid, "SEATS_ORIGINAL", new byte[] {(byte) 0xC3}),
                valid.replace("SUBJECT_ID\tMOV-1", "BAD_SUBJECT\tMOV-1"),
                valid.replace("SUBJECT_ID\tMOV-1", "SUBJECT_ID\tMOV-1\textra"),
                valid.replace("CATALOG_ORIGINAL\t", "BAD_CATALOG\t"),
                valid.replaceFirst("CATALOG_ORIGINAL[^\n]+", "CATALOG_ORIGINAL\tonly-one-value"),
                valid.replace("SEATS_ORIGINAL\tPRESENT", "SEATS_ORIGINAL\tUNKNOWN"),
                valid.replace("SEATS_ORIGINAL\tPRESENT", "BAD_SEATS\tPRESENT"),
                valid.replaceFirst("SEATS_INTENDED[^\n]+", "SEATS_INTENDED\tPRESENT\ta\tb\textra"),
                invalidBase64,
                unpaddedBase64,
                changeFirstDigest(valid));
        byte[] catalogBefore = Files.readAllBytes(paths.catalog());
        byte[] seatsBefore = Files.readAllBytes(paths.seats());
        for (String journal : malformed) {
            Files.writeString(paths.journal(), journal, UTF_8);
            assertThrows(TransactionStorageException.class, () -> transaction(paths).recover());
            assertArrayEquals(catalogBefore, Files.readAllBytes(paths.catalog()));
            assertArrayEquals(seatsBefore, Files.readAllBytes(paths.seats()));
        }

        Files.write(paths.journal(), new byte[] {(byte) 0xC3});
        assertThrows(TransactionStorageException.class, () -> transaction(paths).recover());
    }

    @Test
    void recover_childlessMovieJournal_blocksWithoutMutatingTargets() throws Exception {
        String originalCatalog = "CINECLI-CATALOG\t1\nMOVIE\tMOV-1\tFirst\tPG13\n";
        String intendedCatalog = "CINECLI-CATALOG\t1\n";
        Paths paths = writeState(originalCatalog, null);
        String journal = "CINECLI-CATALOG-TRANSACTION\t1\n"
                + "OPERATION\tDELETE_MOVIE\n"
                + "SUBJECT_ID\tMOV-1\n"
                + "CATALOG_ORIGINAL\t" + digest(originalCatalog.getBytes(UTF_8)) + "\t"
                + Base64.getEncoder().encodeToString(originalCatalog.getBytes(UTF_8)) + "\n"
                + "CATALOG_INTENDED\t" + digest(intendedCatalog.getBytes(UTF_8)) + "\t"
                + Base64.getEncoder().encodeToString(intendedCatalog.getBytes(UTF_8)) + "\n"
                + "SEATS_ORIGINAL\tMISSING\n"
                + "SEATS_INTENDED\tMISSING\n";
        Files.writeString(paths.journal(), journal, UTF_8);

        assertThrows(TransactionStorageException.class, () -> transaction(paths).recover());

        assertAll(
                () -> assertEquals(originalCatalog, Files.readString(paths.catalog(), UTF_8)),
                () -> assertFalse(Files.exists(paths.seats())),
                () -> assertEquals(journal, Files.readString(paths.journal(), UTF_8)));
    }

    @Test
    void recover_noAffectedSeatBytesMustRemainExactAndDivergentPresenceBlocks() throws Exception {
        String unrelatedNoncanonical = """
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-3\tC3
                """;
        Paths paths = writeState(cascadeCatalog(), unrelatedNoncanonical);
        String valid = new String(createPendingJournal(paths), UTF_8);
        byte[] canonicalEquivalent = """
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-3\tC3
                """.getBytes(UTF_8);
        String changedBytes = replaceSeatBytesLine(valid, "SEATS_INTENDED", canonicalEquivalent);
        if (Arrays.equals(seatSnapshotBytes(valid, "SEATS_ORIGINAL"), canonicalEquivalent)) {
            canonicalEquivalent = (new String(canonicalEquivalent, UTF_8).replace("\n", "\r\n"))
                    .getBytes(UTF_8);
            changedBytes = replaceSeatBytesLine(valid, "SEATS_INTENDED", canonicalEquivalent);
        }
        Files.writeString(paths.journal(), changedBytes, UTF_8);
        assertThrows(TransactionStorageException.class, () -> transaction(paths).recover());

        Files.writeString(paths.journal(), valid, UTF_8);
        Files.delete(paths.seats());
        assertThrows(TransactionStorageException.class, () -> transaction(paths).recover());
        assertTrue(Files.exists(paths.journal()));
    }

    @Test
    void commit_verificationDetectsDivergentTargetsAfterDurableIntent() throws Exception {
        for (TransactionOperation verification : List.of(
                TransactionOperation.VERIFY_CATALOG, TransactionOperation.VERIFY_SEATS)) {
            Paths paths = writeState(cascadeCatalog(), "CINECLI-SEATS\t1\n");
            TransactionOperationHook hook = (operation, path) -> {
                if (operation == verification) {
                    Path target = verification == TransactionOperation.VERIFY_CATALOG
                            ? paths.catalog() : paths.seats();
                    Files.writeString(target, "divergent\n", UTF_8);
                }
            };
            MovieDeletionTransaction transaction = transaction(paths, hook);
            MovieDeletionCommitException exception = assertThrows(
                    MovieDeletionCommitException.class,
                    () -> transaction.commit(transaction.prepare("MOV-1")));
            assertEquals(MovieDeletionCommitException.Status.RECOVERY_PENDING, exception.status());
            assertTrue(Files.exists(paths.journal()));
        }
    }

    @Test
    void recover_missingOccupancyJournal_validatesBothMissingLines() throws Exception {
        Paths paths = writeState(cascadeCatalog(), null);
        byte[] validJournal = createPendingJournal(paths);
        String valid = new String(validJournal, UTF_8);
        byte[] originalCatalog = cascadeCatalog().getBytes(UTF_8);
        byte[] intendedCatalog = Files.readAllBytes(paths.catalog());
        String expected = "CINECLI-CATALOG-TRANSACTION\t1\n"
                + "OPERATION\tDELETE_MOVIE\n"
                + "SUBJECT_ID\tMOV-1\n"
                + "CATALOG_ORIGINAL\t" + digest(originalCatalog) + "\t"
                + Base64.getEncoder().encodeToString(originalCatalog) + "\n"
                + "CATALOG_INTENDED\t" + digest(intendedCatalog) + "\t"
                + Base64.getEncoder().encodeToString(intendedCatalog) + "\n"
                + "SEATS_ORIGINAL\tMISSING\n"
                + "SEATS_INTENDED\tMISSING\n";
        assertEquals(expected, valid);
        for (String malformed : List.of(
                valid.replace("SEATS_ORIGINAL\tMISSING", "BAD_SEATS\tMISSING"),
                valid.replace("SEATS_ORIGINAL\tMISSING", "SEATS_ORIGINAL\tUNKNOWN"))) {
            Files.writeString(paths.journal(), malformed, UTF_8);
            assertThrows(TransactionStorageException.class, () -> transaction(paths).recover());
        }
        Files.write(paths.journal(), validJournal);

        assertEquals(RecoveryResult.RECOVERED, transaction(paths).recover());
        assertFalse(Files.exists(paths.seats()));
        assertFalse(Files.exists(paths.journal()));
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
                paths.journal(),
                hook);
    }

    private TransactionOperationHook failing(TransactionOperation expected) {
        return (operation, path) -> {
            if (operation == expected) {
                throw new IOException("simulated " + expected);
            }
        };
    }

    private byte[] createPendingJournal(Paths paths) throws Exception {
        MovieDeletionTransaction transaction = transaction(
                paths, failing(TransactionOperation.VERIFY_CATALOG));
        assertThrows(MovieDeletionCommitException.class,
                () -> transaction.commit(transaction.prepare("MOV-1")));
        return Files.readAllBytes(paths.journal());
    }

    private String replaceBytesLine(String journal, String name, byte[] bytes) throws Exception {
        String replacement = name + "\t" + digest(bytes) + "\t"
                + Base64.getEncoder().encodeToString(bytes);
        return journal.replaceFirst(name + "[^\n]+", java.util.regex.Matcher.quoteReplacement(replacement));
    }

    private String replaceSeatBytesLine(String journal, String name, byte[] bytes) throws Exception {
        String replacement = name + "\tPRESENT\t" + digest(bytes) + "\t"
                + Base64.getEncoder().encodeToString(bytes);
        return journal.replaceFirst(name + "[^\n]+", java.util.regex.Matcher.quoteReplacement(replacement));
    }

    private byte[] snapshotBytes(String journal, String name) {
        String line = Arrays.stream(journal.split("\n"))
                .filter(value -> value.startsWith(name + "\t"))
                .findFirst().orElseThrow();
        return Base64.getDecoder().decode(line.split("\t")[2]);
    }

    private byte[] seatSnapshotBytes(String journal, String name) {
        String line = Arrays.stream(journal.split("\n"))
                .filter(value -> value.startsWith(name + "\t"))
                .findFirst().orElseThrow();
        return Base64.getDecoder().decode(line.split("\t")[3]);
    }

    private String changeFirstDigest(String journal) {
        String line = Arrays.stream(journal.split("\n"))
                .filter(value -> value.startsWith("CATALOG_ORIGINAL\t"))
                .findFirst().orElseThrow();
        String[] fields = line.split("\t");
        char replacement = fields[1].charAt(0) == '0' ? '1' : '0';
        String changed = replacement + fields[1].substring(1);
        return journal.replace(line, fields[0] + "\t" + changed + "\t" + fields[2]);
    }

    private String replaceEncodedValue(String journal, String name, String encoded) {
        String line = Arrays.stream(journal.split("\n"))
                .filter(value -> value.startsWith(name + "\t"))
                .findFirst().orElseThrow();
        String[] fields = line.split("\t");
        return journal.replace(line, fields[0] + "\t" + fields[1] + "\t" + encoded);
    }

    private String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private Paths writeState(String catalog, String seats) throws Exception {
        Path runtime = tempDirectory.resolve(UUID.randomUUID().toString()).resolve("data/runtime");
        Files.createDirectories(runtime);
        Path catalogPath = runtime.resolve("catalog.tsv");
        Path seatsPath = runtime.resolve("seats.tsv");
        Path journalPath = runtime.resolve("catalog-transaction.journal");
        Files.writeString(catalogPath, catalog, UTF_8);
        if (seats != null) {
            Files.writeString(seatsPath, seats, UTF_8);
        }
        return new Paths(catalogPath, seatsPath, journalPath);
    }

    private String cascadeCatalog() {
        return """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-1\tFirst\tPG13
                MOVIE\tMOV-2\tSecond\tR21
                SCREENING\tSCR-2\tMOV-1\t2027-02-03\t09:05
                SCREENING\tSCR-1\tMOV-1\t2027-01-02\t21:30
                SCREENING\tSCR-3\tMOV-2\t2027-03-04\t12:00
                """;
    }

    private record Paths(Path catalog, Path seats, Path journal) {
    }
}
