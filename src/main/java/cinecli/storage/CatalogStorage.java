package cinecli.storage;

import static java.nio.charset.StandardCharsets.UTF_8;

import cinecli.model.Movie;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Initializes and reads the runtime movie catalog.
 */
public final class CatalogStorage {
    private final Path runtimeCatalogPath;
    private final String defaultCatalogResource;
    private final CatalogParser catalogParser = new CatalogParser();

    /**
     * Creates catalog storage for a runtime path and bundled default resource.
     *
     * @param runtimeCatalogPath Mutable runtime catalog path.
     * @param defaultCatalogResource Classpath resource copied when runtime data is missing.
     */
    public CatalogStorage(Path runtimeCatalogPath, String defaultCatalogResource) {
        this.runtimeCatalogPath = Objects.requireNonNull(runtimeCatalogPath);
        this.defaultCatalogResource = Objects.requireNonNull(defaultCatalogResource);
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
            return catalogParser.parse(reader, runtimeCatalogPath.toString());
        } catch (IOException exception) {
            throw new CatalogStorageException(
                    "Unable to read runtime catalog '" + runtimeCatalogPath + "'.", exception);
        }
    }

    private void initializeIfMissing() throws CatalogStorageException {
        if (Files.exists(runtimeCatalogPath)) {
            return;
        }

        try {
            Path parentDirectory = runtimeCatalogPath.getParent();
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }
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
                Files.copy(defaultCatalog, runtimeCatalogPath);
            } catch (FileAlreadyExistsException exception) {
                // Another process initialized the catalog after the existence check.
            }
        }
    }
}
