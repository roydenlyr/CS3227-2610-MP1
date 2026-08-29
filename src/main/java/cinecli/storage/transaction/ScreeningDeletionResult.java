package cinecli.storage.transaction;

import java.time.LocalDateTime;
import java.util.Objects;

/** Immutable values used to report a completed Screening deletion. */
public record ScreeningDeletionResult(
        String screeningId,
        String parentMovieId,
        String parentMovieTitle,
        LocalDateTime startsAt,
        int occupiedSeatCount) {
    /** Creates a deletion result. */
    public ScreeningDeletionResult {
        Objects.requireNonNull(screeningId);
        Objects.requireNonNull(parentMovieId);
        Objects.requireNonNull(parentMovieTitle);
        Objects.requireNonNull(startsAt);
        if (occupiedSeatCount < 0) {
            throw new IllegalArgumentException("occupied seat count must not be negative");
        }
    }
}
