package cinecli.storage;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.model.Movie;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogStorageTest {
    private static final String DEFAULT_RESOURCE = "/cinecli/default-catalog.tsv";

    @TempDir
    Path tempDirectory;

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
