package cinecli.storage.exception;

/**
 * Signals that seat occupancy could not be initialized, read, validated, or updated.
 */
public final class SeatStorageException extends StorageException {
    /**
     * Creates an exception with a user-readable explanation.
     *
     * @param message Explanation of the failure.
     */
    public SeatStorageException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a user-readable explanation and its cause.
     *
     * @param message Explanation of the failure.
     * @param cause Underlying failure.
     */
    public SeatStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
