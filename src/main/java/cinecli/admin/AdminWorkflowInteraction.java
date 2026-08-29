package cinecli.admin;

import cinecli.admin.ui.AdminTerminal;
import cinecli.admin.ui.GlobalCommand;
import cinecli.admin.ui.InputFailure;
import cinecli.admin.ui.SubmittedLine;
import cinecli.admin.ui.TerminalInput;
import java.util.Objects;

/**
 * Owns the common fallible terminal interaction rules for administrator workflows.
 */
final class AdminWorkflowInteraction {
    private static final String OUTPUT_FAILURE = "Unable to write output. CineCLI will exit.\n";
    private static final String INPUT_FAILURE = "Unable to read input. CineCLI will exit.\n";

    private final AdminTerminal terminal;

    AdminWorkflowInteraction(AdminTerminal terminal) {
        this.terminal = Objects.requireNonNull(terminal);
    }

    AdminWorkflowInput read() {
        TerminalInput input = terminal.readLine();
        if (input instanceof SubmittedLine(String value)) {
            return new AdminWorkflowInput(value, null);
        }
        if (input instanceof GlobalCommand(GlobalCommand.Type type)) {
            AdminWorkflowOutcome outcome = switch (type) {
                case ADMIN -> AdminWorkflowOutcome.ADMIN;
                case CUSTOMER -> AdminWorkflowOutcome.CUSTOMER;
                case EXIT -> AdminWorkflowOutcome.EXIT;
            };
            return new AdminWorkflowInput(null, outcome);
        }
        if (input instanceof InputFailure) {
            terminal.writeError(INPUT_FAILURE);
        }
        return new AdminWorkflowInput(null, AdminWorkflowOutcome.TERMINATED);
    }

    boolean write(String text) {
        if (terminal.write(text)) {
            return true;
        }
        terminal.writeError(OUTPUT_FAILURE);
        return false;
    }
}

/** Carries one submitted administrator line or a typed workflow outcome. */
record AdminWorkflowInput(String line, AdminWorkflowOutcome outcome) {
}
