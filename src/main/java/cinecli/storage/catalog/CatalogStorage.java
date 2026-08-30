package cinecli.storage.catalog;

import static java.nio.charset.StandardCharsets.UTF_8;

import cinecli.model.Movie;
import cinecli.storage.exception.CatalogStorageException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Initializes and reads the runtime movie catalog.
 */
public final class CatalogStorage {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private final Path runtimeCatalogPath;
    private final String defaultCatalogResource;
    private final CatalogStorageOperationHook operationHook;
    private final CatalogParser catalogParser = new CatalogParser();

    /**
     * Creates catalog storage for a runtime path and bundled default resource.
     *
     * @param runtimeCatalogPath Mutable runtime catalog path.
     * @param defaultCatalogResource Classpath resource copied when runtime data is missing.
     */
    public CatalogStorage(Path runtimeCatalogPath, String defaultCatalogResource) {
        this(runtimeCatalogPath, defaultCatalogResource, CatalogStorageOperationHook.NONE);
    }

    /**
     * Creates catalog storage with a hook for controlled storage-operation testing.
     *
     * @param runtimeCatalogPath Mutable runtime catalog path.
     * @param defaultCatalogResource Classpath resource copied when runtime data is missing.
     * @param operationHook Hook invoked before storage operations.
     */
    CatalogStorage(
            Path runtimeCatalogPath,
            String defaultCatalogResource,
            CatalogStorageOperationHook operationHook) {
        this.runtimeCatalogPath = Objects.requireNonNull(runtimeCatalogPath)
                .toAbsolutePath()
                .normalize();
        this.defaultCatalogResource = Objects.requireNonNull(defaultCatalogResource);
        this.operationHook = Objects.requireNonNull(operationHook);
    }

    /**
     * Initializes missing runtime data and loads the complete catalog.
     *
     * @return Movies in persisted display order.
     * @throws CatalogStorageException If initialization, reading, or validation fails.
     */
    public List<Movie> load() throws CatalogStorageException {
        initializeIfMissing();
        try (BufferedReader reader = Files.newBufferedReader(runtimeCatalogPath, UTF_8)) {
            operationHook.before(CatalogStorageOperation.READ_TARGET, runtimeCatalogPath);
            return catalogParser.parse(reader, runtimeCatalogPath.toString());
        } catch (IOException exception) {
            throw new CatalogStorageException(
                    "Unable to read runtime catalog '" + runtimeCatalogPath + "'.", exception);
        }
    }

    /**
     * Atomically saves and validates a complete catalogue state.
     *
     * @param movies Movies in persisted display order.
     * @throws CatalogStorageException If validation or atomic persistence fails.
     */
    public void save(List<Movie> movies) throws CatalogStorageException {
        String serializedCatalog = serialize(movies);
        catalogParser.parse(new StringReader(serializedCatalog), "proposed catalogue");

        Path temporaryPath = null;
        try {
            Path parentDirectory = runtimeCatalogPath.getParent();
            operationHook.before(CatalogStorageOperation.CREATE_DIRECTORIES, parentDirectory);
            Files.createDirectories(parentDirectory);
            operationHook.before(CatalogStorageOperation.CREATE_TEMPORARY, parentDirectory);
            temporaryPath = Files.createTempFile(parentDirectory, "cinecli-catalog-", ".tmp");
            operationHook.before(CatalogStorageOperation.WRITE_TEMPORARY, temporaryPath);
            Files.writeString(
                    temporaryPath,
                    serializedCatalog,
                    UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            operationHook.before(CatalogStorageOperation.FORCE_TEMPORARY, temporaryPath);
            try (FileChannel channel = FileChannel.open(temporaryPath, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            operationHook.before(CatalogStorageOperation.ATOMIC_REPLACE, runtimeCatalogPath);
            Files.move(
                    temporaryPath,
                    runtimeCatalogPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new CatalogStorageException(
                    "Unable to update runtime catalog '" + runtimeCatalogPath
                            + "' because the file system does not support atomic replacement."
                            + " The existing data was preserved.",
                    exception);
        } catch (IOException exception) {
            throw new CatalogStorageException(
                    "Unable to update runtime catalog '" + runtimeCatalogPath
                            + "'. The existing data was preserved.",
                    exception);
        } finally {
            deleteTemporaryFileIfPresent(temporaryPath);
        }
    }

    /**
     * Serializes a complete Movie catalogue in the current tab-separated format.
     *
     * @param movies Movies in persisted display order.
     * @return Serialized catalogue text.
     * @throws CatalogStorageException If the catalogue contains a null value.
     */
    static String serialize(List<Movie> movies) throws CatalogStorageException {
        if (movies == null) {
            throw new CatalogStorageException("Proposed catalogue must not be null.");
        }
        StringBuilder serializedCatalog = new StringBuilder("CINECLI-CATALOG\t1\n");
        for (Movie movie : movies) {
            if (movie == null) {
                throw new CatalogStorageException("Proposed catalogue must not contain null movies.");
            }
            serializedCatalog.append("MOVIE\t")
                    .append(movie.id()).append('\t')
                    .append(movie.title()).append('\t')
                    .append(movie.contentRating()).append('\n');
        }
        for (Movie movie : movies) {
            for (cinecli.model.Screening screening : movie.screenings()) {
                serializedCatalog.append("SCREENING\t")
                        .append(screening.id()).append('\t')
                        .append(movie.id()).append('\t')
                        .append(DATE_FORMATTER.format(screening.startsAt())).append('\t')
                        .append(TIME_FORMATTER.format(screening.startsAt())).append('\n');
            }
        }
        return serializedCatalog.toString();
    }

    private void deleteTemporaryFileIfPresent(Path temporaryPath) {
        if (temporaryPath == null) {
            return;
        }
        try {
            operationHook.before(CatalogStorageOperation.DELETE_TEMPORARY, temporaryPath);
            Files.deleteIfExists(temporaryPath);
        } catch (IOException exception) {
            // Preserve the primary operation result when temporary cleanup fails.
        }
    }

    private void initializeIfMissing() throws CatalogStorageException {
        if (Files.exists(runtimeCatalogPath)) {
            return;
        }

        try {
            Path parentDirectory = runtimeCatalogPath.getParent();
            operationHook.before(CatalogStorageOperation.CREATE_DIRECTORIES, parentDirectory);
            Files.createDirectories(parentDirectory);
            copyDefaultCatalog();
        } catch (IOException exception) {
            throw new CatalogStorageException(
                    "Unable to initialize runtime catalog '" + runtimeCatalogPath + "'.", exception);
        }
    }

    private void copyDefaultCatalog() throws IOException, CatalogStorageException {
        try (InputStream defaultCatalog = CatalogStorage.class.getResourceAsStream(defaultCatalogResource)) {
            if (defaultCatalog == null) {
                throw new CatalogStorageException(
                        "Bundled default catalog '" + defaultCatalogResource + "' is missing.");
            }
            try {
                operationHook.before(CatalogStorageOperation.COPY_DEFAULT, runtimeCatalogPath);
                Files.copy(defaultCatalog, runtimeCatalogPath);
            } catch (FileAlreadyExistsException exception) {
                // Another process initialized the catalog after the existence check.
            }
        }
    }
}
