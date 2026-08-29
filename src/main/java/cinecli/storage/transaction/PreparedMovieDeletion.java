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

    public String movieId() {
        return movie.id();
    }

    public String title() {
        return movie.title();
    }

    public ContentRating contentRating() {
        return movie.contentRating();
    }

    public int displayPosition() {
        return displayPosition;
    }

    public List<ScreeningDeletionImpact> screeningImpacts() {
        return screeningImpacts;
    }

    public int screeningCount() {
        return screeningImpacts.size();
    }

    public int occupiedSeatCount() {
        return screeningImpacts.stream().mapToInt(ScreeningDeletionImpact::occupiedSeatCount).sum();
    }

    public boolean isCascading() {
        return !screeningImpacts.isEmpty();
    }
}
