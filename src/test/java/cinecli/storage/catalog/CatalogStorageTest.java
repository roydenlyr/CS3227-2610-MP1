package cinecli.storage.catalog;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.model.Movie;
import cinecli.model.ContentRating;
import cinecli.model.Screening;
import cinecli.storage.exception.CatalogStorageException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogStorageTest {
    private static final String DEFAULT_RESOURCE = "/cinecli/default-catalog.tsv";

    @TempDir
    Path tempDirectory;

    @Test
    void save_completeValidState_writesCanonicalBytesAndRoundTrips() throws Exception {
        Path runtimeCatalog = writeRuntimeCatalog("CINECLI-CATALOG\t1\n");
        CatalogStorage catalogStorage = new CatalogStorage(runtimeCatalog, DEFAULT_RESOURCE);
        List<Movie> movies = List.of(
                new Movie("MOV-B", "Étoile  Meridian", ContentRating.M18, List.of(
                        new Screening("SCR-2", LocalDateTime.of(2027, 2, 3, 9, 5)),
                        new Screening("SCR-1", LocalDateTime.of(2027, 1, 2, 21, 30)))),
                new Movie("MOV-A", "After", ContentRating.PG13, List.of()));

        catalogStorage.save(movies);

        String expected = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-B\tÉtoile  Meridian\tM18
                MOVIE\tMOV-A\tAfter\tPG13
                SCREENING\tSCR-2\tMOV-B\t2027-02-03\t09:05
                SCREENING\tSCR-1\tMOV-B\t2027-01-02\t21:30
                """;
        assertAll(
                () -> assertEquals(expected, Files.readString(runtimeCatalog, UTF_8)),
                () -> assertEquals(movies, catalogStorage.load()));
    }

    @Test
    void save_invalidCompleteState_preservesOriginalBytes() throws Exception {
        Path runtimeCatalog = writeRuntimeCatalog("CINECLI-CATALOG\t1\n");
        byte[] originalBytes = Files.readAllBytes(runtimeCatalog);
        CatalogStorage catalogStorage = new CatalogStorage(runtimeCatalog, DEFAULT_RESOURCE);
        List<Movie> invalid = List.of(
                new Movie("MOV-1", "First", ContentRating.PG13, List.of(
                        new Screening("SCR-X", LocalDateTime.of(2027, 1, 1, 10, 0)))),
                new Movie("MOV-2", "Second", ContentRating.R21, List.of(
                        new Screening("SCR-X", LocalDateTime.of(2027, 1, 2, 10, 0)))));

        assertThrows(CatalogStorageException.class, () -> catalogStorage.save(invalid));

        assertArrayEquals(originalBytes, Files.readAllBytes(runtimeCatalog));
    }

    @Test
    void save_nullStateOrMovie_rejectsBeforeChangingTarget() throws Exception {
        Path runtimeCatalog = writeRuntimeCatalog("CINECLI-CATALOG\t1\n");
        byte[] original = Files.readAllBytes(runtimeCatalog);
        CatalogStorage catalogStorage = new CatalogStorage(runtimeCatalog, DEFAULT_RESOURCE);
        List<Movie> withNull = new java.util.ArrayList<>();
        withNull.add(null);

        assertAll(
                () -> assertThrows(CatalogStorageException.class,
                        () -> catalogStorage.save(null)),
                () -> assertThrows(CatalogStorageException.class,
                        () -> catalogStorage.save(withNull)),
                () -> assertArrayEquals(original, Files.readAllBytes(runtimeCatalog)));
    }

    @Test
    void save_atomicStageFailures_preserveOriginalAndPrimaryCause() throws Exception {
        for (CatalogStorageOperation failedOperation : List.of(
                CatalogStorageOperation.CREATE_DIRECTORIES,
                CatalogStorageOperation.CREATE_TEMPORARY,
                CatalogStorageOperation.WRITE_TEMPORARY,
                CatalogStorageOperation.FORCE_TEMPORARY)) {
            Path runtimeCatalog = tempDirectory.resolve(failedOperation.name()).resolve("catalog.tsv");
            Files.createDirectories(runtimeCatalog.getParent());
            Files.writeString(runtimeCatalog, "CINECLI-CATALOG\t1\n", UTF_8);
            byte[] original = Files.readAllBytes(runtimeCatalog);
            IOException cause = new IOException("simulated " + failedOperation);
            CatalogStorage storage = new CatalogStorage(
                    runtimeCatalog,
                    DEFAULT_RESOURCE,
                    (operation, path) -> {
                        if (operation == failedOperation) {
                            throw cause;
                        }
                    });

            CatalogStorageException exception = assertThrows(
                    CatalogStorageException.class, () -> storage.save(List.of()));

            assertEquals(cause, exception.getCause());
            assertArrayEquals(original, Files.readAllBytes(runtimeCatalog));
        }
    }

    @Test
    void save_atomicMoveUnsupportedAndCleanupFailure_preservePrimaryResult() throws Exception {
        Path runtimeCatalog = tempDirectory.resolve("atomic/catalog.tsv");
        Files.createDirectories(runtimeCatalog.getParent());
        Files.writeString(runtimeCatalog, "CINECLI-CATALOG\t1\n", UTF_8);
        byte[] original = Files.readAllBytes(runtimeCatalog);
        CatalogStorage storage = new CatalogStorage(
                runtimeCatalog,
                DEFAULT_RESOURCE,
                (operation, path) -> {
                    if (operation == CatalogStorageOperation.ATOMIC_REPLACE) {
                        throw new java.nio.file.AtomicMoveNotSupportedException(
                                path.toString(), path.toString(), "simulated");
                    }
                    if (operation == CatalogStorageOperation.DELETE_TEMPORARY) {
                        throw new IOException("cleanup");
                    }
                });

        CatalogStorageException exception = assertThrows(
                CatalogStorageException.class, () -> storage.save(List.of()));

        assertAll(
                () -> assertTrue(exception.getMessage().contains("atomic replacement")),
                () -> assertArrayEquals(original, Files.readAllBytes(runtimeCatalog)));
    }

    @Test
    void transactionSnapshot_targetDisappearsAfterValidatedRead_wrapsCause() throws Exception {
        Path runtimeCatalog = tempDirectory.resolve("snapshot/catalog.tsv");
        Files.createDirectories(runtimeCatalog.getParent());
        Files.writeString(runtimeCatalog, "CINECLI-CATALOG\t1\n", UTF_8);
        CatalogStorage storage = new CatalogStorage(
                runtimeCatalog,
                DEFAULT_RESOURCE,
                (operation, path) -> {
                    if (operation == CatalogStorageOperation.READ_TARGET) {
                        Files.delete(path);
                    }
                });
        CatalogTransactionAdapter adapter = new CatalogTransactionAdapter(storage, runtimeCatalog);

        CatalogStorageException exception = assertThrows(
                CatalogStorageException.class, adapter::loadSnapshot);

        assertTrue(exception.getMessage().contains("snapshot runtime catalog"));
    }

    @Test
    void load_missingRuntimeData_copiesAndLoadsBundledDefaults() throws Exception {
        Path runtimeCatalog = tempDirectory.resolve("data/runtime/catalog.tsv");
        CatalogStorage catalogStorage = new CatalogStorage(runtimeCatalog, DEFAULT_RESOURCE);

        List<Movie> movies = catalogStorage.load();

        assertAll(
                () -> assertTrue(Files.exists(runtimeCatalog)),
                () -> assertFalse(movies.isEmpty()),
                () -> assertEquals("MOV-001", movies.get(0).id()),
                () -> assertArrayEquals(readDefaultCatalog(), Files.readAllBytes(runtimeCatalog)));
    }

    @Test
    void load_existingRuntimeData_usesFileWithoutOverwritingIt() throws Exception {
        String existingCatalog = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-LOCAL\tLocal Only\tM18
                """;
        Path runtimeCatalog = writeRuntimeCatalog(existingCatalog);
        byte[] originalBytes = Files.readAllBytes(runtimeCatalog);

        List<Movie> movies = new CatalogStorage(runtimeCatalog, DEFAULT_RESOURCE).load();

        assertAll(
                () -> assertEquals(List.of("MOV-LOCAL"),
                        movies.stream().map(Movie::id).toList()),
                () -> assertArrayEquals(originalBytes, Files.readAllBytes(runtimeCatalog)));
    }

    @Test
    void load_existingUtf8Data_decodesMovieTitle() throws Exception {
        String existingCatalog = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-UTF8\tÉtoile Meridian\tPG13
                """;
        Path runtimeCatalog = writeRuntimeCatalog(existingCatalog);

        List<Movie> movies = new CatalogStorage(runtimeCatalog, DEFAULT_RESOURCE).load();

        assertEquals("Étoile Meridian", movies.getFirst().title());
    }

    @Test
    void load_existingEmptyValidCatalog_returnsEmptyWithoutSeeding() throws Exception {
        Path runtimeCatalog = writeRuntimeCatalog("CINECLI-CATALOG\t1\n");
        byte[] originalBytes = Files.readAllBytes(runtimeCatalog);

        List<Movie> movies = new CatalogStorage(runtimeCatalog, DEFAULT_RESOURCE).load();

        assertAll(
                () -> assertTrue(movies.isEmpty()),
                () -> assertArrayEquals(originalBytes, Files.readAllBytes(runtimeCatalog)));
    }

    @Test
    void load_existingMalformedCatalog_failsWithoutOverwritingIt() throws IOException {
        Path runtimeCatalog = writeRuntimeCatalog("not a catalog\n");
        byte[] originalBytes = Files.readAllBytes(runtimeCatalog);
        CatalogStorage catalogStorage = new CatalogStorage(runtimeCatalog, DEFAULT_RESOURCE);

        CatalogStorageException exception = assertThrows(
                CatalogStorageException.class, catalogStorage::load);

        assertAll(
                () -> assertTrue(exception.getMessage().contains("Malformed catalog")),
                () -> assertArrayEquals(originalBytes, Files.readAllBytes(runtimeCatalog)));
    }

    @Test
    void load_afterInitialization_isIdempotent() throws CatalogStorageException {
        Path runtimeCatalog = tempDirectory.resolve("data/runtime/catalog.tsv");
        CatalogStorage catalogStorage = new CatalogStorage(runtimeCatalog, DEFAULT_RESOURCE);

        List<Movie> moviesFromFirstLoad = catalogStorage.load();
        List<Movie> moviesFromSecondLoad = catalogStorage.load();

        assertEquals(moviesFromFirstLoad, moviesFromSecondLoad);
    }

    @Test
    void load_missingBundledDefault_failsClearly() {
        Path runtimeCatalog = tempDirectory.resolve("data/runtime/catalog.tsv");
        CatalogStorage catalogStorage = new CatalogStorage(runtimeCatalog, "/missing.tsv");

        CatalogStorageException exception = assertThrows(
                CatalogStorageException.class, catalogStorage::load);

        assertTrue(exception.getMessage().contains("Bundled default catalog"));
    }

    @Test
    void load_readFailure_wrapsCauseWithoutChangingExistingFile() throws Exception {
        Path runtimeCatalog = writeRuntimeCatalog("CINECLI-CATALOG\t1\n");
        byte[] originalBytes = Files.readAllBytes(runtimeCatalog);
        IOException cause = new IOException("simulated read failure");
        CatalogStorage catalogStorage = new CatalogStorage(
                runtimeCatalog,
                DEFAULT_RESOURCE,
                (operation, path) -> {
                    if (operation == CatalogStorageOperation.READ_TARGET) {
                        throw cause;
                    }
                });

        CatalogStorageException exception = assertThrows(
                CatalogStorageException.class, catalogStorage::load);

        assertAll(
                () -> assertEquals(cause, exception.getCause()),
                () -> assertArrayEquals(originalBytes, Files.readAllBytes(runtimeCatalog)));
    }

    @Test
    void load_initializationFailure_wrapsCauseAndLeavesFileMissing() {
        Path runtimeCatalog = tempDirectory.resolve("unavailable/catalog.tsv");
        IOException cause = new IOException("simulated directory failure");
        CatalogStorage catalogStorage = new CatalogStorage(
                runtimeCatalog,
                DEFAULT_RESOURCE,
                (operation, path) -> {
                    if (operation == CatalogStorageOperation.CREATE_DIRECTORIES) {
                        throw cause;
                    }
                });

        CatalogStorageException exception = assertThrows(
                CatalogStorageException.class, catalogStorage::load);

        assertAll(
                () -> assertEquals(cause, exception.getCause()),
                () -> assertFalse(Files.exists(runtimeCatalog)));
    }

    @Test
    void load_concurrentInitialization_usesFileCreatedByOtherCaller() throws Exception {
        Path runtimeCatalog = tempDirectory.resolve("race/catalog.tsv");
        String concurrentCatalog = "CINECLI-CATALOG\t1\n";
        CatalogStorage catalogStorage = new CatalogStorage(
                runtimeCatalog,
                DEFAULT_RESOURCE,
                (operation, path) -> {
                    if (operation == CatalogStorageOperation.COPY_DEFAULT) {
                        Files.writeString(path, concurrentCatalog, UTF_8);
                    }
                });

        List<Movie> movies = catalogStorage.load();

        assertAll(
                () -> assertTrue(movies.isEmpty()),
                () -> assertEquals(concurrentCatalog, Files.readString(runtimeCatalog, UTF_8)));
    }

    private Path writeRuntimeCatalog(String content) throws IOException {
        Path runtimeCatalog = tempDirectory.resolve("catalog.tsv");
        Files.writeString(runtimeCatalog, content, UTF_8);
        return runtimeCatalog;
    }

    private byte[] readDefaultCatalog() throws IOException {
        try (InputStream defaultCatalog = CatalogStorageTest.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            assertNotNull(defaultCatalog);
            return defaultCatalog.readAllBytes();
        }
    }
}
