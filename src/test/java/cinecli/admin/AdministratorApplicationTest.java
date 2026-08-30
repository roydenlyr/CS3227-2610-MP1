package cinecli.admin;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.admin.ui.AdminTerminal;
import cinecli.admin.ui.EndOfInput;
import cinecli.admin.ui.GlobalCommand;
import cinecli.admin.ui.InputFailure;
import cinecli.admin.ui.SubmittedLine;
import cinecli.admin.ui.TerminalInput;
import cinecli.storage.catalog.CatalogStorage;
import cinecli.storage.catalog.CatalogTransactionAdapter;
import cinecli.storage.pricing.PricingStorage;
import cinecli.storage.seat.SeatStorage;
import cinecli.storage.seat.SeatTransactionAdapter;
import cinecli.storage.transaction.MovieDeletionTransaction;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdministratorApplicationTest {
    private static final String DEFAULT_CATALOG_RESOURCE = "/cinecli/default-catalog.tsv";

    @TempDir
    Path tempDirectory;

    @Test
    void run_homepageChoices_delegateAndReturnToHomepage() {
        ScriptedTerminal terminal = new ScriptedTerminal(List.of(
                new SubmittedLine("1"),
                new SubmittedLine("0"),
                new SubmittedLine("2"),
                new SubmittedLine("0"),
                new SubmittedLine("3"),
                new SubmittedLine("0"),
                global(GlobalCommand.Type.EXIT)));

        AdminWorkflowOutcome outcome = application(terminal).run();

        assertAll(
                () -> assertEquals(AdminWorkflowOutcome.EXIT, outcome),
                () -> assertTrue(terminal.output().contains("Administrator Home")),
                () -> assertTrue(terminal.output().contains("Movie Management")),
                () -> assertTrue(terminal.output().contains("Screening Management")),
                () -> assertTrue(terminal.output().contains("Pricing and Promotions Management")));
    }

    @Test
    void run_zero_returnsCustomer() {
        ScriptedTerminal terminal = new ScriptedTerminal(lines("0"));

        assertEquals(AdminWorkflowOutcome.CUSTOMER, application(terminal).run());
    }

    @Test
    void run_globalCommandsAndTerminalInputFailures_returnTypedOutcomes() {
        for (TerminalInput input : List.of(
                new GlobalCommand(GlobalCommand.Type.ADMIN),
                new GlobalCommand(GlobalCommand.Type.CUSTOMER),
                new GlobalCommand(GlobalCommand.Type.EXIT),
                new EndOfInput(),
                new InputFailure())) {
            ScriptedTerminal terminal = new ScriptedTerminal(List.of(input));

            AdminWorkflowOutcome outcome = application(terminal).run();

            if (input instanceof GlobalCommand(GlobalCommand.Type type)) {
                AdminWorkflowOutcome expected = switch (type) {
                    case ADMIN -> AdminWorkflowOutcome.ADMIN;
                    case CUSTOMER -> AdminWorkflowOutcome.CUSTOMER;
                    case EXIT -> AdminWorkflowOutcome.EXIT;
                };
                assertEquals(expected, outcome);
            } else {
                assertEquals(AdminWorkflowOutcome.TERMINATED, outcome);
            }
        }
    }

    @Test
    void run_invalidChoiceAndChildGlobalCommand_reportOrPropagate() {
        ScriptedTerminal invalidTerminal = new ScriptedTerminal(List.of(
                new SubmittedLine("9"), global(GlobalCommand.Type.EXIT)));
        ScriptedTerminal childTerminal = new ScriptedTerminal(List.of(
                new SubmittedLine("1"), global(GlobalCommand.Type.CUSTOMER)));

        assertAll(
                () -> assertEquals(AdminWorkflowOutcome.EXIT, application(invalidTerminal).run()),
                () -> assertTrue(invalidTerminal.output().contains("Enter 0, 1, 2, or 3.")),
                () -> assertEquals(AdminWorkflowOutcome.CUSTOMER, application(childTerminal).run()));
    }

    @Test
    void run_outputFailure_terminates() {
        ScriptedTerminal terminal = new ScriptedTerminal(List.of(), false);

        assertEquals(AdminWorkflowOutcome.TERMINATED, application(terminal).run());
    }

    @Test
    void run_invalidChoiceWhenErrorCannotBeWritten_terminates() {
        ScriptedTerminal terminal = new ScriptedTerminal(List.of(new SubmittedLine("9")), true) {
            @Override
            public boolean write(String text) {
                return !text.contains("Enter 0, 1, 2, or 3.");
            }
        };

        assertEquals(AdminWorkflowOutcome.TERMINATED, application(terminal).run());
    }

    private AdministratorApplication application(AdminTerminal terminal) {
        Path catalog = tempDirectory.resolve("catalog.tsv");
        Path seats = tempDirectory.resolve("seats.tsv");
        CatalogStorage catalogStorage = new CatalogStorage(catalog, DEFAULT_CATALOG_RESOURCE);
        SeatStorage seatStorage = new SeatStorage(seats);
        MovieDeletionTransaction deletionTransaction = new MovieDeletionTransaction(
                new CatalogTransactionAdapter(catalogStorage, catalog),
                new SeatTransactionAdapter(seatStorage, seats),
                tempDirectory.resolve("catalog-transaction.journal"));
        return new AdministratorApplication(
                new MovieManagementApplication(catalogStorage, deletionTransaction, terminal),
                new ScreeningManagementApplication(catalogStorage, deletionTransaction, terminal),
                new PricingManagementApplication(new PricingStorage(tempDirectory.resolve("pricing.tsv")), terminal),
                terminal);
    }

    private List<TerminalInput> lines(String... values) {
        return java.util.Arrays.stream(values).map(SubmittedLine::new).map(TerminalInput.class::cast).toList();
    }

    private GlobalCommand global(GlobalCommand.Type type) {
        return new GlobalCommand(type);
    }

    private static class ScriptedTerminal implements AdminTerminal {
        private final Deque<TerminalInput> inputs;
        private final StringBuilder output = new StringBuilder();
        private final boolean isWritable;

        private ScriptedTerminal(List<TerminalInput> inputs) {
            this(inputs, true);
        }

        private ScriptedTerminal(List<TerminalInput> inputs, boolean isWritable) {
            this.inputs = new ArrayDeque<>(inputs);
            this.isWritable = isWritable;
        }

        @Override
        public TerminalInput readLine() {
            return inputs.isEmpty() ? new EndOfInput() : inputs.removeFirst();
        }

        @Override
        public boolean write(String text) {
            output.append(text);
            return isWritable;
        }

        @Override
        public boolean writeError(String text) {
            output.append(text);
            return isWritable;
        }

        private String output() {
            return output.toString();
        }
    }
}
