package cinecli.storage.exception;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

class StorageExceptionTest {
    @Test
    void constructors_withCause_preserveMessageAndCause() {
        RuntimeException cause = new RuntimeException("root cause");
        CatalogStorageException catalogException =
                new CatalogStorageException("catalog failed", cause);
        SeatStorageException seatException = new SeatStorageException("seats failed", cause);
        TransactionStorageException transactionException =
                new TransactionStorageException("transaction failed", cause);
        TransactionStorageException transactionWithoutCause =
                new TransactionStorageException("transaction only");
        MovieDeletionCommitException commitException = new MovieDeletionCommitException(
                MovieDeletionCommitException.Status.NOT_APPLIED, "commit failed");

        assertAll(
                () -> assertEquals("catalog failed", catalogException.getMessage()),
                () -> assertEquals(cause, catalogException.getCause()),
                () -> assertEquals("seats failed", seatException.getMessage()),
                () -> assertEquals(cause, seatException.getCause()),
                () -> assertEquals(cause, transactionException.getCause()),
                () -> assertEquals("transaction only", transactionWithoutCause.getMessage()),
                () -> assertEquals("commit failed", commitException.getMessage()),
                () -> assertEquals(
                        MovieDeletionCommitException.Status.NOT_APPLIED, commitException.status()),
                () -> assertInstanceOf(StorageException.class, catalogException),
                () -> assertInstanceOf(StorageException.class, seatException));
    }
}
