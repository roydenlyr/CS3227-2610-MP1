package cinecli.storage.transaction;

import cinecli.model.ContentRating;
import cinecli.model.Movie;
import cinecli.model.SeatCoordinate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Opaque, single-use prepared Movie deletion with a public impact preview. */
public final class PreparedMovieDeletion {
    private final Movie movie;
    private final int displayPosition;
    private final List<ScreeningDeletionImpact> screeningImpacts;
    final List<Movie> intendedCatalog;
    final byte[] originalCatalogBytes;
    final byte[] intendedCatalogBytes;
    final TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> originalSeats;
    final TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> intendedSeats;
    boolean isConsumed;

    PreparedMovieDeletion(
            Movie movie,
            int displayPosition,
            List<ScreeningDeletionImpact> screeningImpacts,
            List<Movie> intendedCatalog,
            byte[] originalCatalogBytes,
            byte[] intendedCatalogBytes,
            TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> originalSeats,
            TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> intendedSeats) {
        this.movie = movie;
        this.displayPosition = displayPosition;
        this.screeningImpacts = List.copyOf(screeningImpacts);
        this.intendedCatalog = List.copyOf(intendedCatalog);
        this.originalCatalogBytes = Arrays.copyOf(originalCatalogBytes, originalCatalogBytes.length);
        this.intendedCatalogBytes = Arrays.copyOf(intendedCatalogBytes, intendedCatalogBytes.length);
        this.originalSeats = originalSeats;
        this.intendedSeats = intendedSeats;
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
