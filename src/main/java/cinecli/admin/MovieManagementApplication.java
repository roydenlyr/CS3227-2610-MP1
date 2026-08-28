package cinecli.admin;

import cinecli.admin.ui.EndOfInput;
import cinecli.admin.ui.GlobalCommand;
import cinecli.admin.ui.InputFailure;
import cinecli.admin.ui.MovieManagementTerminal;
import cinecli.admin.ui.SubmittedLine;
import cinecli.admin.ui.TerminalInput;
import cinecli.model.ContentRating;
import cinecli.model.Movie;
import cinecli.storage.catalog.CatalogStorage;
import cinecli.storage.exception.CatalogStorageException;
import cinecli.storage.exception.MovieDeletionCommitException;
import cinecli.storage.exception.MovieDeletionPreparationException;
import cinecli.storage.exception.TransactionStorageException;
import cinecli.storage.transaction.MovieDeletionResult;
import cinecli.storage.transaction.MovieDeletionTransaction;
import cinecli.storage.transaction.PreparedMovieDeletion;
import cinecli.storage.transaction.RecoveryResult;
import cinecli.storage.transaction.ScreeningDeletionImpact;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Coordinates administrator Movie-management workflows. */
public final class MovieManagementApplication {
    private static final String OUTPUT_FAILURE = "Unable to write output. CineCLI will exit.\n";
    private static final String INPUT_FAILURE = "Unable to read input. CineCLI will exit.\n";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("0|[1-9][0-9]*");
    private static final DateTimeFormatter SCREENING_TIME = DateTimeFormatter
            .ofPattern("dd MMM uuuu, HH:mm", Locale.ENGLISH);

    private final CatalogStorage catalogStorage;
    private final MovieDeletionTransaction deletionTransaction;
    private final MovieManagementTerminal terminal;
    private final MovieIdGenerator movieIdGenerator;

    /** Creates the production workflow with random version-4 UUID generation. */
    public MovieManagementApplication(
            CatalogStorage catalogStorage,
            MovieDeletionTransaction deletionTransaction,
            MovieManagementTerminal terminal) {
        this(catalogStorage, deletionTransaction, terminal, UUID::randomUUID);
    }

    MovieManagementApplication(
            CatalogStorage catalogStorage,
            MovieDeletionTransaction deletionTransaction,
            MovieManagementTerminal terminal,
            MovieIdGenerator movieIdGenerator) {
        this.catalogStorage = catalogStorage;
        this.deletionTransaction = deletionTransaction;
        this.terminal = terminal;
        this.movieIdGenerator = movieIdGenerator;
    }

    /** Runs Movie management until a typed navigation or termination outcome occurs. */
    public MovieManagementOutcome run() {
        MovieManagementOutcome recoveryOutcome = recoverPendingDeletion();
        if (recoveryOutcome != null) {
            return recoveryOutcome;
        }
        while (true) {
            List<Movie> movies;
            try {
                movies = catalogStorage.load();
            } catch (CatalogStorageException exception) {
                return reportStorageFailure("Unable to access movie management: ", exception);
            }
            if (!write(renderManagement(movies))) {
                return MovieManagementOutcome.TERMINATED;
            }
            Integer choice;
            while (true) {
                ReadResult input = read();
                if (input.outcome != null) {
                    return input.outcome;
                }
                choice = parseNumber(input.line, 0, 3);
                if (choice != null) {
                    break;
                }
                if (!write("Enter 0, 1, 2, or 3.\nEnter choice:\n")) {
                    return MovieManagementOutcome.TERMINATED;
                }
            }
            MovieManagementOutcome outcome;
            if (choice == 0) {
                outcome = MovieManagementOutcome.BACK;
            } else if (choice == 1) {
                outcome = addMovie(movies);
            } else if (choice == 2) {
                outcome = movies.isEmpty()
                        ? showEmptyAction("There are no movies to edit.\n")
                        : editMovie(movies);
            } else {
                outcome = movies.isEmpty()
                        ? showEmptyAction("There are no movies to delete.\n")
                        : deleteMovie(movies);
            }
            if (outcome != null) {
                return outcome;
            }
        }
    }

