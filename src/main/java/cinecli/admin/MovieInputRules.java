package cinecli.admin;

import cinecli.model.ContentRating;
import java.util.List;

/** Interprets Movie-specific administrator input without performing terminal I/O. */
final class MovieInputRules {
    private static final List<ContentRating> RATINGS = List.of(
            ContentRating.PG13,
            ContentRating.M18,
            ContentRating.R21);

    enum TitleStatus {
        VALID,
        CONTROL_CHARACTERS,
        BLANK
    }

    record TitleResult(TitleStatus status, String title) {
    }

    TitleResult interpretTitle(String input) {
        if (input.codePoints().anyMatch(Character::isISOControl)) {
            return new TitleResult(TitleStatus.CONTROL_CHARACTERS, null);
        }
        String title = stripUnicodeWhitespace(input);
        return title.isEmpty()
                ? new TitleResult(TitleStatus.BLANK, null)
                : new TitleResult(TitleStatus.VALID, title);
    }

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
