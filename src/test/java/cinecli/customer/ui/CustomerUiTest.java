package cinecli.customer.ui;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.admin.ui.AdminTerminal;
import cinecli.admin.ui.EndOfInput;
import cinecli.admin.ui.TerminalInput;
import cinecli.model.Bill;
import cinecli.model.ContentRating;
import cinecli.model.Movie;
import cinecli.model.Pricing;
import cinecli.model.PromoCode;
import cinecli.model.Screening;
import cinecli.model.SeatCoordinate;
import cinecli.model.SnackMenuItem;
import cinecli.model.SnackSelection;
import cinecli.model.TicketSelection;
import cinecli.model.TicketType;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CustomerUiTest {

    @Test
    void input_endOfFileAndFailure_returnFalseAndReportOnlyFailure() {
        StringWriter eofError = new StringWriter();
        CustomerUi eofUi = new CustomerUi(new StringReader(""), new StringWriter(), eofError);
        StringWriter failureError = new StringWriter();
        Reader failingReader = new Reader() {
            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
                throw new IOException("simulated input failure");
            }

            @Override
            public void close() {
                // Nothing to close.
            }
        };
        CustomerUi failingUi = new CustomerUi(failingReader, new StringWriter(), failureError);

        assertAll(
                () -> org.junit.jupiter.api.Assertions.assertFalse(eofUi.hasUserProceeded()),
                () -> org.junit.jupiter.api.Assertions.assertFalse(failingUi.hasUserProceeded()),
                () -> assertEquals("", eofError.toString()),
                () -> assertTrue(failureError.toString().contains("Unable to read input")));
    }

    @Test
    void seatAvailabilityMessages_writeToTheirDesignatedStreams() {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        CustomerUi customerUi = new CustomerUi(new StringReader(""), output, error);

        customerUi.showNoSeatsAvailable();
        customerUi.showSeatStorageError("disk unavailable");
        customerUi.showPricingStorageError("pricing unavailable");

        assertAll(
                () -> assertTrue(output.toString().contains("No seats are available")),
                () -> assertTrue(error.toString().contains("disk unavailable")),
                () -> assertTrue(error.toString().contains("pricing unavailable")));
    }
    @Test
    void showCatalog_multipleScreenings_labelsTimingsWithLetters() {
        StringWriter output = new StringWriter();
        CustomerUi customerUi = createUi(output);
        Movie movie = new Movie(
                "MOV-001",
                "Orbit of Echoes",
                ContentRating.PG13,
                List.of(
                        new Screening("SCR-001", LocalDateTime.of(2026, 8, 29, 13, 30)),
                        new Screening("SCR-002", LocalDateTime.of(2026, 8, 29, 18, 0))));

        customerUi.showCatalog(List.of(movie));

        assertAll(
                () -> assertTrue(output.toString().contains("A. 29 Aug 2026, 13:30")),
                () -> assertTrue(output.toString().contains("B. 29 Aug 2026, 18:00")));
    }

    @Test
    void showSeatMap_fixedLayout_placesScreenAboveGAndNumberAxisBelowA() {
        StringWriter output = new StringWriter();
        CustomerUi customerUi = createUi(output);
        Movie movie = new Movie(
                "MOV-001",
                "Orbit of Echoes",
                ContentRating.PG13,
                List.of());
        Screening screening = new Screening(
                "SCR-001", LocalDateTime.of(2026, 8, 29, 13, 30));

        customerUi.showSeatMap(
                movie, screening, Set.of(new SeatCoordinate('G', 4), new SeatCoordinate('A', 20)));

        List<String> lines = output.toString().lines().toList();
        int screenIndex = indexOfTrimmedLine(lines, "SCREEN");
        int rowGIndex = indexOfStartingLine(lines, "G   ");
        int rowAIndex = indexOfStartingLine(lines, "A   ");
        int numberAxisIndex = indexOfStartingLine(lines, "     1  2");
        String[] rowGFields = lines.get(rowGIndex).strip().split("\\s+");
        String[] rowAFields = lines.get(rowAIndex).strip().split("\\s+");
        String[] axisFields = lines.get(numberAxisIndex).strip().split("\\s+");

        assertAll(
                () -> assertTrue(screenIndex < rowGIndex),
                () -> assertTrue(rowGIndex < rowAIndex),
                () -> assertEquals(6, rowAIndex - rowGIndex),
                () -> assertTrue(rowAIndex < numberAxisIndex),
                () -> assertEquals(21, rowGFields.length),
                () -> assertEquals(21, rowAFields.length),
                () -> assertEquals("X", rowGFields[4]),
                () -> assertEquals("X", rowAFields[20]),
                () -> assertEquals("1", axisFields[0]),
                () -> assertEquals("20", axisFields[19]),
                () -> assertTrue(output.toString().contains(
                        "X = Taken or tentatively selected")));
    }

    @Test
    void showTicketTypeMenu_fixedTypes_displaysExactPrices() {
        StringWriter output = new StringWriter();
        CustomerUi customerUi = createUi(output);

        customerUi.showTicketTypeMenu(List.of(TicketType.values()), Pricing.defaults());

        List<String> nonblankLines = output.toString().lines()
                .filter(line -> !line.isBlank())
                .toList();
        assertEquals(
                List.of(
                        "Ticket Types",
                        "1. Adult - S$11.00",
                        "2. Senior - S$4.50",
                        "3. Student - S$7.00"),
                nonblankLines);
    }

    @Test
    void requestTicketType_confirmedSeat_identifiesSeat() {
        StringWriter output = new StringWriter();
        CustomerUi customerUi = createUi(output);

        customerUi.requestTicketType(new SeatCoordinate('G', 4));

        assertEquals(
                "Choose a ticket type for seat G4 (1-3):",
                output.toString().strip());
    }

    @Test
    void showTicketSelections_multipleTypes_displaysSeatOrderAndPrices() {
        StringWriter output = new StringWriter();
        CustomerUi customerUi = createUi(output);

        customerUi.showTicketSelections(List.of(
                new TicketSelection(new SeatCoordinate('G', 4), TicketType.ADULT, 1100),
                new TicketSelection(new SeatCoordinate('G', 5), TicketType.SENIOR, 450)));

        List<String> nonblankLines = output.toString().lines()
                .filter(line -> !line.isBlank())
                .toList();
        assertEquals(
                List.of(
                        "Selected Tickets",
                        "- G4: Adult - S$11.00",
                        "- G5: Senior - S$4.50"),
                nonblankLines);
    }

    @Test
    void requestPromoCode_displaysCodesDiscountsAndSkipOption() {
        StringWriter output = new StringWriter();
        CustomerUi customerUi = createUi(output);

        customerUi.requestPromoCode(Pricing.defaults().promotions());

        assertEquals(
                "Enter a promo code (CS2103 for 20% off or CS3227 for 99% off),"
                        + " or press ENTER to skip:",
                output.toString().strip());
    }

    @Test
    void requestPromoCode_noPromotionsExplainsThatNoneAreAvailable() {
        StringWriter output = new StringWriter();
        CustomerUi customerUi = createUi(output);

        customerUi.requestPromoCode(List.of());

        assertTrue(output.toString().contains("no promotions are currently available"));
    }

    @Test
    void showBill_mixedSelectionsAndPromo_displaysItemizedExactAmounts() {
        StringWriter output = new StringWriter();
        CustomerUi customerUi = createUi(output);
        Movie movie = createBillMovie();
        Screening screening = createBillScreening();
        Bill bill = new Bill(
                List.of(
                        new TicketSelection(
                                new SeatCoordinate('G', 4), TicketType.ADULT, 1100),
                        new TicketSelection(
                                new SeatCoordinate('G', 5), TicketType.SENIOR, 450)),
                List.of(
                        new SnackSelection(SnackMenuItem.POPCORN_COMBO, 2, 700),
                        new SnackSelection(SnackMenuItem.NACHOS, 3, 600)),
                Optional.of(new PromoCode("CS2103", 20)));

        customerUi.showBill(bill, movie, screening);

        List<String> nonblankLines = output.toString().lines()
                .filter(line -> !line.isBlank())
                .toList();
        assertEquals(
                List.of(
                        "============================================================",
                        "                        BILL SUMMARY",
                        "============================================================",
                        "TICKETS",
                        "------------------------------------------------------------",
                        "Movie: Orbit of Echoes",
                        "Time: 29 Aug 2026, 13:30",
                        "Seat        Type                                  Unit Price",
                        "------------------------------------------------------------",
                        "G4          Adult                                    S$11.00",
                        "G5          Senior                                    S$4.50",
                        "------------------------------------------------------------",
                        "Ticket Subtotal:                                     S$15.50",
                        "SNACKS AND COMBOS",
                        "------------------------------------------------------------",
                        "Qty         Item                                  Unit Price",
                        "------------------------------------------------------------",
                        "2           Popcorn Combo                             S$7.00",
                        "            (Popcorn + Soft Drink)",
                        "3           Nachos                                    S$6.00",
                        "------------------------------------------------------------",
                        "Snack Subtotal:                                      S$32.00",
                        "PROMOTION",
                        "------------------------------------------------------------",
                        "Promo Code:                                           CS2103",
                        "Discount:                                            20% OFF",
                        "Amount Saved:                                        -S$9.50",
                        "============================================================",
                        "Subtotal:                                            S$47.50",
                        "Discount:                                            -S$9.50",
                        "------------------------------------------------------------",
                        "TOTAL:                                               S$38.00",
                        "============================================================"),
                nonblankLines);
    }

    @Test
    void showBill_maximumSnackQuantity_formatsLongAmounts() {
        StringWriter output = new StringWriter();
        CustomerUi customerUi = createUi(output);
        Bill bill = new Bill(
                List.of(new TicketSelection(
                        new SeatCoordinate('A', 1), TicketType.ADULT, 1100)),
                List.of(new SnackSelection(
                        SnackMenuItem.NACHOS_COMBO, Integer.MAX_VALUE, 800)));

        customerUi.showBill(bill, createBillMovie(), createBillScreening());

        assertAll(
                () -> assertTrue(output.toString().contains(
                        "S$17,179,869,176.00")),
                () -> assertTrue(output.toString().contains(
                        "S$17,179,869,187.00")),
                () -> assertTrue(output.toString().contains("Promo Code:")),
                () -> assertTrue(output.toString().contains("None")),
                () -> assertTrue(output.toString().contains("0% OFF")),
                () -> assertTrue(output.toString().contains("-S$0.00")));
    }

    @Test
    void showSnackMenu_fixedItems_groupsItemsWithExactPrices() {
        StringWriter output = new StringWriter();
        CustomerUi customerUi = createUi(output);

        customerUi.showSnackMenu(List.of(SnackMenuItem.values()), Pricing.defaults());

        List<String> nonblankLines = output.toString().lines()
                .filter(line -> !line.isBlank())
                .toList();
        assertEquals(
                List.of(
                        "Snack and Combo Menu",
                        "Snacks:",
                        "1. Popcorn - S$5.00",
                        "2. Nachos - S$6.00",
                        "3. Soft Drink - S$3.00",
                        "Combos:",
                        "4. Popcorn Combo (Popcorn + Soft Drink) - S$7.00",
                        "5. Nachos Combo (Nachos + Soft Drink) - S$8.00",
                        "0. Finish selection (or skip if none selected)"),
                nonblankLines);
    }

    @Test
    void requestSnackQuantity_combo_displaysItemName() {
        StringWriter output = new StringWriter();
        CustomerUi customerUi = createUi(output);

        customerUi.requestSnackQuantity(SnackMenuItem.NACHOS_COMBO);

        assertEquals(
                "Enter quantity for Nachos Combo (Nachos + Soft Drink) (positive whole number):",
                output.toString().strip());
    }

    @Test
    void showSnackSelectionAdded_combo_displaysQuantityAndUnitPrice() {
        StringWriter output = new StringWriter();
        CustomerUi customerUi = createUi(output);

        customerUi.showSnackSelectionAdded(
                new SnackSelection(SnackMenuItem.NACHOS_COMBO, 2, 800));

        assertEquals(
                "Snack/combo added: 2 x Nachos Combo (Nachos + Soft Drink) - S$8.00 each",
                output.toString().strip());
    }

    @Test
    void showSnackSelections_multipleItems_displaysOriginalOrderAndUnitPrices() {
        StringWriter output = new StringWriter();
        CustomerUi customerUi = createUi(output);

        customerUi.showSnackSelections(List.of(
                new SnackSelection(SnackMenuItem.POPCORN_COMBO, 2, 700),
                new SnackSelection(SnackMenuItem.NACHOS, 3, 600)));

        List<String> nonblankLines = output.toString().lines()
                .filter(line -> !line.isBlank())
                .toList();
        assertEquals(
                List.of(
                        "Selected Snacks and Combos",
                        "- 2 x Popcorn Combo (Popcorn + Soft Drink) - S$7.00 each",
                        "- 3 x Nachos - S$6.00 each"),
                nonblankLines);
    }

    @Test
    void terminalWriter_tracksWriteFailureAndKeepsCloseOwnedByApplication() throws Exception {
        AdminTerminal failingTerminal = new AdminTerminal() {
            @Override
            public TerminalInput readLine() {
                return new EndOfInput();
            }

            @Override
            public boolean write(String text) {
                return false;
            }

            @Override
            public boolean writeError(String text) {
                return false;
            }
        };
        CustomerUi customerUi = new CustomerUi(failingTerminal);

        customerUi.showSeatsConfirmed(Set.of(SeatCoordinate.parse("A1")));
        Field outputField = CustomerUi.class.getDeclaredField("output");
        outputField.setAccessible(true);
        ((PrintWriter) outputField.get(customerUi)).close();

        assertFalse(customerUi.isOutputAvailable());
    }

    @Test
    void terminalWriter_tracksIndependentErrorWriteFailure() {
        AdminTerminal errorFailingTerminal = new AdminTerminal() {
            @Override
            public TerminalInput readLine() {
                return new EndOfInput();
            }

            @Override
            public boolean write(String text) {
                return true;
            }

            @Override
            public boolean writeError(String text) {
                return false;
            }
        };
        CustomerUi customerUi = new CustomerUi(errorFailingTerminal);

        customerUi.showSeatStorageError("simulated failure");

        assertFalse(customerUi.isOutputAvailable());
    }

    private CustomerUi createUi(StringWriter output) {
        return new CustomerUi(new StringReader(""), output, new StringWriter());
    }

    private Movie createBillMovie() {
        return new Movie(
                "MOV-001", "Orbit of Echoes", ContentRating.PG13, List.of());
    }

    private Screening createBillScreening() {
        return new Screening("SCR-001", LocalDateTime.of(2026, 8, 29, 13, 30));
    }

    private int indexOfTrimmedLine(List<String> lines, String expectedLine) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).strip().equals(expectedLine)) {
                return i;
            }
        }
        throw new AssertionError("Line not found: " + expectedLine);
    }

    private int indexOfStartingLine(List<String> lines, String prefix) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(prefix)) {
                return i;
            }
        }
        throw new AssertionError("Line not found with prefix: " + prefix);
    }
}
