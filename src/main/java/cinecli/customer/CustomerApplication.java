package cinecli.customer;

import cinecli.customer.parser.ScreeningSelection;
import cinecli.customer.ui.CustomerUi;
import cinecli.model.Bill;
import cinecli.model.Movie;
import cinecli.model.PromoCode;
import cinecli.model.Screening;
import cinecli.model.SeatCoordinate;
import cinecli.model.SnackMenuItem;
import cinecli.model.SnackSelection;
import cinecli.model.TicketSelection;
import cinecli.model.TicketType;
import cinecli.storage.catalog.CatalogStorage;
import cinecli.storage.exception.CatalogStorageException;
import cinecli.storage.exception.SeatStorageException;
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

/**
 * Coordinates customer catalog, seat, ticket, snack, promotion, and billing operations.
 */
public final class CustomerApplication {
    private static final List<SnackMenuItem> SNACK_MENU_ITEMS =
            List.of(SnackMenuItem.values());
    private static final List<TicketType> TICKET_TYPES = List.of(TicketType.values());

    private final CustomerUi customerUi;
    private final CatalogStorage catalogStorage;
    private final SeatStorage seatStorage;

    /**
     * Creates a customer workflow using the supplied UI and storage.
     *
     * @param customerUi Customer-facing text UI.
     * @param catalogStorage Movie catalog storage.
     * @param seatStorage Temporary seat occupancy storage.
     */
    public CustomerApplication(
            CustomerUi customerUi, CatalogStorage catalogStorage, SeatStorage seatStorage) {
        this.customerUi = Objects.requireNonNull(customerUi);
        this.catalogStorage = Objects.requireNonNull(catalogStorage);
        this.seatStorage = Objects.requireNonNull(seatStorage);
    }

    /**
     * Runs the customer workflow from the welcome screen through the final bill.
     */
    public void run() {
        customerUi.showWelcome();
        if (!customerUi.hasUserProceeded()) {
            return;
        }

        List<Movie> movies;
        try {
            movies = catalogStorage.load();
        } catch (CatalogStorageException exception) {
            customerUi.showCatalogError(exception.getMessage());
            return;
        }

        customerUi.showCatalog(movies);
        if (!hasScreenings(movies)) {
            return;
        }

        SelectedScreening selectedScreening = requestScreening(movies);
        if (selectedScreening == null) {
            return;
        }
        Set<SeatCoordinate> confirmedSeats = runSeatSelection(
                selectedScreening, collectScreeningIds(movies));
        if (confirmedSeats == null) {
            return;
        }

        Bill bill = requestBill(confirmedSeats);
        if (bill != null) {
            customerUi.showBill(
                    bill, selectedScreening.movie(), selectedScreening.screening());
        }
    }

    private Bill requestBill(Set<SeatCoordinate> confirmedSeats) {
        List<TicketSelection> ticketSelections = runTicketSelection(confirmedSeats);
        if (ticketSelections == null) {
            return null;
        }
        List<SnackSelection> snackSelections = runSnackSelection();
        if (snackSelections == null) {
            return null;
        }
        PromoSelection promoSelection = requestPromoCode();
        if (promoSelection == null) {
            return null;
        }

        return new Bill(
                ticketSelections, snackSelections, promoSelection.promoCode());
    }

    private SelectedScreening requestScreening(List<Movie> movies) {
        while (true) {
            String input = customerUi.requestScreeningSelection();
            if (input == null) {
                return null;
            }

            try {
                ScreeningSelection selection = ScreeningSelection.parse(input);
                return resolveScreening(movies, selection);
            } catch (IllegalArgumentException exception) {
                customerUi.showScreeningSelectionError(exception.getMessage());
            }
        }
    }

