package cinecli.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogModelValidationTest {
    @Test
    void movie_blankIdentityAndTitle_rejectsBoundaryPartition() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Movie(" ", "Title", ContentRating.PG13, List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Movie("MOV-1", " ", ContentRating.PG13, List.of())));
    }

    @Test
    void screening_blankIdentity_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Screening(" ", LocalDateTime.of(2026, 1, 1, 10, 0)));
    }
}
