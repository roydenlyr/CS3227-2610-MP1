package cinecli.storage.exception;

/**
 * Signals a checked failure while accessing shared CineCLI persistence.
 */
public abstract class StorageException extends Exception {
    /**
     * Creates a storage exception with a user-readable explanation.
     *
     * @param message Explanation of the failure.
     */
    protected StorageException(String message) {
        super(message);
    }

    /**
     * Creates a storage exception with a user-readable explanation and its cause.
     *
     * @param message Explanation of the failure.
     * @param cause Underlying failure.
     */
    protected StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