    private SelectedScreening resolveScreening(
            List<Movie> movies, ScreeningSelection selection) {
        if (selection.movieNumber() > movies.size()) {
            throw new IllegalArgumentException(
                    "movie " + selection.movieNumber() + " does not exist");
        }

        Movie movie = movies.get(selection.movieNumber() - 1);
        if (selection.timingNumber() > movie.screenings().size()) {
            String timingLabel = ScreeningSelection.formatTimingLabel(selection.timingNumber());
            throw new IllegalArgumentException(
                    "movie " + selection.movieNumber() + " does not have timing " + timingLabel);
        }

        Screening screening = movie.screenings().get(selection.timingNumber() - 1);
        return new SelectedScreening(movie, screening);
    }

    private Set<SeatCoordinate> runSeatSelection(
            SelectedScreening selectedScreening, Set<String> knownScreeningIds) {
        Set<SeatCoordinate> takenSeats;
        try {
            takenSeats = seatStorage.loadTakenSeats(
                    selectedScreening.screening().id(), knownScreeningIds);
        } catch (SeatStorageException exception) {
            customerUi.showSeatStorageError(exception.getMessage());
            return null;
        }

        while (true) {
            customerUi.showSeatMap(
                    selectedScreening.movie(), selectedScreening.screening(), takenSeats);
            if (takenSeats.size() == SeatCoordinate.TOTAL_SEATS) {
                customerUi.showNoSeatsAvailable();
                return null;
            }

            String input = customerUi.requestSeatSelection();
            if (input == null) {
                return null;
            }
            if (input.strip().equalsIgnoreCase("CANCEL")) {
                customerUi.showSeatSelectionCancelled();
                return null;
            }

            Set<SeatCoordinate> tentativeSeats;
            try {
                tentativeSeats = parseSeatCoordinates(input);
                rejectTakenSeats(tentativeSeats, takenSeats);
            } catch (IllegalArgumentException exception) {
                customerUi.showSeatSelectionError(exception.getMessage());
                continue;
            }

            Set<SeatCoordinate> unavailableSeats = new LinkedHashSet<>(takenSeats);
            unavailableSeats.addAll(tentativeSeats);
            customerUi.showSeatMap(
                    selectedScreening.movie(), selectedScreening.screening(), unavailableSeats);

            Boolean isConfirmed = requestConfirmation(tentativeSeats);
            if (isConfirmed == null) {
                return null;
            }
            if (!isConfirmed) {
                customerUi.showTentativeSelectionCleared();
                continue;
            }

            try {
                seatStorage.confirmSeats(
                        selectedScreening.screening().id(), tentativeSeats, knownScreeningIds);
                customerUi.showSeatsConfirmed(tentativeSeats);
            } catch (SeatStorageException exception) {
                customerUi.showSeatStorageError(exception.getMessage());
                return null;
            }
            return tentativeSeats;
        }
    }

    private List<TicketSelection> runTicketSelection(Set<SeatCoordinate> confirmedSeats) {
        customerUi.showTicketTypeMenu(TICKET_TYPES);
        List<TicketSelection> selections = new ArrayList<>();
        for (SeatCoordinate seat : confirmedSeats.stream().sorted().toList()) {
            TicketSelection selection = requestTicketSelection(seat);
            if (selection == null) {
                return null;
            }
            selections.add(selection);
        }

        List<TicketSelection> completedSelections = List.copyOf(selections);
        customerUi.showTicketSelections(completedSelections);
        return completedSelections;
    }

    private TicketSelection requestTicketSelection(SeatCoordinate seat) {
        while (true) {
            String input = customerUi.requestTicketType(seat);
            if (input == null) {
                return null;
            }

            try {
                return new TicketSelection(seat, TicketType.parse(input));
            } catch (IllegalArgumentException exception) {
                customerUi.showTicketTypeError(exception.getMessage());
            }
        }
    }

