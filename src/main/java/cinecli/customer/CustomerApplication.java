package cinecli.customer;

import cinecli.admin.ui.EndOfInput;
import cinecli.admin.ui.GlobalCommand;
import cinecli.admin.ui.InputFailure;
import cinecli.admin.ui.SubmittedLine;
import cinecli.admin.ui.TerminalInput;
import cinecli.customer.parser.ScreeningSelection;
import cinecli.customer.ui.CustomerUi;
import cinecli.model.Bill;
import cinecli.model.Movie;
import cinecli.model.Pricing;
import cinecli.model.PromoCode;
import cinecli.model.Screening;
import cinecli.model.SeatCoordinate;
import cinecli.model.SnackMenuItem;
import cinecli.model.SnackSelection;
import cinecli.model.TicketSelection;
import cinecli.model.TicketType;
import cinecli.storage.catalog.CatalogStorage;
import cinecli.storage.exception.CatalogStorageException;
import cinecli.storage.exception.PricingStorageException;
import cinecli.storage.exception.SeatStorageException;
import cinecli.storage.pricing.PricingStorage;
import cinecli.storage.seat.SeatStorage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Coordinates one customer purchase and its typed role-routing handoff. */
public final class CustomerApplication {
    private static final List<SnackMenuItem> SNACK_MENU_ITEMS = List.of(SnackMenuItem.values());
    private static final List<TicketType> TICKET_TYPES = List.of(TicketType.values());

    private final CustomerUi customerUi;
    private final CatalogStorage catalogStorage;
    private final SeatStorage seatStorage;
    private final PricingStorage pricingStorage;

    /** Creates a customer workflow using the supplied UI and storage. */
    public CustomerApplication(CustomerUi customerUi, CatalogStorage catalogStorage,
            SeatStorage seatStorage, PricingStorage pricingStorage) {
        this.customerUi = Objects.requireNonNull(customerUi);
        this.catalogStorage = Objects.requireNonNull(catalogStorage);
        this.seatStorage = Objects.requireNonNull(seatStorage);
        this.pricingStorage = Objects.requireNonNull(pricingStorage);
    }

    /** Runs one customer session and returns the requested router transition. */
    public CustomerWorkflowOutcome run() {
        customerUi.showWelcome();
        InputResult welcome = read(customerUi.readInput());
        if (welcome.outcome() != null) {
            return welcome.outcome();
        }
        Pricing pricing;
        try {
            pricing = pricingStorage.load();
        } catch (PricingStorageException exception) {
            customerUi.showPricingStorageError(exception.getMessage());
            return CustomerWorkflowOutcome.TERMINATED;
        }
        List<Movie> movies;
        try {
            movies = catalogStorage.load();
        } catch (CatalogStorageException exception) {
            customerUi.showCatalogError(exception.getMessage());
            return CustomerWorkflowOutcome.TERMINATED;
        }
        customerUi.showCatalog(movies);
        if (!customerUi.isOutputAvailable() || !hasScreenings(movies)) {
            return CustomerWorkflowOutcome.TERMINATED;
        }
        Result<SelectedScreening> selectedScreening = requestScreening(movies);
        if (selectedScreening.outcome() != null) {
            return selectedScreening.outcome();
        }
        return completePurchase(selectedScreening.value(), pricing, collectScreeningIds(movies));
    }

    private CustomerWorkflowOutcome completePurchase(SelectedScreening selectedScreening,
            Pricing pricing, Set<String> knownScreeningIds) {
        while (true) {
            Result<Set<SeatCoordinate>> seats = runSeatSelection(selectedScreening, knownScreeningIds);
            if (seats.outcome() != null) {
                return seats.outcome();
            }
            Result<Bill> bill = requestBill(seats.value(), pricing);
            if (bill.outcome() != null) {
                return bill.outcome();
            }
            String billText = customerUi.renderBill(
                    bill.value(), selectedScreening.movie(), selectedScreening.screening());
            try {
                seatStorage.confirmSeats(selectedScreening.screening().id(), seats.value(), knownScreeningIds);
            } catch (SeatStorageException exception) {
                if (exception.getMessage().contains("Seats are already taken for screening")) {
                    customerUi.showSeatSelectionError(
                            "selected seats are no longer available; choose again");
                    if (!customerUi.isOutputAvailable()) {
                        return CustomerWorkflowOutcome.TERMINATED;
                    }
                    continue;
                }
                customerUi.showSeatStorageError(exception.getMessage());
                return requestPostSession();
            }
            customerUi.writeBill(billText);
            if (!customerUi.isOutputAvailable()) {
                return CustomerWorkflowOutcome.TERMINATED;
            }
            return requestPostSession();
        }
    }

