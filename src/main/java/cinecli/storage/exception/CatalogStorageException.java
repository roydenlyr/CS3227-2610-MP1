package cinecli.storage.exception;

/**
 * Signals that the movie catalog could not be initialized, read, or validated.
 */
public final class CatalogStorageException extends Exception {
    /**
     * Creates an exception with a user-readable explanation.
     *
     * @param message Explanation of the failure.
     */
    public CatalogStorageException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a user-readable explanation and its cause.
     *
     * @param message Explanation of the failure.
     * @param cause Underlying failure.
     */
    public CatalogStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
