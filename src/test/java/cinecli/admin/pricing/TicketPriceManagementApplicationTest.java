package cinecli.admin.pricing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.admin.AdminWorkflowOutcome;
import cinecli.admin.ui.AdminTerminal;
import cinecli.admin.ui.EndOfInput;
import cinecli.admin.ui.GlobalCommand;
import cinecli.admin.ui.InputFailure;
import cinecli.admin.ui.SubmittedLine;
import cinecli.admin.ui.TerminalInput;
import cinecli.model.TicketType;
import cinecli.storage.pricing.PricingStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TicketPriceManagementApplicationTest {
    @TempDir
    Path tempDirectory;

    @Test
    void run_listsFixedTicketsAndEditsMinimumAndMaximumPrices() throws Exception {
        Fixture fixture = fixture(lines("1", "1", "0.01", "Y", "1", "3", "9999.99", "Y", "0"));

        assertEquals(AdminWorkflowOutcome.BACK, fixture.application().run());

        assertAll(
                () -> assertTrue(fixture.terminal().output().contains("1. Adult - S$11.00")),
                () -> assertTrue(fixture.terminal().output().contains("2. Senior - S$4.50")),
                () -> assertTrue(fixture.terminal().output().contains("3. Student - S$7.00")),
                () -> assertTrue(fixture.terminal().output().contains("S$11.00 -> S$0.01")),
                () -> assertEquals(1, pricing(fixture.path()).ticketPriceInCents(TicketType.ADULT)),
                () -> assertEquals(999_999,
                        pricing(fixture.path()).ticketPriceInCents(TicketType.STUDENT)));
    }

    @Test
    void run_retriesInvalidExactMoneyAndNeverUsesFloatingPointRounding() throws Exception {
        Fixture fixture = fixture(lines(
                "1", "1", "0.009", "0.00", "10000.00", "1.0", "10.01", "Y", "0"));

        assertEquals(AdminWorkflowOutcome.BACK, fixture.application().run());

        assertAll(
                () -> assertEquals(4, occurrences(fixture.terminal().output(),
                        "Enter a price from S$0.01 through S$9,999.99 with exactly two decimal places.")),
                () -> assertEquals(1001, pricing(fixture.path()).ticketPriceInCents(TicketType.ADULT)));
    }

    @Test
    void run_cancellationAndUnchangedPricePreserveOriginalBytes() throws Exception {
        for (List<TerminalInput> inputs : List.of(
                lines("1", "0", "0"),
                lines("1", "/cancel", "0"),
                lines("1", "1", "/cancel", "0"),
                lines("1", "1", "12.00", "N", "0"),
                lines("1", "1", "11.00", "0"))) {
            Fixture fixture = fixture(inputs);
            byte[] original = Files.readAllBytes(fixture.path());

            assertEquals(AdminWorkflowOutcome.BACK, fixture.application().run());

            assertArrayEquals(original, Files.readAllBytes(fixture.path()));
        }
    }

    @Test
    void run_propagatesGlobalCommandsAndTerminalFailuresWithoutSaving() throws Exception {
        for (GlobalCommand.Type type : GlobalCommand.Type.values()) {
            Fixture fixture = fixture(List.of(new SubmittedLine("1"), new SubmittedLine("1"),
                    new GlobalCommand(type)));
            byte[] original = Files.readAllBytes(fixture.path());

            assertEquals(AdminWorkflowOutcome.valueOf(type.name()), fixture.application().run());
            assertArrayEquals(original, Files.readAllBytes(fixture.path()));
        }
        Fixture inputFailure = fixture(List.of(new InputFailure()));
        Fixture outputFailure = fixture(lines("0"));
        outputFailure.terminal().failWriteNumber(1);

        assertAll(
                () -> assertEquals(AdminWorkflowOutcome.TERMINATED, inputFailure.application().run()),
                () -> assertEquals("Unable to read input. CineCLI will exit.\n",
                        inputFailure.terminal().errors()),
                () -> assertEquals(AdminWorkflowOutcome.TERMINATED, outputFailure.application().run()),
                () -> assertEquals("Unable to write output. CineCLI will exit.\n",
                        outputFailure.terminal().errors()));
    }

    @Test
    void run_coversInteractionAlternativesAndOutputFailures() throws Exception {
        for (GlobalCommand.Type type : GlobalCommand.Type.values()) {
            for (List<TerminalInput> prefix : List.of(
                    List.<TerminalInput>of(), lines("1"), lines("1", "1"),
                    lines("1", "1", "12.00"))) {
                List<TerminalInput> inputs = new ArrayList<>(prefix);
                inputs.add(new GlobalCommand(type));
                Fixture fixture = fixture(inputs);
                assertEquals(AdminWorkflowOutcome.valueOf(type.name()), fixture.application().run());
            }
        }
        List<String[]> flows = List.of(
                new String[] {"invalid", "0"},
                new String[] {"1", "invalid", "0", "0"},
                new String[] {"1", "1", "invalid", "/cancel", "0"},
                new String[] {"1", "1", "12.00", "invalid", "N", "0"},
                new String[] {"1", "0", "0"},
                new String[] {"1", "1", "11.00", "0"},
                new String[] {"1", "1", "12.00", "Y", "0"});
        for (String[] flow : flows) {
            Fixture baseline = fixture(lines(flow));
            baseline.application().run();
            for (int failedWrite = 1; failedWrite <= baseline.terminal().writeCount(); failedWrite++) {
                Fixture fixture = fixture(lines(flow));
                fixture.terminal().failWriteNumber(failedWrite);
                assertEquals(AdminWorkflowOutcome.TERMINATED, fixture.application().run());
            }
        }
    }

    @Test
    void run_reportsAccessAndSaveFailuresTruthfully() throws Exception {
        Path malformedPath = tempDirectory.resolve("malformed-ticket.tsv");
        Files.writeString(malformedPath, "malformed\n");
        ScriptedTerminal malformedTerminal = new ScriptedTerminal(lines("0"));
        TicketPriceManagementApplication malformed = new TicketPriceManagementApplication(
                new PricingStorage(malformedPath), malformedTerminal);
        ScriptedTerminal malformedOutputFailure = new ScriptedTerminal(lines("0"));
        malformedOutputFailure.failWriteNumber(1);
        TicketPriceManagementApplication malformedWithOutputFailure = new TicketPriceManagementApplication(
                new PricingStorage(malformedPath), malformedOutputFailure);

        Fixture failedSave = fixture(lines("1", "1", "12.00", "Y"));
        failedSave.terminal().runBeforeReadNumber(4, () -> replaceParentWithFile(failedSave.path()));

        assertAll(
                () -> assertEquals(AdminWorkflowOutcome.BACK, malformed.run()),
                () -> assertTrue(malformedTerminal.output().startsWith(
                        "Unable to access ticket price management: ")),
                () -> assertEquals(AdminWorkflowOutcome.TERMINATED, malformedWithOutputFailure.run()),
                () -> assertEquals(AdminWorkflowOutcome.BACK, failedSave.application().run()),
                () -> assertTrue(failedSave.terminal().output().contains(
                        "Ticket price change was not saved: ")));
    }

    private Fixture fixture(List<TerminalInput> inputs) throws Exception {
        Path path = tempDirectory.resolve("ticket-" + java.util.UUID.randomUUID())
                .resolve("pricing.tsv");
        PricingStorage storage = new PricingStorage(path);
        storage.load();
        ScriptedTerminal terminal = new ScriptedTerminal(inputs);
        return new Fixture(new TicketPriceManagementApplication(storage, terminal), terminal, path);
    }

    private cinecli.model.Pricing pricing(Path path) throws Exception {
        return new PricingStorage(path).load();
    }

    private List<TerminalInput> lines(String... values) {
        return Arrays.stream(values).map(SubmittedLine::new).map(TerminalInput.class::cast).toList();
    }

    private int occurrences(String text, String fragment) {
        return text.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
    }

    private void replaceParentWithFile(Path pricingPath) {
        try {
            Files.delete(pricingPath);
            Files.delete(pricingPath.getParent());
            Files.writeString(pricingPath.getParent(), "blocked");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class ScriptedTerminal implements AdminTerminal {
        private final Deque<TerminalInput> inputs;
        private final List<String> outputs = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private int writeCount;
        private int readCount;
        private int failedWriteNumber = -1;
        private int hookedReadNumber = -1;
        private Runnable beforeRead;

        private ScriptedTerminal(List<TerminalInput> inputs) {
            this.inputs = new ArrayDeque<>(inputs);
        }

        @Override
        public TerminalInput readLine() {
            readCount++;
            if (readCount == hookedReadNumber) {
                beforeRead.run();
            }
            return inputs.isEmpty() ? new EndOfInput() : inputs.removeFirst();
        }

        @Override
        public boolean write(String output) {
            writeCount++;
            if (writeCount == failedWriteNumber) {
                return false;
            }
            outputs.add(output);
            return true;
        }

        @Override
        public boolean writeError(String output) {
            errors.add(output);
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

        private void runBeforeReadNumber(int readNumber, Runnable action) {
            hookedReadNumber = readNumber;
            beforeRead = action;
        }
    }

    private record Fixture(
            TicketPriceManagementApplication application, ScriptedTerminal terminal, Path path) {
    }
}
