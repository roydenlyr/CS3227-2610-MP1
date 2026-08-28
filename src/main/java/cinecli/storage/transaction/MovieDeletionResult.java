package cinecli.storage.transaction;

import java.util.Objects;

/** Immutable values used to report a completed Movie deletion. */
public record MovieDeletionResult(
        String movieId, String title, int screeningCount, int occupiedSeatCount) {
    /** Creates a deletion result. */
    public MovieDeletionResult {
        Objects.requireNonNull(movieId);
        Objects.requireNonNull(title);
        if (screeningCount < 0 || occupiedSeatCount < 0) {
            throw new IllegalArgumentException("deletion counts must not be negative");
        }
    }
}
