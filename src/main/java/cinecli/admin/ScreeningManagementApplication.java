package cinecli.admin;

import cinecli.admin.AdminInputRules.Confirmation;
import cinecli.admin.ScreeningManagementText.ScreeningLocation;
import cinecli.admin.ui.AdminTerminal;
import cinecli.admin.ui.GlobalCommand;
import cinecli.admin.ui.InputFailure;
import cinecli.admin.ui.SubmittedLine;
import cinecli.admin.ui.TerminalInput;
import cinecli.model.Movie;
import cinecli.model.Screening;
import cinecli.storage.catalog.CatalogStorage;
import cinecli.storage.exception.CatalogStorageException;
import cinecli.storage.exception.ScreeningDeletionCommitException;
import cinecli.storage.exception.ScreeningDeletionPreparationException;
import cinecli.storage.exception.TransactionStorageException;
import cinecli.storage.transaction.CatalogRecoveryGate;
import cinecli.storage.transaction.MovieDeletionTransaction;
import cinecli.storage.transaction.PreparedScreeningDeletion;
import cinecli.storage.transaction.ScreeningDeletionResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Coordinates administrator Screening-management workflows. */
public final class ScreeningManagementApplication {
    private static final String OUTPUT_FAILURE = "Unable to write output. CineCLI will exit.\n";
    private static final String INPUT_FAILURE = "Unable to read input. CineCLI will exit.\n";

    private final CatalogStorage catalogStorage;
    private final MovieDeletionTransaction deletionTransaction;
    private final CatalogRecoveryGate recoveryGate;
    private final AdminTerminal terminal;
    private final UuidGenerator screeningIdGenerator;
    private final AdminInputRules inputRules = new AdminInputRules();
    private final ScreeningInputRules screeningInputRules = new ScreeningInputRules();
    private final ScreeningManagementText text = new ScreeningManagementText();

    /** Creates the production workflow with random version-4 UUID generation. */
    public ScreeningManagementApplication(
            CatalogStorage catalogStorage,
            MovieDeletionTransaction deletionTransaction,
            AdminTerminal terminal) {
        this(catalogStorage, deletionTransaction, terminal, UUID::randomUUID);
    }

    ScreeningManagementApplication(
            CatalogStorage catalogStorage,
            MovieDeletionTransaction deletionTransaction,
            AdminTerminal terminal,
            UuidGenerator screeningIdGenerator) {
        this.catalogStorage = catalogStorage;
        this.deletionTransaction = deletionTransaction;
        this.recoveryGate = new CatalogRecoveryGate(deletionTransaction);
        this.terminal = terminal;
        this.screeningIdGenerator = screeningIdGenerator;
    }

    /** Runs Screening management until a typed navigation or termination outcome occurs. */
    public AdminWorkflowOutcome run() {
        AdminWorkflowOutcome recoveryOutcome = recoverPendingDeletion();
        if (recoveryOutcome != null) {
            return recoveryOutcome;
        }
        while (true) {
            List<Movie> movies;
            try {
                movies = catalogStorage.load();
            } catch (CatalogStorageException exception) {
                return reportStorageFailure(ScreeningManagementText.ACCESS_FAILURE_PREFIX, exception);
            }
            List<ScreeningLocation> screenings = locationsFor(movies);
            if (!write(text.management(screenings))) {
                return AdminWorkflowOutcome.TERMINATED;
            }
            ActionResult action = requestAction();
            if (action.outcome != null) {
                return action.outcome;
            }
            AdminWorkflowOutcome outcome;
            if (action.choice == 0) {
                outcome = AdminWorkflowOutcome.BACK;
            } else if (action.choice == 1) {
                outcome = movies.isEmpty()
                        ? showEmptyAction(ScreeningManagementText.EMPTY_ADD) : addScreening(movies);
            } else if (action.choice == 2) {
                outcome = screenings.isEmpty()
                        ? showEmptyAction(ScreeningManagementText.EMPTY_EDIT) : editScreening(movies, screenings);
            } else {
                outcome = screenings.isEmpty()
                        ? showEmptyAction(ScreeningManagementText.EMPTY_DELETE) : deleteScreening(screenings);
            }
            if (outcome != null) {
                return outcome;
            }
        }
    }

    private AdminWorkflowOutcome recoverPendingDeletion() {
        try {
            if (recoveryGate.recoverBeforeAccess()
                    && !write(ScreeningManagementText.RECOVERY_COMPLETED)) {
                return AdminWorkflowOutcome.TERMINATED;
            }
            return null;
        } catch (TransactionStorageException exception) {
            return reportStorageFailure(ScreeningManagementText.ACCESS_FAILURE_PREFIX, exception);
        }
    }

