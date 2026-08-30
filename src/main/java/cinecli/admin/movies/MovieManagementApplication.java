package cinecli.admin.movies;

import cinecli.admin.AdminInputRules;
import cinecli.admin.AdminInputRules.Confirmation;
import cinecli.admin.AdminWorkflowInput;
import cinecli.admin.AdminWorkflowInteraction;
import cinecli.admin.AdminWorkflowOutcome;
import cinecli.admin.UuidGenerator;
import cinecli.admin.ui.AdminTerminal;
import cinecli.model.ContentRating;
import cinecli.model.Movie;
import cinecli.storage.catalog.CatalogStorage;
import cinecli.storage.exception.CatalogStorageException;
import cinecli.storage.exception.MovieDeletionCommitException;
import cinecli.storage.exception.MovieDeletionPreparationException;
import cinecli.storage.transaction.MovieDeletionResult;
import cinecli.storage.transaction.MovieDeletionTransaction;
import cinecli.storage.transaction.PreparedMovieDeletion;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Coordinates administrator Movie-management workflows. */
public final class MovieManagementApplication {
    private final CatalogStorage catalogStorage;
    private final MovieDeletionTransaction deletionTransaction;
    private final AdminWorkflowInteraction interaction;
    private final UuidGenerator movieIdGenerator;
    private final AdminInputRules inputRules = new AdminInputRules();
    private final MovieInputRules movieInputRules = new MovieInputRules();
    private final MovieManagementText text = new MovieManagementText();

    /** Creates the production workflow with random version-4 UUID generation. */
    public MovieManagementApplication(
            CatalogStorage catalogStorage,
            MovieDeletionTransaction deletionTransaction,
            MovieManagementTerminal terminal) {
        this(catalogStorage, deletionTransaction, (AdminTerminal) terminal, UUID::randomUUID);
    }

    /** Creates a production workflow using the shared administrator terminal seam. */
    public MovieManagementApplication(
            CatalogStorage catalogStorage,
            MovieDeletionTransaction deletionTransaction,
            AdminTerminal terminal) {
        this(catalogStorage, deletionTransaction, terminal, UUID::randomUUID);
    }

    MovieManagementApplication(
            CatalogStorage catalogStorage,
            MovieDeletionTransaction deletionTransaction,
            AdminTerminal terminal,
            UuidGenerator movieIdGenerator) {
        this.catalogStorage = catalogStorage;
        this.deletionTransaction = deletionTransaction;
        this.interaction = new AdminWorkflowInteraction(terminal);
        this.movieIdGenerator = movieIdGenerator;
    }

    /** Runs Movie management until a typed navigation or termination outcome occurs. */
    public AdminWorkflowOutcome run() {
        while (true) {
            List<Movie> movies;
            try {
                movies = catalogStorage.load();
            } catch (CatalogStorageException exception) {
                return reportStorageFailure(MovieManagementText.ACCESS_FAILURE_PREFIX, exception);
            }
            if (!write(text.management(movies))) {
                return AdminWorkflowOutcome.TERMINATED;
            }
            Integer choice;
            while (true) {
                ReadResult input = read();
                if (input.outcome != null) {
                    return input.outcome;
                }
                choice = inputRules.parseNumber(input.line, 0, 3);
                if (choice != null) {
                    break;
                }
                if (!write(MovieManagementText.ACTION_ERROR_WITH_PROMPT)) {
                    return AdminWorkflowOutcome.TERMINATED;
                }
            }
            AdminWorkflowOutcome outcome;
            if (choice == 0) {
                outcome = AdminWorkflowOutcome.BACK;
            } else if (choice == 1) {
                outcome = addMovie(movies);
            } else if (choice == 2) {
                outcome = movies.isEmpty()
                        ? showEmptyAction(MovieManagementText.EMPTY_EDIT)
                        : editMovie(movies);
            } else {
                outcome = movies.isEmpty()
                        ? showEmptyAction(MovieManagementText.EMPTY_DELETE)
                        : deleteMovie(movies);
            }
            if (outcome != null) {
                return outcome;
            }
        }
    }