    private List<SnackSelection> runSnackSelection() {
        customerUi.showSnackMenu(SNACK_MENU_ITEMS);
        Map<SnackMenuItem, SnackSelection> selections = new LinkedHashMap<>();
        while (true) {
            String input = customerUi.requestSnackSelection();
            if (input == null) {
                return null;
            }
            if (input.strip().equals("0")) {
                List<SnackSelection> completedSelections =
                        List.copyOf(selections.values());
                if (selections.isEmpty()) {
                    customerUi.showSnackSelectionSkipped();
                } else {
                    customerUi.showSnackSelections(completedSelections);
                }
                return completedSelections;
            }

            try {
                SnackMenuItem menuItem = SnackMenuItem.parse(input);
                SnackSelection selection = requestSnackQuantity(menuItem);
                if (selection == null) {
                    return null;
                }

                boolean isUpdated = selections.put(menuItem, selection) != null;
                if (isUpdated) {
                    customerUi.showSnackSelectionUpdated(selection);
                } else {
                    customerUi.showSnackSelectionAdded(selection);
                }
            } catch (IllegalArgumentException exception) {
                customerUi.showSnackSelectionError(exception.getMessage());
            }
        }
    }

    private PromoSelection requestPromoCode() {
        while (true) {
            String input = customerUi.requestPromoCode();
            if (input == null) {
                return null;
            }
            if (input.isBlank()) {
                return new PromoSelection(Optional.empty());
            }

            try {
                return new PromoSelection(Optional.of(PromoCode.parse(input)));
            } catch (IllegalArgumentException exception) {
                customerUi.showPromoCodeError(exception.getMessage());
            }
        }
    }

    private SnackSelection requestSnackQuantity(SnackMenuItem menuItem) {
        while (true) {
            String input = customerUi.requestSnackQuantity(menuItem);
            if (input == null) {
                return null;
            }

            try {
                return new SnackSelection(menuItem, SnackSelection.parseQuantity(input));
            } catch (IllegalArgumentException exception) {
                customerUi.showSnackQuantityError(exception.getMessage());
            }
        }
    }

    private Set<SeatCoordinate> parseSeatCoordinates(String input) {
        String normalizedInput = input.strip();
        if (normalizedInput.isEmpty()) {
            throw new IllegalArgumentException("enter at least one seat coordinate");
        }

        Set<SeatCoordinate> seats = new LinkedHashSet<>();
        for (String coordinateText : normalizedInput.split("\\s+")) {
            SeatCoordinate coordinate = SeatCoordinate.parse(
                    coordinateText.toUpperCase(Locale.ROOT));
            if (!seats.add(coordinate)) {
                throw new IllegalArgumentException(
                        "seat " + coordinate + " was entered more than once");
            }
        }
        return Set.copyOf(seats);
    }

    private void rejectTakenSeats(
            Set<SeatCoordinate> tentativeSeats, Set<SeatCoordinate> takenSeats) {
        for (SeatCoordinate coordinate : tentativeSeats) {
            if (takenSeats.contains(coordinate)) {
                throw new IllegalArgumentException("seat " + coordinate + " is already taken");
            }
        }
    }

    private Boolean requestConfirmation(Set<SeatCoordinate> tentativeSeats) {
        while (true) {
            String input = customerUi.requestSeatConfirmation(tentativeSeats);
            if (input == null) {
                return null;
            }

            String normalizedInput = input.strip().toUpperCase(Locale.ROOT);
            if (normalizedInput.equals("Y")) {
                return true;
            }
            if (normalizedInput.equals("N")) {
                return false;
            }
            customerUi.showSeatSelectionError("enter Y to confirm or N to choose again");
        }
    }

    private boolean hasScreenings(List<Movie> movies) {
        return movies.stream().anyMatch(movie -> !movie.screenings().isEmpty());
    }

    private Set<String> collectScreeningIds(List<Movie> movies) {
        Set<String> screeningIds = new LinkedHashSet<>();
        for (Movie movie : movies) {
            for (Screening screening : movie.screenings()) {
                screeningIds.add(screening.id());
            }
        }
        return Set.copyOf(screeningIds);
    }

    private record SelectedScreening(Movie movie, Screening screening) {
    }

    private record PromoSelection(Optional<PromoCode> promoCode) {
        private PromoSelection {
            Objects.requireNonNull(promoCode);
        }
    }

}