    private MovieManagementOutcome recoverPendingDeletion() {
        try {
            if (deletionTransaction.recover() == RecoveryResult.RECOVERED
                    && !write("Pending movie deletion recovery completed.\n")) {
                return MovieManagementOutcome.TERMINATED;
            }
            return null;
        } catch (TransactionStorageException exception) {
            return reportStorageFailure("Unable to access movie management: ", exception);
        }
    }

    private MovieManagementOutcome showEmptyAction(String message) {
        return write(message) ? null : MovieManagementOutcome.TERMINATED;
    }

    private MovieManagementOutcome addMovie(List<Movie> movies) {
        ValueResult<String> titleResult = requestTitle();
        if (titleResult.outcome != null) {
            return titleResult.outcome;
        }
        if (titleResult.isCancelled) {
            return cancel("Movie addition cancelled.\n");
        }
        ValueResult<ContentRating> ratingResult = requestRating();
        if (ratingResult.outcome != null) {
            return ratingResult.outcome;
        }
        if (ratingResult.isCancelled) {
            return cancel("Movie addition cancelled.\n");
        }
        String id = generateUniqueId(movies);
        String preview = "Add Movie Preview\n"
                + "ID: " + id + "\n"
                + "Title: " + titleResult.value + "\n"
                + "Rating: " + ratingResult.value + "\n"
                + "Display position: " + (movies.size() + 1) + "\n"
                + "Confirm add? (Y/N):\n";
        ConfirmationResult confirmation = confirm(preview, "Confirm add? (Y/N):\n");
        if (confirmation.outcome != null) {
            return confirmation.outcome;
        }
        if (!confirmation.isConfirmed) {
            return cancel("Movie addition cancelled.\n");
        }
        List<Movie> intended = new ArrayList<>(movies);
        intended.add(new Movie(id, titleResult.value, ratingResult.value, List.of()));
        try {
            catalogStorage.save(intended);
        } catch (CatalogStorageException exception) {
            return reportStorageFailure("Movie change was not saved: ", exception);
        }
        return write("Movie added: " + titleResult.value + " [" + id + "].\n")
                ? null : MovieManagementOutcome.TERMINATED;
    }

