package cinecli.ui;

import cinecli.model.Bill;
import cinecli.model.Movie;
import cinecli.model.PromoCode;
import cinecli.model.Screening;
import cinecli.model.ScreeningSelection;
import cinecli.model.SeatCoordinate;
import cinecli.model.SnackMenuItem;
import cinecli.model.SnackSelection;
import cinecli.model.TicketSelection;
import cinecli.model.TicketType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Displays the customer catalog, seat selection, ticket selection, and snack selection.
 */
public final class CustomerUi {
    private static final DateTimeFormatter SCREENING_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM uuuu, HH:mm", Locale.ENGLISH);
    private static final String NO_MOVIES_MESSAGE = "No movies are currently available.";

    private final BufferedReader input;
    private final PrintWriter output;
    private final PrintWriter errorOutput;

    /**
     * Creates a customer text UI.
     *
     * @param input User input source.
     * @param output Normal output destination.
     * @param errorOutput Error output destination.
     */
    public CustomerUi(Reader input, Writer output, Writer errorOutput) {
        this.input = new BufferedReader(Objects.requireNonNull(input));
        this.output = new PrintWriter(Objects.requireNonNull(output), true);
        this.errorOutput = new PrintWriter(Objects.requireNonNull(errorOutput), true);
    }

    /**
     * Shows the customer welcome screen.
     */
    public void showWelcome() {
        output.println("""
                 ________  ___  ________   _______   ________  ___       ___    \s
                |\\   ____\\|\\  \\|\\   ___  \\|\\  ___ \\ |\\   ____\\|\\  \\     |\\  \\   \s
                \\ \\  \\___|\\ \\  \\ \\  \\\\ \\  \\ \\   __/|\\ \\  \\___|\\ \\  \\    \\ \\  \\  \s
                 \\ \\  \\    \\ \\  \\ \\  \\\\ \\  \\ \\  \\_|/_\\ \\  \\    \\ \\  \\    \\ \\  \\ \s
                  \\ \\  \\____\\ \\  \\ \\  \\\\ \\  \\ \\  \\_|\\ \\ \\  \\____\\ \\  \\____\\ \\  \\\s
                   \\ \\_______\\ \\__\\ \\__\\\\ \\__\\ \\_______\\ \\_______\\ \\_______\\ \\__\\
                    \\|_______|\\|__|\\|__| \\|__|\\|_______|\\|_______|\\|_______|\\|__|
                """);
        output.println("Welcome to CineCLI");
        output.println("Press ENTER to proceed");
    }

    /**
     * Waits until the user submits one line by pressing ENTER.
     *
     * @return True if input was submitted, or false if input ended or could not be read.
     */
    public boolean hasUserProceeded() {
        return readLine() != null;
    }

    /**
     * Requests a catalog screening code from the user.
     *
     * @return Submitted code, or null if input ended or could not be read.
     */
    public String requestScreeningSelection() {
        output.println();
        output.println("Select a screening using the movie number and timing letter (e.g., 3B):");
        return readLine();
    }

    /**
     * Shows why a catalog screening code was rejected.
     *
     * @param message Rejection explanation.
     */
    public void showScreeningSelectionError(String message) {
        output.println("Invalid screening selection: " + message);
    }

    /**
     * Shows the fixed seat map for a selected screening.
     *
     * @param movie Selected movie.
     * @param screening Selected screening.
     * @param unavailableSeats Taken and tentatively selected seats.
     */
    public void showSeatMap(
            Movie movie, Screening screening, Set<SeatCoordinate> unavailableSeats) {
        Objects.requireNonNull(movie);
        Objects.requireNonNull(screening);
        Objects.requireNonNull(unavailableSeats);

        output.println();
        output.println("Seat Selection");
        output.println("Movie: " + movie.title());
        output.println("Screening: " + screening.startsAt().format(SCREENING_TIME_FORMATTER));
        output.println();
        output.println("                               SCREEN");
        output.println("    " + "=".repeat(SeatCoordinate.LAST_NUMBER * 3));
        for (char row = SeatCoordinate.LAST_ROW; row >= SeatCoordinate.FIRST_ROW; row--) {
            output.println(formatSeatRow(row, unavailableSeats));
        }
        output.println(formatSeatNumberAxis());
        output.println();
        output.println("O = Available    X = Taken or tentatively selected");
    }