    private ActionResult requestAction() {
        while (true) {
            ReadResult input = read();
            if (input.outcome != null) {
                return ActionResult.outcome(input.outcome);
            }
            Integer choice = inputRules.parseNumber(input.line, 0, 3);
            if (choice != null) {
                return ActionResult.choice(choice);
            }
            if (!write(ScreeningManagementText.ACTION_ERROR_WITH_PROMPT)) {
                return ActionResult.outcome(AdminWorkflowOutcome.TERMINATED);
            }
        }
    }

    private AdminWorkflowOutcome showEmptyAction(String message) {
        return write(message) ? null : AdminWorkflowOutcome.TERMINATED;
    }

    private AdminWorkflowOutcome addScreening(List<Movie> movies) {
        ValueResult<Movie> parent = requestParent(movies);
        if (parent.outcome != null) {
            return parent.outcome;
        }
        if (parent.isCancelled) {
            return cancel(ScreeningManagementText.ADD_CANCELLED);
        }
        ValueResult<LocalDate> date = requestDate();
        if (date.outcome != null) {
            return date.outcome;
        }
        if (date.isCancelled) {
            return cancel(ScreeningManagementText.ADD_CANCELLED);
        }
        ValueResult<LocalTime> time = requestTime();
        if (time.outcome != null) {
            return time.outcome;
        }
        if (time.isCancelled) {
            return cancel(ScreeningManagementText.ADD_CANCELLED);
        }
        String id = generateUniqueId(movies);
        int position = displayPositionForAppend(movies, parent.value);
        ConfirmationResult confirmation = confirm(
                text.addPreview(parent.value, id, date.value, time.value, position),
                ScreeningManagementText.ADD_CONFIRMATION_PROMPT);
        if (confirmation.outcome != null) {
            return confirmation.outcome;
        }
        if (!confirmation.isConfirmed) {
            return cancel(ScreeningManagementText.ADD_CANCELLED);
        }
        List<Movie> intended = appendScreening(movies, parent.value.id(),
                new Screening(id, LocalDateTime.of(date.value, time.value)));
        try {
            catalogStorage.save(intended);
        } catch (CatalogStorageException exception) {
            return reportStorageFailure(ScreeningManagementText.CHANGE_NOT_SAVED_PREFIX, exception);
        }
        return write(text.added(parent.value, id)) ? null : AdminWorkflowOutcome.TERMINATED;
    }

    private AdminWorkflowOutcome editScreening(
            List<Movie> movies, List<ScreeningLocation> screenings) {
        TargetResult target = requestTarget(screenings.size(), "edit");
        if (target.outcome != null) {
            return target.outcome;
        }
        if (target.isBack) {
            return target.isCancelled ? cancel(ScreeningManagementText.EDIT_CANCELLED) : null;
        }
        ScreeningLocation location = screenings.get(target.index);
        LocalDate proposedDate = location.screening().startsAt().toLocalDate();
        LocalTime proposedTime = location.screening().startsAt().toLocalTime();
        while (true) {
            if (!write(text.editMenu(location, proposedDate, proposedTime))) {
                return AdminWorkflowOutcome.TERMINATED;
            }
            ReadResult input = read();
            if (input.outcome != null) {
                return input.outcome;
            }
            if (inputRules.isCancel(input.line)) {
                return cancel(ScreeningManagementText.EDIT_CANCELLED);
            }
            Integer choice = inputRules.parseNumber(input.line, 0, 3);
            if (choice == null) {
                if (!write(ScreeningManagementText.EDIT_MENU_ERROR)) {
                    return AdminWorkflowOutcome.TERMINATED;
                }
                continue;
            }
            if (choice == 0) {
                return cancel(ScreeningManagementText.EDIT_CANCELLED);
            }
            if (choice == 1) {
                ValueResult<LocalDate> date = requestDate();
                if (date.outcome != null) {
                    return date.outcome;
                }
                if (date.isCancelled) {
                    return cancel(ScreeningManagementText.EDIT_CANCELLED);
                }
                proposedDate = date.value;
                if (proposedDate.equals(location.screening().startsAt().toLocalDate())
                        && !write(ScreeningManagementText.DATE_UNCHANGED)) {
                    return AdminWorkflowOutcome.TERMINATED;
                }
                continue;
            }
            if (choice == 2) {
                ValueResult<LocalTime> time = requestTime();
                if (time.outcome != null) {
                    return time.outcome;
                }
                if (time.isCancelled) {
                    return cancel(ScreeningManagementText.EDIT_CANCELLED);
                }
                proposedTime = time.value;
                if (proposedTime.equals(location.screening().startsAt().toLocalTime())
                        && !write(ScreeningManagementText.TIME_UNCHANGED)) {
                    return AdminWorkflowOutcome.TERMINATED;
                }
                continue;
            }
            if (proposedDate.equals(location.screening().startsAt().toLocalDate())
                    && proposedTime.equals(location.screening().startsAt().toLocalTime())) {
                if (!write(ScreeningManagementText.NO_CHANGES)) {
                    return AdminWorkflowOutcome.TERMINATED;
                }
                continue;
            }
            ConfirmationResult confirmation = confirm(
                    text.editPreview(location, proposedDate, proposedTime),
                    ScreeningManagementText.EDIT_CONFIRMATION_PROMPT);
            if (confirmation.outcome != null) {
                return confirmation.outcome;
            }
            if (!confirmation.isConfirmed) {
                return cancel(ScreeningManagementText.EDIT_CANCELLED);
            }
            List<Movie> intended = replaceScreening(
                    movies, location, LocalDateTime.of(proposedDate, proposedTime));
            try {
                catalogStorage.save(intended);
            } catch (CatalogStorageException exception) {
                return reportStorageFailure(ScreeningManagementText.CHANGE_NOT_SAVED_PREFIX, exception);
            }
            return write(text.updated(location)) ? null : AdminWorkflowOutcome.TERMINATED;
        }
    }