    private AdminWorkflowOutcome showEmptyAction(String message) {
        return write(message) ? null : AdminWorkflowOutcome.TERMINATED;
    }

    private AdminWorkflowOutcome addMovie(List<Movie> movies) {
        ValueResult<String> titleResult = requestTitle();
        if (titleResult.outcome != null) {
            return titleResult.outcome;
        }
        if (titleResult.isCancelled) {
            return cancel(MovieManagementText.ADD_CANCELLED);
        }
        ValueResult<ContentRating> ratingResult = requestRating();
        if (ratingResult.outcome != null) {
            return ratingResult.outcome;
        }
        if (ratingResult.isCancelled) {
            return cancel(MovieManagementText.ADD_CANCELLED);
        }
        String id = generateUniqueId(movies);
        String preview = text.addPreview(
                id, titleResult.value, ratingResult.value, movies.size() + 1);
        ConfirmationResult confirmation = confirm(
                preview, MovieManagementText.ADD_CONFIRMATION_PROMPT);
        if (confirmation.outcome != null) {
            return confirmation.outcome;
        }
        if (!confirmation.isConfirmed) {
            return cancel(MovieManagementText.ADD_CANCELLED);
        }
        List<Movie> intended = new ArrayList<>(movies);
        intended.add(new Movie(id, titleResult.value, ratingResult.value, List.of()));
        try {
            catalogStorage.save(intended);
        } catch (CatalogStorageException exception) {
            return reportStorageFailure(MovieManagementText.CHANGE_NOT_SAVED_PREFIX, exception);
        }
        return write(text.added(titleResult.value, id))
                ? null : AdminWorkflowOutcome.TERMINATED;
    }

    private AdminWorkflowOutcome editMovie(List<Movie> movies) {
        TargetResult target = requestTarget(movies.size(), "edit");
        if (target.outcome != null) {
            return target.outcome;
        }
        if (target.isBack) {
            return target.isCancelled ? cancel(MovieManagementText.EDIT_CANCELLED) : null;
        }
        int index = target.index;
        Movie original = movies.get(index);
        String proposedTitle = original.title();
        ContentRating proposedRating = original.contentRating();
        while (true) {
            String menu = text.editMenu(
                    original, index + 1, proposedTitle, proposedRating);
            if (!write(menu)) {
                return AdminWorkflowOutcome.TERMINATED;
            }
            ReadResult input = read();
            if (input.outcome != null) {
                return input.outcome;
            }
            if (inputRules.isCancel(input.line)) {
                return cancel(MovieManagementText.EDIT_CANCELLED);
            }
            Integer choice = inputRules.parseNumber(input.line, 0, 3);
            if (choice == null) {
                if (!write(MovieManagementText.EDIT_MENU_ERROR)) {
                    return AdminWorkflowOutcome.TERMINATED;
                }
                continue;
            }
            if (choice == 0) {
                return cancel(MovieManagementText.EDIT_CANCELLED);
            }
            if (choice == 1) {
                ValueResult<String> title = requestTitle();
                if (title.outcome != null) {
                    return title.outcome;
                }
                if (title.isCancelled) {
                    return cancel(MovieManagementText.EDIT_CANCELLED);
                }
                proposedTitle = title.value;
                if (proposedTitle.equals(original.title())
                        && !write(MovieManagementText.TITLE_UNCHANGED)) {
                    return AdminWorkflowOutcome.TERMINATED;
                }
                continue;
            }
            if (choice == 2) {
                ValueResult<ContentRating> rating = requestRating();
                if (rating.outcome != null) {
                    return rating.outcome;
                }
                if (rating.isCancelled) {
                    return cancel(MovieManagementText.EDIT_CANCELLED);
                }
                proposedRating = rating.value;
                if (proposedRating == original.contentRating()
                        && !write(MovieManagementText.RATING_UNCHANGED)) {
                    return AdminWorkflowOutcome.TERMINATED;
                }
                continue;
            }
            if (proposedTitle.equals(original.title())
                    && proposedRating == original.contentRating()) {
                if (!write(MovieManagementText.NO_CHANGES)) {
                    return AdminWorkflowOutcome.TERMINATED;
                }
                continue;
            }
            String preview = text.editPreview(
                    original, index + 1, proposedTitle, proposedRating);
            ConfirmationResult confirmation = confirm(
                    preview, MovieManagementText.EDIT_CONFIRMATION_PROMPT);
            if (confirmation.outcome != null) {
                return confirmation.outcome;
            }
            if (!confirmation.isConfirmed) {
                return cancel(MovieManagementText.EDIT_CANCELLED);
            }
            Movie updated = new Movie(
                    original.id(), proposedTitle, proposedRating, original.screenings());
            List<Movie> intended = new ArrayList<>(movies);
            intended.set(index, updated);
            try {
                catalogStorage.save(intended);
            } catch (CatalogStorageException exception) {
                return reportStorageFailure(
                        MovieManagementText.CHANGE_NOT_SAVED_PREFIX, exception);
            }
            return write(text.updated(proposedTitle, original.id()))
                    ? null : AdminWorkflowOutcome.TERMINATED;
        }
    }