    private Result<Bill> requestBill(Set<SeatCoordinate> seats, Pricing pricing) {
        Result<List<TicketSelection>> tickets = runTicketSelection(seats, pricing);
        if (tickets.outcome() != null) {
            return Result.outcome(tickets.outcome());
        }
        Result<List<SnackSelection>> snacks = runSnackSelection(pricing);
        if (snacks.outcome() != null) {
            return Result.outcome(snacks.outcome());
        }
        Result<Optional<PromoCode>> promoCode = requestPromoCode(pricing);
        if (promoCode.outcome() != null) {
            return Result.outcome(promoCode.outcome());
        }
        return Result.value(new Bill(tickets.value(), snacks.value(), promoCode.value()));
    }

    private Result<SelectedScreening> requestScreening(List<Movie> movies) {
        while (true) {
            InputResult input = read(customerUi.requestScreeningSelection());
            if (input.outcome() != null) {
                return Result.outcome(input.outcome());
            }
            if (isCancelled(input.line())) {
                return Result.outcome(CustomerWorkflowOutcome.CUSTOMER);
            }
            try {
                ScreeningSelection selection = ScreeningSelection.parse(input.line());
                if (selection.movieNumber() > movies.size()) {
                    throw new IllegalArgumentException("movie " + selection.movieNumber()
                            + " does not exist");
                }
                Movie movie = movies.get(selection.movieNumber() - 1);
                if (selection.timingNumber() > movie.screenings().size()) {
                    throw new IllegalArgumentException("movie " + selection.movieNumber()
                            + " does not have timing "
                            + ScreeningSelection.formatTimingLabel(selection.timingNumber()));
                }
                return Result.value(new SelectedScreening(movie,
                        movie.screenings().get(selection.timingNumber() - 1)));
            } catch (IllegalArgumentException exception) {
                customerUi.showScreeningSelectionError(exception.getMessage());
            }
        }
    }

    private Result<Set<SeatCoordinate>> runSeatSelection(SelectedScreening selectedScreening,
            Set<String> knownScreeningIds) {
        Set<SeatCoordinate> takenSeats;
        try {
            takenSeats = seatStorage.loadTakenSeats(selectedScreening.screening().id(), knownScreeningIds);
        } catch (SeatStorageException exception) {
            customerUi.showSeatStorageError(exception.getMessage());
            return Result.outcome(CustomerWorkflowOutcome.TERMINATED);
        }
        while (true) {
            customerUi.showSeatMap(selectedScreening.movie(), selectedScreening.screening(), takenSeats);
            if (!customerUi.isOutputAvailable()) {
                return Result.outcome(CustomerWorkflowOutcome.TERMINATED);
            }
            if (takenSeats.size() == SeatCoordinate.TOTAL_SEATS) {
                customerUi.showNoSeatsAvailable();
                return Result.outcome(CustomerWorkflowOutcome.TERMINATED);
            }
            InputResult input = read(customerUi.requestSeatSelection());
            if (input.outcome() != null) {
                return Result.outcome(input.outcome());
            }
            if (isCancelled(input.line())) {
                customerUi.showSeatSelectionCancelled();
                return Result.outcome(CustomerWorkflowOutcome.CUSTOMER);
            }
            Set<SeatCoordinate> tentativeSeats;
            try {
                tentativeSeats = parseSeatCoordinates(input.line());
                rejectTakenSeats(tentativeSeats, takenSeats);
            } catch (IllegalArgumentException exception) {
                customerUi.showSeatSelectionError(exception.getMessage());
                continue;
            }
            Set<SeatCoordinate> unavailableSeats = new LinkedHashSet<>(takenSeats);
            unavailableSeats.addAll(tentativeSeats);
            customerUi.showSeatMap(selectedScreening.movie(), selectedScreening.screening(), unavailableSeats);
            Result<Boolean> confirmed = requestConfirmation(tentativeSeats);
            if (confirmed.outcome() != null) {
                return Result.outcome(confirmed.outcome());
            }
            if (confirmed.value()) {
                return Result.value(tentativeSeats);
            }
            customerUi.showTentativeSelectionCleared();
        }
    }