    private MovieManagementOutcome editMovie(List<Movie> movies) {
        TargetResult target = requestTarget(movies.size(), "edit");
        if (target.outcome != null) {
            return target.outcome;
        }
        if (target.isBack) {
            return target.isCancelled ? cancel("Movie edit cancelled.\n") : null;
        }
        int index = target.index;
        Movie original = movies.get(index);
        String proposedTitle = original.title();
        ContentRating proposedRating = original.contentRating();
        while (true) {
            String menu = renderEditMenu(original, index + 1, proposedTitle, proposedRating);
            if (!write(menu)) {
                return MovieManagementOutcome.TERMINATED;
            }
            ReadResult input = read();
            if (input.outcome != null) {
                return input.outcome;
            }
            if (isCancel(input.line)) {
                return cancel("Movie edit cancelled.\n");
            }
            Integer choice = parseNumber(input.line, 0, 3);
            if (choice == null) {
                if (!write("Enter 0, 1, 2, or 3.\n")) {
                    return MovieManagementOutcome.TERMINATED;
                }
                continue;
            }
            if (choice == 0) {
                return cancel("Movie edit cancelled.\n");
            }
            if (choice == 1) {
                ValueResult<String> title = requestTitle();
                if (title.outcome != null) {
                    return title.outcome;
                }
                if (title.isCancelled) {
                    return cancel("Movie edit cancelled.\n");
                }
                proposedTitle = title.value;
                if (proposedTitle.equals(original.title())
                        && !write("The movie title is unchanged.\n")) {
                    return MovieManagementOutcome.TERMINATED;
                }
                continue;
            }
            if (choice == 2) {
                ValueResult<ContentRating> rating = requestRating();
                if (rating.outcome != null) {
                    return rating.outcome;
                }
                if (rating.isCancelled) {
                    return cancel("Movie edit cancelled.\n");
                }
                proposedRating = rating.value;
                if (proposedRating == original.contentRating()
                        && !write("The movie rating is unchanged.\n")) {
                    return MovieManagementOutcome.TERMINATED;
                }
                continue;
            }
            if (proposedTitle.equals(original.title()) && proposedRating == original.contentRating()) {
                if (!write("No movie changes have been made.\n")) {
                    return MovieManagementOutcome.TERMINATED;
                }
                continue;
            }
            String preview = renderEditPreview(
                    original, index + 1, proposedTitle, proposedRating);
            ConfirmationResult confirmation = confirm(preview, "Confirm edit? (Y/N):\n");
            if (confirmation.outcome != null) {
                return confirmation.outcome;
            }
            if (!confirmation.isConfirmed) {
                return cancel("Movie edit cancelled.\n");
            }
            Movie updated = new Movie(
                    original.id(), proposedTitle, proposedRating, original.screenings());
            List<Movie> intended = new ArrayList<>(movies);
            intended.set(index, updated);
            try {
                catalogStorage.save(intended);
            } catch (CatalogStorageException exception) {
                return reportStorageFailure("Movie change was not saved: ", exception);
            }
            return write("Movie updated: " + proposedTitle + " [" + original.id() + "].\n")
                    ? null : MovieManagementOutcome.TERMINATED;
        }
    }

    private MovieManagementOutcome deleteMovie(List<Movie> movies) {
        TargetResult target = requestTarget(movies.size(), "delete");
        if (target.outcome != null) {
            return target.outcome;
        }
        if (target.isBack) {
            return target.isCancelled ? cancel("Movie deletion cancelled.\n") : null;
        }
        PreparedMovieDeletion deletion;
        try {
            deletion = deletionTransaction.prepare(movies.get(target.index).id());
        } catch (MovieDeletionPreparationException exception) {
            return reportStorageFailure("Unable to prepare movie deletion: ", exception);
        }
        ConfirmationResult confirmation = confirm(
                renderDeletePreview(deletion), "Confirm delete? (Y/N):\n");
        if (confirmation.outcome != null) {
            return confirmation.outcome;
        }
        if (!confirmation.isConfirmed) {
            return cancel("Movie deletion cancelled.\n");
        }
        MovieDeletionResult result;
        try {
            result = deletionTransaction.commit(deletion);
        } catch (MovieDeletionCommitException exception) {
            String prefix = exception.status() == MovieDeletionCommitException.Status.NOT_APPLIED
                    ? "Movie deletion was not applied: "
                    : "Movie deletion is confirmed but recovery is pending: ";
            return reportStorageFailure(prefix, exception);
        }
        String success = "Movie deleted: " + result.title() + " [" + result.movieId()
                + "]; removed " + result.screeningCount() + " screening(s) and "
                + result.occupiedSeatCount() + " occupied seat(s).\n";
        return write(success) ? null : MovieManagementOutcome.TERMINATED;
    }

    private ValueResult<String> requestTitle() {
        while (true) {
            if (!write("Enter movie title (/cancel to cancel):\n")) {
                return ValueResult.outcome(MovieManagementOutcome.TERMINATED);
            }
            ReadResult input = read();
            if (input.outcome != null) {
                return ValueResult.outcome(input.outcome);
            }
            if (isCancel(input.line)) {
                return ValueResult.cancelled();
            }
            if (input.line.codePoints().anyMatch(Character::isISOControl)) {
                if (!write("Movie title must not contain tabs or control characters.\n")) {
                    return ValueResult.outcome(MovieManagementOutcome.TERMINATED);
                }
                continue;
            }
            String title = stripUnicodeWhitespace(input.line);
            if (title.isEmpty()) {
                if (!write("Movie title must not be blank.\n")) {
                    return ValueResult.outcome(MovieManagementOutcome.TERMINATED);
                }
                continue;
            }
            return ValueResult.value(title);
        }
    }