    private AdminWorkflowOutcome deleteMovie(List<Movie> movies) {
        TargetResult target = requestTarget(movies.size(), "delete");
        if (target.outcome != null) {
            return target.outcome;
        }
        if (target.isBack) {
            return target.isCancelled ? cancel(MovieManagementText.DELETE_CANCELLED) : null;
        }
        PreparedMovieDeletion deletion;
        try {
            deletion = deletionTransaction.prepare(movies.get(target.index).id());
        } catch (MovieDeletionPreparationException exception) {
            return reportStorageFailure(
                    MovieManagementText.DELETION_PREPARATION_FAILURE_PREFIX, exception);
        }
        ConfirmationResult confirmation = confirm(
                text.deletePreview(deletion),
                MovieManagementText.DELETE_CONFIRMATION_PROMPT);
        if (confirmation.outcome != null) {
            return confirmation.outcome;
        }
        if (!confirmation.isConfirmed) {
            return cancel(MovieManagementText.DELETE_CANCELLED);
        }
        MovieDeletionResult result;
        try {
            result = deletionTransaction.commit(deletion);
        } catch (MovieDeletionCommitException exception) {
            String prefix = exception.status() == MovieDeletionCommitException.Status.NOT_APPLIED
                    ? MovieManagementText.DELETION_NOT_APPLIED_PREFIX
                    : MovieManagementText.DELETION_RECOVERY_PENDING_PREFIX;
            return reportStorageFailure(prefix, exception);
        }
        return write(text.deleted(result))
                ? null : AdminWorkflowOutcome.TERMINATED;
    }

    private ValueResult<String> requestTitle() {
        while (true) {
            if (!write(MovieManagementText.TITLE_PROMPT)) {
                return ValueResult.outcome(AdminWorkflowOutcome.TERMINATED);
            }
            ReadResult input = read();
            if (input.outcome != null) {
                return ValueResult.outcome(input.outcome);
            }
            if (inputRules.isCancel(input.line)) {
                return ValueResult.cancelled();
            }
            MovieInputRules.TitleResult title = movieInputRules.interpretTitle(input.line);
            if (title.status() == MovieInputRules.TitleStatus.CONTROL_CHARACTERS) {
                if (!write(MovieManagementText.TITLE_CONTROL_ERROR)) {
                    return ValueResult.outcome(AdminWorkflowOutcome.TERMINATED);
                }
                continue;
            }
            if (title.status() == MovieInputRules.TitleStatus.BLANK) {
                if (!write(MovieManagementText.TITLE_BLANK_ERROR)) {
                    return ValueResult.outcome(AdminWorkflowOutcome.TERMINATED);
                }
                continue;
            }
            return ValueResult.value(title.title());
        }
    }