    private AdminWorkflowOutcome deleteScreening(List<ScreeningLocation> screenings) {
        TargetResult target = requestTarget(screenings.size(), "delete");
        if (target.outcome != null) {
            return target.outcome;
        }
        if (target.isBack) {
            return target.isCancelled ? cancel(ScreeningManagementText.DELETE_CANCELLED) : null;
        }
        PreparedScreeningDeletion deletion;
        try {
            deletion = deletionTransaction.prepareScreening(screenings.get(target.index).screening().id());
        } catch (ScreeningDeletionPreparationException exception) {
            return reportStorageFailure(
                    ScreeningManagementText.DELETION_PREPARATION_FAILURE_PREFIX, exception);
        }
        ConfirmationResult confirmation = confirm(
                text.deletePreview(deletion), ScreeningManagementText.DELETE_CONFIRMATION_PROMPT);
        if (confirmation.outcome != null) {
            return confirmation.outcome;
        }
        if (!confirmation.isConfirmed) {
            return cancel(ScreeningManagementText.DELETE_CANCELLED);
        }
        ScreeningDeletionResult result;
        try {
            result = deletionTransaction.commitScreening(deletion);
        } catch (ScreeningDeletionCommitException exception) {
            String prefix = exception.status() == ScreeningDeletionCommitException.Status.NOT_APPLIED
                    ? ScreeningManagementText.DELETION_NOT_APPLIED_PREFIX
                    : ScreeningManagementText.DELETION_RECOVERY_PENDING_PREFIX;
            return reportStorageFailure(prefix, exception);
        }
        return write(text.deleted(result)) ? null : AdminWorkflowOutcome.TERMINATED;
    }

    private ValueResult<Movie> requestParent(List<Movie> movies) {
        while (true) {
            if (!write(text.parentSelection(movies))) {
                return ValueResult.outcome(AdminWorkflowOutcome.TERMINATED);
            }
            ReadResult input = read();
            if (input.outcome != null) {
                return ValueResult.outcome(input.outcome);
            }
            if (inputRules.isCancel(input.line)) {
                return ValueResult.cancelled();
            }
            Integer choice = inputRules.parseNumber(input.line, 0, movies.size());
            if (choice == null) {
                if (!write(text.parentError(movies.size()))) {
                    return ValueResult.outcome(AdminWorkflowOutcome.TERMINATED);
                }
                continue;
            }
            return choice == 0 ? ValueResult.cancelled() : ValueResult.value(movies.get(choice - 1));
        }
    }

    private ValueResult<LocalDate> requestDate() {
        while (true) {
            if (!write(ScreeningManagementText.DATE_PROMPT)) {
                return ValueResult.outcome(AdminWorkflowOutcome.TERMINATED);
            }
            ReadResult input = read();
            if (input.outcome != null) {
                return ValueResult.outcome(input.outcome);
            }
            if (inputRules.isCancel(input.line)) {
                return ValueResult.cancelled();
            }
            LocalDate date = screeningInputRules.parseDate(input.line);
            if (date != null) {
                return ValueResult.value(date);
            }
            if (!write(ScreeningManagementText.DATE_ERROR)) {
                return ValueResult.outcome(AdminWorkflowOutcome.TERMINATED);
            }
        }
    }

    private ValueResult<LocalTime> requestTime() {
        while (true) {
            if (!write(ScreeningManagementText.TIME_PROMPT)) {
                return ValueResult.outcome(AdminWorkflowOutcome.TERMINATED);
            }
            ReadResult input = read();
            if (input.outcome != null) {
                return ValueResult.outcome(input.outcome);
            }
            if (inputRules.isCancel(input.line)) {
                return ValueResult.cancelled();
            }
            LocalTime time = screeningInputRules.parseTime(input.line);
            if (time != null) {
                return ValueResult.value(time);
            }
            if (!write(ScreeningManagementText.TIME_ERROR)) {
                return ValueResult.outcome(AdminWorkflowOutcome.TERMINATED);
            }
        }
    }

