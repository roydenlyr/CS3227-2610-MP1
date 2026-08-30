package cinecli.admin.pricing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.admin.AdminWorkflowOutcome;
import cinecli.admin.ui.AdminTerminal;
import cinecli.admin.ui.EndOfInput;
import cinecli.admin.ui.GlobalCommand;
import cinecli.admin.ui.SubmittedLine;
import cinecli.admin.ui.TerminalInput;
import cinecli.storage.pricing.PricingStorage;
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

class PricingManagementApplicationTest {
    @TempDir
    Path tempDirectory;

    @Test
    void run_listsAllCurrentPricingAndVisitsEachChildWorkflow() throws Exception {
        Fixture fixture = fixture(lines("1", "0", "2", "0", "3", "0", "0"));

        assertEquals(AdminWorkflowOutcome.BACK, fixture.application().run());

        String output = fixture.terminal().output();
        assertAll(
                () -> assertTrue(output.contains("Pricing and Promotions Management")),
                () -> assertTrue(output.contains("Adult - S$11.00")),
                () -> assertTrue(output.contains("Popcorn Combo (Popcorn + Soft Drink) - S$7.00")),
                () -> assertTrue(output.contains("CS2103 - 20% off")),
                () -> assertTrue(output.contains("Ticket Price Management")),
                () -> assertTrue(output.contains("Snack and Combo Price Management")),
                () -> assertTrue(output.contains("Promotion Management")));
    }

    @Test
    void run_retriesInvalidChoiceAndPropagatesChildGlobalCommand() throws Exception {
        Fixture fixture = fixture(List.of(
                new SubmittedLine("invalid"),
                new SubmittedLine("1"),
                new GlobalCommand(GlobalCommand.Type.CUSTOMER)));

        assertEquals(AdminWorkflowOutcome.CUSTOMER, fixture.application().run());

        assertTrue(fixture.terminal().output().contains("Enter 0, 1, 2, or 3."));
    }

    @Test
    void run_accessFailureReturnsBackAndOutputFailureTerminates() throws Exception {
        Path malformedPath = tempDirectory.resolve("malformed/pricing.tsv");
        Files.createDirectories(malformedPath.getParent());
        Files.writeString(malformedPath, "malformed\n");
        ScriptedTerminal malformedTerminal = new ScriptedTerminal(lines("0"));
        PricingManagementApplication malformed = new PricingManagementApplication(
                new PricingStorage(malformedPath), malformedTerminal);

        Fixture outputFailure = fixture(lines("0"));
        outputFailure.terminal().failWriteNumber(1);

        assertAll(
                () -> assertEquals(AdminWorkflowOutcome.BACK, malformed.run()),
                () -> assertTrue(malformedTerminal.output().startsWith(
                        "Unable to access pricing management: ")),
                () -> assertEquals(AdminWorkflowOutcome.TERMINATED,
                        outputFailure.application().run()),
                () -> assertEquals("Unable to write output. CineCLI will exit.\n",
                        outputFailure.terminal().errors()));
    }

    @Test
    void run_handlesTerminalOutcomesAndAllPricingHomeAlternatives() throws Exception {
        Fixture inputFailure = fixture(List.of(new cinecli.admin.ui.InputFailure()));
        Fixture invalidOutputFailure = fixture(lines("invalid"));
        invalidOutputFailure.terminal().failWriteNumber(2);

        Path emptyPricingPath = tempDirectory.resolve("empty/pricing.tsv");
        PricingStorage emptyStorage = new PricingStorage(emptyPricingPath);
        emptyStorage.save(PricingReplacements.withPromotions(cinecli.model.Pricing.defaults(), List.of()));
        ScriptedTerminal emptyTerminal = new ScriptedTerminal(lines("0"));
        PricingManagementApplication empty = new PricingManagementApplication(emptyStorage, emptyTerminal);

        Path malformedPricingPath = tempDirectory.resolve("malformed-output/pricing.tsv");
        Files.createDirectories(malformedPricingPath.getParent());
        Files.writeString(malformedPricingPath, "malformed\n");
        ScriptedTerminal malformedTerminal = new ScriptedTerminal(lines("0"));
        malformedTerminal.failWriteNumber(1);
        PricingManagementApplication malformed = new PricingManagementApplication(
                new PricingStorage(malformedPricingPath), malformedTerminal);

        assertAll(
                () -> assertEquals(AdminWorkflowOutcome.TERMINATED, inputFailure.application().run()),
                () -> assertEquals(AdminWorkflowOutcome.TERMINATED,
                        invalidOutputFailure.application().run()),
                () -> assertEquals(AdminWorkflowOutcome.BACK, empty.run()),
                () -> assertTrue(emptyTerminal.output().contains(
                        "No promotions are currently available.")),
                () -> assertEquals(AdminWorkflowOutcome.TERMINATED, malformed.run()));
    }

    private Fixture fixture(List<TerminalInput> inputs) throws Exception {
        Path pricingPath = tempDirectory.resolve(UUID.randomUUID().toString()).resolve("pricing.tsv");
        PricingStorage storage = new PricingStorage(pricingPath);
        storage.load();
        ScriptedTerminal terminal = new ScriptedTerminal(inputs);
        return new Fixture(new PricingManagementApplication(storage, terminal), terminal);
    }

    private List<TerminalInput> lines(String... values) {
        return Arrays.stream(values).map(SubmittedLine::new).map(TerminalInput.class::cast).toList();
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
    }

    private record Fixture(PricingManagementApplication application, ScriptedTerminal terminal) {
    }
}
