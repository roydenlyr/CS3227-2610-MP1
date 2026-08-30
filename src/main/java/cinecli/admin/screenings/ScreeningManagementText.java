package cinecli.admin.screenings;

import cinecli.model.Movie;
import cinecli.model.Screening;
import cinecli.storage.transaction.PreparedScreeningDeletion;
import cinecli.storage.transaction.ScreeningDeletionResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Renders complete administrator-visible Screening-management output blocks. */
final class ScreeningManagementText {
    static final String ACCESS_FAILURE_PREFIX = "Unable to access screening management: ";
    static final String ACTION_ERROR_WITH_PROMPT = "Enter 0, 1, 2, or 3.\nEnter choice:\n";
    static final String EDIT_MENU_ERROR = "Enter 0, 1, 2, or 3.\n";
    static final String EMPTY_ADD = "There are no movies available to add a screening.\n";
    static final String EMPTY_EDIT = "There are no screenings to edit.\n";
    static final String EMPTY_DELETE = "There are no screenings to delete.\n";
    static final String RECOVERY_COMPLETED = "Pending catalogue deletion recovery completed.\n";
    static final String ADD_CANCELLED = "Screening addition cancelled.\n";
    static final String EDIT_CANCELLED = "Screening edit cancelled.\n";
    static final String DELETE_CANCELLED = "Screening deletion cancelled.\n";
    static final String ADD_CONFIRMATION_PROMPT = "Confirm add? (Y/N):\n";
    static final String EDIT_CONFIRMATION_PROMPT = "Confirm edit? (Y/N):\n";
    static final String DELETE_CONFIRMATION_PROMPT = "Confirm delete? (Y/N):\n";
    static final String CHANGE_NOT_SAVED_PREFIX = "Screening change was not saved: ";
    static final String DATE_ERROR = "Enter a real date in yyyy-MM-dd format.\n";
    static final String TIME_ERROR = "Enter a real time in HH:mm format.\n";
    static final String DATE_UNCHANGED = "The screening date is unchanged.\n";
    static final String TIME_UNCHANGED = "The screening time is unchanged.\n";
    static final String NO_CHANGES = "No screening changes have been made.\n";
    static final String DELETION_PREPARATION_FAILURE_PREFIX =
            "Unable to prepare screening deletion: ";
    static final String DELETION_NOT_APPLIED_PREFIX = "Screening deletion was not applied: ";
    static final String DELETION_RECOVERY_PENDING_PREFIX =
            "Screening deletion is confirmed but recovery is pending: ";
    static final String DATE_PROMPT = "Enter screening date (yyyy-MM-dd, /cancel to cancel):\n";
    static final String TIME_PROMPT = "Enter screening time (HH:mm, /cancel to cancel):\n";
    static final String CONFIRMATION_ERROR = "Enter Y to confirm or N to cancel.\n";

    private static final DateTimeFormatter SCREENING_TIME = DateTimeFormatter
            .ofPattern("dd MMM uuuu, HH:mm", Locale.ENGLISH);

    /**
     * Returns the Screening management screen.
     *
     * @param screenings Screening locations in display order.
     * @return Complete management screen.
     */
    String management(List<ScreeningLocation> screenings) {
        StringBuilder screen = new StringBuilder("Screening Management\n\nScreenings\n");
        if (screenings.isEmpty()) {
            screen.append("No screenings are currently available.\n");
        } else {
            for (ScreeningLocation location : screenings) {
                screen.append(location.displayPosition()).append(". ")
                        .append(location.movie().title()).append('\n')
                        .append("   Movie ID: ").append(location.movie().id()).append('\n')
                        .append("   Screening ID: ").append(location.screening().id()).append('\n')
                        .append("   Starts at: ").append(SCREENING_TIME.format(
                                location.screening().startsAt())).append('\n');
            }
        }
        return screen.append("\nActions\n1. Add screening\n2. Edit screening\n3. Delete screening\n"
                + "0. Back\nEnter choice:\n").toString();
    }

    /**
     * Returns the parent-Movie selection screen for a proposed Screening addition.
     *
     * @param movies Selectable Movies in display order.
     * @return Complete parent-selection screen.
     */
    String parentSelection(List<Movie> movies) {
        StringBuilder selection = new StringBuilder("Select Parent Movie\n");
        for (int i = 0; i < movies.size(); i++) {
            Movie movie = movies.get(i);
            selection.append(i + 1).append(". ").append(movie.title()).append('\n')
                    .append("   ID: ").append(movie.id()).append('\n');
        }
        return selection.append("Enter parent movie number (0 to cancel):\n").toString();
    }

    /**
     * Returns the confirmation preview for a proposed Screening addition.
     *
     * @param movie Proposed parent Movie.
     * @param id Proposed Screening identifier.
     * @param date Proposed Screening date.
     * @param time Proposed Screening time.
     * @param position Proposed one-based display position.
     * @return Complete addition preview.
     */
    String addPreview(Movie movie, String id, LocalDate date, LocalTime time, int position) {
        return "Add Screening Preview\n"
                + "Parent movie: " + movie.title() + " [" + movie.id() + "]\n"
                + "Screening ID: " + id + "\n"
                + "Starts at: " + SCREENING_TIME.format(LocalDateTime.of(date, time)) + "\n"
                + "Display position: " + position + "\n"
                + "Confirm add? (Y/N):\n";
    }

