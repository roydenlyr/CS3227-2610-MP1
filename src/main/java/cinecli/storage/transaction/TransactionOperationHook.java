package cinecli.storage.transaction;

import java.io.IOException;
import java.nio.file.Path;

/** Injects deterministic file failures at transaction durability boundaries. */
@FunctionalInterface
interface TransactionOperationHook {
    TransactionOperationHook NONE = (operation, path) -> { };

    /** Runs immediately before one typed transaction operation. */
    void before(TransactionOperation operation, Path path) throws IOException;
}
