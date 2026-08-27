package cinecli.storage;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Provides a test seam at individual file-system operations.
 */
@FunctionalInterface
interface StorageOperationHook {
    StorageOperationHook NONE = (operation, path) -> { };

    /**
     * Runs immediately before a named file operation.
     *
     * @param operation Stable operation name.
     * @param path Operation target.
     * @throws IOException If the operation should fail.
     */
    void before(String operation, Path path) throws IOException;
}