    private Result<List<TicketSelection>> runTicketSelection(Set<SeatCoordinate> seats,
            Pricing pricing) {
        customerUi.showTicketTypeMenu(TICKET_TYPES, pricing);
        List<TicketSelection> selections = new ArrayList<>();
        for (SeatCoordinate seat : seats.stream().sorted().toList()) {
            Result<TicketSelection> selection = requestTicketSelection(seat, pricing);
            if (selection.outcome() != null) {
                return Result.outcome(selection.outcome());
            }
            selections.add(selection.value());
        }
        List<TicketSelection> completed = List.copyOf(selections);
        customerUi.showTicketSelections(completed);
        return customerUi.isOutputAvailable() ? Result.value(completed)
                : Result.outcome(CustomerWorkflowOutcome.TERMINATED);
    }

    private Result<TicketSelection> requestTicketSelection(SeatCoordinate seat, Pricing pricing) {
        while (true) {
            InputResult input = read(customerUi.requestTicketType(seat));
            if (input.outcome() != null) {
                return Result.outcome(input.outcome());
            }
            if (isCancelled(input.line())) {
                return Result.outcome(CustomerWorkflowOutcome.CUSTOMER);
            }
            try {
                TicketType ticketType = TicketType.parse(input.line());
                return Result.value(new TicketSelection(seat, ticketType,
                        pricing.ticketPriceInCents(ticketType)));
            } catch (IllegalArgumentException exception) {
                customerUi.showTicketTypeError(exception.getMessage());
            }
        }
    }

    private Result<List<SnackSelection>> runSnackSelection(Pricing pricing) {
        customerUi.showSnackMenu(SNACK_MENU_ITEMS, pricing);
        Map<SnackMenuItem, SnackSelection> selections = new LinkedHashMap<>();
        while (true) {
            InputResult input = read(customerUi.requestSnackSelection());
            if (input.outcome() != null) {
                return Result.outcome(input.outcome());
            }
            if (isCancelled(input.line())) {
                return Result.outcome(CustomerWorkflowOutcome.CUSTOMER);
            }
            if (input.line().strip().equals("0")) {
                List<SnackSelection> completed = List.copyOf(selections.values());
                if (completed.isEmpty()) {
                    customerUi.showSnackSelectionSkipped();
                } else {
                    customerUi.showSnackSelections(completed);
                }
                return customerUi.isOutputAvailable() ? Result.value(completed)
                        : Result.outcome(CustomerWorkflowOutcome.TERMINATED);
            }
            try {
                SnackMenuItem menuItem = SnackMenuItem.parse(input.line());
                Result<SnackSelection> selection = requestSnackQuantity(menuItem, pricing);
                if (selection.outcome() != null) {
                    return Result.outcome(selection.outcome());
                }
                if (selections.put(menuItem, selection.value()) == null) {
                    customerUi.showSnackSelectionAdded(selection.value());
                } else {
                    customerUi.showSnackSelectionUpdated(selection.value());
                }
            } catch (IllegalArgumentException exception) {
                customerUi.showSnackSelectionError(exception.getMessage());
            }
        }
    }

    private Result<Optional<PromoCode>> requestPromoCode(Pricing pricing) {
        while (true) {
            InputResult input = read(customerUi.requestPromoCode(pricing.promotions()));
            if (input.outcome() != null) {
                return Result.outcome(input.outcome());
            }
            if (isCancelled(input.line())) {
                return Result.outcome(CustomerWorkflowOutcome.CUSTOMER);
            }
            if (input.line().isBlank()) {
                return Result.value(Optional.empty());
            }
            try {
                String code = new PromoCode(input.line(), 1).code();
                Optional<PromoCode> promotion = pricing.findPromotion(code);
                if (promotion.isEmpty()) {
                    throw new IllegalArgumentException("promo code " + code + " is not available");
                }
                return Result.value(promotion);
            } catch (IllegalArgumentException exception) {
                customerUi.showPromoCodeError(exception.getMessage());
            }
        }
    }

    private Result<SnackSelection> requestSnackQuantity(SnackMenuItem menuItem, Pricing pricing) {
        while (true) {
            InputResult input = read(customerUi.requestSnackQuantity(menuItem));
            if (input.outcome() != null) {
                return Result.outcome(input.outcome());
            }
            if (isCancelled(input.line())) {
                return Result.outcome(CustomerWorkflowOutcome.CUSTOMER);
            }
            try {
                return Result.value(new SnackSelection(menuItem, SnackSelection.parseQuantity(input.line()),
                        pricing.snackPriceInCents(menuItem)));
            } catch (IllegalArgumentException exception) {
                customerUi.showSnackQuantityError(exception.getMessage());
            }
        }
    }

