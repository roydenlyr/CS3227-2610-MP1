package cinecli.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents a movie and its available screenings.
 */
public record Movie(
        String id,
        String title,
        ContentRating contentRating,
        List<Screening> screenings) {

    /**
     * Creates an immutable movie.
     *
     * @param id Stable movie identifier.
     * @param title Display title.
     * @param contentRating Display-only content rating.
     * @param screenings Available screenings in display order.
     */
    public Movie(String id, String title, ContentRating contentRating, List<Screening> screenings) {
        this.id = requireText(id, "id");
        this.title = requireText(title, "title");
        this.contentRating = Objects.requireNonNull(contentRating);
        this.screenings = List.copyOf(screenings);
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
