package cinecli.storage.exception;

/** Signals that a Movie deletion cannot be prepared without changing data. */
public final class MovieDeletionPreparationException extends StorageException {
    /** Creates a preparation exception. */
    public MovieDeletionPreparationException(String message) {
        super(message);
    }

    /** Creates a preparation exception with its cause. */
    public MovieDeletionPreparationException(String message, Throwable cause) {
        super(message, cause);
    }
}
