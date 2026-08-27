package cinecli.storage.exception;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StorageExceptionTest {
    @Test
    void constructors_withCause_preserveMessageAndCause() {
        RuntimeException cause = new RuntimeException("root cause");
        CatalogStorageException catalogException =
                new CatalogStorageException("catalog failed", cause);
        SeatStorageException seatException = new SeatStorageException("seats failed", cause);

        assertAll(
                () -> assertEquals("catalog failed", catalogException.getMessage()),
                () -> assertEquals(cause, catalogException.getCause()),
                () -> assertEquals("seats failed", seatException.getMessage()),
                () -> assertEquals(cause, seatException.getCause()));
    }
}
