package cinecli.storage.exception;

/** Signals that a durable catalogue transaction cannot be recovered safely. */
public final class TransactionStorageException extends StorageException {
    /** Creates a transaction storage exception. */
    public TransactionStorageException(String message) {
        super(message);
    }

    /** Creates a transaction storage exception with its cause. */
    public TransactionStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
