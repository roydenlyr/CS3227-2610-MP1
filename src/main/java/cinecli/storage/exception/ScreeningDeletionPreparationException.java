package cinecli.storage.exception;

/** Signals that a Screening deletion cannot be prepared without changing data. */
public final class ScreeningDeletionPreparationException extends StorageException {
    /** Creates a preparation exception. */
    public ScreeningDeletionPreparationException(String message) {
        super(message);
    }

    /** Creates a preparation exception with its cause. */
    public ScreeningDeletionPreparationException(String message, Throwable cause) {
        super(message, cause);
    }
}
