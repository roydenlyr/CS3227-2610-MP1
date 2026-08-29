package cinecli.admin;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.admin.ui.AdminTerminal;
import cinecli.admin.ui.EndOfInput;
import cinecli.admin.ui.GlobalCommand;
import cinecli.admin.ui.InputFailure;
import cinecli.admin.ui.SubmittedLine;
import cinecli.admin.ui.TerminalInput;
import cinecli.storage.catalog.CatalogStorage;
import cinecli.storage.catalog.CatalogTransactionAdapter;
import cinecli.storage.seat.SeatStorage;
import cinecli.storage.seat.SeatTransactionAdapter;
import cinecli.storage.transaction.MovieDeletionTransaction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScreeningManagementApplicationTest {
    private static final String DEFAULT_RESOURCE = "/cinecli/default-catalog.tsv";
    private static final UUID FIRST_UUID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID SECOND_UUID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @TempDir
    Path tempDirectory;

    @Test
    void run_listsFlattenedScreeningsInPersistedMovieMajorOrder() throws Exception {
        Fixture fixture = fixture(catalog(), null, lines("0"));

        assertEquals(AdminWorkflowOutcome.BACK, fixture.application().run());

        assertEquals("""
                Screening Management

                Screenings
                1. First
                   Movie ID: MOV-1
                   Screening ID: SCR-1
                   Starts at: 02 Jan 2027, 21:30
                2. First
                   Movie ID: MOV-1
                   Screening ID: SCR-2
                   Starts at: 03 Jan 2027, 09:00
                3. Second
                   Movie ID: MOV-2
                   Screening ID: SCR-3
                   Starts at: 01 Jan 2027, 10:00

                Actions
                1. Add screening
                2. Edit screening
                3. Delete screening
                0. Back
                Enter choice:
                """, fixture.terminal().output());
    }

    @Test
    void run_addRetriesValidationAndCollisionThenAppendsToSelectedParent() throws Exception {
        String input = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-1\tFirst\tPG13
                MOVIE\tMOV-2\tSecond\tM18
                SCREENING\tSCR-11111111-1111-4111-8111-111111111111\tMOV-1\t2027-01-02\t21:30
                """;
        Fixture fixture = fixture(input, "CINECLI-SEATS\t1\n", lines(
                "1", "-1", "2", "2027-2-01", "2027-02-29", "2027-02-28",
                "9:00", "24:00", "09:00", "bad", "Y", "0"), FIRST_UUID, SECOND_UUID);
        byte[] originalSeats = Files.readAllBytes(fixture.paths().seats());

        assertEquals(AdminWorkflowOutcome.BACK, fixture.application().run());

        assertAll(
                () -> assertTrue(fixture.terminal().output().contains(
                        "Enter a movie number from 1 to 2, or 0 to cancel.")),
                () -> assertEquals(2, occurrences(fixture.terminal().output(),
                        "Enter a real date in yyyy-MM-dd format.")),
                () -> assertEquals(2, occurrences(fixture.terminal().output(),
                        "Enter a real time in HH:mm format.")),
                () -> assertTrue(fixture.terminal().output().contains(
                        "Screening ID: SCR-22222222-2222-4222-8222-222222222222")),
                () -> assertTrue(Files.readString(fixture.paths().catalog(), UTF_8).contains(
                        "SCREENING\tSCR-22222222-2222-4222-8222-222222222222\tMOV-2\t2027-02-28\t09:00")),
                () -> assertArrayEquals(originalSeats, Files.readAllBytes(fixture.paths().seats())));
    }

    @Test
    void run_addUnavailableAndCancellationsPreserveCatalog() throws Exception {
        Fixture empty = fixture("CINECLI-CATALOG\t1\n", null, lines("1", "0"));
        assertEquals(AdminWorkflowOutcome.BACK, empty.application().run());
        assertTrue(empty.terminal().output().contains("There are no movies available to add a screening."));

        for (List<TerminalInput> inputs : List.of(
                lines("1", "0", "0"),
                lines("1", "/cancel", "0"),
                lines("1", "1", "/cancel", "0"),
                lines("1", "1", "2027-01-01", "/cancel", "0"),
                lines("1", "1", "2027-01-01", "10:00", "N", "0"))) {
            Fixture fixture = fixture(catalog(), null, inputs);
            byte[] original = Files.readAllBytes(fixture.paths().catalog());
            assertEquals(AdminWorkflowOutcome.BACK, fixture.application().run());
            assertArrayEquals(original, Files.readAllBytes(fixture.paths().catalog()));
        }

        Fixture firstMovie = fixture(catalog(), null,
                lines("1", "1", "2027-02-02", "10:00", "N", "0"));
        assertEquals(AdminWorkflowOutcome.BACK, firstMovie.application().run());
        assertTrue(firstMovie.terminal().output().contains("Display position: 3"));
    }

    @Test
    void run_editDateAndTimePreservesIdParentPositionAndOccupancy() throws Exception {
        String seats = """
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-1\tA1
                TAKEN_SEAT\tSCR-3\tB2
                """;
        Fixture fixture = fixture(catalog(), seats, lines(
                "2", "1", "1", "2027-02-03", "2", "08:15", "3", "Y", "0"));
        byte[] originalSeats = Files.readAllBytes(fixture.paths().seats());

        assertEquals(AdminWorkflowOutcome.BACK, fixture.application().run());

        assertAll(
                () -> assertTrue(fixture.terminal().output().contains(
                        "Date: 2027-01-02 -> 2027-02-03")),
                () -> assertTrue(fixture.terminal().output().contains("Occupancy will be preserved.")),
                () -> assertEquals("""
                        CINECLI-CATALOG\t1
                        MOVIE\tMOV-1\tFirst\tPG13
                        MOVIE\tMOV-2\tSecond\tM18
                        SCREENING\tSCR-1\tMOV-1\t2027-02-03\t08:15
                        SCREENING\tSCR-2\tMOV-1\t2027-01-03\t09:00
                        SCREENING\tSCR-3\tMOV-2\t2027-01-01\t10:00
                        """, Files.readString(fixture.paths().catalog(), UTF_8)),
                () -> assertArrayEquals(originalSeats, Files.readAllBytes(fixture.paths().seats())));
    }

    @Test
    void run_editValidatesNoOpAndCancelPreservesExactCatalog() throws Exception {
        Fixture fixture = fixture(catalog(), null, lines(
                "2", "1", "9", "1", "invalid", "2027-01-02", "2", "21:30", "3", "0", "0"));
        byte[] original = Files.readAllBytes(fixture.paths().catalog());

        assertEquals(AdminWorkflowOutcome.BACK, fixture.application().run());

        assertAll(
                () -> assertTrue(fixture.terminal().output().contains("Enter 0, 1, 2, or 3.")),
                () -> assertTrue(fixture.terminal().output().contains(
                        "The screening date is unchanged.")),
                () -> assertTrue(fixture.terminal().output().contains(
                        "The screening time is unchanged.")),
                () -> assertTrue(fixture.terminal().output().contains("No screening changes have been made.")),
                () -> assertArrayEquals(original, Files.readAllBytes(fixture.paths().catalog())));
    }

    @Test
    void run_editCancellationAndSingleFieldPreviewsCoverAlternatives() throws Exception {
        for (List<TerminalInput> inputs : List.of(
                lines("2", "0", "0"),
                lines("2", "/cancel", "0"),
                lines("2", "1", "/cancel", "0"),
                lines("2", "1", "1", "/cancel", "0"),
                lines("2", "1", "2", "/cancel", "0"),
                lines("2", "1", "1", "2027-02-02", "3", "N", "0"),
                lines("2", "1", "2", "08:00", "3", "N", "0"))) {
            Fixture fixture = fixture(catalog(), null, inputs);
            byte[] original = Files.readAllBytes(fixture.paths().catalog());
            assertEquals(AdminWorkflowOutcome.BACK, fixture.application().run());
            assertArrayEquals(original, Files.readAllBytes(fixture.paths().catalog()));
        }
    }

    @Test
    void run_deletePreviewsOnlyCountThenClearsTargetOccupancyAndRedraws() throws Exception {
        Fixture fixture = fixture(catalog(), """
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-1\tA1
                TAKEN_SEAT\tSCR-1\tB2
                TAKEN_SEAT\tSCR-2\tC3
                """, lines("3", "1", "maybe", "Y", "0"));

        assertEquals(AdminWorkflowOutcome.BACK, fixture.application().run());

        assertAll(
                () -> assertTrue(fixture.terminal().output().contains("Occupied seats to delete: 2")),
                () -> assertFalse(fixture.terminal().output().contains("A1")),
                () -> assertTrue(fixture.terminal().output().contains(
                        "Enter Y to confirm or N to cancel.")),
                () -> assertEquals("""
                        CINECLI-SEATS\t1
                        TAKEN_SEAT\tSCR-2\tC3
                        """, Files.readString(fixture.paths().seats(), UTF_8)),
                () -> assertFalse(Files.readString(fixture.paths().catalog(), UTF_8)
                        .contains("SCREENING\tSCR-1\t")),
                () -> assertTrue(fixture.terminal().output().contains(
                        "Screening deleted: First [SCR-1]; removed 2 occupied seat(s).")));
    }

    @Test
    void run_deleteCancellationAndMalformedOccupancyFailClosed() throws Exception {
        Fixture cancelled = fixture(catalog(), null, lines("3", "0", "3", "/cancel", "3", "1", "N", "0"));
        byte[] original = Files.readAllBytes(cancelled.paths().catalog());
        assertEquals(AdminWorkflowOutcome.BACK, cancelled.application().run());
        assertArrayEquals(original, Files.readAllBytes(cancelled.paths().catalog()));

        Fixture malformed = fixture(catalog(), "malformed\n", lines("3", "1"));
        byte[] catalogBefore = Files.readAllBytes(malformed.paths().catalog());
        byte[] seatsBefore = Files.readAllBytes(malformed.paths().seats());
        assertEquals(AdminWorkflowOutcome.BACK, malformed.application().run());
        assertAll(
                () -> assertTrue(malformed.terminal().output().contains(
                        "Unable to prepare screening deletion: ")),
                () -> assertArrayEquals(catalogBefore, Files.readAllBytes(malformed.paths().catalog())),
                () -> assertArrayEquals(seatsBefore, Files.readAllBytes(malformed.paths().seats())));
    }

    @Test
    void run_globalCommandsAndTerminalFailuresLeaveDataUnchanged() throws Exception {
        for (GlobalCommand.Type type : GlobalCommand.Type.values()) {
            for (List<TerminalInput> prefix : List.of(
                    List.<TerminalInput>of(), lines("1"), lines("1", "1"),
                    lines("1", "1", "2027-01-01"), lines("2"), lines("2", "1"),
                    lines("3"), lines("3", "1"))) {
                List<TerminalInput> inputs = new ArrayList<>(prefix);
                inputs.add(new GlobalCommand(type));
                Fixture fixture = fixture(catalog(), null, inputs);
                byte[] original = Files.readAllBytes(fixture.paths().catalog());
                assertEquals(AdminWorkflowOutcome.valueOf(type.name()), fixture.application().run());
                assertArrayEquals(original, Files.readAllBytes(fixture.paths().catalog()));
            }
        }
        Fixture eof = fixture(catalog(), null, List.of(new EndOfInput()));
        Fixture inputFailure = fixture(catalog(), null, List.of(new InputFailure()));
        Fixture outputFailure = fixture(catalog(), null, lines("0"));
        outputFailure.terminal().failWriteNumber(1);
        assertAll(
                () -> assertEquals(AdminWorkflowOutcome.TERMINATED, eof.application().run()),
                () -> assertEquals(AdminWorkflowOutcome.TERMINATED, inputFailure.application().run()),
                () -> assertEquals("Unable to read input. CineCLI will exit.\n",
                        inputFailure.terminal().errors()),
                () -> assertEquals(AdminWorkflowOutcome.TERMINATED, outputFailure.application().run()),
                () -> assertEquals("Unable to write output. CineCLI will exit.\n",
                        outputFailure.terminal().errors()));
    }

    @Test
    void run_emptyActionsAndAccessFailuresReportAndReturnBack() throws Exception {
        Fixture empty = fixture("CINECLI-CATALOG\t1\n", null, lines("2", "3", "0"));
        assertEquals(AdminWorkflowOutcome.BACK, empty.application().run());
        assertAll(
                () -> assertTrue(empty.terminal().output().contains("There are no screenings to edit.")),
                () -> assertTrue(empty.terminal().output().contains("There are no screenings to delete.")));

        Fixture malformedCatalog = fixture("malformed\n", null, lines("0"));
        assertEquals(AdminWorkflowOutcome.BACK, malformedCatalog.application().run());
        assertTrue(malformedCatalog.terminal().output().startsWith(
                "Unable to access screening management: "));

        Fixture malformedJournal = fixture(catalog(), null, lines("0"));
        Files.writeString(malformedJournal.paths().journal(), "malformed\n", UTF_8);
        assertEquals(AdminWorkflowOutcome.BACK, malformedJournal.application().run());
        assertTrue(malformedJournal.terminal().output().startsWith(
                "Unable to access screening management: "));

        Fixture emptyOutputFailure = fixture("CINECLI-CATALOG\t1\n", null, lines("2"));
        emptyOutputFailure.terminal().failWriteNumber(2);
        assertEquals(AdminWorkflowOutcome.TERMINATED, emptyOutputFailure.application().run());
    }

    @Test
    void run_publicConstructorAndMissingOrUnaffectedSeatsSupportDelete() throws Exception {
        Fixture missing = fixture(catalog(), null, lines("3", "1", "Y", "0"));
        ScreeningManagementApplication application = new ScreeningManagementApplication(
                new CatalogStorage(missing.paths().catalog(), DEFAULT_RESOURCE),
                transactionFor(missing.paths()), missing.terminal());
        assertEquals(AdminWorkflowOutcome.BACK, application.run());
        assertFalse(Files.exists(missing.paths().seats()));

        Fixture unaffected = fixture(catalog(), "CINECLI-SEATS\t1\nTAKEN_SEAT\tSCR-2\tA1\n",
                lines("3", "1", "Y", "0"));
        byte[] originalSeats = Files.readAllBytes(unaffected.paths().seats());
        assertEquals(AdminWorkflowOutcome.BACK, unaffected.application().run());
        assertArrayEquals(originalSeats, Files.readAllBytes(unaffected.paths().seats()));
    }

    @Test
    void run_outputFailureAtEveryRenderedBlockTerminatesWithoutRollingBackDurableChanges()
            throws Exception {
        List<String[]> flows = List.of(
                new String[] {"bad", "0"},
                new String[] {"1", "bad", "1", "bad", "2027-01-01", "bad", "10:00", "bad", "Y", "0"},
                new String[] {"2", "bad", "1", "bad", "1", "bad", "2027-01-01", "2", "bad",
                        "10:00", "3", "bad", "Y", "0"},
                new String[] {"2", "1", "1", "2027-01-02", "2", "21:30", "3", "0", "0"},
                new String[] {"2", "1", "0", "0"},
                new String[] {"3", "bad", "1", "bad", "Y", "0"},
                new String[] {"2", "0", "0"});
        for (String[] flow : flows) {
            Fixture baseline = fixture(catalog(), "CINECLI-SEATS\t1\n", lines(flow));
            baseline.application().run();
            for (int failedWrite = 1; failedWrite <= baseline.terminal().writeCount(); failedWrite++) {
                Fixture fixture = fixture(catalog(), "CINECLI-SEATS\t1\n", lines(flow));
                fixture.terminal().failWriteNumber(failedWrite);
                assertEquals(AdminWorkflowOutcome.TERMINATED, fixture.application().run());
            }
        }
    }

    private int occurrences(String text, String fragment) {
        return text.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
    }

    private Fixture fixture(String catalog, String seats, List<TerminalInput> inputs, UUID... uuids)
            throws Exception {
        Path runtime = tempDirectory.resolve(UUID.randomUUID().toString());
        Files.createDirectories(runtime);
        Paths paths = new Paths(runtime.resolve("catalog.tsv"), runtime.resolve("seats.tsv"),
                runtime.resolve("catalog-transaction.journal"));
        Files.writeString(paths.catalog(), catalog, UTF_8);
        if (seats != null) {
            Files.writeString(paths.seats(), seats, UTF_8);
        }
        CatalogStorage catalogStorage = new CatalogStorage(paths.catalog(), DEFAULT_RESOURCE);
        ScriptedTerminal terminal = new ScriptedTerminal(inputs);
        Deque<UUID> generatedIds = new ArrayDeque<>(Arrays.asList(uuids));
        UuidGenerator generator = generatedIds.isEmpty() ? () -> SECOND_UUID : generatedIds::removeFirst;
        ScreeningManagementApplication application = new ScreeningManagementApplication(
                catalogStorage, transactionFor(paths), terminal, generator);
        return new Fixture(application, terminal, paths);
    }

    private MovieDeletionTransaction transactionFor(Paths paths) {
        return new MovieDeletionTransaction(
                new CatalogTransactionAdapter(new CatalogStorage(paths.catalog(), DEFAULT_RESOURCE), paths.catalog()),
                new SeatTransactionAdapter(new SeatStorage(paths.seats()), paths.seats()), paths.journal());
    }

    private List<TerminalInput> lines(String... values) {
        return Arrays.stream(values).map(SubmittedLine::new).map(TerminalInput.class::cast).toList();
    }

    private String catalog() {
        return """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-1\tFirst\tPG13
                MOVIE\tMOV-2\tSecond\tM18
                SCREENING\tSCR-1\tMOV-1\t2027-01-02\t21:30
                SCREENING\tSCR-2\tMOV-1\t2027-01-03\t09:00
                SCREENING\tSCR-3\tMOV-2\t2027-01-01\t10:00
                """;
    }

    private static final class ScriptedTerminal implements AdminTerminal {
        private final Deque<TerminalInput> inputs;
        private final List<String> outputs = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private int writeCount;
        private int failedWriteNumber = -1;

        private ScriptedTerminal(List<TerminalInput> inputs) {
            this.inputs = new ArrayDeque<>(inputs);
        }

        @Override
        public TerminalInput readLine() {
            return inputs.isEmpty() ? new EndOfInput() : inputs.removeFirst();
        }

        @Override
        public boolean write(String text) {
            writeCount++;
            if (writeCount == failedWriteNumber) {
                return false;
            }
            outputs.add(text);
            return true;
        }

        @Override
        public boolean writeError(String text) {
            errors.add(text);
            return true;
        }

        private String output() {
            return String.join("", outputs);
        }

        private String errors() {
            return String.join("", errors);
        }

        private void failWriteNumber(int writeNumber) {
            failedWriteNumber = writeNumber;
        }

        private int writeCount() {
            return writeCount;
        }
    }

    private record Paths(Path catalog, Path seats, Path journal) {
    }

    private record Fixture(
            ScreeningManagementApplication application, ScriptedTerminal terminal, Paths paths) {
    }
}
