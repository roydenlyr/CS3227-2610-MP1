package cinecli.storage.exception;

/**
 * Signals that pricing data could not be initialized, read, validated, or saved.
 */
public final class PricingStorageException extends StorageException {
    /**
     * Creates an exception with a user-readable explanation.
     *
     * @param message Explanation of the failure.
     */
    public PricingStorageException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a user-readable explanation and its cause.
     *
     * @param message Explanation of the failure.
     * @param cause Underlying failure.
     */
    public PricingStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