    /**
     * Requests one or more seat coordinates from the user.
     *
     * @return Submitted coordinates, or null if input ended or could not be read.
     */
    public String requestSeatSelection() {
        output.println("Enter seat coordinates separated by spaces (e.g., G4 G5), or CANCEL:");
        return readLine();
    }

    /**
     * Requests confirmation for tentatively selected seats.
     *
     * @param seats Tentatively selected seats.
     * @return Submitted answer, or null if input ended or could not be read.
     */
    public String requestSeatConfirmation(Set<SeatCoordinate> seats) {
        String seatList = formatSeatList(seats);
        output.println("Confirm seats " + seatList + "? (Y/N):");
        return readLine();
    }

    /**
     * Shows why a seat selection was rejected.
     *
     * @param message Rejection explanation.
     */
    public void showSeatSelectionError(String message) {
        output.println("Invalid seat selection: " + message);
    }

    /**
     * Shows that the current tentative seat selection was cleared.
     */
    public void showSeatSelectionCancelled() {
        output.println("Seat selection cancelled.");
    }

    /**
     * Shows that tentative seats were cleared so the user can choose again.
     */
    public void showTentativeSelectionCleared() {
        output.println("Tentative seat selection cleared.");
    }

    /**
     * Shows that the selected seats were confirmed.
     *
     * @param seats Confirmed seats.
     */
    public void showSeatsConfirmed(Set<SeatCoordinate> seats) {
        String seatList = formatSeatList(seats);
        output.println("Seats confirmed: " + seatList);
    }

    /**
     * Shows the fixed customer ticket types and prices.
     *
     * @param ticketTypes Ticket types in display order.
     */
    public void showTicketTypeMenu(List<TicketType> ticketTypes) {
        Objects.requireNonNull(ticketTypes);
        output.println();
        output.println("Ticket Types");
        for (TicketType ticketType : ticketTypes) {
            output.println(ticketType.getMenuNumber() + ". "
                    + ticketType.getDisplayName() + " - "
                    + formatPrice(ticketType.getPriceInCents()));
        }
    }

    /**
     * Requests a customer ticket type for one confirmed seat.
     *
     * @param seat Confirmed seat requiring a ticket type.
     * @return Submitted ticket type number, or null if input ended or could not be read.
     */
    public String requestTicketType(SeatCoordinate seat) {
        Objects.requireNonNull(seat);
        output.println("Choose a ticket type for seat " + seat + " (1-3):");
        return readLine();
    }

    /**
     * Shows why a customer ticket type was rejected.
     *
     * @param message Rejection explanation.
     */
    public void showTicketTypeError(String message) {
        output.println("Invalid ticket type: " + message);
    }

    /**
     * Shows the selected ticket type and price for every confirmed seat.
     *
     * @param selections Completed ticket selections in seat order.
     */
    public void showTicketSelections(List<TicketSelection> selections) {
        Objects.requireNonNull(selections);
        output.println();
        output.println("Selected Tickets");
        for (TicketSelection selection : selections) {
            output.println("- " + formatTicketSelection(selection));
        }
    }

    /**
     * Shows the fixed snack and combo menu.
     *
     * @param menuItems Snack and combo items in display order.
     */
    public void showSnackMenu(List<SnackMenuItem> menuItems) {
        Objects.requireNonNull(menuItems);
        output.println();
        output.println("Snack and Combo Menu");
        showSnackMenuSection("Snacks", menuItems, false);
        showSnackMenuSection("Combos", menuItems, true);
        output.println("0. Finish selection (or skip if none selected)");
    }

    /**
     * Requests a snack or combo menu number from the user.
     *
     * @return Submitted menu number, or null if input ended or could not be read.
     */
    public String requestSnackSelection() {
        output.println("Choose an item by number, or enter 0 to finish:");
        return readLine();
    }