    private Result<Boolean> requestConfirmation(Set<SeatCoordinate> seats) {
        while (true) {
            InputResult input = read(customerUi.requestSeatConfirmation(seats));
            if (input.outcome() != null) {
                return Result.outcome(input.outcome());
            }
            if (isCancelled(input.line())) {
                customerUi.showTentativeSelectionCleared();
                return Result.outcome(CustomerWorkflowOutcome.CUSTOMER);
            }
            String answer = input.line().strip().toUpperCase(Locale.ROOT);
            if (answer.equals("Y")) {
                return Result.value(true);
            }
            if (answer.equals("N")) {
                return Result.value(false);
            }
            customerUi.showSeatSelectionError("enter Y to confirm or N to choose again");
        }
    }

    private CustomerWorkflowOutcome requestPostSession() {
        customerUi.showPostSessionPrompt();
        InputResult input = read(customerUi.readInput());
        return input.outcome() == null ? CustomerWorkflowOutcome.CUSTOMER : input.outcome();
    }

    private InputResult read(TerminalInput input) {
        if (!customerUi.isOutputAvailable()) {
            return InputResult.outcome(CustomerWorkflowOutcome.TERMINATED);
        }
        if (input instanceof SubmittedLine(String line)) {
            return InputResult.line(line);
        }
        if (input instanceof GlobalCommand(GlobalCommand.Type type)) {
            return InputResult.outcome(switch (type) {
                case ADMIN -> CustomerWorkflowOutcome.ADMIN;
                case CUSTOMER -> CustomerWorkflowOutcome.CUSTOMER;
                case EXIT -> CustomerWorkflowOutcome.EXIT;
            });
        }
        if (input instanceof InputFailure) {
            customerUi.showInputFailure();
        }
        if (input instanceof EndOfInput || input instanceof InputFailure) {
            return InputResult.outcome(CustomerWorkflowOutcome.TERMINATED);
        }
        throw new IllegalStateException("Unsupported terminal input: " + input);
    }

    private boolean isCancelled(String input) {
        return input.strip().equalsIgnoreCase("CANCEL") || input.strip().equalsIgnoreCase("/cancel");
    }

    private Set<SeatCoordinate> parseSeatCoordinates(String input) {
        if (input.strip().isEmpty()) {
            throw new IllegalArgumentException("enter at least one seat coordinate");
        }
        Set<SeatCoordinate> seats = new LinkedHashSet<>();
        for (String text : input.strip().split("\\s+")) {
            SeatCoordinate seat = SeatCoordinate.parse(text.toUpperCase(Locale.ROOT));
            if (!seats.add(seat)) {
                throw new IllegalArgumentException("seat " + seat + " was entered more than once");
            }
        }
        return Set.copyOf(seats);
    }

    private void rejectTakenSeats(Set<SeatCoordinate> tentativeSeats, Set<SeatCoordinate> takenSeats) {
        for (SeatCoordinate seat : tentativeSeats) {
            if (takenSeats.contains(seat)) {
                throw new IllegalArgumentException("seat " + seat + " is already taken");
            }
        }
    }

    private boolean hasScreenings(List<Movie> movies) {
        return movies.stream().anyMatch(movie -> !movie.screenings().isEmpty());
    }

    private Set<String> collectScreeningIds(List<Movie> movies) {
        Set<String> ids = new LinkedHashSet<>();
        for (Movie movie : movies) {
            for (Screening screening : movie.screenings()) {
                ids.add(screening.id());
            }
        }
        return Set.copyOf(ids);
    }

    private record SelectedScreening(Movie movie, Screening screening) {
    }

    private record InputResult(String line, CustomerWorkflowOutcome outcome) {
        private static InputResult line(String line) {
            return new InputResult(Objects.requireNonNull(line), null);
        }

        private static InputResult outcome(CustomerWorkflowOutcome outcome) {
            return new InputResult(null, Objects.requireNonNull(outcome));
        }
    }

    private record Result<T>(T value, CustomerWorkflowOutcome outcome) {
        private static <T> Result<T> value(T value) {
            return new Result<>(Objects.requireNonNull(value), null);
        }

        private static <T> Result<T> outcome(CustomerWorkflowOutcome outcome) {
            return new Result<>(null, Objects.requireNonNull(outcome));
        }
    }
}
