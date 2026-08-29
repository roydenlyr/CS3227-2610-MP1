package cinecli.storage.pricing;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Provides an internal test seam at individual pricing file-system operations.
 */
@FunctionalInterface
interface PricingStorageOperationHook {
    PricingStorageOperationHook NONE = (operation, path) -> { };

    /**
     * Runs immediately before a named file operation.
     *
     * @param operation Typed file-system operation.
     * @param path Operation target.
     * @throws IOException If the operation should fail.
     */
    void before(PricingStorageOperation operation, Path path) throws IOException;
}
