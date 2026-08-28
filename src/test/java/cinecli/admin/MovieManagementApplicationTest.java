package cinecli.admin;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.admin.ui.EndOfInput;
import cinecli.admin.ui.GlobalCommand;
import cinecli.admin.ui.InputFailure;
import cinecli.admin.ui.MovieManagementTerminal;
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

class MovieManagementApplicationTest {
    private static final String DEFAULT_RESOURCE = "/cinecli/default-catalog.tsv";
    private static final UUID FIRST_UUID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID SECOND_UUID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @TempDir
    Path tempDirectory;

    @Test
    void run_nonemptyCatalog_listsCompleteMoviesInPersistedOrder() throws Exception {
        Fixture fixture = fixture("""
                CINECLI-CATALOG\t1
                MOVIE\tMOV-2\tÉtoile  Meridian\tM18
                MOVIE\tMOV-1\tFirst\tPG13
                SCREENING\tSCR-1\tMOV-2\t2027-01-02\t21:30
                """, null, lines("0"));

        MovieManagementOutcome outcome = fixture.application().run();

        assertAll(
                () -> assertEquals(MovieManagementOutcome.BACK, outcome),
                () -> assertEquals("""
                        Movie Management

                        Movies
                        1. Étoile  Meridian
                           ID: MOV-2
                           Rating: M18
                           Screenings: 1
                        2. First
                           ID: MOV-1
                           Rating: PG13
                           Screenings: 0

                        Actions
                        1. Add movie
                        2. Edit movie
                        3. Delete movie
                        0. Back
                        Enter choice:
                        """, fixture.terminal().output()));
    }

    @Test
    void run_emptyCatalog_keepsActionsAndRejectsEmptyEditAndDelete() throws Exception {
        Fixture fixture = fixture(
                "CINECLI-CATALOG\t1\n", null, lines("2", "3", "0"));

        assertEquals(MovieManagementOutcome.BACK, fixture.application().run());

        assertAll(
                () -> assertTrue(fixture.terminal().output().contains(
                        "No movies are currently available.")),
                () -> assertTrue(fixture.terminal().output().contains(
                        "There are no movies to edit.")),
                () -> assertTrue(fixture.terminal().output().contains(
                        "There are no movies to delete.")));
    }

