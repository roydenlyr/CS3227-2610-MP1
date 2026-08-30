package cinecli.storage.exception;

import java.util.Objects;

/** Signals a confirmed deletion failure with an explicit durable-intent status. */
public final class MovieDeletionCommitException extends StorageException {
    /** Describes whether durable deletion intent exists. */
    public enum Status {
        NOT_APPLIED,
        RECOVERY_PENDING
    }

    private final Status status;

    /** Creates a commit exception. */
    public MovieDeletionCommitException(Status status, String message) {
        super(message);
        this.status = Objects.requireNonNull(status);
    }

    /** Creates a commit exception with its cause. */
    public MovieDeletionCommitException(Status status, String message, Throwable cause) {
        super(message, cause);
        this.status = Objects.requireNonNull(status);
    }

    /** Returns the durable-intent status. */
    public Status status() {
        return status;
    }
}