    private ValueResult<ContentRating> requestRating() {
        String prompt = MovieManagementText.RATING_PROMPT;
        while (true) {
            if (!write(prompt)) {
                return ValueResult.outcome(AdminWorkflowOutcome.TERMINATED);
            }
            ReadResult input = read();
            if (input.outcome != null) {
                return ValueResult.outcome(input.outcome);
            }
            if (inputRules.isCancel(input.line)) {
                return ValueResult.cancelled();
            }
            Integer rating = inputRules.parseNumber(input.line, 1, 3);
            if (rating == null) {
                if (!write(MovieManagementText.RATING_ERROR)) {
                    return ValueResult.outcome(AdminWorkflowOutcome.TERMINATED);
                }
                continue;
            }
            return ValueResult.value(movieInputRules.contentRatingFor(rating));
        }
    }

    private TargetResult requestTarget(int count, String action) {
        String prompt = text.targetPrompt(action);
        while (true) {
            if (!write(prompt)) {
                return TargetResult.outcome(AdminWorkflowOutcome.TERMINATED);
            }
            ReadResult input = read();
            if (input.outcome != null) {
                return TargetResult.outcome(input.outcome);
            }
            if (inputRules.isCancel(input.line)) {
                return TargetResult.cancelled();
            }
            Integer target = inputRules.parseNumber(input.line, 0, count);
            if (target == null) {
                if (!write(text.targetError(count))) {
                    return TargetResult.outcome(AdminWorkflowOutcome.TERMINATED);
                }
                continue;
            }
            return target == 0 ? TargetResult.back() : TargetResult.index(target - 1);
        }
    }

    private ConfirmationResult confirm(String preview, String prompt) {
        if (!write(preview)) {
            return ConfirmationResult.outcome(AdminWorkflowOutcome.TERMINATED);
        }
        while (true) {
            ReadResult input = read();
            if (input.outcome != null) {
                return ConfirmationResult.outcome(input.outcome);
            }
            Confirmation answer = inputRules.parseConfirmation(input.line);
            if (answer == Confirmation.CONFIRMED) {
                return ConfirmationResult.confirmed();
            }
            if (answer == Confirmation.CANCELLED) {
                return ConfirmationResult.cancelled();
            }
            if (!write(MovieManagementText.CONFIRMATION_ERROR + prompt)) {
                return ConfirmationResult.outcome(AdminWorkflowOutcome.TERMINATED);
            }
        }
    }

    private ReadResult read() {
        AdminWorkflowInput input = interaction.read();
        return new ReadResult(input.line(), input.outcome());
    }

    private boolean write(String text) {
        return interaction.write(text);
    }

    private AdminWorkflowOutcome cancel(String message) {
        return write(message) ? null : AdminWorkflowOutcome.TERMINATED;
    }

    private AdminWorkflowOutcome reportStorageFailure(String prefix, Exception exception) {
        return write(text.storageFailure(prefix, exception))
                ? AdminWorkflowOutcome.BACK : AdminWorkflowOutcome.TERMINATED;
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

    private record ReadResult(String line, AdminWorkflowOutcome outcome) {
    }

    private static final class ValueResult<T> {
        private final T value;
        private final boolean isCancelled;
        private final AdminWorkflowOutcome outcome;

        private ValueResult(T value, boolean isCancelled, AdminWorkflowOutcome outcome) {
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

        private static <T> ValueResult<T> outcome(AdminWorkflowOutcome outcome) {
            return new ValueResult<>(null, false, outcome);
        }
    }

    private record TargetResult(
            int index, boolean isBack, boolean isCancelled, AdminWorkflowOutcome outcome) {
        private static TargetResult index(int index) {
            return new TargetResult(index, false, false, null);
        }

        private static TargetResult back() {
            return new TargetResult(-1, true, false, null);
        }

        private static TargetResult cancelled() {
            return new TargetResult(-1, true, true, null);
        }

        private static TargetResult outcome(AdminWorkflowOutcome outcome) {
            return new TargetResult(-1, false, false, outcome);
        }
    }

    private record ConfirmationResult(boolean isConfirmed, AdminWorkflowOutcome outcome) {
        private static ConfirmationResult confirmed() {
            return new ConfirmationResult(true, null);
        }

        private static ConfirmationResult cancelled() {
            return new ConfirmationResult(false, null);
        }

        private static ConfirmationResult outcome(AdminWorkflowOutcome outcome) {
            return new ConfirmationResult(false, outcome);
        }
    }
}
