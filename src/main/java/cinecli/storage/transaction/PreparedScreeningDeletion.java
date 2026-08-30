package cinecli.storage.transaction;

import cinecli.model.Movie;
import cinecli.model.Screening;
import cinecli.model.SeatCoordinate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Opaque, single-use prepared Screening deletion with a public impact preview. */
public final class PreparedScreeningDeletion extends PreparedCatalogDeletion {
    private final Movie parentMovie;
    private final Screening screening;
    private final int displayPosition;
    private final int occupiedSeatCount;

    PreparedScreeningDeletion(
            Movie parentMovie,
            Screening screening,
            int displayPosition,
            int occupiedSeatCount,
            List<Movie> intendedCatalog,
            byte[] originalCatalogBytes,
            byte[] intendedCatalogBytes,
            TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> originalSeats,
            TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> intendedSeats) {
        super(intendedCatalog, originalCatalogBytes, intendedCatalogBytes, originalSeats, intendedSeats);
        this.parentMovie = Objects.requireNonNull(parentMovie);
        this.screening = Objects.requireNonNull(screening);
        if (displayPosition < 1 || occupiedSeatCount < 0) {
            throw new IllegalArgumentException("screening deletion values are invalid");
        }
        this.displayPosition = displayPosition;
        this.occupiedSeatCount = occupiedSeatCount;
    }

    /**
     * Returns the identifier of the Screening scheduled for deletion.
     *
     * @return Screening identifier.
     */
    public String screeningId() {
        return screening.id();
    }

    /**
     * Returns the identifier of the Screening's parent Movie.
     *
     * @return Parent Movie identifier.
     */
    public String parentMovieId() {
        return parentMovie.id();
    }

    /**
     * Returns the title of the Screening's parent Movie.
     *
     * @return Parent Movie title.
     */
    public String parentMovieTitle() {
        return parentMovie.title();
    }

    /**
     * Returns the scheduled start time of the Screening.
     *
     * @return Screening start time.
     */
    public java.time.LocalDateTime startsAt() {
        return screening.startsAt();
    }

    /**
     * Returns the one-based position shown for the Screening in its parent Movie's list.
     *
     * @return One-based display position.
     */
    public int displayPosition() {
        return displayPosition;
    }

    /**
     * Returns the number of occupied seats that the deletion would release.
     *
     * @return Number of occupied seats.
     */
    public int occupiedSeatCount() {
        return occupiedSeatCount;
    }
}