    /**
     * Requests the quantity of a snack or combo.
     *
     * @param menuItem Menu item whose quantity is requested.
     * @return Submitted quantity, or null if input ended or could not be read.
     */
    public String requestSnackQuantity(SnackMenuItem menuItem) {
        Objects.requireNonNull(menuItem);
        output.println("Enter quantity for " + menuItem.getDisplayName()
                + " (positive whole number):");
        return readLine();
    }

    /**
     * Shows why a snack or combo selection was rejected.
     *
     * @param message Rejection explanation.
     */
    public void showSnackSelectionError(String message) {
        output.println("Invalid snack selection: " + message);
    }

    /**
     * Shows why a snack or combo quantity was rejected.
     *
     * @param message Rejection explanation.
     */
    public void showSnackQuantityError(String message) {
        output.println("Invalid snack quantity: " + message);
    }

    /**
     * Shows a newly added snack or combo selection.
     *
     * @param selection Added selection.
     */
    public void showSnackSelectionAdded(SnackSelection selection) {
        Objects.requireNonNull(selection);
        output.println("Snack/combo added: " + formatSnackSelection(selection));
    }

    /**
     * Shows an updated snack or combo selection.
     *
     * @param selection Updated selection.
     */
    public void showSnackSelectionUpdated(SnackSelection selection) {
        Objects.requireNonNull(selection);
        output.println("Snack/combo updated: " + formatSnackSelection(selection));
    }

    /**
     * Shows all snack and combo selections in their original selection order.
     *
     * @param selections Completed selections.
     */
    public void showSnackSelections(List<SnackSelection> selections) {
        Objects.requireNonNull(selections);
        output.println();
        output.println("Selected Snacks and Combos");
        for (SnackSelection selection : selections) {
            output.println("- " + formatSnackSelection(selection));
        }
    }

    /**
     * Shows that the customer skipped the optional snack and combo menu.
     */
    public void showSnackSelectionSkipped() {
        output.println("No snacks or combos selected.");
    }

    /**
     * Requests an optional promotion code.
     *
     * @return Submitted code, a blank line to skip, or null if input ended or could not be read.
     */
    public String requestPromoCode() {
        output.println();
        output.println("Enter a promo code (CS2103 for 20% off or CS3227 for 99% off),"
                + " or press ENTER to skip:");
        return readLine();
    }

    /**
     * Shows why a promotion code was rejected.
     *
     * @param message Rejection explanation.
     */
    public void showPromoCodeError(String message) {
        output.println("Invalid promo code: " + message);
    }

    /**
     * Shows the itemized bill and exact payable total.
     *
     * @param bill Completed customer bill.
     */
    public void showBill(Bill bill) {
        Objects.requireNonNull(bill);
        output.println();
        output.println("Bill Summary");
        output.println("Tickets:");
        for (TicketSelection selection : bill.ticketSelections()) {
            output.println("- " + formatTicketSelection(selection));
        }
        output.println("Ticket subtotal: " + formatPrice(bill.getTicketSubtotalInCents()));

        output.println("Snacks and Combos:");
        if (bill.snackSelections().isEmpty()) {
            output.println("None");
        } else {
            for (SnackSelection selection : bill.snackSelections()) {
                output.println("- " + formatSnackSelection(selection));
            }
        }
        output.println("Snack subtotal: " + formatPrice(bill.getSnackSubtotalInCents()));
        output.println("Subtotal: " + formatPrice(bill.getSubtotalInCents()));

        if (bill.promoCode().isPresent()) {
            PromoCode promoCode = bill.promoCode().orElseThrow();
            output.println("Promo code: " + promoCode.getCode()
                    + " (" + promoCode.getDiscountPercentage() + "% off)");
            output.println("Discount: -" + formatPrice(bill.getDiscountInCents()));
        } else {
            output.println("Promo code: None");
        }
        output.println("Total: " + formatPrice(bill.getTotalInCents()));
    }

    /**
     * Shows that the selected screening has no available seats.
     */
    public void showNoSeatsAvailable() {
        output.println("No seats are available for this screening.");
    }

