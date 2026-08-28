package cinecli.storage.catalog;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Provides an internal test seam at individual catalogue file-system operations.
 */
@FunctionalInterface
interface CatalogStorageOperationHook {
    CatalogStorageOperationHook NONE = (operation, path) -> { };

    /**
     * Runs immediately before a named file operation.
     *
     * @param operation Typed file-system operation.
     * @param path Operation target.
     * @throws IOException If the operation should fail.
     */
    void before(CatalogStorageOperation operation, Path path) throws IOException;
}
