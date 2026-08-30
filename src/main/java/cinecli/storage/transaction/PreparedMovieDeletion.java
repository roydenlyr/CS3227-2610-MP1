package cinecli.storage.transaction;

import cinecli.model.ContentRating;
import cinecli.model.Movie;
import cinecli.model.SeatCoordinate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Opaque, single-use prepared Movie deletion with a public impact preview. */
public final class PreparedMovieDeletion extends PreparedCatalogDeletion {
    private final Movie movie;
    private final int displayPosition;
    private final List<ScreeningDeletionImpact> screeningImpacts;
    PreparedMovieDeletion(
            Movie movie,
            int displayPosition,
            List<ScreeningDeletionImpact> screeningImpacts,
            List<Movie> intendedCatalog,
            byte[] originalCatalogBytes,
            byte[] intendedCatalogBytes,
            TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> originalSeats,
            TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> intendedSeats) {
        super(intendedCatalog, originalCatalogBytes, intendedCatalogBytes, originalSeats, intendedSeats);
        this.movie = movie;
        this.displayPosition = displayPosition;
        this.screeningImpacts = List.copyOf(screeningImpacts);
    }

    /**
     * Returns the identifier of the Movie scheduled for deletion.
     *
     * @return Movie identifier.
     */
    public String movieId() {
        return movie.id();
    }

    /**
     * Returns the title of the Movie scheduled for deletion.
     *
     * @return Movie title.
     */
    public String title() {
        return movie.title();
    }

    /**
     * Returns the content rating of the Movie scheduled for deletion.
     *
     * @return Movie content rating.
     */
    public ContentRating contentRating() {
        return movie.contentRating();
    }

    /**
     * Returns the one-based position shown for the Movie in the management list.
     *
     * @return One-based display position.
     */
    public int displayPosition() {
        return displayPosition;
    }

    /**
     * Returns the immutable deletion impacts for the Movie's screenings.
     *
     * @return Screening deletion impacts in display order.
     */
    public List<ScreeningDeletionImpact> screeningImpacts() {
        return screeningImpacts;
    }

    /**
     * Returns the number of screenings that the deletion would remove.
     *
     * @return Number of affected screenings.
     */
    public int screeningCount() {
        return screeningImpacts.size();
    }

    /**
     * Returns the total occupied seats across screenings that the deletion would remove.
     *
     * @return Total number of occupied seats.
     */
    public int occupiedSeatCount() {
        return screeningImpacts.stream().mapToInt(ScreeningDeletionImpact::occupiedSeatCount).sum();
    }

    /**
     * Returns whether deleting the Movie would also delete one or more screenings.
     *
     * @return Whether the deletion is cascading.
     */
    public boolean isCascading() {
        return !screeningImpacts.isEmpty();
    }
}