    private ValueResult<ContentRating> requestRating() {
        String prompt = "Content Ratings\n1. PG13\n2. M18\n3. R21\n"
                + "Enter rating number (/cancel to cancel):\n";
        while (true) {
            if (!write(prompt)) {
                return ValueResult.outcome(MovieManagementOutcome.TERMINATED);
            }
            ReadResult input = read();
            if (input.outcome != null) {
                return ValueResult.outcome(input.outcome);
            }
            if (isCancel(input.line)) {
                return ValueResult.cancelled();
            }
            Integer rating = parseNumber(input.line, 1, 3);
            if (rating == null) {
                if (!write("Enter 1 for PG13, 2 for M18, or 3 for R21.\n")) {
                    return ValueResult.outcome(MovieManagementOutcome.TERMINATED);
                }
                continue;
            }
            return ValueResult.value(ContentRating.values()[rating - 1]);
        }
    }

    private TargetResult requestTarget(int count, String action) {
        String prompt = "Enter movie number to " + action + " (0 to go back):\n";
        while (true) {
            if (!write(prompt)) {
                return TargetResult.outcome(MovieManagementOutcome.TERMINATED);
            }
            ReadResult input = read();
            if (input.outcome != null) {
                return TargetResult.outcome(input.outcome);
            }
            if (isCancel(input.line)) {
                return TargetResult.cancelled();
            }
            Integer target = parseNumber(input.line, 0, count);
            if (target == null) {
                if (!write("Enter a movie number from 1 to " + count + ", or 0 to go back.\n")) {
                    return TargetResult.outcome(MovieManagementOutcome.TERMINATED);
                }
                continue;
            }
            return target == 0 ? TargetResult.back() : TargetResult.index(target - 1);
        }
    }

    private ConfirmationResult confirm(String preview, String prompt) {
        if (!write(preview)) {
            return ConfirmationResult.outcome(MovieManagementOutcome.TERMINATED);
        }
        while (true) {
            ReadResult input = read();
            if (input.outcome != null) {
                return ConfirmationResult.outcome(input.outcome);
            }
            String normalized = input.line.strip();
            if (normalized.equalsIgnoreCase("Y")) {
                return ConfirmationResult.confirmed();
            }
            if (normalized.equalsIgnoreCase("N") || normalized.equalsIgnoreCase("/cancel")) {
                return ConfirmationResult.cancelled();
            }
            if (!write("Enter Y to confirm or N to cancel.\n" + prompt)) {
                return ConfirmationResult.outcome(MovieManagementOutcome.TERMINATED);
            }
        }
    }

    private ReadResult read() {
        TerminalInput input = terminal.readLine();
        if (input instanceof SubmittedLine line) {
            return new ReadResult(line.value(), null);
        }
        if (input instanceof GlobalCommand command) {
            MovieManagementOutcome outcome = switch (command.type()) {
                case ADMIN -> MovieManagementOutcome.ADMIN;
                case CUSTOMER -> MovieManagementOutcome.CUSTOMER;
                case EXIT -> MovieManagementOutcome.EXIT;
            };
            return new ReadResult(null, outcome);
        }
        if (input instanceof InputFailure) {
            terminal.writeError(INPUT_FAILURE);
        }
        return new ReadResult(null, MovieManagementOutcome.TERMINATED);
    }

    private boolean write(String text) {
        if (terminal.write(text)) {
            return true;
        }
        terminal.writeError(OUTPUT_FAILURE);
        return false;
    }

