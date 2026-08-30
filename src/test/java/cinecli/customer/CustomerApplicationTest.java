package cinecli.customer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.admin.ui.AdminTerminal;
import cinecli.admin.ui.EndOfInput;
import cinecli.admin.ui.InputFailure;
import cinecli.admin.ui.SubmittedLine;
import cinecli.admin.ui.TerminalInput;
import cinecli.customer.ui.CustomerUi;
import cinecli.storage.catalog.CatalogStorage;
import cinecli.storage.pricing.PricingStorage;
import cinecli.storage.seat.SeatStorage;
import cinecli.storage.seat.SeatStorageTestSupport;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
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
    void run_malformedPricing_reportsFailureBeforeSeatStorageIsAccessed() throws IOException {
        Path runtimeCatalog = tempDirectory.resolve("catalog.tsv");
        Path runtimeSeats = tempDirectory.resolve("seats.tsv");
        Path runtimePricing = tempDirectory.resolve("pricing.tsv");
        Files.writeString(runtimeCatalog, singleScreeningCatalog(), UTF_8);
        Files.writeString(runtimePricing, "not valid pricing\n", UTF_8);
        StringWriter normalOutput = new StringWriter();
        StringWriter errorOutput = new StringWriter();

        new CustomerApplication(
                new CustomerUi(new StringReader("\n"), normalOutput, errorOutput),
                new CatalogStorage(runtimeCatalog, DEFAULT_RESOURCE),
                new SeatStorage(runtimeSeats),
                new PricingStorage(runtimePricing)).run();

        assertAll(
                () -> assertTrue(errorOutput.toString().contains("Unable to load pricing:")),
                () -> assertFalse(normalOutput.toString().contains("Movie Catalog")),
                () -> assertFalse(Files.exists(runtimeSeats)),
                () -> assertEquals("not valid pricing\n", Files.readString(runtimePricing, UTF_8)));
    }

    @Test
    void run_customPricing_capturesAndRendersLoadedPrices() throws IOException {
        Path runtimeCatalog = tempDirectory.resolve("catalog.tsv");
        Path runtimeSeats = tempDirectory.resolve("seats.tsv");
        Path runtimePricing = tempDirectory.resolve("pricing.tsv");
        Files.writeString(runtimeCatalog, singleScreeningCatalog(), UTF_8);
        Files.writeString(runtimePricing, """
                CINECLI-PRICING\t1
                TICKET_PRICE\tADULT\t12.34
                TICKET_PRICE\tSENIOR\t4.50
                TICKET_PRICE\tSTUDENT\t7.00
                SNACK_PRICE\tPOPCORN\t6.78
                SNACK_PRICE\tNACHOS\t6.00
                SNACK_PRICE\tSOFT_DRINK\t3.00
                SNACK_PRICE\tPOPCORN_COMBO\t7.00
                SNACK_PRICE\tNACHOS_COMBO\t8.00
                PROMOTION\tSAVE\t25
                """, UTF_8);
        StringWriter normalOutput = new StringWriter();
        StringWriter errorOutput = new StringWriter();

        new CustomerApplication(
                new CustomerUi(
                        new StringReader("\n1A\nA1\nY\n1\n1\n2\n0\nsave\n"),
                        normalOutput,
                        errorOutput),
                new CatalogStorage(runtimeCatalog, DEFAULT_RESOURCE),
                new SeatStorage(runtimeSeats),
                new PricingStorage(runtimePricing)).run();

        assertAll(
                () -> assertTrue(normalOutput.toString().contains("1. Adult - S$12.34")),
                () -> assertTrue(normalOutput.toString().contains("1. Popcorn - S$6.78")),
                () -> assertTrue(normalOutput.toString().contains(
                        "- A1: Adult - S$12.34")),
                () -> assertTrue(normalOutput.toString().contains(
                        "- 2 x Popcorn - S$6.78 each")),
                () -> assertTrue(hasBillLine(normalOutput.toString(), "Promo Code:", "SAVE")),
                () -> assertTrue(hasBillLine(normalOutput.toString(), "TOTAL:", "S$19.43")),
                () -> assertEquals("", errorOutput.toString()));
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
                new StringReader("\n1A\nA1\nY\n1\n0\n\n"), normalOutput, errorOutput);

        new CustomerApplication(
                customerUi,
                new CatalogStorage(runtimeCatalog, DEFAULT_RESOURCE),
                SeatStorageTestSupport.failingReplacement(runtimeSeats),
                new PricingStorage(tempDirectory.resolve("pricing.tsv"))).run();

        assertAll(
                () -> assertTrue(errorOutput.toString().contains(
                        "Unable to load or update seat availability")),
                () -> assertTrue(normalOutput.toString().contains("Ticket Types")),
                () -> assertFalse(normalOutput.toString().contains("BILL SUMMARY")),
                () -> assertFalse(Files.exists(runtimeSeats)));
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
                () -> assertTrue(ticketMenuIndex >= 0),
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
                () -> assertEquals(Set.of("catalog.tsv", "pricing.tsv", "seats.tsv"), runtimeFileNames),
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
                () -> assertFalse(Files.exists(applicationOutput.runtimeSeats())),
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
                        "is not available"))),
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

    @Test
    void run_globalCommandsAtEveryDeferredPrompt_discardTentativeSeats() throws IOException {
        List<String> prefixes = List.of(
                "\n/admin\n",
                "\n1A\n/admin\n",
                "\n1A\nA1\n/admin\n",
                "\n1A\nA1\nY\n/admin\n",
                "\n1A\nA1\nY\n1\n/admin\n",
                "\n1A\nA1\nY\n1\n1\n/admin\n",
                "\n1A\nA1\nY\n1\n0\n/admin\n");

        for (String input : prefixes) {
            Path runtimeSeats = tempDirectory.resolve("deferred-" + prefixes.indexOf(input) + ".tsv");

            CustomerWorkflowOutcome outcome = runWithTerminal(
                    new CustomerUi(new StringReader(input), new StringWriter(), new StringWriter()), runtimeSeats);

            assertAll(
                    () -> assertEquals(CustomerWorkflowOutcome.ADMIN, outcome),
                    () -> assertFalse(Files.exists(runtimeSeats)));
        }
    }

    @Test
    void run_customerAndExitCommands_returnTheirTypedOutcomes() throws IOException {
        Path customerSeats = tempDirectory.resolve("customer-command.tsv");
        Path exitSeats = tempDirectory.resolve("exit-command.tsv");

        assertAll(
                () -> assertEquals(
                        CustomerWorkflowOutcome.CUSTOMER,
                        runWithTerminal(
                                new CustomerUi(
                                        new StringReader("\n/customer\n"),
                                        new StringWriter(),
                                        new StringWriter()),
                                customerSeats)),
                () -> assertFalse(Files.exists(customerSeats)),
                () -> assertEquals(
                        CustomerWorkflowOutcome.EXIT,
                        runWithTerminal(
                                new CustomerUi(
                                        new StringReader("\n/exit\n"),
                                        new StringWriter(),
                                        new StringWriter()),
                                exitSeats)),
                () -> assertFalse(Files.exists(exitSeats)));
    }

    @Test
    void run_localCancellationAtEveryDeferredPrompt_discardsTentativeSeats() throws IOException {
        List<String> inputs = List.of(
                "\nCANCEL\n",
                "\n1A\nA1\n/cancel\n",
                "\n1A\nA1\nY\n/cancel\n",
                "\n1A\nA1\nY\n1\n/cancel\n",
                "\n1A\nA1\nY\n1\n1\n/cancel\n",
                "\n1A\nA1\nY\n1\n0\n/cancel\n");

        for (String input : inputs) {
            Path runtimeSeats = tempDirectory.resolve("cancel-" + inputs.indexOf(input) + ".tsv");

            CustomerWorkflowOutcome outcome = runWithTerminal(
                    new CustomerUi(new StringReader(input), new StringWriter(), new StringWriter()), runtimeSeats);

            assertAll(
                    () -> assertEquals(CustomerWorkflowOutcome.CUSTOMER, outcome),
                    () -> assertFalse(Files.exists(runtimeSeats)));
        }
    }

    @Test
    void run_finalConflictSuppressesFirstBillAndReturnsToFreshSeatSelection() throws IOException {
        Path runtimeSeats = tempDirectory.resolve("conflict.tsv");
        ScriptedTerminal terminal = ScriptedTerminal.fromLines(
                List.of("", "1A", "A1", "Y", "1", "0", "", "B1", "Y", "1", "0", ""),
                text -> true,
                () -> Files.writeString(
                        runtimeSeats,
                        "CINECLI-SEATS\t1\nTAKEN_SEAT\tSCR-001\tA1\n",
                        UTF_8));

        CustomerWorkflowOutcome outcome = runWithTerminal(new CustomerUi(terminal), runtimeSeats);

        assertAll(
                () -> assertEquals(CustomerWorkflowOutcome.TERMINATED, outcome),
                () -> assertEquals(1, terminal.output().split("BILL SUMMARY", -1).length - 1),
                () -> assertTrue(terminal.output().contains(
                        "selected seats are no longer available; choose again")),
                () -> assertEquals(
                        "CINECLI-SEATS\t1\n"
                                + "TAKEN_SEAT\tSCR-001\tA1\n"
                                + "TAKEN_SEAT\tSCR-001\tB1\n",
                        Files.readString(runtimeSeats, UTF_8)));
    }

    @Test
    void run_finalConflictOutputFailure_terminatesWithoutBill() throws IOException {
        Path runtimeSeats = tempDirectory.resolve("conflict-output.tsv");
        ScriptedTerminal terminal = ScriptedTerminal.fromLines(
                List.of("", "1A", "A1", "Y", "1", "0", ""),
                text -> !text.contains("selected seats are no longer available"),
                () -> Files.writeString(
                        runtimeSeats,
                        "CINECLI-SEATS\t1\nTAKEN_SEAT\tSCR-001\tA1\n",
                        UTF_8));

        CustomerWorkflowOutcome outcome = runWithTerminal(new CustomerUi(terminal), runtimeSeats);

        assertAll(
                () -> assertEquals(CustomerWorkflowOutcome.TERMINATED, outcome),
                () -> assertFalse(terminal.output().contains("BILL SUMMARY")),
                () -> assertEquals(
                        "CINECLI-SEATS\t1\nTAKEN_SEAT\tSCR-001\tA1\n",
                        Files.readString(runtimeSeats, UTF_8)));
    }

    @Test
    void run_outputFailuresAtDeferredStages_preserveUnfinalizedState() throws IOException {
        List<OutputFailureCase> cases = List.of(
                new OutputFailureCase("Seat Selection", "\n1A\n"),
                new OutputFailureCase("Selected Tickets", "\n1A\nA1\nY\n1\n"),
                new OutputFailureCase("No snacks or combos selected.", "\n1A\nA1\nY\n1\n0\n"));

        for (OutputFailureCase failureCase : cases) {
            Path runtimeSeats = tempDirectory.resolve("output-"
                    + cases.indexOf(failureCase) + ".tsv");
            ScriptedTerminal terminal = new ScriptedTerminal(
                    lines(failureCase.input()), text -> !text.contains(failureCase.failingText()), () -> { });

            CustomerWorkflowOutcome outcome = runWithTerminal(new CustomerUi(terminal), runtimeSeats);

            assertAll(
                    () -> assertEquals(CustomerWorkflowOutcome.TERMINATED, outcome),
                    () -> assertFalse(Files.exists(runtimeSeats)));
        }
    }

    @Test
    void run_billOutputFailureRetainsDurablyConfirmedSeats() throws IOException {
        Path runtimeSeats = tempDirectory.resolve("bill-output.tsv");
        ScriptedTerminal terminal = new ScriptedTerminal(
                lines("\n1A\nA1\nY\n1\n0\n\n"),
                text -> !text.contains("BILL SUMMARY"), () -> { });

        CustomerWorkflowOutcome outcome = runWithTerminal(new CustomerUi(terminal), runtimeSeats);

        assertAll(
                () -> assertEquals(CustomerWorkflowOutcome.TERMINATED, outcome),
                () -> assertFalse(terminal.output().contains("BILL SUMMARY")),
                () -> assertTrue(Files.exists(runtimeSeats)),
                () -> assertTrue(Files.readString(runtimeSeats, UTF_8).contains("TAKEN_SEAT\tSCR-001\tA1")));
    }

    @Test
    void run_successfulPurchaseWithBlankPostSessionInput_returnsCustomer() throws IOException {
        Path runtimeSeats = tempDirectory.resolve("post-session.tsv");

        CustomerWorkflowOutcome outcome = runWithTerminal(
                new CustomerUi(
                        new StringReader("\n1A\nA1\nY\n1\n0\n\n\n"),
                        new StringWriter(),
                        new StringWriter()),
                runtimeSeats);

        assertAll(
                () -> assertEquals(CustomerWorkflowOutcome.CUSTOMER, outcome),
                () -> assertTrue(Files.exists(runtimeSeats)));
    }

    @Test
    void run_inputAndInitialOutputFailures_terminateSafely() throws IOException {
        Path inputFailureSeats = tempDirectory.resolve("input-failure.tsv");
        ScriptedTerminal inputFailureTerminal = new ScriptedTerminal(
                List.<TerminalInput>of(new InputFailure()), text -> true, () -> { });
        Path outputFailureSeats = tempDirectory.resolve("initial-output-failure.tsv");
        ScriptedTerminal outputFailureTerminal = new ScriptedTerminal(
                List.<TerminalInput>of(new SubmittedLine("")), text -> false, () -> { });

        assertAll(
                () -> assertEquals(
                        CustomerWorkflowOutcome.TERMINATED,
                        runWithTerminal(new CustomerUi(inputFailureTerminal), inputFailureSeats)),
                () -> assertTrue(inputFailureTerminal.errorOutput().contains("Unable to read input")),
                () -> assertFalse(Files.exists(inputFailureSeats)),
                () -> assertEquals(
                        CustomerWorkflowOutcome.TERMINATED,
                        runWithTerminal(new CustomerUi(outputFailureTerminal), outputFailureSeats)),
                () -> assertFalse(Files.exists(outputFailureSeats)));
    }

    @Test
    void run_catalogOutputFailure_terminatesBeforeScreeningSelection() throws IOException {
        Path runtimeSeats = tempDirectory.resolve("catalog-output-failure.tsv");
        ScriptedTerminal terminal = new ScriptedTerminal(
                lines("\n"), text -> !text.contains("Movie Catalog"), () -> { });

        CustomerWorkflowOutcome outcome = runWithTerminal(new CustomerUi(terminal), runtimeSeats);

        assertAll(
                () -> assertEquals(CustomerWorkflowOutcome.TERMINATED, outcome),
                () -> assertFalse(Files.exists(runtimeSeats)),
                () -> assertFalse(terminal.output().contains("Screening Selection")));
    }

    @Test
    void read_unexpectedTerminalResult_throwsIllegalStateException() throws Exception {
        CustomerApplication application = new CustomerApplication(
                new CustomerUi(new StringReader(""), new StringWriter(), new StringWriter()),
                new CatalogStorage(tempDirectory.resolve("unexpected-catalog.tsv"), DEFAULT_RESOURCE),
                new SeatStorage(tempDirectory.resolve("unexpected-seats.tsv")),
                new PricingStorage(tempDirectory.resolve("unexpected-pricing.tsv")));
        Method read = CustomerApplication.class.getDeclaredMethod("read", TerminalInput.class);
        read.setAccessible(true);

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class, () -> read.invoke(application, new Object[] {null}));

        assertTrue(exception.getCause() instanceof IllegalStateException);
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
        PricingStorage pricingStorage = new PricingStorage(tempDirectory.resolve("pricing.tsv"));

        new CustomerApplication(customerUi, catalogStorage, seatStorage, pricingStorage).run();
        return new ApplicationOutput(
                normalOutput.toString(), errorOutput.toString(), runtimeSeats);
    }

    private record ApplicationOutput(
            String normalOutput, String errorOutput, Path runtimeSeats) {
    }

    private CustomerWorkflowOutcome runWithTerminal(CustomerUi customerUi, Path runtimeSeats)
            throws IOException {
        Path runtimeCatalog = runtimeSeats.resolveSibling(runtimeSeats.getFileName() + ".catalog.tsv");
        Files.writeString(runtimeCatalog, singleScreeningCatalog(), UTF_8);
        return new CustomerApplication(
                customerUi,
                new CatalogStorage(runtimeCatalog, DEFAULT_RESOURCE),
                new SeatStorage(runtimeSeats),
                new PricingStorage(runtimeSeats.resolveSibling(runtimeSeats.getFileName() + ".pricing.tsv")))
                .run();
    }

    private List<TerminalInput> lines(String input) {
        return input.lines().map(SubmittedLine::new).map(TerminalInput.class::cast).toList();
    }

    private record OutputFailureCase(String failingText, String input) {
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws IOException;
    }

    private static final class ScriptedTerminal implements AdminTerminal {
        private final Deque<TerminalInput> inputs;
        private final Predicate<String> isWritable;
        private final CheckedAction beforeSeventhInput;
        private final StringBuilder output = new StringBuilder();
        private final StringBuilder errorOutput = new StringBuilder();
        private int inputCount;

        private ScriptedTerminal(
                List<TerminalInput> inputs, Predicate<String> isWritable, CheckedAction beforeSeventhInput) {
            this.inputs = new ArrayDeque<>(inputs);
            this.isWritable = isWritable;
            this.beforeSeventhInput = beforeSeventhInput;
        }

        private static ScriptedTerminal fromLines(
                List<String> inputs, Predicate<String> isWritable, CheckedAction beforeSeventhInput) {
            return new ScriptedTerminal(
                    inputs.stream().map(SubmittedLine::new).map(TerminalInput.class::cast).toList(),
                    isWritable,
                    beforeSeventhInput);
        }

        @Override
        public TerminalInput readLine() {
            inputCount++;
            if (inputCount == 7) {
                try {
                    beforeSeventhInput.run();
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
            }
            return inputs.isEmpty() ? new EndOfInput() : inputs.removeFirst();
        }

        @Override
        public boolean write(String text) {
            if (!isWritable.test(text)) {
                return false;
            }
            output.append(text);
            return true;
        }

        @Override
        public boolean writeError(String text) {
            if (!isWritable.test(text)) {
                return false;
            }
            errorOutput.append(text);
            return true;
        }

        private String output() {
            return output.toString();
        }

        private String errorOutput() {
            return errorOutput.toString();
        }
    }
}
