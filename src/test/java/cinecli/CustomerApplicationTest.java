package cinecli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.storage.CatalogStorage;
import cinecli.storage.SeatStorage;
import cinecli.storage.SeatStorageTestSupport;
import cinecli.ui.CustomerUi;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CustomerApplicationTest {
    private static final String DEFAULT_RESOURCE = "/cinecli/default-catalog.tsv";

    @TempDir
    Path tempDirectory;

    @Test
    void run_validCatalog_displaysWelcomeBeforePersistedCatalog() throws IOException {
        String catalog = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-CUSTOM\tLanterns Beyond Dawn\tM18
                SCREENING\tSCR-CUSTOM\tMOV-CUSTOM\t2026-10-12\t09:05
                """;

        ApplicationOutput applicationOutput = runWithCatalog(catalog);

        String output = applicationOutput.normalOutput();
        int welcomeIndex = output.indexOf("Welcome to CineCLI");
        int promptIndex = output.indexOf("Press ENTER to proceed");
        int movieIndex = output.indexOf("Lanterns Beyond Dawn");
        assertAll(
                () -> assertTrue(welcomeIndex >= 0),
                () -> assertTrue(promptIndex > welcomeIndex),
                () -> assertTrue(movieIndex > promptIndex),
                () -> assertTrue(output.contains("Rating: M18")),
                () -> assertTrue(output.contains("12 Oct 2026, 09:05")),
                () -> assertEquals("", applicationOutput.errorOutput()));
    }

    @Test
    void run_emptyValidCatalog_displaysFriendlyMessage() throws IOException {
        ApplicationOutput applicationOutput = runWithCatalog("CINECLI-CATALOG\t1\n");

        assertAll(
                () -> assertTrue(applicationOutput.normalOutput()
                        .contains("No movies are currently available.")),
                () -> assertEquals("", applicationOutput.errorOutput()));
    }

    @Test
    void run_endOfInputAtWelcome_stopsBeforeLoadingData() throws IOException {
        String catalog = "CINECLI-CATALOG\t1\n";

        ApplicationOutput applicationOutput = runWithData(catalog, null, "");

        assertAll(
                () -> assertTrue(applicationOutput.normalOutput().contains("Welcome to CineCLI")),
                () -> assertFalse(Files.exists(applicationOutput.runtimeSeats())),
                () -> assertEquals("", applicationOutput.errorOutput()));
    }

    @Test
    void run_timingBeyondMovieAndEmptyMovieBeforeAvailableMovie_reprompts() throws IOException {
        String catalog = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-EMPTY\tNo Shows\tPG13
                MOVIE\tMOV-LIVE\tHas Show\tM18
                SCREENING\tSCR-LIVE\tMOV-LIVE\t2026-10-10\t10:00
                """;

        ApplicationOutput applicationOutput = runWithData(
                catalog, null, "\n2B\n2A\nCANCEL\n");

        assertAll(
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "movie 2 does not have timing B")),
                () -> assertTrue(applicationOutput.normalOutput().contains("Movie: Has Show")),
                () -> assertEquals("", applicationOutput.errorOutput()));
    }

    @Test
    void run_malformedSeatFile_reportsStorageFailureWithoutOverwriting() throws IOException {
        String catalog = singleScreeningCatalog();
        String seats = "malformed seats\n";

        ApplicationOutput applicationOutput = runWithData(catalog, seats, "\n1A\n");

        assertAll(
                () -> assertTrue(applicationOutput.errorOutput().contains(
                        "Unable to load or update seat availability")),
                () -> assertEquals(seats, Files.readString(applicationOutput.runtimeSeats(), UTF_8)),
                () -> assertFalse(applicationOutput.normalOutput().contains("Ticket Types")));
    }

    @Test
    void run_fullyOccupiedScreening_reportsNoAvailability() throws IOException {
        StringBuilder seats = new StringBuilder("CINECLI-SEATS\t1\n");
        for (char row = 'A'; row <= 'G'; row++) {
            for (int number = 1; number <= 20; number++) {
                seats.append("TAKEN_SEAT\tSCR-001\t").append(row).append(number).append('\n');
            }
        }

        ApplicationOutput applicationOutput = runWithData(
                singleScreeningCatalog(), seats.toString(), "\n1A\n");

        assertAll(
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "No seats are available for this screening")),
                () -> assertFalse(applicationOutput.normalOutput().contains("Select one or more seats")),
                () -> assertEquals("", applicationOutput.errorOutput()));
    }

    @Test
    void run_endOfInputAtEachPurchasePrompt_stopsWithoutLaterSections() throws IOException {
        List<String> inputs = List.of(
                "\n1A\n",
                "\n1A\nA1\n",
                "\n1A\nA1\nY\n",
                "\n1A\nA1\nY\n1\n",
                "\n1A\nA1\nY\n1\n1\n",
                "\n1A\nA1\nY\n1\n0\n");

        for (String input : inputs) {
            Path runtimeSeats = tempDirectory.resolve("seats.tsv");
            Files.deleteIfExists(runtimeSeats);
            ApplicationOutput applicationOutput = runWithData(
                    singleScreeningCatalog(), null, input);
            assertFalse(applicationOutput.normalOutput().contains("BILL SUMMARY"));
        }
    }

    @Test
    void run_blankSeatSelection_repromptsThenCancels() throws IOException {
        ApplicationOutput applicationOutput = runWithData(
                singleScreeningCatalog(), null, "\n1A\n   \nCANCEL\n");

        assertAll(
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "enter at least one seat coordinate")),
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "Seat selection cancelled")));
    }

    @Test
    void run_finalSeatWriteFailure_reportsErrorAndSuppressesLaterWorkflow() throws IOException {
        Path runtimeCatalog = tempDirectory.resolve("catalog.tsv");
        Path runtimeSeats = tempDirectory.resolve("seats.tsv");
        Files.writeString(runtimeCatalog, singleScreeningCatalog(), UTF_8);
        StringWriter normalOutput = new StringWriter();
        StringWriter errorOutput = new StringWriter();
        CustomerUi customerUi = new CustomerUi(
                new StringReader("\n1A\nA1\nY\n"), normalOutput, errorOutput);

        new CustomerApplication(
                customerUi,
                new CatalogStorage(runtimeCatalog, DEFAULT_RESOURCE),
                SeatStorageTestSupport.failingReplacement(runtimeSeats)).run();

        assertAll(
                () -> assertTrue(errorOutput.toString().contains(
                        "Unable to load or update seat availability")),
                () -> assertFalse(normalOutput.toString().contains("Ticket Types")),
                () -> assertEquals("CINECLI-SEATS\t1\n", Files.readString(runtimeSeats, UTF_8)));
    }

    @Test
    void run_malformedCatalog_displaysErrorWithoutPartialCatalog() throws IOException {
        String malformedCatalog = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-VISIBLE\tThis Must Not Be Displayed\tPG13
                BROKEN\tVALUE
                """;

        ApplicationOutput applicationOutput = runWithCatalog(malformedCatalog);

        assertAll(
                () -> assertFalse(applicationOutput.normalOutput()
                        .contains("This Must Not Be Displayed")),
                () -> assertTrue(applicationOutput.errorOutput()
                        .contains("Unable to load the movie catalog")),
                () -> assertTrue(applicationOutput.errorOutput().contains("line 3")),
                () -> assertFalse(applicationOutput.errorOutput().contains("Exception")));
    }

    @Test
    void run_validScreeningSeatsAndMultipleItems_completesPostSeatSelectionFlow()
            throws IOException {
        String catalog = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-001\tFirst Film\tPG13
                MOVIE\tMOV-002\tSecond Film\tM18
                MOVIE\tMOV-003\tThird Film\tR21
                SCREENING\tSCR-001\tMOV-001\t2026-10-10\t10:00
                SCREENING\tSCR-003-A\tMOV-003\t2026-10-12\t14:00
                SCREENING\tSCR-003-B\tMOV-003\t2026-10-12\t18:30
                """;

        ApplicationOutput applicationOutput = runWithData(
                catalog, null, "\n3b\ng5 g4\ny\n1\n2\n4\n2\n2\n3\n0\n cS2103 \n");

        String persistedSeats = Files.readString(applicationOutput.runtimeSeats(), UTF_8);
        String normalOutput = applicationOutput.normalOutput();
        List<String> rowGStates = applicationOutput.normalOutput().lines()
                .filter(line -> line.startsWith("G   "))
                .toList();
        int seatsConfirmedIndex = normalOutput.indexOf("Seats confirmed: G4, G5");
        int ticketMenuIndex = normalOutput.indexOf("Ticket Types");
        int ticketSelectionsIndex = normalOutput.indexOf("Selected Tickets");
        int snackMenuIndex = normalOutput.indexOf("Snack and Combo Menu");
        int comboAddedIndex = normalOutput.indexOf(
                "Snack/combo added: 2 x Popcorn Combo (Popcorn + Soft Drink) - S$7.00 each");
        int snackAddedIndex = normalOutput.indexOf(
                "Snack/combo added: 3 x Nachos - S$6.00 each");
        int selectionsIndex = normalOutput.indexOf("Selected Snacks and Combos");
        int promoPromptIndex = normalOutput.indexOf("Enter a promo code");
        int billIndex = normalOutput.indexOf("BILL SUMMARY");
        assertAll(
                () -> assertTrue(normalOutput.contains("B. 12 Oct 2026, 18:30")),
                () -> assertTrue(normalOutput.contains("Movie: Third Film")),
                () -> assertTrue(normalOutput.contains("SCREEN")),
                () -> assertTrue(normalOutput.contains(
                        "Confirm seats G4, G5? (Y/N):")),
                () -> assertTrue(seatsConfirmedIndex >= 0),
                () -> assertTrue(ticketMenuIndex > seatsConfirmedIndex),
                () -> assertTrue(ticketSelectionsIndex > ticketMenuIndex),
                () -> assertTrue(snackMenuIndex > ticketSelectionsIndex),
                () -> assertTrue(normalOutput.contains(
                        "Choose a ticket type for seat G4 (1-3):")),
                () -> assertTrue(normalOutput.contains(
                        "Choose a ticket type for seat G5 (1-3):")),
                () -> assertTrue(normalOutput.contains("- G4: Adult - S$11.00")),
                () -> assertTrue(normalOutput.contains("- G5: Senior - S$4.50")),
                () -> assertTrue(comboAddedIndex > snackMenuIndex),
                () -> assertTrue(snackAddedIndex > comboAddedIndex),
                () -> assertTrue(selectionsIndex > snackAddedIndex),
                () -> assertTrue(promoPromptIndex > selectionsIndex),
                () -> assertTrue(billIndex > promoPromptIndex),
                () -> assertTrue(normalOutput.contains(
                        "- 2 x Popcorn Combo (Popcorn + Soft Drink) - S$7.00 each")),
                () -> assertTrue(normalOutput.contains("- 3 x Nachos - S$6.00 each")),
                () -> assertTrue(hasBillLine(normalOutput, "Ticket Subtotal:", "S$15.50")),
                () -> assertTrue(hasBillLine(normalOutput, "Snack Subtotal:", "S$32.00")),
                () -> assertTrue(hasBillLine(normalOutput, "Subtotal:", "S$47.50")),
                () -> assertTrue(hasBillLine(normalOutput, "Promo Code:", "CS2103")),
                () -> assertTrue(hasBillLine(normalOutput, "Discount:", "20% OFF")),
                () -> assertTrue(hasBillLine(normalOutput, "Amount Saved:", "-S$9.50")),
                () -> assertTrue(hasBillLine(normalOutput, "TOTAL:", "S$38.00")),
                () -> assertEquals(2, rowGStates.size()),
                () -> assertEquals("O", rowGStates.get(0).strip().split("\\s+")[4]),
                () -> assertEquals("X", rowGStates.get(1).strip().split("\\s+")[4]),
                () -> assertEquals(
                        "CINECLI-SEATS\t1\n"
                                + "TAKEN_SEAT\tSCR-003-B\tG4\n"
                                + "TAKEN_SEAT\tSCR-003-B\tG5\n",
                        persistedSeats),
                () -> assertEquals("", applicationOutput.errorOutput()));
    }

    @Test
    void run_skipSnackSelection_completesWithoutChoosingItem() throws IOException {
        String catalog = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-001\tFirst Film\tPG13
                SCREENING\tSCR-001\tMOV-001\t2026-10-10\t10:00
                """;

        ApplicationOutput applicationOutput = runWithData(
                catalog, null, "\n1A\nA1\nY\n3\n0\n   \n");

        assertAll(
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "Snack and Combo Menu")),
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "No snacks or combos selected.")),
                () -> assertFalse(applicationOutput.normalOutput().contains(
                        "Snack/combo added:")),
                () -> assertFalse(applicationOutput.normalOutput().contains(
                        "Invalid promo code:")),
                () -> assertTrue(hasBillLine(
                        applicationOutput.normalOutput(), "Snack Subtotal:", "S$0.00")),
                () -> assertTrue(hasBillLine(
                        applicationOutput.normalOutput(), "Promo Code:", "None")),
                () -> assertTrue(hasBillLine(
                        applicationOutput.normalOutput(), "TOTAL:", "S$7.00")),
                () -> assertEquals("", applicationOutput.errorOutput()));
    }

    @Test
    void run_invalidSnackSelections_repromptsUntilSnackSelected() throws IOException {
        String catalog = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-001\tFirst Film\tPG13
                SCREENING\tSCR-001\tMOV-001\t2026-10-10\t10:00
                """;

        ApplicationOutput applicationOutput = runWithData(
                catalog, null, "\n1A\nA1\nY\n1\ntwo\n6\n2\n3\n0\n\n");

        List<String> snackErrors = applicationOutput.normalOutput().lines()
                .filter(line -> line.startsWith("Invalid snack selection:"))
                .toList();
        assertAll(
                () -> assertEquals(2, snackErrors.size()),
                () -> assertTrue(snackErrors.stream().allMatch(line -> line.contains(
                        "selection must be an item number from 1 through 5"))),
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "- 3 x Nachos - S$6.00 each")),
                () -> assertEquals("", applicationOutput.errorOutput()));
    }

    @Test
    void run_repeatedSnackSelection_replacesEarlierQuantity() throws IOException {
        String catalog = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-001\tFirst Film\tPG13
                SCREENING\tSCR-001\tMOV-001\t2026-10-10\t10:00
                """;

        ApplicationOutput applicationOutput = runWithData(
                catalog, null, "\n1A\nA1\nY\n1\n1\n2\n1\n5\n0\n\n");

        List<String> summaryLines = applicationOutput.normalOutput().lines()
                .filter(line -> line.startsWith("- ") && line.contains("Popcorn"))
                .toList();
        assertAll(
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "Snack/combo added: 2 x Popcorn - S$5.00 each")),
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "Snack/combo updated: 5 x Popcorn - S$5.00 each")),
                () -> assertEquals(
                        List.of(
                                "- 5 x Popcorn - S$5.00 each"),
                        summaryLines),
                () -> assertEquals("", applicationOutput.errorOutput()));
    }

    @Test
    void run_malformedSnackQuantities_repromptsForSameItem() throws IOException {
        String catalog = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-001\tFirst Film\tPG13
                SCREENING\tSCR-001\tMOV-001\t2026-10-10\t10:00
                """;

        ApplicationOutput applicationOutput = runWithData(
                catalog, null,
                "\n1A\nA1\nY\n1\n1\n\ntwo\n1.5\n2147483648\n2\n0\n\n");

        List<String> quantityErrors = applicationOutput.normalOutput().lines()
                .filter(line -> line.startsWith("Invalid snack quantity:"))
                .toList();
        long quantityPromptCount = applicationOutput.normalOutput().lines()
                .filter(line -> line.equals(
                        "Enter quantity for Popcorn (positive whole number):"))
                .count();
        assertAll(
                () -> assertEquals(4, quantityErrors.size()),
                () -> assertTrue(quantityErrors.stream().allMatch(line -> line.contains(
                        "quantity must be a whole number from 1 through 2147483647"))),
                () -> assertEquals(5, quantityPromptCount),
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "- 2 x Popcorn - S$5.00 each")),
                () -> assertEquals("", applicationOutput.errorOutput()));
    }

    @Test
    void run_nonPositiveSnackQuantities_repromptsForSameItem() throws IOException {
        String catalog = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-001\tFirst Film\tPG13
                SCREENING\tSCR-001\tMOV-001\t2026-10-10\t10:00
                """;

        ApplicationOutput applicationOutput = runWithData(
                catalog, null, "\n1A\nA1\nY\n1\n1\n0\n-1\n1\n0\n\n");

        List<String> quantityErrors = applicationOutput.normalOutput().lines()
                .filter(line -> line.startsWith("Invalid snack quantity:"))
                .toList();
        assertAll(
                () -> assertEquals(2, quantityErrors.size()),
                () -> assertTrue(quantityErrors.stream().allMatch(line -> line.contains(
                        "quantity must be a whole number from 1 through 2147483647"))),
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "- 1 x Popcorn - S$5.00 each")),
                () -> assertEquals("", applicationOutput.errorOutput()));
    }

    @Test
    void run_newSession_doesNotRetainSnackSelectionsOrCreateSnackData() throws IOException {
        String catalog = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-001\tFirst Film\tPG13
                SCREENING\tSCR-001\tMOV-001\t2026-10-10\t10:00
                """;

        ApplicationOutput firstRun = runWithData(
                catalog, null, "\n1A\nA1\nY\n1\n3\n2\n0\n\n");
        ApplicationOutput secondRun = runWithData(
                catalog, null, "\n1A\nA2\nY\n2\n0\n\n");
        Set<String> runtimeFileNames;
        try (Stream<Path> runtimeFiles = Files.list(tempDirectory)) {
            runtimeFileNames = runtimeFiles
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }

        assertAll(
                () -> assertTrue(firstRun.normalOutput().contains(
                        "- 2 x Soft Drink - S$3.00 each")),
                () -> assertTrue(secondRun.normalOutput().contains(
                        "No snacks or combos selected.")),
                () -> assertFalse(secondRun.normalOutput().contains(
                        "- 2 x Soft Drink - S$3.00 each")),
                () -> assertEquals(Set.of("catalog.tsv", "seats.tsv"), runtimeFileNames),
                () -> assertEquals("", firstRun.errorOutput()),
                () -> assertEquals("", secondRun.errorOutput()));
    }

    @Test
    void run_takenSeat_rejectsSeatAndConfirmsDifferentSelection() throws IOException {
        String catalog = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-001\tFirst Film\tPG13
                SCREENING\tSCR-001\tMOV-001\t2026-10-10\t10:00
                """;
        String seats = """
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-001\tG4
                """;

        ApplicationOutput applicationOutput = runWithData(
                catalog, seats, "\n1A\nG4\nG5\nY\n2\n0\n\n");

        assertAll(
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "seat G4 is already taken")),
                () -> assertTrue(applicationOutput.normalOutput().contains("Seats confirmed: G5")),
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "- G5: Senior - S$4.50")),
                () -> assertTrue(hasBillLine(
                        applicationOutput.normalOutput(), "TOTAL:", "S$4.50")),
                () -> assertEquals(
                        "CINECLI-SEATS\t1\n"
                                + "TAKEN_SEAT\tSCR-001\tG4\n"
                                + "TAKEN_SEAT\tSCR-001\tG5\n",
                        Files.readString(applicationOutput.runtimeSeats(), UTF_8)),
                () -> assertEquals("", applicationOutput.errorOutput()));
    }

    @Test
    void run_declinedTentativeSelection_clearsItWithoutPersisting() throws IOException {
        String catalog = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-001\tFirst Film\tPG13
                SCREENING\tSCR-001\tMOV-001\t2026-10-10\t10:00
                """;

        ApplicationOutput applicationOutput = runWithData(
                catalog, null, "\n1A\nG4\nN\nCANCEL\n");

        assertAll(
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "Confirm seats G4? (Y/N):")),
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "Tentative seat selection cleared.")),
                () -> assertFalse(applicationOutput.normalOutput().contains(
                        "Ticket Types")),
                () -> assertFalse(applicationOutput.normalOutput().contains(
                        "Snack and Combo Menu")),
                () -> assertFalse(applicationOutput.normalOutput().contains(
                        "Enter a promo code")),
                () -> assertFalse(applicationOutput.normalOutput().contains(
                        "BILL SUMMARY")),
                () -> assertEquals(
                        "CINECLI-SEATS\t1\n",
                        Files.readString(applicationOutput.runtimeSeats(), UTF_8)),
                () -> assertEquals("", applicationOutput.errorOutput()));
    }

    @Test
    void run_invalidScreeningCode_repromptsUntilExistingScreeningSelected() throws IOException {
        String catalog = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-001\tFirst Film\tPG13
                SCREENING\tSCR-001\tMOV-001\t2026-10-10\t10:00
                """;

        ApplicationOutput applicationOutput = runWithData(
                catalog, null, "\n9Z\n1A\nCANCEL\n");

        assertAll(
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "Invalid screening selection: movie 9 does not exist")),
                () -> assertTrue(applicationOutput.normalOutput().contains("Movie: First Film")),
                () -> assertEquals("", applicationOutput.errorOutput()));
    }

    @Test
    void run_invalidSeatsAndConfirmation_repromptsUntilConfirmed() throws IOException {
        String catalog = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-001\tFirst Film\tPG13
                SCREENING\tSCR-001\tMOV-001\t2026-10-10\t10:00
                """;

        ApplicationOutput applicationOutput = runWithData(
                catalog, null, "\n1A\nH1\nA1 A1\nA1\nMAYBE\nY\n1\n0\n\n");

        assertAll(
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "seat coordinate must use a row A-G followed by a number 1-20")),
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "seat A1 was entered more than once")),
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "enter Y to confirm or N to choose again")),
                () -> assertTrue(applicationOutput.normalOutput().contains("Seats confirmed: A1")),
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "- A1: Adult - S$11.00")),
                () -> assertEquals("", applicationOutput.errorOutput()));
    }

    @Test
    void run_invalidTicketTypes_repromptsForSameSeatBeforeContinuing() throws IOException {
        String catalog = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-001\tFirst Film\tPG13
                SCREENING\tSCR-001\tMOV-001\t2026-10-10\t10:00
                """;

        ApplicationOutput applicationOutput = runWithData(
                catalog, null, "\n1A\nA1\nY\n\ntwo\n0\n4\n3\n0\n\n");

        List<String> ticketErrors = applicationOutput.normalOutput().lines()
                .filter(line -> line.startsWith("Invalid ticket type:"))
                .toList();
        long ticketPromptCount = applicationOutput.normalOutput().lines()
                .filter(line -> line.equals("Choose a ticket type for seat A1 (1-3):"))
                .count();
        assertAll(
                () -> assertEquals(4, ticketErrors.size()),
                () -> assertTrue(ticketErrors.stream().allMatch(line -> line.contains(
                        "selection must be a ticket type number from 1 through 3"))),
                () -> assertEquals(5, ticketPromptCount),
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "- A1: Student - S$7.00")),
                () -> assertTrue(applicationOutput.normalOutput().contains(
                        "Snack and Combo Menu")),
                () -> assertEquals("", applicationOutput.errorOutput()));
    }

    @Test
    void run_invalidPromoCodes_repromptsThenAppliesTrimmedCaseInsensitiveCode()
            throws IOException {
        String catalog = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-001\tFirst Film\tPG13
                SCREENING\tSCR-001\tMOV-001\t2026-10-10\t10:00
                """;

        ApplicationOutput applicationOutput = runWithData(
                catalog, null,
                "\n1A\nA1\nY\n2\n0\nSAVE20\nCS21030\n cS3227 \n");

        List<String> promoErrors = applicationOutput.normalOutput().lines()
                .filter(line -> line.startsWith("Invalid promo code:"))
                .toList();
        long promoPromptCount = applicationOutput.normalOutput().lines()
                .filter(line -> line.startsWith("Enter a promo code"))
                .count();
        assertAll(
                () -> assertEquals(2, promoErrors.size()),
                () -> assertTrue(promoErrors.stream().allMatch(line -> line.contains(
                        "promo code must be CS2103 or CS3227"))),
                () -> assertEquals(3, promoPromptCount),
                () -> assertTrue(hasBillLine(
                        applicationOutput.normalOutput(), "Subtotal:", "S$4.50")),
                () -> assertTrue(hasBillLine(
                        applicationOutput.normalOutput(), "Promo Code:", "CS3227")),
                () -> assertTrue(hasBillLine(
                        applicationOutput.normalOutput(), "Discount:", "99% OFF")),
                () -> assertTrue(hasBillLine(
                        applicationOutput.normalOutput(), "Amount Saved:", "-S$4.45")),
                () -> assertTrue(hasBillLine(
                        applicationOutput.normalOutput(), "TOTAL:", "S$0.05")),
                () -> assertEquals("", applicationOutput.errorOutput()));
    }

    private ApplicationOutput runWithCatalog(String catalog) throws IOException {
        return runWithData(catalog, null, "\n");
    }

    private String singleScreeningCatalog() {
        return """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-001\tFirst Film\tPG13
                SCREENING\tSCR-001\tMOV-001\t2026-10-10\t10:00
                """;
    }

    private boolean hasBillLine(String output, String label, String value) {
        return output.lines()
                .anyMatch(line -> line.startsWith(label) && line.endsWith(value));
    }

    private ApplicationOutput runWithData(String catalog, String seats, String input)
            throws IOException {
        Path runtimeCatalog = tempDirectory.resolve("catalog.tsv");
        Path runtimeSeats = tempDirectory.resolve("seats.tsv");
        Files.writeString(runtimeCatalog, catalog, UTF_8);
        if (seats != null) {
            Files.writeString(runtimeSeats, seats, UTF_8);
        }
        StringWriter normalOutput = new StringWriter();
        StringWriter errorOutput = new StringWriter();
        CustomerUi customerUi = new CustomerUi(
                new StringReader(input), normalOutput, errorOutput);
        CatalogStorage catalogStorage = new CatalogStorage(runtimeCatalog, DEFAULT_RESOURCE);
        SeatStorage seatStorage = new SeatStorage(runtimeSeats);

        new CustomerApplication(customerUi, catalogStorage, seatStorage).run();
        return new ApplicationOutput(
                normalOutput.toString(), errorOutput.toString(), runtimeSeats);
    }

    private record ApplicationOutput(
            String normalOutput, String errorOutput, Path runtimeSeats) {
    }
}