    private MovieManagementOutcome cancel(String message) {
        return write(message) ? null : MovieManagementOutcome.TERMINATED;
    }

    private MovieManagementOutcome reportStorageFailure(String prefix, Exception exception) {
        return write(prefix + exception.getMessage() + "\n")
                ? MovieManagementOutcome.BACK : MovieManagementOutcome.TERMINATED;
    }

    private String renderManagement(List<Movie> movies) {
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

    private String renderEditMenu(
            Movie movie, int position, String title, ContentRating rating) {
        return "Edit Movie\nID: " + movie.id() + "\nDisplay position: " + position
                + "\nTitle: " + title + "\nRating: " + rating
                + "\n\n1. Change title\n2. Change rating\n3. Review changes\n"
                + "0. Cancel edit\nEnter choice:\n";
    }

    private String renderEditPreview(
            Movie original, int position, String title, ContentRating rating) {
        String titleLine = title.equals(original.title())
                ? title + " (unchanged)" : original.title() + " -> " + title;
        String ratingLine = rating == original.contentRating()
                ? rating + " (unchanged)" : original.contentRating() + " -> " + rating;
        return "Edit Movie Preview\nID: " + original.id() + "\nDisplay position: " + position
                + "\nTitle: " + titleLine + "\nRating: " + ratingLine
                + "\nConfirm edit? (Y/N):\n";
    }

    private String renderDeletePreview(PreparedMovieDeletion deletion) {
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

    private String generateUniqueId(List<Movie> movies) {
        Set<String> existingIds = new HashSet<>();
        movies.stream().map(Movie::id).forEach(existingIds::add);
        String candidate;
        do {
            candidate = "MOV-" + movieIdGenerator.generate().toString().toLowerCase(Locale.ROOT);
        } while (existingIds.contains(candidate));
        return candidate;
    }

    private Integer parseNumber(String input, int minimum, int maximum) {
        String normalized = input.strip();
        if (!NUMBER_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        try {
            int value = Integer.parseInt(normalized);
            return value >= minimum && value <= maximum ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isCancel(String input) {
        return input.strip().equalsIgnoreCase("/cancel");
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

    private record ReadResult(String line, MovieManagementOutcome outcome) {
    }

    private static final class ValueResult<T> {
        private final T value;
        private final boolean isCancelled;
        private final MovieManagementOutcome outcome;

        private ValueResult(T value, boolean isCancelled, MovieManagementOutcome outcome) {
            this.value = value;
            this.isCancelled = isCancelled;
            this.outcome = outcome;
        }

        private static <T> ValueResult<T> value(T value) {
            return new ValueResult<>(value, false, null);
        }

        private static <T> ValueResult<T> cancelled() {
            return new ValueResult<>(null, true, null);
        }

        private static <T> ValueResult<T> outcome(MovieManagementOutcome outcome) {
            return new ValueResult<>(null, false, outcome);
        }
    }

    private record TargetResult(
            int index, boolean isBack, boolean isCancelled, MovieManagementOutcome outcome) {
        private static TargetResult index(int index) {
            return new TargetResult(index, false, false, null);
        }

        private static TargetResult back() {
            return new TargetResult(-1, true, false, null);
        }

        private static TargetResult cancelled() {
            return new TargetResult(-1, true, true, null);
        }

        private static TargetResult outcome(MovieManagementOutcome outcome) {
            return new TargetResult(-1, false, false, outcome);
        }
    }

    private record ConfirmationResult(boolean isConfirmed, MovieManagementOutcome outcome) {
        private static ConfirmationResult confirmed() {
            return new ConfirmationResult(true, null);
        }

        private static ConfirmationResult cancelled() {
            return new ConfirmationResult(false, null);
        }

        private static ConfirmationResult outcome(MovieManagementOutcome outcome) {
            return new ConfirmationResult(false, outcome);
        }
    }
}