    /**
     * Shows a seat data failure without exposing a stack trace.
     *
     * @param message User-readable failure explanation.
     */
    public void showSeatStorageError(String message) {
        errorOutput.println("Unable to load or update seat availability: " + message);
    }

    /**
     * Shows all movies and their available screenings.
     *
     * @param movies Movies to display.
     */
    public void showCatalog(List<Movie> movies) {
        Objects.requireNonNull(movies);
        output.println();
        output.println("Movie Catalog");
        if (movies.isEmpty()) {
            output.println(NO_MOVIES_MESSAGE);
            return;
        }

        for (int i = 0; i < movies.size(); i++) {
            showMovie(i + 1, movies.get(i));
        }
    }

    /**
     * Shows a catalog loading failure without exposing a stack trace.
     *
     * @param message User-readable failure explanation.
     */
    public void showCatalogError(String message) {
        errorOutput.println("Unable to load the movie catalog: " + message);
    }

    private void showMovie(int displayNumber, Movie movie) {
        output.println(displayNumber + ". " + movie.title());
        output.println("   Rating: " + movie.contentRating());
        output.println("   Screenings:");
        if (movie.screenings().isEmpty()) {
            output.println("   No screenings available.");
            return;
        }

        for (int i = 0; i < movie.screenings().size(); i++) {
            Screening screening = movie.screenings().get(i);
            String timingLabel = ScreeningSelection.formatTimingLabel(i + 1);
            output.println("   " + timingLabel + ". "
                    + screening.startsAt().format(SCREENING_TIME_FORMATTER));
        }
    }

    private String formatSeatRow(char row, Set<SeatCoordinate> unavailableSeats) {
        StringBuilder seatRow = new StringBuilder();
        seatRow.append(row).append("   ");
        for (int number = SeatCoordinate.FIRST_NUMBER;
                number <= SeatCoordinate.LAST_NUMBER;
                number++) {
            SeatCoordinate coordinate = new SeatCoordinate(row, number);
            char marker = unavailableSeats.contains(coordinate) ? 'X' : 'O';
            seatRow.append(' ').append(marker).append(' ');
        }
        return seatRow.toString();
    }

    private String formatSeatNumberAxis() {
        StringBuilder axis = new StringBuilder("    ");
        for (int number = SeatCoordinate.FIRST_NUMBER;
                number <= SeatCoordinate.LAST_NUMBER;
                number++) {
            if (number < 10) {
                axis.append(' ');
            }
            axis.append(number).append(' ');
        }
        return axis.toString();
    }

    private String formatSeatList(Set<SeatCoordinate> seats) {
        return seats.stream()
                .sorted()
                .map(SeatCoordinate::toString)
                .collect(Collectors.joining(", "));
    }

    private void showSnackMenuSection(
            String heading, List<SnackMenuItem> menuItems, boolean isCombo) {
        output.println(heading + ":");
        for (SnackMenuItem menuItem : menuItems) {
            if (menuItem.isCombo() == isCombo) {
                output.println(menuItem.getMenuNumber() + ". " + formatSnackMenuItem(menuItem));
            }
        }
    }

    private String formatSnackMenuItem(SnackMenuItem menuItem) {
        return menuItem.getDisplayName() + " - " + formatPrice(menuItem.getPriceInCents());
    }

    private String formatSnackSelection(SnackSelection selection) {
        return selection.quantity() + " x " + selection.menuItem().getDisplayName()
                + " - " + formatPrice(selection.menuItem().getPriceInCents()) + " each";
    }

    private String formatTicketSelection(TicketSelection selection) {
        TicketType ticketType = selection.ticketType();
        return selection.seat() + ": " + ticketType.getDisplayName()
                + " - " + formatPrice(ticketType.getPriceInCents());
    }

    private String formatPrice(long priceInCents) {
        return String.format(
                Locale.ROOT, "S$%d.%02d", priceInCents / 100, priceInCents % 100);
    }

    private String readLine() {
        try {
            return input.readLine();
        } catch (IOException exception) {
            errorOutput.println("Unable to read input. CineCLI will exit.");
            return null;
        }
    }
}
