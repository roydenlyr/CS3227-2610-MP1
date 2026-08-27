package cinecli.storage.catalog;

import static java.nio.charset.StandardCharsets.UTF_8;

import cinecli.model.Movie;
import cinecli.storage.exception.CatalogStorageException;
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
            operationHook.before("read", runtimeCatalogPath);
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
            operationHook.before("create-directories", parentDirectory);
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
                operationHook.before("copy-default", runtimeCatalogPath);
                Files.copy(defaultCatalog, runtimeCatalogPath);
            } catch (FileAlreadyExistsException exception) {
                // Another process initialized the catalog after the existence check.
            }
        }
    }
}