    /**
     * Returns the edit menu for a Screening and its tentative schedule.
     *
     * @param location Original Screening location.
     * @param date Tentative Screening date.
     * @param time Tentative Screening time.
     * @return Complete edit menu.
     */
    String editMenu(ScreeningLocation location, LocalDate date, LocalTime time) {
        return "Edit Screening\n"
                + "Parent movie: " + location.movie().title() + " [" + location.movie().id() + "]\n"
                + "Screening ID: " + location.screening().id() + "\n"
                + "Display position: " + location.displayPosition() + "\n"
                + "Date: " + date + "\n"
                + "Time: " + time + "\n\n"
                + "1. Change date\n2. Change time\n3. Review changes\n"
                + "0. Cancel edit\nEnter choice:\n";
    }

    /**
     * Returns the confirmation preview for proposed Screening schedule changes.
     *
     * @param location Original Screening location.
     * @param date Tentative Screening date.
     * @param time Tentative Screening time.
     * @return Complete edit preview.
     */
    String editPreview(ScreeningLocation location, LocalDate date, LocalTime time) {
        LocalDateTime original = location.screening().startsAt();
        String dateLine = date.equals(original.toLocalDate())
                ? date + " (unchanged)" : original.toLocalDate() + " -> " + date;
        String timeLine = time.equals(original.toLocalTime())
                ? time + " (unchanged)" : original.toLocalTime() + " -> " + time;
        return "Edit Screening Preview\n"
                + "Parent movie: " + location.movie().title() + " [" + location.movie().id() + "]\n"
                + "Screening ID: " + location.screening().id() + "\n"
                + "Display position: " + location.displayPosition() + "\n"
                + "Date: " + dateLine + "\n"
                + "Time: " + timeLine + "\n"
                + "Occupancy will be preserved.\n"
                + "Confirm edit? (Y/N):\n";
    }

    /**
     * Returns the confirmation preview for a prepared Screening deletion.
     *
     * @param deletion Prepared deletion and its impact.
     * @return Complete deletion preview.
     */
    String deletePreview(PreparedScreeningDeletion deletion) {
        return "Delete Screening Preview\n"
                + "Parent movie: " + deletion.parentMovieTitle() + " ["
                + deletion.parentMovieId() + "]\n"
                + "Screening ID: " + deletion.screeningId() + "\n"
                + "Starts at: " + SCREENING_TIME.format(deletion.startsAt()) + "\n"
                + "Display position: " + deletion.displayPosition() + "\n"
                + "Occupied seats to delete: " + deletion.occupiedSeatCount() + "\n"
                + "Confirm delete? (Y/N):\n";
    }

    /**
     * Returns the prompt for selecting a Screening for an action.
     *
     * @param action Action to describe.
     * @return Screening-selection prompt.
     */
    String targetPrompt(String action) {
        return "Enter screening number to " + action + " (0 to go back):\n";
    }

    /**
     * Returns the validation message for an invalid Screening selection.
     *
     * @param count Number of selectable Screenings.
     * @return Selection-error message.
     */
    String targetError(int count) {
        return "Enter a screening number from 1 to " + count + ", or 0 to go back.\n";
    }

    /**
     * Returns the validation message for an invalid parent-Movie selection.
     *
     * @param count Number of selectable Movies.
     * @return Parent-selection error message.
     */
    String parentError(int count) {
        return "Enter a movie number from 1 to " + count + ", or 0 to cancel.\n";
    }

    /**
     * Returns the success message for an added Screening.
     *
     * @param movie Parent Movie of the added Screening.
     * @param id Added Screening identifier.
     * @return Addition-success message.
     */
    String added(Movie movie, String id) {
        return "Screening added: " + movie.title() + " [" + id + "].\n";
    }

    /**
     * Returns the success message for an updated Screening.
     *
     * @param location Updated Screening location.
     * @return Update-success message.
     */
    String updated(ScreeningLocation location) {
        return "Screening updated: " + location.movie().title() + " ["
                + location.screening().id() + "].\n";
    }

    /**
     * Returns the success message for a committed Screening deletion.
     *
     * @param result Committed deletion result.
     * @return Deletion-success message.
     */
    String deleted(ScreeningDeletionResult result) {
        return "Screening deleted: " + result.parentMovieTitle() + " ["
                + result.screeningId() + "]; removed " + result.occupiedSeatCount()
                + " occupied seat(s).\n";
    }

    /**
     * Returns a storage-failure message with the supplied context and cause message.
     *
     * @param prefix Contextual failure prefix.
     * @param exception Storage failure to describe.
     * @return Complete failure message.
     */
    String storageFailure(String prefix, Exception exception) {
        return prefix + exception.getMessage() + "\n";
    }

    /**
     * Identifies a Screening's parent Movie and positions in their display order.
     *
     * @param movie Parent Movie.
     * @param screening Screening within the parent Movie.
     * @param movieIndex Zero-based parent Movie position.
     * @param screeningIndex Zero-based Screening position within its Movie.
     * @param displayPosition One-based position in the global Screening list.
     */
    record ScreeningLocation(
            Movie movie,
            Screening screening,
            int movieIndex,
            int screeningIndex,
            int displayPosition) {
    }
}