    @Test
    void run_addConfirmed_retriesValidationAndCollisionThenAppends() throws Exception {
        String original = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-11111111-1111-4111-8111-111111111111\tExisting\tPG13
                """;
        Fixture fixture = fixture(original, null, lines(
                "1", "\tbad", "\u2003  新  星  \u00A0", "PG13", "2", "maybe", " y ", "0"),
                FIRST_UUID, SECOND_UUID);

        assertEquals(MovieManagementOutcome.BACK, fixture.application().run());

        assertAll(
                () -> assertTrue(fixture.terminal().output().contains(
                        "Movie title must not contain tabs or control characters.")),
                () -> assertTrue(fixture.terminal().output().contains(
                        "Enter 1 for PG13, 2 for M18, or 3 for R21.")),
                () -> assertTrue(fixture.terminal().output().contains(
                        "ID: MOV-22222222-2222-4222-8222-222222222222")),
                () -> assertTrue(fixture.terminal().output().contains(
                        "Title: 新  星")),
                () -> assertTrue(fixture.terminal().output().contains(
                        "Enter Y to confirm or N to cancel.")),
                () -> assertTrue(Files.readString(fixture.paths().catalog(), UTF_8).contains(
                        "MOVIE\tMOV-22222222-2222-4222-8222-222222222222\t新  星\tM18")));
    }

    @Test
    void run_addCancelledAtRating_preservesExactCatalog() throws Exception {
        Fixture fixture = fixture(simpleCatalog(), null, lines("1", "Draft", "/CANCEL", "0"));
        byte[] original = Files.readAllBytes(fixture.paths().catalog());

        assertEquals(MovieManagementOutcome.BACK, fixture.application().run());

        assertAll(
                () -> assertTrue(fixture.terminal().output().contains("Movie addition cancelled.")),
                () -> assertArrayEquals(original, Files.readAllBytes(fixture.paths().catalog())));
    }

    @Test
    void run_editBothConfirmed_preservesIdentityPositionAndScreenings() throws Exception {
        Fixture fixture = fixture(simpleCatalog(), null, lines(
                "2", "1", "1", "Updated", "2", "3", "3", "Y", "0"));

        assertEquals(MovieManagementOutcome.BACK, fixture.application().run());

        assertAll(
                () -> assertTrue(fixture.terminal().output().contains("First -> Updated")),
                () -> assertTrue(fixture.terminal().output().contains("PG13 -> R21")),
                () -> assertTrue(fixture.terminal().output().contains(
                        "Movie updated: Updated [MOV-1].")),
                () -> assertEquals("""
                        CINECLI-CATALOG\t1
                        MOVIE\tMOV-1\tUpdated\tR21
                        SCREENING\tSCR-1\tMOV-1\t2027-01-02\t21:30
                        """, Files.readString(fixture.paths().catalog(), UTF_8)));
    }

    @Test
    void run_editNoOpThenCancel_reportsWithoutWriting() throws Exception {
        Fixture fixture = fixture(simpleCatalog(), null, lines(
                "2", "1", "1", "First", "2", "1", "3", "0", "0"));
        byte[] original = Files.readAllBytes(fixture.paths().catalog());

        assertEquals(MovieManagementOutcome.BACK, fixture.application().run());

        assertAll(
                () -> assertTrue(fixture.terminal().output().contains("The movie title is unchanged.")),
                () -> assertTrue(fixture.terminal().output().contains("The movie rating is unchanged.")),
                () -> assertTrue(fixture.terminal().output().contains("No movie changes have been made.")),
                () -> assertTrue(fixture.terminal().output().contains("Movie edit cancelled.")),
                () -> assertArrayEquals(original, Files.readAllBytes(fixture.paths().catalog())));
    }

    @Test
    void run_cascadeDelete_previewsCountsAndPersistsThenRedraws() throws Exception {
        Fixture fixture = fixture(simpleCatalog(), """
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-1\tA1
                TAKEN_SEAT\tSCR-1\tB2
                """, lines("3", "1", "Y", "0"));

        assertEquals(MovieManagementOutcome.BACK, fixture.application().run());

        assertAll(
                () -> assertTrue(fixture.terminal().output().contains(
                        "1. SCR-1 - 02 Jan 2027, 21:30 - Occupied seats: 2")),
                () -> assertFalse(fixture.terminal().output().contains("A1")),
                () -> assertTrue(fixture.terminal().output().contains(
                        "removed 1 screening(s) and 2 occupied seat(s).")),
                () -> assertEquals("CINECLI-CATALOG\t1\n",
                        Files.readString(fixture.paths().catalog(), UTF_8)),
                () -> assertEquals("CINECLI-SEATS\t1\n",
                        Files.readString(fixture.paths().seats(), UTF_8)),
                () -> assertFalse(Files.exists(fixture.paths().journal())));
    }

    @Test
    void run_typedGlobalEofInputAndOutputFailures_areFailClosed() throws Exception {
        Fixture global = fixture(simpleCatalog(), null,
                List.of(new GlobalCommand(GlobalCommand.Type.CUSTOMER)));
        Fixture eof = fixture(simpleCatalog(), null, List.of(new EndOfInput()));
        Fixture failedInput = fixture(simpleCatalog(), null, List.of(new InputFailure()));
        Fixture failedOutput = fixture(simpleCatalog(), null, lines("0"));
        failedOutput.terminal().failWriteNumber(1);

        assertAll(
                () -> assertEquals(MovieManagementOutcome.CUSTOMER, global.application().run()),
                () -> assertEquals(MovieManagementOutcome.TERMINATED, eof.application().run()),
                () -> assertEquals(MovieManagementOutcome.TERMINATED, failedInput.application().run()),
                () -> assertEquals("Unable to read input. CineCLI will exit.\n",
                        failedInput.terminal().errors()),
                () -> assertEquals(MovieManagementOutcome.TERMINATED, failedOutput.application().run()),
                () -> assertEquals("Unable to write output. CineCLI will exit.\n",
                        failedOutput.terminal().errors()));
    }

    @Test
    void run_numericTitleRatingAndConfirmationPartitions_repromptExactly() throws Exception {
        Fixture fixture = fixture("CINECLI-CATALOG\t1\n", null, lines(
                "", "-1", "+1", "1.0", "1e0", "1 0", "01", "٤", "2147483648", "4",
                "1", "\u2003\u00A0", "0", "0", "4", "PG13", "2147483648", "1",
                "", "yes", "N", "0"));

        assertEquals(MovieManagementOutcome.BACK, fixture.application().run());

        assertAll(
                () -> assertEquals(10, occurrences(
                        fixture.terminal().output(), "Enter 0, 1, 2, or 3.")),
                () -> assertTrue(fixture.terminal().output().contains(
                        "Movie title must not be blank.")),
                () -> assertEquals(4, occurrences(fixture.terminal().output(),
                        "Enter 1 for PG13, 2 for M18, or 3 for R21.")),
                () -> assertEquals(2, occurrences(fixture.terminal().output(),
                        "Enter Y to confirm or N to cancel.")),
                () -> assertTrue(fixture.terminal().output().contains("Movie addition cancelled.")));
    }

    @Test
    void run_editAndDeleteCancellationAlternatives_preserveData() throws Exception {
        for (List<TerminalInput> inputs : List.of(
                lines("2", "0", "0"),
                lines("2", "/cancel", "0"),
                lines("2", "2", "0", "0"),
                lines("2", "1", "/cancel", "0"),
                lines("2", "1", "2", "/cancel", "0"),
                lines("2", "1", "1", "Changed", "3", "N", "0"),
                lines("3", "0", "0"),
                lines("3", "/cancel", "0"),
                lines("3", "1", "bad", "/cancel", "0"))) {
            Fixture fixture = fixture(simpleCatalog(), null, inputs);
            byte[] original = Files.readAllBytes(fixture.paths().catalog());
            assertEquals(MovieManagementOutcome.BACK, fixture.application().run());
            assertArrayEquals(original, Files.readAllBytes(fixture.paths().catalog()));
        }
    }

    @Test
    void run_globalCommandsAtNestedPrompts_returnTypedOutcomesWithoutWrites() throws Exception {
        for (GlobalCommand.Type type : GlobalCommand.Type.values()) {
            MovieManagementOutcome expected = MovieManagementOutcome.valueOf(type.name());
            for (List<TerminalInput> prefix : List.of(
                    List.<TerminalInput>of(),
                    lines("1"),
                    lines("1", "Title"),
                    lines("1", "Title", "1"),
                    lines("2"),
                    lines("2", "1"),
                    lines("3"),
                    lines("3", "1"))) {
                List<TerminalInput> inputs = new ArrayList<>(prefix);
                inputs.add(new GlobalCommand(type));
                Fixture fixture = fixture(simpleCatalog(), null, inputs);
                byte[] original = Files.readAllBytes(fixture.paths().catalog());
                assertEquals(expected, fixture.application().run());
                assertArrayEquals(original, Files.readAllBytes(fixture.paths().catalog()));
            }
        }
    }

    @Test
    void run_outputFailureAtEveryRenderedBlock_terminatesAndNeverRevertsDurableData()
            throws Exception {
        List<String[]> flows = List.of(
                new String[] {"bad", "0"},
                new String[] {"1", "\tbad", "\u2003", "Title", "4", "1", "bad", "Y", "0"},
                new String[] {"2", "9", "1", "bad", "1", "First", "2", "1", "3", "0"},
                new String[] {"2", "1", "1", "Changed", "3", "bad", "N", "0"},
                new String[] {"2", "1", "2", "2", "3", "Y", "0"},
                new String[] {"3", "9", "1", "bad", "N", "0"},
                new String[] {"3", "1", "Y", "0"});
        for (String[] flow : flows) {
            Fixture baseline = fixture(simpleCatalog(), "CINECLI-SEATS\t1\n", lines(flow));
            baseline.application().run();
            int writeCount = baseline.terminal().writeCount();
            for (int failedWrite = 1; failedWrite <= writeCount; failedWrite++) {
                Fixture fixture = fixture(simpleCatalog(), "CINECLI-SEATS\t1\n", lines(flow));
                fixture.terminal().failWriteNumber(failedWrite);
                assertEquals(MovieManagementOutcome.TERMINATED, fixture.application().run());
                assertEquals("Unable to write output. CineCLI will exit.\n",
                        fixture.terminal().errors());
            }
        }
    }

    @Test
    void run_childlessDelete_showsNoCascadeAndLeavesMalformedSeatsUntouched() throws Exception {
        String catalog = """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-1\tFirst\tPG13
                """;
        Fixture fixture = fixture(catalog, "malformed\n", lines("3", "1", "Y", "0"));
        byte[] originalSeats = Files.readAllBytes(fixture.paths().seats());

        assertEquals(MovieManagementOutcome.BACK, fixture.application().run());

        assertAll(
                () -> assertTrue(fixture.terminal().output().contains(
                        "This movie has no screenings or occupied seats.")),
                () -> assertArrayEquals(originalSeats, Files.readAllBytes(fixture.paths().seats())),
                () -> assertEquals("CINECLI-CATALOG\t1\n",
                        Files.readString(fixture.paths().catalog(), UTF_8)));
    }

    @Test
    void run_accessAndPreparationFailures_reportExactPrefixesAndReturnBack() throws Exception {
        Fixture malformedCatalog = fixture("malformed\n", null, lines("0"));
        assertEquals(MovieManagementOutcome.BACK, malformedCatalog.application().run());
        assertTrue(malformedCatalog.terminal().output().startsWith(
                "Unable to access movie management: "));

        Fixture malformedJournal = fixture(simpleCatalog(), null, lines("0"));
        Files.writeString(malformedJournal.paths().journal(), "malformed\n", UTF_8);
        assertEquals(MovieManagementOutcome.BACK, malformedJournal.application().run());
        assertTrue(malformedJournal.terminal().output().startsWith(
                "Unable to access movie management: "));

        Fixture malformedSeats = fixture(simpleCatalog(), "malformed\n", lines("3", "1"));
        assertEquals(MovieManagementOutcome.BACK, malformedSeats.application().run());
        assertTrue(malformedSeats.terminal().output().contains(
                "Unable to prepare movie deletion: "));
    }

    @Test
    void run_publicConstructorAndLocalTitleCancel_coverProductionAdapters() throws Exception {
        Fixture fixture = fixture("CINECLI-CATALOG\t1\n", null, lines("1", "/cancel", "0"));
        MovieManagementApplication application = new MovieManagementApplication(
                new CatalogStorage(fixture.paths().catalog(), DEFAULT_RESOURCE),
                transactionFor(fixture.paths()),
                fixture.terminal());

        assertEquals(MovieManagementOutcome.BACK, application.run());
        assertTrue(fixture.terminal().output().contains("Movie addition cancelled."));
    }

    @Test
    void run_editFieldCancelRatingOnlyPreviewAndEmptyMessageFailure_coverAlternatives()
            throws Exception {
        Fixture titleCancel = fixture(simpleCatalog(), null, lines("2", "1", "1", "/cancel", "0"));
        assertEquals(MovieManagementOutcome.BACK, titleCancel.application().run());

        Fixture ratingOnly = fixture(simpleCatalog(), null, lines("2", "1", "2", "2", "3", "N", "0"));
        assertEquals(MovieManagementOutcome.BACK, ratingOnly.application().run());
        assertTrue(ratingOnly.terminal().output().contains("Title: First (unchanged)"));

        Fixture emptyFailure = fixture("CINECLI-CATALOG\t1\n", null, lines("2"));
        emptyFailure.terminal().failWriteNumber(2);
        assertEquals(MovieManagementOutcome.TERMINATED, emptyFailure.application().run());
    }

    private int occurrences(String text, String fragment) {
        return text.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
    }

    private Fixture fixture(String catalog, String seats, List<TerminalInput> inputs, UUID... uuids)
            throws Exception {
        Path runtime = tempDirectory.resolve(UUID.randomUUID().toString());
        Files.createDirectories(runtime);
        Paths paths = new Paths(
                runtime.resolve("catalog.tsv"),
                runtime.resolve("seats.tsv"),
                runtime.resolve("catalog-transaction.journal"));
        Files.writeString(paths.catalog(), catalog, UTF_8);
        if (seats != null) {
            Files.writeString(paths.seats(), seats, UTF_8);
        }
        CatalogStorage catalogStorage = new CatalogStorage(paths.catalog(), DEFAULT_RESOURCE);
        SeatStorage seatStorage = new SeatStorage(paths.seats());
        MovieDeletionTransaction transaction = new MovieDeletionTransaction(
                new CatalogTransactionAdapter(catalogStorage, paths.catalog()),
                new SeatTransactionAdapter(seatStorage, paths.seats()),
                paths.journal());
        ScriptedTerminal terminal = new ScriptedTerminal(inputs);
        Deque<UUID> generatedIds = new ArrayDeque<>(Arrays.asList(uuids));
        MovieIdGenerator generator = generatedIds.isEmpty() ? () -> SECOND_UUID : generatedIds::removeFirst;
        MovieManagementApplication application = new MovieManagementApplication(
                catalogStorage, transaction, terminal, generator);
        return new Fixture(application, terminal, paths);
    }

    private MovieDeletionTransaction transactionFor(Paths paths) {
        CatalogStorage catalogStorage = new CatalogStorage(paths.catalog(), DEFAULT_RESOURCE);
        SeatStorage seatStorage = new SeatStorage(paths.seats());
        return new MovieDeletionTransaction(
                new CatalogTransactionAdapter(catalogStorage, paths.catalog()),
                new SeatTransactionAdapter(seatStorage, paths.seats()),
                paths.journal());
    }

    private List<TerminalInput> lines(String... values) {
        return Arrays.stream(values).map(SubmittedLine::new).map(TerminalInput.class::cast).toList();
    }

    private String simpleCatalog() {
        return """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-1\tFirst\tPG13
                SCREENING\tSCR-1\tMOV-1\t2027-01-02\t21:30
                """;
    }

    private static final class ScriptedTerminal implements MovieManagementTerminal {
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
            MovieManagementApplication application, ScriptedTerminal terminal, Paths paths) {
    }
}