    private TargetResult requestTarget(int count, String action) {
        while (true) {
            if (!write(text.targetPrompt(action))) {
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
            if (!write(ScreeningManagementText.CONFIRMATION_ERROR + prompt)) {
                return ConfirmationResult.outcome(AdminWorkflowOutcome.TERMINATED);
            }
        }
    }

    private ReadResult read() {
        TerminalInput input = terminal.readLine();
        if (input instanceof SubmittedLine(String value)) {
            return new ReadResult(value, null);
        }
        if (input instanceof GlobalCommand(GlobalCommand.Type type)) {
            AdminWorkflowOutcome outcome = switch (type) {
            case ADMIN -> AdminWorkflowOutcome.ADMIN;
            case CUSTOMER -> AdminWorkflowOutcome.CUSTOMER;
            case EXIT -> AdminWorkflowOutcome.EXIT;
            };
            return new ReadResult(null, outcome);
        }
        if (input instanceof InputFailure) {
            terminal.writeError(INPUT_FAILURE);
        }
        return new ReadResult(null, AdminWorkflowOutcome.TERMINATED);
    }

    private boolean write(String output) {
        if (terminal.write(output)) {
            return true;
        }
        terminal.writeError(OUTPUT_FAILURE);
        return false;
    }

    private AdminWorkflowOutcome cancel(String message) {
        return write(message) ? null : AdminWorkflowOutcome.TERMINATED;
    }

    private AdminWorkflowOutcome reportStorageFailure(String prefix, Exception exception) {
        return write(text.storageFailure(prefix, exception))
                ? AdminWorkflowOutcome.BACK : AdminWorkflowOutcome.TERMINATED;
    }

    private List<ScreeningLocation> locationsFor(List<Movie> movies) {
        List<ScreeningLocation> locations = new ArrayList<>();
        for (int movieIndex = 0; movieIndex < movies.size(); movieIndex++) {
            Movie movie = movies.get(movieIndex);
            for (int screeningIndex = 0; screeningIndex < movie.screenings().size(); screeningIndex++) {
                locations.add(new ScreeningLocation(
                        movie, movie.screenings().get(screeningIndex), movieIndex, screeningIndex,
                        locations.size() + 1));
            }
        }
        return locations;
    }

    private List<Movie> appendScreening(List<Movie> movies, String movieId, Screening screening) {
        List<Movie> intended = new ArrayList<>();
        for (Movie movie : movies) {
            if (!movie.id().equals(movieId)) {
                intended.add(movie);
                continue;
            }
            List<Screening> screenings = new ArrayList<>(movie.screenings());
            screenings.add(screening);
            intended.add(new Movie(movie.id(), movie.title(), movie.contentRating(), screenings));
        }
        return intended;
    }

    private int displayPositionForAppend(List<Movie> movies, Movie parentMovie) {
        int parentIndex = movies.indexOf(parentMovie);
        int position = 0;
        for (int index = 0; index <= parentIndex; index++) {
            position += movies.get(index).screenings().size();
        }
        return position + 1;
    }

    private List<Movie> replaceScreening(
            List<Movie> movies, ScreeningLocation location, LocalDateTime startsAt) {
        List<Movie> intended = new ArrayList<>(movies);
        Movie parent = location.movie();
        List<Screening> screenings = new ArrayList<>(parent.screenings());
        screenings.set(location.screeningIndex(), new Screening(location.screening().id(), startsAt));
        intended.set(location.movieIndex(), new Movie(
                parent.id(), parent.title(), parent.contentRating(), screenings));
        return intended;
    }

    private String generateUniqueId(List<Movie> movies) {
        Set<String> existingIds = new HashSet<>();
        for (Movie movie : movies) {
            existingIds.add(movie.id());
            movie.screenings().stream().map(Screening::id).forEach(existingIds::add);
        }
        String candidate;
        do {
            candidate = "SCR-" + screeningIdGenerator.generate().toString().toLowerCase(Locale.ROOT);
        } while (existingIds.contains(candidate));
        return candidate;
    }

    private record ReadResult(String line, AdminWorkflowOutcome outcome) {
    }

    private record ActionResult(Integer choice, AdminWorkflowOutcome outcome) {
        private static ActionResult choice(int choice) {
            return new ActionResult(choice, null);
        }

        private static ActionResult outcome(AdminWorkflowOutcome outcome) {
            return new ActionResult(null, outcome);
        }
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
