package cinecli.admin;

import cinecli.model.ContentRating;
import cinecli.model.Movie;
import cinecli.storage.transaction.MovieDeletionResult;
import cinecli.storage.transaction.PreparedMovieDeletion;
import cinecli.storage.transaction.ScreeningDeletionImpact;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Renders complete administrator-visible Movie-management output blocks. */
final class MovieManagementText {
    static final String ACCESS_FAILURE_PREFIX = "Unable to access movie management: ";
    static final String ACTION_ERROR_WITH_PROMPT = "Enter 0, 1, 2, or 3.\nEnter choice:\n";
    static final String EDIT_MENU_ERROR = "Enter 0, 1, 2, or 3.\n";
    static final String EMPTY_EDIT = "There are no movies to edit.\n";
    static final String EMPTY_DELETE = "There are no movies to delete.\n";
    static final String RECOVERY_COMPLETED = "Pending movie deletion recovery completed.\n";
    static final String ADD_CANCELLED = "Movie addition cancelled.\n";
    static final String EDIT_CANCELLED = "Movie edit cancelled.\n";
    static final String DELETE_CANCELLED = "Movie deletion cancelled.\n";
    static final String ADD_CONFIRMATION_PROMPT = "Confirm add? (Y/N):\n";
    static final String EDIT_CONFIRMATION_PROMPT = "Confirm edit? (Y/N):\n";
    static final String DELETE_CONFIRMATION_PROMPT = "Confirm delete? (Y/N):\n";
    static final String CHANGE_NOT_SAVED_PREFIX = "Movie change was not saved: ";
    static final String TITLE_UNCHANGED = "The movie title is unchanged.\n";
    static final String RATING_UNCHANGED = "The movie rating is unchanged.\n";
    static final String NO_CHANGES = "No movie changes have been made.\n";
    static final String DELETION_PREPARATION_FAILURE_PREFIX =
            "Unable to prepare movie deletion: ";
    static final String DELETION_NOT_APPLIED_PREFIX = "Movie deletion was not applied: ";
    static final String DELETION_RECOVERY_PENDING_PREFIX =
            "Movie deletion is confirmed but recovery is pending: ";
    static final String TITLE_PROMPT = "Enter movie title (/cancel to cancel):\n";
    static final String TITLE_CONTROL_ERROR =
            "Movie title must not contain tabs or control characters.\n";
    static final String TITLE_BLANK_ERROR = "Movie title must not be blank.\n";
    static final String RATING_PROMPT = "Content Ratings\n1. PG13\n2. M18\n3. R21\n"
            + "Enter rating number (/cancel to cancel):\n";
    static final String RATING_ERROR = "Enter 1 for PG13, 2 for M18, or 3 for R21.\n";
    static final String CONFIRMATION_ERROR = "Enter Y to confirm or N to cancel.\n";

    private static final DateTimeFormatter SCREENING_TIME = DateTimeFormatter
            .ofPattern("dd MMM uuuu, HH:mm", Locale.ENGLISH);

    String management(List<Movie> movies) {
        StringBuilder screen = new StringBuilder("Movie Management\n\nMovies\n");
        if (movies.isEmpty()) {
            screen.append("No movies are currently available.\n");
        } else {
            for (int i = 0; i < movies.size(); i++) {
                Movie movie = movies.get(i);
                screen.append(i + 1).append(". ").append(movie.title()).append('\n')
                        .append("   ID: ").append(movie.id()).append('\n')
                        .append("   Rating: ").append(movie.contentRating()).append('\n')
                        .append("   Screenings: ").append(movie.screenings().size()).append('\n');
            }
        }
        return screen.append("\nActions\n1. Add movie\n2. Edit movie\n3. Delete movie\n"
                + "0. Back\nEnter choice:\n").toString();
    }

    String addPreview(String id, String title, ContentRating rating, int position) {
        return "Add Movie Preview\n"
                + "ID: " + id + "\n"
                + "Title: " + title + "\n"
                + "Rating: " + rating + "\n"
                + "Display position: " + position + "\n"
                + "Confirm add? (Y/N):\n";
    }

    String editMenu(
            Movie movie, int position, String title, ContentRating rating) {
        return "Edit Movie\nID: " + movie.id() + "\nDisplay position: " + position
                + "\nTitle: " + title + "\nRating: " + rating
                + "\n\n1. Change title\n2. Change rating\n3. Review changes\n"
                + "0. Cancel edit\nEnter choice:\n";
    }

    String editPreview(
            Movie original, int position, String title, ContentRating rating) {
        String titleLine = title.equals(original.title())
                ? title + " (unchanged)" : original.title() + " -> " + title;
        String ratingLine = rating == original.contentRating()
                ? rating + " (unchanged)" : original.contentRating() + " -> " + rating;
        return "Edit Movie Preview\nID: " + original.id() + "\nDisplay position: " + position
                + "\nTitle: " + titleLine + "\nRating: " + ratingLine
                + "\nConfirm edit? (Y/N):\n";
    }

    String deletePreview(PreparedMovieDeletion deletion) {
        StringBuilder preview = new StringBuilder("Delete Movie Preview\n")
                .append("ID: ").append(deletion.movieId()).append('\n')
                .append("Title: ").append(deletion.title()).append('\n')
                .append("Rating: ").append(deletion.contentRating()).append('\n')
                .append("Display position: ").append(deletion.displayPosition()).append('\n')
                .append("Screenings to delete: ").append(deletion.screeningCount()).append('\n');
        int number = 1;
        for (ScreeningDeletionImpact impact : deletion.screeningImpacts()) {
            preview.append(number++).append(". ").append(impact.screeningId()).append(" - ")
                    .append(SCREENING_TIME.format(impact.startsAt()))
                    .append(" - Occupied seats: ").append(impact.occupiedSeatCount()).append('\n');
        }
        preview.append("Total occupied seats to delete: ")
                .append(deletion.occupiedSeatCount()).append('\n');
        if (deletion.isCascading()) {
            preview.append("WARNING: Deleting this movie also deletes all listed screenings "
                    + "and their occupied-seat data.\n");
        } else {
            preview.append("This movie has no screenings or occupied seats.\n");
        }
        return preview.append("Confirm delete? (Y/N):\n").toString();
    }

    String targetPrompt(String action) {
        return "Enter movie number to " + action + " (0 to go back):\n";
    }

    String targetError(int count) {
        return "Enter a movie number from 1 to " + count + ", or 0 to go back.\n";
    }

    String added(String title, String id) {
        return "Movie added: " + title + " [" + id + "].\n";
    }

    String updated(String title, String id) {
        return "Movie updated: " + title + " [" + id + "].\n";
    }

    String deleted(MovieDeletionResult result) {
        return "Movie deleted: " + result.title() + " [" + result.movieId()
                + "]; removed " + result.screeningCount() + " screening(s) and "
                + result.occupiedSeatCount() + " occupied seat(s).\n";
    }

    String storageFailure(String prefix, Exception exception) {
        return prefix + exception.getMessage() + "\n";
    }
}
