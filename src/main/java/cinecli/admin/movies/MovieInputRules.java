package cinecli.admin.movies;

import cinecli.model.ContentRating;
import java.util.List;

/** Interprets Movie-specific administrator input without performing terminal I/O. */
final class MovieInputRules {
    private static final List<ContentRating> RATINGS = List.of(
            ContentRating.PG13,
            ContentRating.M18,
            ContentRating.R21);

    /** Represents the result category of validating a submitted Movie title. */
    enum TitleStatus {
        VALID,
        CONTROL_CHARACTERS,
        BLANK
    }

    /**
     * Carries a title-validation result and its normalized title when valid.
     *
     * @param status Validation category.
     * @param title Normalized title, or {@code null} when validation fails.
     */
    record TitleResult(TitleStatus status, String title) {
    }

    /**
     * Validates a Movie title and removes leading and trailing Unicode whitespace.
     *
     * @param input Submitted title text.
     * @return Validation category and normalized title, where applicable.
     */
    TitleResult interpretTitle(String input) {
        if (input.codePoints().anyMatch(Character::isISOControl)) {
            return new TitleResult(TitleStatus.CONTROL_CHARACTERS, null);
        }
        String title = stripUnicodeWhitespace(input);
        return title.isEmpty()
                ? new TitleResult(TitleStatus.BLANK, null)
                : new TitleResult(TitleStatus.VALID, title);
    }

    /**
     * Returns the content rating represented by an administrator menu number.
     *
     * @param menuNumber One-based rating menu number.
     * @return Content rating at the supplied menu number.
     */
    ContentRating contentRatingFor(int menuNumber) {
        return RATINGS.get(menuNumber - 1);
    }

    private String stripUnicodeWhitespace(String input) {
        int start = 0;
        int end = input.length();
        while (start < end) {
            int codePoint = input.codePointAt(start);
            if (!isUnicodeWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = input.codePointBefore(end);
            if (!isUnicodeWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return input.substring(start, end);
    }

    private boolean isUnicodeWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
