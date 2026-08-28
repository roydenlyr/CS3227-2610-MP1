package cinecli.storage.transaction;

import java.time.LocalDateTime;
import java.util.Objects;

/** Administrator-visible impact for one child screening. */
public record ScreeningDeletionImpact(String screeningId, LocalDateTime startsAt, int occupiedSeatCount) {
    /** Creates an immutable impact entry. */
    public ScreeningDeletionImpact {
        Objects.requireNonNull(screeningId);
        Objects.requireNonNull(startsAt);
        if (occupiedSeatCount < 0) {
            throw new IllegalArgumentException("occupied seat count must not be negative");
        }
    }
}
