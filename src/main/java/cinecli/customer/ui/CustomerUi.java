package cinecli.customer.ui;

import cinecli.customer.parser.ScreeningSelection;
import cinecli.admin.ui.AdminTerminal;
import cinecli.admin.ui.InputFailure;
import cinecli.admin.ui.SubmittedLine;
import cinecli.admin.ui.TerminalInput;
import cinecli.app.Utf8Terminal;
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
    private static final String BILL_TITLE = "BILL SUMMARY";
    private static final int BILL_WIDTH = 60;
    private static final int BILL_TITLE_END_COLUMN =
            (BILL_WIDTH + BILL_TITLE.length()) / 2;
    private static final String BILL_MAJOR_RULE = "=".repeat(BILL_WIDTH);
    private static final String BILL_SECTION_RULE = "-".repeat(BILL_WIDTH);
    private static final DateTimeFormatter SCREENING_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM uuuu, HH:mm", Locale.ENGLISH);
    private static final String NO_MOVIES_MESSAGE = "No movies are currently available.";

    private final AdminTerminal terminal;
    private final TerminalWriter outputWriter;
    private final TerminalWriter errorWriter;
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
        this(new Utf8Terminal(input, output, errorOutput));
    }

    /**
     * Creates a customer text UI over the shared application terminal.
     *
     * @param terminal Shared terminal adapter.
     */
    public CustomerUi(AdminTerminal terminal) {
        this.terminal = Objects.requireNonNull(terminal);
        this.outputWriter = new TerminalWriter(terminal, false);
        this.errorWriter = new TerminalWriter(terminal, true);
        this.output = new PrintWriter(outputWriter, true);
        this.errorOutput = new PrintWriter(errorWriter, true);
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
        TerminalInput input = readInput();
        if (input instanceof InputFailure) {
            showInputFailure();
        }
        return input instanceof SubmittedLine;
    }

    /**
     * Reads the next typed terminal result after displaying a customer prompt.
     *
     * @return Typed terminal result.
     */
    public TerminalInput readInput() {
        return terminal.readLine();
    }

    /**
     * Requests a catalog screening code from the user.
     *
     * @return Submitted code, or null if input ended or could not be read.
     */
    public TerminalInput requestScreeningSelection() {
        output.println();
        output.println("Select a screening using the movie number and timing letter (e.g., 3B):");
        return readInput();
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
    public TerminalInput requestSeatSelection() {
        output.println("Enter seat coordinates separated by spaces (e.g., G4 G5), or CANCEL:");
        return readInput();
    }

    /**
     * Requests confirmation for tentatively selected seats.
     *
     * @param seats Tentatively selected seats.
     * @return Submitted answer, or null if input ended or could not be read.
     */
    public TerminalInput requestSeatConfirmation(Set<SeatCoordinate> seats) {
        String seatList = formatSeatList(seats);
        output.println("Confirm seats " + seatList + "? (Y/N):");
        return readInput();
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
     * @param pricing Pricing that supplies the displayed prices.
     */
    public void showTicketTypeMenu(List<TicketType> ticketTypes, Pricing pricing) {
        Objects.requireNonNull(ticketTypes);
        Objects.requireNonNull(pricing);
        output.println();
        output.println("Ticket Types");
        for (TicketType ticketType : ticketTypes) {
            output.println(ticketType.getMenuNumber() + ". "
                    + ticketType.getDisplayName() + " - "
                    + formatPrice(pricing.ticketPriceInCents(ticketType)));
        }
    }

    /**
     * Requests a customer ticket type for one confirmed seat.
     *
     * @param seat Confirmed seat requiring a ticket type.
     * @return Submitted ticket type number, or null if input ended or could not be read.
     */
    public TerminalInput requestTicketType(SeatCoordinate seat) {
        Objects.requireNonNull(seat);
        output.println("Choose a ticket type for seat " + seat + " (1-3):");
        return readInput();
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
     * @param pricing Pricing that supplies the displayed prices.
     */
    public void showSnackMenu(List<SnackMenuItem> menuItems, Pricing pricing) {
        Objects.requireNonNull(menuItems);
        Objects.requireNonNull(pricing);
        output.println();
        output.println("Snack and Combo Menu");
        showSnackMenuSection("Snacks", menuItems, false, pricing);
        showSnackMenuSection("Combos", menuItems, true, pricing);
        output.println("0. Finish selection (or skip if none selected)");
    }

    /**
     * Requests a snack or combo menu number from the user.
     *
     * @return Submitted menu number, or null if input ended or could not be read.
     */
    public TerminalInput requestSnackSelection() {
        output.println("Choose an item by number, or enter 0 to finish:");
        return readInput();
    }

    /**
     * Requests the quantity of a snack or combo.
     *
     * @param menuItem Menu item whose quantity is requested.
     * @return Submitted quantity, or null if input ended or could not be read.
     */
    public TerminalInput requestSnackQuantity(SnackMenuItem menuItem) {
        Objects.requireNonNull(menuItem);
        output.println("Enter quantity for " + menuItem.getDisplayName()
                + " (positive whole number):");
        return readInput();
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
     * @param promotions Promotions available to apply.
     * @return Submitted code, a blank line to skip, or null if input ended or could not be read.
     */
    public TerminalInput requestPromoCode(List<PromoCode> promotions) {
        Objects.requireNonNull(promotions);
        output.println();
        output.println("Enter a promo code (" + formatPromotions(promotions)
                + "), or press ENTER to skip:");
        return readInput();
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
     * @param movie Selected movie.
     * @param screening Selected screening.
     */
    public void showBill(Bill bill, Movie movie, Screening screening) {
        writeBill(renderBill(bill, movie, screening));
    }

    /**
     * Renders the complete bill without writing to the terminal.
     *
     * @param bill Completed customer bill.
     * @param movie Selected movie.
     * @param screening Selected screening.
     * @return Complete bill-summary text.
     */
    public String renderBill(Bill bill, Movie movie, Screening screening) {
        Objects.requireNonNull(bill);
        Objects.requireNonNull(movie);
        Objects.requireNonNull(screening);

        StringBuilder billText = new StringBuilder();
        appendBillHeader(billText);
        appendBillTickets(billText, bill, movie, screening);
        appendBillSnacks(billText, bill);
        appendBillPromotion(billText, bill);
        appendBillTotals(billText, bill);
        return billText.toString();
    }

    /**
     * Writes a previously rendered bill-summary block.
     *
     * @param billText Complete bill-summary text.
     */
    public void writeBill(String billText) {
        output.print(Objects.requireNonNull(billText));
    }

    /**
     * Returns whether the shared terminal has accepted all attempted output.
     *
     * @return True when no terminal write has failed.
     */
    public boolean isOutputAvailable() {
        return !outputWriter.hasFailed() && !errorWriter.hasFailed();
    }

    /** Shows an input-failure message before the workflow terminates. */
    public void showInputFailure() {
        errorOutput.println("Unable to read input. CineCLI will exit.");
    }

    /** Shows the post-session role-routing prompt. */
    public void showPostSessionPrompt() {
        output.println("Press ENTER to start a new customer session, or enter /admin or /exit:");
    }

    private void appendBillHeader(StringBuilder billText) {
        billText.append('\n');
        appendLine(billText, BILL_MAJOR_RULE);
        billText.append(String.format(Locale.ROOT, "%" + BILL_TITLE_END_COLUMN + "s%n", BILL_TITLE));
        appendLine(billText, BILL_MAJOR_RULE);
    }

    private void appendBillTickets(
            StringBuilder billText, Bill bill, Movie movie, Screening screening) {
        billText.append('\n');
        appendLine(billText, "TICKETS");
        appendLine(billText, BILL_SECTION_RULE);
        appendLine(billText, "Movie: " + movie.title());
        appendLine(billText, "Time: " + screening.startsAt().format(SCREENING_TIME_FORMATTER));
        billText.append('\n');
        appendBillTableRow(billText, "Seat", "Type", "Unit Price");
        appendLine(billText, BILL_SECTION_RULE);
        for (TicketSelection selection : bill.ticketSelections()) {
            TicketType ticketType = selection.ticketType();
            appendBillTableRow(
                    billText,
                    selection.seat().toString(),
                    ticketType.getDisplayName(),
                    formatPrice(selection.unitPriceInCents()));
        }
        appendLine(billText, BILL_SECTION_RULE);
        appendBillLabelValue(
                billText,
                "Ticket Subtotal:", formatPrice(bill.getTicketSubtotalInCents()));
    }

    private void appendBillSnacks(StringBuilder billText, Bill bill) {
        billText.append('\n');
        appendLine(billText, "SNACKS AND COMBOS");
        appendLine(billText, BILL_SECTION_RULE);
        appendBillTableRow(billText, "Qty", "Item", "Unit Price");
        appendLine(billText, BILL_SECTION_RULE);
        if (bill.snackSelections().isEmpty()) {
            appendLine(billText, "None");
        } else {
            for (SnackSelection selection : bill.snackSelections()) {
                appendBillSnackSelection(billText, selection);
            }
        }
        appendLine(billText, BILL_SECTION_RULE);
        appendBillLabelValue(
                billText,
                "Snack Subtotal:", formatPrice(bill.getSnackSubtotalInCents()));
    }

    private void appendBillPromotion(StringBuilder billText, Bill bill) {
        billText.append('\n');
        appendLine(billText, "PROMOTION");
        appendLine(billText, BILL_SECTION_RULE);
        if (bill.promoCode().isPresent()) {
            PromoCode promoCode = bill.promoCode().orElseThrow();
            appendBillLabelValue(billText, "Promo Code:", promoCode.code());
            appendBillLabelValue(
                    billText,
                    "Discount:", promoCode.discountPercentage() + "% OFF");
        } else {
            appendBillLabelValue(billText, "Promo Code:", "None");
            appendBillLabelValue(billText, "Discount:", "0% OFF");
        }
        appendBillLabelValue(
                billText,
                "Amount Saved:", "-" + formatPrice(bill.getDiscountInCents()));
    }

    private void appendBillTotals(StringBuilder billText, Bill bill) {
        billText.append('\n');
        appendLine(billText, BILL_MAJOR_RULE);
        appendBillLabelValue(billText, "Subtotal:", formatPrice(bill.getSubtotalInCents()));
        appendBillLabelValue(billText, "Discount:", "-" + formatPrice(bill.getDiscountInCents()));
        appendLine(billText, BILL_SECTION_RULE);
        appendBillLabelValue(billText, "TOTAL:", formatPrice(bill.getTotalInCents()));
        appendLine(billText, BILL_MAJOR_RULE);
    }

    private void appendBillSnackSelection(StringBuilder billText, SnackSelection selection) {
        String displayName = selection.menuItem().getDisplayName();
        int descriptionIndex = displayName.indexOf(" (");
        String itemName = descriptionIndex < 0
                ? displayName
                : displayName.substring(0, descriptionIndex);
        appendBillTableRow(
                billText,
                Integer.toString(selection.quantity()),
                itemName,
                formatPrice(selection.unitPriceInCents()));
        if (descriptionIndex >= 0) {
            billText.append(String.format(
                    Locale.ROOT, "%12s%s%n", "", displayName.substring(descriptionIndex + 1)));
        }
    }

    private void appendBillTableRow(
            StringBuilder billText, String firstColumn, String secondColumn, String thirdColumn) {
        billText.append(String.format(
                Locale.ROOT, "%-12s%-30s%18s%n", firstColumn, secondColumn, thirdColumn));
    }

    private void appendBillLabelValue(StringBuilder billText, String label, String value) {
        billText.append(String.format(Locale.ROOT, "%-30s%30s%n", label, value));
    }

    private void appendLine(StringBuilder billText, String line) {
        billText.append(line).append('\n');
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
     * Shows a pricing data failure without exposing a stack trace.
     *
     * @param message User-readable failure explanation.
     */
    public void showPricingStorageError(String message) {
        errorOutput.println("Unable to load pricing: " + message);
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
            String heading, List<SnackMenuItem> menuItems, boolean isCombo, Pricing pricing) {
        output.println(heading + ":");
        for (SnackMenuItem menuItem : menuItems) {
            if (menuItem.isCombo() == isCombo) {
                output.println(menuItem.getMenuNumber() + ". "
                        + formatSnackMenuItem(menuItem, pricing));
            }
        }
    }

    private String formatSnackMenuItem(SnackMenuItem menuItem, Pricing pricing) {
        return menuItem.getDisplayName() + " - "
                + formatPrice(pricing.snackPriceInCents(menuItem));
    }

    private String formatSnackSelection(SnackSelection selection) {
        return selection.quantity() + " x " + selection.menuItem().getDisplayName()
                + " - " + formatPrice(selection.unitPriceInCents()) + " each";
    }

    private String formatTicketSelection(TicketSelection selection) {
        TicketType ticketType = selection.ticketType();
        return selection.seat() + ": " + ticketType.getDisplayName()
                + " - " + formatPrice(selection.unitPriceInCents());
    }

    private String formatPromotions(List<PromoCode> promotions) {
        if (promotions.isEmpty()) {
            return "no promotions are currently available";
        }
        return promotions.stream()
                .map(promotion -> promotion.code() + " for "
                        + promotion.discountPercentage() + "% off")
                .collect(Collectors.joining(" or "));
    }

    private String formatPrice(long priceInCents) {
        return String.format(
                Locale.ROOT, "S$%,d.%02d", priceInCents / 100, priceInCents % 100);
    }

    private static final class TerminalWriter extends Writer {
        private final AdminTerminal terminal;
        private final boolean isError;
        private boolean hasFailed;

        private TerminalWriter(AdminTerminal terminal, boolean isError) {
            this.terminal = terminal;
            this.isError = isError;
        }

        @Override
        public void write(char[] characters, int offset, int length) {
            String text = new String(characters, offset, length);
            boolean isWritten = isError ? terminal.writeError(text) : terminal.write(text);
            if (!isWritten) {
                hasFailed = true;
            }
        }

        @Override
        public void flush() {
            // AdminTerminal writes and flushes complete supplied text blocks.
        }

        @Override
        public void close() {
            // The application owns the shared terminal lifecycle.
        }

        private boolean hasFailed() {
            return hasFailed;
        }
    }
}
