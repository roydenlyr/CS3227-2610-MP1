package cinecli.storage.transaction;

import cinecli.storage.exception.TransactionStorageException;
import java.util.Objects;

/** Provides the shared recovery gate required before access to affected catalogue data. */
public final class CatalogRecoveryGate {
    private final MovieDeletionTransaction deletionTransaction;

    /** Creates an access gate backed by the catalogue deletion transaction foundation. */
    public CatalogRecoveryGate(MovieDeletionTransaction deletionTransaction) {
        this.deletionTransaction = Objects.requireNonNull(deletionTransaction);
    }

    /** Completes any durable catalogue transaction before affected data is accessed. */
    public boolean recoverBeforeAccess() throws TransactionStorageException {
        return deletionTransaction.recover() == RecoveryResult.RECOVERED;
    }
}
