package cinecli.storage.exception;

import java.util.Objects;

/** Signals a confirmed Screening deletion failure with an explicit durable-intent status. */
public final class ScreeningDeletionCommitException extends StorageException {
    /** Describes whether durable deletion intent exists. */
    public enum Status {
        NOT_APPLIED,
        RECOVERY_PENDING
    }

    private final Status status;

    /** Creates a commit exception. */
    public ScreeningDeletionCommitException(Status status, String message, Throwable cause) {
        super(message, cause);
        this.status = Objects.requireNonNull(status);
    }

    /** Returns the durable-intent status. */
    public Status status() {
        return status;
    }
}
